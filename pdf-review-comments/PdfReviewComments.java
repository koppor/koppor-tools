///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS org.apache.pdfbox:pdfbox:3.0.5
//DEPS org.aesh:aesh:2.7

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.registry.CommandRegistry;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.CommandRuntime;
import org.aesh.command.AeshCommandRuntimeBuilder;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.apache.pdfbox.text.PDFTextStripperByArea;

/**
 * Extracts every olive-highlighted text block and its review comment from a PDF
 * and renders them as markdown ("Detailed Comments on the Text").
 *
 * <p>The tool reads PDF <em>Highlight</em> text-markup annotations, keeps the ones
 * whose color matches (olive by default), extracts the underlying page text from
 * each highlight's QuadPoints, and pairs it with the annotation's comment
 * ({@code /Contents}).</p>
 */
@CommandDefinition(
        name = "pdf-review-comments",
        description = "Extract olive-highlighted text and its comments from a PDF into markdown.")
public class PdfReviewComments implements Command<CommandInvocation> {

    @Option(shortName = 'c', name = "color", defaultValue = "808000",
            description = "Highlight color to match, hex RRGGBB (default olive 808000)")
    private String color;

    @Option(shortName = 't', name = "tolerance", defaultValue = "0.15",
            description = "Per-channel color match tolerance in 0..1 (default 0.15)")
    private double tolerance;

    @Option(shortName = 'o', name = "out",
            description = "Output markdown file (default: <input>.md next to the PDF)")
    private String out;

    @Option(shortName = 'h', name = "help", hasValue = false, overrideRequired = true,
            description = "Show this help and exit")
    private boolean help;

    @Argument(description = "Input PDF file", required = true)
    private String input;

    // ---- CLI wiring (aesh) -------------------------------------------------

    public static void main(String[] args) throws Exception {
        CommandRegistry<CommandInvocation> registry =
                AeshCommandRegistryBuilder.<CommandInvocation>builder()
                        .command(PdfReviewComments.class)
                        .create();
        CommandRuntime<CommandInvocation> runtime =
                AeshCommandRuntimeBuilder.<CommandInvocation>builder()
                        .commandRegistry(registry)
                        .build();
        try {
            runtime.executeCommand("pdf-review-comments " + toCommandLine(args));
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            System.exit(2);
        }
    }

    /** Joins raw OS arguments into one aesh command line, quoting each so that
     *  spaces and Windows backslashes survive the parser. */
    private static String toCommandLine(String[] args) {
        StringBuilder line = new StringBuilder();
        for (String arg : args) {
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append('\'').append(arg.replace("'", "'\\''")).append('\'');
        }
        return line.toString();
    }

