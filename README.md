# koppor-tools

A small collection of personal command-line tools, each self-contained and runnable with
[jbang](https://www.jbang.dev/) — no build step, no install. Each tool is also packaged as a
[Claude Agent Skill](https://docs.claude.com/en/docs/claude-code/skills) under [`skills/`](skills/).

## Prerequisites

- [jbang](https://www.jbang.dev/download/) (which will fetch a JDK for you if needed)

## Tools

| Tool | Description |
| --- | --- |
| [`pdf-review-comments`](skills/pdf-review-comments/) | Extract olive-highlighted text and its comments from a reviewed PDF into markdown. |

## Running directly

Each tool can be run by path:

```console
jbang skills/pdf-review-comments/PdfReviewComments.java paper.pdf
```

or, once this repository is registered as a jbang catalog, by alias:

```console
jbang catalog add --name koppor https://github.com/koppor/koppor-tools/blob/main/jbang-catalog.json
jbang pdf-review-comments@koppor paper.pdf
```

## Installing as a Claude skill

The tools double as Claude skills, installable with the [skills.sh](https://www.skills.sh/) CLI
(no publish step — it reads this GitHub repo directly):

```console
# into the current project (.claude/skills/)
npx skills add koppor/koppor-tools

# or globally (~/.claude/skills/)
npx skills add -g koppor/koppor-tools
```

They are equally valid as native Claude Code plugin skills — the `skills/<name>/SKILL.md` layout
is the standard skill directory, so you can also copy a folder into `~/.claude/skills/` by hand.
Each skill shells out to jbang, so jbang must be on `PATH`.

## License

[MIT](LICENSE) © Oliver Kopp
