# Hanamaru — specs

These specs are **forward-looking**: they describe what the application is meant
to become, what has been decided and why, and what is still to be discovered.
Each module file has the same three sections:

- **Intent**: what the module is for
- **Decided**: choices already made, with their reason
- **Open**: questions to answer while building it, often by trying things

When a module is built, its file is updated to describe what exists, and its
open questions are either answered or moved on. Detail is added as it is
discovered, not before.

Start with [`00-overview.md`](00-overview.md): it holds the goal, the
principles and the shared vocabulary that every other file relies on.

## Status board

| # | Module | Status |
|---|--------|--------|
| [00](00-overview.md) | Overview, principles, vocabulary, roadmap | reference |
| [01](01-application-architecture.md) | Application architecture and stack | built |
| [02](02-reference-data.md) | Reference data (kana, kanji, dictionary, sentences) | KanjiVG, JMdict, JLPT lists, Sudachi, KANJIDIC2 imported |
| [03](03-learner-model.md) | Learner model and spaced repetition | built for kana, words, grammar points and the units' kanji |
| [04](04-placement.md) | Placement tests | built for kana and grammar |
| [05](05-writing.md) | Writing with the stylus, stroke order | built for kana, and for kanji in My lesson; tolerances to tune |
| [06](06-curriculum-and-lessons.md) | Curriculum and lessons | 71 units (point, theme, words, symbols), N5, N4 and the start of N3, built |
| [07](07-text-generation.md) | Generated texts and progressive kanji | sentences and lesson texts built, kanji shown as they are learned |
| [08](08-vocabulary-practice.md) | Vocabulary practice | rapid series built |
| [09](09-sessions.md) | Sessions: time budget, context, formats | Today, My lesson (grammar, words, kana, kanji), Express for kana, built |
| [10](10-llm-runtime.md) | LLM runtime and the AI tutor | judges meanings, writes sentences and texts, corrects exercises |
| [11](11-speech.md) | Speech: synthesis, recognition, conversation | planned |

## Build order

See the roadmap in [`00-overview.md`](00-overview.md#roadmap). In short: the
skeleton and the kana module come first, because they are useful at once and
they shake out the writing module on a small set before kanji.
