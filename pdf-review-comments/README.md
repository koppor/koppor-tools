# pdf-review-comments

Extract olive-highlighted text blocks and their review comments from a PDF and render them
as markdown.

When reviewing a paper, you mark text blocks with an **olive** highlight and attach a comment
to each. This tool reads those PDF *Highlight* annotations, pulls the underlying text and the
comment, and produces a "Detailed Comments on the Text" section.

## Usage

```console
jbang PdfReviewComments.java [options] <input.pdf>
```

| Option | Default | Description |
| --- | --- | --- |
| `-c`, `--color <RRGGBB>` | `808000` (olive) | Highlight color to match, as a hex triplet. |
| `-t`, `--tolerance <n>` | `0.15` | Per-channel color match tolerance, `0..1`. Different PDF tools store slightly different shades. |
| `-o`, `--out <file>` | `<input>.md` | Output file. By default a sibling `.md` next to the PDF. |
| `-h`, `--help` | | Show help. |

## Example

```console
jbang PdfReviewComments.java main.pdf
```

produces `main.md`:

```markdown
## Detailed Comments on the Text

Page: 1: "Systematic literature reviews (SLRs) are comprehensive examinations of existing research on a particular topic, conducted according to a well-defined and transparent methodology [2]."

Maybe use other reference

---
```

## How it works

- Reads every PDF `Highlight` text-markup annotation on each page.
- Keeps the ones whose annotation color matches the target (olive) within the tolerance,
  converting any color space (RGB, gray, CMYK) to RGB first.
- Extracts the text under each highlight's `QuadPoints` with PDFBox's
  `PDFTextStripperByArea` (a highlight spanning several lines yields several quads, which are
  concatenated in reading order).
- Rejoins words split by a line-wrap hyphen (`comprehen- sive` → `comprehensive`) while
  leaving real compounds such as `well-defined` intact.
- Pairs that text with the annotation's comment (`/Contents`).
- Emits findings ordered by page, then top-to-bottom on the page.

## Limitations

- Only PDF *Highlight* annotations are read — text drawn in an olive font (with no annotation)
  is not detected.
- Text extraction assumes unrotated pages.
- The comment is read from the highlight's own `/Contents`; comments stored only on a separate
  reply annotation are not followed.

## Dependencies

Declared inline via jbang directives — [Apache PDFBox](https://pdfbox.apache.org/) for PDF
parsing and [aesh](https://github.com/aeshell/aesh) for the CLI.
