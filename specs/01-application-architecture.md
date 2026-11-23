# 01 — Application architecture and stack

**Status:** skeleton built

## Intent

A local web application: a backend on the learner's machine serves a page
opened in the browser. The browser is the best host for stylus input (Pointer
Events, pressure, telling the pen from a finger or a palm) and for Japanese
typography (`<ruby>` furigana, vertical text if ever wanted).

## Decided

- **Stack and layout**:
  - Scala 3, built with **Mill**, JDK 25 (Ox runs on virtual threads)
  - `backend`: **Tapir** on Netty (sync server), **Ox** for concurrency
  - `frontend`: **Scala.js** with **Laminar**, bundled by **Vite**
  - `shared`: compiled for both; the Tapir endpoints and the types exchanged,
    with **jsoniter-scala** codecs, plus logic that must run in the browser
    (stroke matching, romaji → kana conversion)
- **SQLite** for the learner's data, through plain JDBC with small helpers
  (`Database` in the backend): one connection, one caller at a time. Its schema
  will be moved on by **Flyway** migrations, in the backend's resources under
  `db/migration`; Flyway is added with the first one, the learner model
  ([`03`](03-learner-model.md)).
- **Ports**: the backend listens on `localhost:4323` (`HANAMARU_PORT`), Vite's
  dev server on 5175, away from the usual defaults so that other local
  applications can run beside it. Only `localhost`: nothing else on the network
  reaches the learner's data.
- **All the Japanese tooling stays on the JVM**: a morphological analyser
  with a Java implementation (Sudachi or Kuromoji), XML reference data parsed in
  Scala, FSRS implemented in Scala. No Python service.
- Models run as **separate server processes** (llama-server, speech servers),
  launched and supervised by the backend and spoken to over HTTP
  ([`10`](10-llm-runtime.md), [`11`](11-speech.md)). No model runs on the GPU
  through a stock framework (PyTorch and the like): one long GPU launch can hang
  the desktop.
- **Fonts are bundled** with the page. A textbook-style (*kyōkasho-tai*) font
  is needed to show characters as they are handwritten. A Gothic or Mincho font
  can mislead on stroke shapes. **Klee One** (OFL) is bundled, with its
  Japanese subsets, from the `@fontsource/klee-one` npm package.
- **No CSS framework**: the page's own stylesheet (`frontend/app.css`). The
  screens are few, mostly a large writing canvas and cards.
- **Nothing personal in the repository**: no paths to the learner's home, no
  biography. Local paths (model files, binaries) come from a configuration file
  outside the repository.

## Name

The application is **Hanamaru** (花丸: the flower-shaped circle Japanese
teachers draw on good work). Scala package `hanamaru`, environment variables
prefixed `HANAMARU_`, data under `~/.local/share/hanamaru` (learner data) and
`~/.cache/hanamaru` (what can be rebuilt: imported reference data, generated
audio).

## Open

- Sudachi or Kuromoji (with which dictionary), judged on readings and on how
  well words are split for furigana.
- Whether generated content (texts, exercises, audio) is kept and reused across
  repetitions of a lesson, and for how long.
- Whether the backend should listen beyond `localhost`, once a flat tablet
  joins.
