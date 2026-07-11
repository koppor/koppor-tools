# koppor-tools

A small collection of personal command-line tools, each self-contained and runnable with
[jbang](https://www.jbang.dev/) — no build step, no install.

## Prerequisites

- [jbang](https://www.jbang.dev/download/) (which will fetch a JDK for you if needed)

## Tools

| Tool | Description |
| --- | --- |
| [`pdf-review-comments`](pdf-review-comments/) | Extract olive-highlighted text and its comments from a reviewed PDF into markdown. |

## Running

Each tool can be run directly by path:

```console
jbang pdf-review-comments/PdfReviewComments.java paper.pdf
```

or, once this repository is registered as a jbang catalog, by alias:

```console
jbang catalog add --name koppor https://github.com/koppor/koppor-tools/blob/main/jbang-catalog.json
jbang pdf-review-comments@koppor paper.pdf
```

## License

[MIT](LICENSE) © Oliver Kopp