    @Override
    public CommandResult execute(CommandInvocation ci) {
        if (help) {
            ci.getShell().writeln(usage());
            return CommandResult.SUCCESS;
        }
        try {
            Rgb target = Rgb.ofHex(color);
            Path in = Path.of(input);
            if (!Files.isRegularFile(in)) {
                ci.getShell().writeln("error: input file not found: " + in);
                return CommandResult.FAILURE;
            }
            List<Finding> findings = extract(in, target, tolerance);
            String markdown = render(findings);
            Path outPath = (out != null && !out.isBlank()) ? Path.of(out) : siblingMarkdown(in);
            Files.writeString(outPath, markdown);
            ci.getShell().writeln("Wrote " + findings.size() + " comment(s) to " + outPath);
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            ci.getShell().writeln("error: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }

    private static String usage() {
        return """
               Usage: pdf-review-comments [options] <input.pdf>

               Options:
                 -c, --color <RRGGBB>   Highlight color to match (default olive 808000)
                 -t, --tolerance <n>    Per-channel match tolerance 0..1 (default 0.15)
                 -o, --out <file>       Output markdown file (default <input>.md)
                 -h, --help             Show this help
               """;
    }

    // ---- Core extraction (pure PDFBox, no CLI dependency) ------------------

    static List<Finding> extract(Path pdf, Rgb target, double tolerance) throws IOException {
        List<Finding> findings = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            for (int pageIndex = 0; pageIndex < doc.getNumberOfPages(); pageIndex++) {
                PDPage page = doc.getPage(pageIndex);

                List<PDAnnotationTextMarkup> highlights = new ArrayList<>();
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (!(annotation instanceof PDAnnotationTextMarkup markup)) {
                        continue;
                    }
                    if (!"Highlight".equals(markup.getSubtype())) {
                        continue;
                    }
                    if (colorMatches(markup.getColor(), target, tolerance)) {
                        highlights.add(markup);
                    }
                }
                if (highlights.isEmpty()) {
                    continue;
                }

                // Register one text region per quad (a highlight may span several lines),
                // then extract them all in a single pass over the page.
                PDFTextStripperByArea stripper = new PDFTextStripperByArea();
                stripper.setSortByPosition(true);
                PDRectangle cropBox = page.getCropBox();
                for (int h = 0; h < highlights.size(); h++) {
                    float[] quads = highlights.get(h).getQuadPoints();
                    if (quads == null) {
                        continue;
                    }
                    for (int q = 0; q + 8 <= quads.length; q += 8) {
                        stripper.addRegion(regionName(h, q), quadRegion(quads, q, cropBox));
                    }
                }
                stripper.extractRegions(page);

                for (int h = 0; h < highlights.size(); h++) {
                    PDAnnotationTextMarkup markup = highlights.get(h);
                    float[] quads = markup.getQuadPoints();
                    if (quads == null) {
                        continue;
                    }
                    StringBuilder text = new StringBuilder();
                    for (int q = 0; q + 8 <= quads.length; q += 8) {
                        String regionText = stripper.getTextForRegion(regionName(h, q));
                        if (regionText != null) {
                            text.append(regionText).append(' ');
                        }
                    }
                    String markedText = dehyphenate(normalize(text.toString()));
                    String comment = normalize(markup.getContents());
                    if (markedText.isEmpty() && comment.isEmpty()) {
                        continue;
                    }
                    findings.add(new Finding(pageIndex + 1, topY(quads), markedText, comment));
                }
            }
        }
        findings.sort(Comparator.comparingInt((Finding f) -> f.page())
                .thenComparing(Comparator.comparingDouble((Finding f) -> f.topY()).reversed()));
        return findings;
    }

    private static String regionName(int highlightIndex, int quadOffset) {
        return "h" + highlightIndex + "_q" + quadOffset;
    }

    /**
     * Converts a highlight quad (8 floats, PDF user space, origin bottom-left)
     * into a text-stripper region rectangle (origin top-left of the crop box).
     */
    private static Rectangle2D quadRegion(float[] quads, int offset, PDRectangle cropBox) {
        float minX = min4(quads[offset], quads[offset + 2], quads[offset + 4], quads[offset + 6]);
        float maxX = max4(quads[offset], quads[offset + 2], quads[offset + 4], quads[offset + 6]);
        float minY = min4(quads[offset + 1], quads[offset + 3], quads[offset + 5], quads[offset + 7]);
        float maxY = max4(quads[offset + 1], quads[offset + 3], quads[offset + 5], quads[offset + 7]);

        float pad = 1f; // guard against off-by-a-hair clipping of the first/last glyph
        double x = minX - cropBox.getLowerLeftX() - pad;
        double y = cropBox.getHeight() - (maxY - cropBox.getLowerLeftY()) - pad;
        double w = (maxX - minX) + 2 * pad;
        double h = (maxY - minY) + 2 * pad;
        return new Rectangle2D.Double(x, y, w, h);
    }

    static boolean colorMatches(PDColor color, Rgb target, double tolerance) {
        if (color == null) {
            return false;
        }
        float[] rgb;
        try {
            rgb = color.getColorSpace().toRGB(color.getComponents());
        } catch (IOException e) {
            return false;
        }
        if (rgb.length < 3) {
            return false;
        }
        return Math.abs(rgb[0] - target.r()) <= tolerance
                && Math.abs(rgb[1] - target.g()) <= tolerance
                && Math.abs(rgb[2] - target.b()) <= tolerance;
    }

    // ---- Rendering ---------------------------------------------------------

    static String render(List<Finding> findings) {
        StringBuilder md = new StringBuilder("## Detailed Comments on the Text\n\n");
        for (Finding f : findings) {
            md.append("Page: ").append(f.page()).append(": \"").append(f.markedText()).append("\"\n\n");
            md.append(f.comment()).append("\n\n");
            md.append("---\n\n");
        }
        return md.toString();
    }

    // ---- Small helpers -----------------------------------------------------

    private static Path siblingMarkdown(Path pdf) {
        String name = pdf.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = (dot >= 0) ? name.substring(0, dot) : name;
        Path parent = pdf.toAbsolutePath().getParent();
        return parent.resolve(base + ".md");
    }

    /** Collapses all runs of whitespace (incl. newlines) to single spaces and trims. */
    static String normalize(String s) {
        return (s == null) ? "" : s.replaceAll("\\s+", " ").trim();
    }

    /**
     * Rejoins words split by a line-wrap hyphen, e.g. {@code "comprehen- sive"} to
     * {@code "comprehensive"}. Only a hyphen followed by whitespace between two letters is
     * removed, so genuine compounds ({@code "well-defined"}, which carry no space) are kept.
     */
    static String dehyphenate(String s) {
        return s.replaceAll("(\\p{L})-\\s+(\\p{L})", "$1$2");
    }

    private static float topY(float[] quads) {
        float top = quads[1];
        for (int i = 3; i < quads.length; i += 2) {
            top = Math.max(top, quads[i]);
        }
        return top;
    }

    private static float min4(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max4(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    // ---- Value types -------------------------------------------------------

    /** One highlighted text block and its comment. {@code topY} is used only for ordering. */
    record Finding(int page, float topY, String markedText, String comment) {}

    /** Normalized RGB color in 0..1. */
    record Rgb(float r, float g, float b) {
        static Rgb ofHex(String hex) {
            String h = hex.strip();
            if (h.startsWith("#")) {
                h = h.substring(1);
            }
            if (h.length() != 6) {
                throw new IllegalArgumentException("color must be 6 hex digits (RRGGBB): " + hex);
            }
            int rgb = Integer.parseInt(h, 16);
            return new Rgb(
                    ((rgb >> 16) & 0xFF) / 255f,
                    ((rgb >> 8) & 0xFF) / 255f,
                    (rgb & 0xFF) / 255f);
        }
    }
}
