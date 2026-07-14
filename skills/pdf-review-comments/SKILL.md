---
name: pdf-review-comments
description: Extract olive-highlighted text blocks and their review comments from a reviewed PDF into a "Detailed Comments on the Text" markdown section. Use when the user reviewed a paper by highlighting text in olive and attaching comments, and wants those highlights plus comments pulled out of the PDF.
allowed-tools: Bash(jbang *)
---

# PDF Review Comments

Extract olive-highlighted text and its review comments from a PDF and render them as markdown.

## Prerequisite

[jbang](https://www.jbang.dev/) must be installed (`jbang --version`). It fetches the required
JDK and dependencies (Apache PDFBox, aesh) automatically on first run.

## How to run

Run the bundled jbang script against the user's PDF:

```bash
jbang "${CLAUDE_SKILL_DIR}/PdfReviewComments.java" "<path/to/input.pdf>"
```

By default this writes `<input>.md` next to the PDF and prints how many comments were found.

Options (pass after the script, before or around the PDF path):

- `-o, --out <file>` — write markdown to a specific file instead of the sibling `.md`.
- `-c, --color <RRGGBB>` — match a different highlight color (default olive `808000`).
- `-t, --tolerance <n>` — per-channel color match tolerance `0..1` (default `0.15`).
- `-h, --help` — show usage.

Example — capture to stdout for inline display instead of a sibling file:

```bash
jbang "${CLAUDE_SKILL_DIR}/PdfReviewComments.java" -o /dev/stdout "paper.pdf"
```

## After running

Report where the markdown was written and, if helpful, show the extracted findings. Each finding
is formatted as:

```markdown
## Detailed Comments on the Text

Page: 1: "the highlighted text"

the reviewer comment

---
```

## Notes

- Only PDF *Highlight* annotations are read; olive-colored font text (no annotation) is ignored.
- Words split by a line-wrap hyphen are rejoined (`compre- hensive` → `comprehensive`); real
  compounds like `well-defined` are kept.
- See [README.md](README.md) for full behavior and limitations.
