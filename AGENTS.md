# Repository Instructions

Hanamaru, a local application to learn Japanese at one's own pace: reading, writing with a stylus, grammar and vocabulary, with a local AI model for the interactivity. Scala 3, built with Mill.

- Web application: `backend` (Tapir on Netty, Ox) serves `frontend` (Scala.js, Laminar), with `shared` compiled for both
- Models (llama-server, speech) run as separate processes the backend supervises

## Start here

The design lives in `specs/`. **Read `specs/README.md` and `specs/00-overview.md` before any task**, then the spec of the module the task touches. The specs are forward-looking: each has *Decided* and *Open* sections; when a module is built, update its spec to match the code.

## Git

The developer manages Git themselves: never run `git init`, never commit, never push.

## Conventions: `.llm/`

Project conventions live in `.llm/`. Read only the files your task touches, before starting.

| File | Read it when |
|---|---|
| `build.md` | running any Mill command, compiling, formatting, running the application, adding dependencies |
| `scala-conventions.md` | writing any Scala: syntax, imports, visibility |
| `good-practices.md` | writing any code: naming, comments, unused code |
| `backend-architecture.md` | routes and their implementation |
| `frontend-architecture.md` | the page: bus, services, components |

## Maintaining `.llm/`

These files are read on every task, so every line costs every later task. **Leave them alone by default.**

Add a line only when all three hold:

1. Someone working on a *different* task would get something wrong without it.
2. It cannot be learned from the code that task would open anyway.
3. It is a rule or a trap, not a description of what was built.

Never add a description of a feature, a list of classes or files, or a justification of an implementation. State a rule in one or two sentences, name one place in the code that shows it, and remove what it makes obsolete. Knowledge local to one file is a comment in that file.

Conventions change only when a human asks. If the code you are writing contradicts one, say so instead of editing the rule.
