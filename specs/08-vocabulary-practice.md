# 08 — Vocabulary practice

**Status:** planned

## Intent

Vocabulary is what the learner finds hardest to retain, so it gets the most
varied practice, and whole sessions can be devoted to it ([`09`](09-sessions.md)).

## Decided

- **Both directions**: recognition (Japanese → French) and, above all,
  production (French → Japanese, typed). Production is graded on the typed
  answer, accepting any form JMdict gives (kanji or kana).
- **Rapid series**: the words of a set in **random order, one after the
  other**, each to be translated, mixing both directions. This format was asked
  for explicitly.
- **Words in fresh sentences**: at each review, the word can appear in a new
  sentence generated with known words only ([`07`](07-text-generation.md)). The
  learner learns the word, not one sentence.
- **Hidden reviews in reading texts** ([`07`](07-text-generation.md)).
- **Mnemonics on demand**: the tutor proposes one when a word will not stick.
- **Thematic sets** tied to the lessons.
- **Audio** with each word once speech synthesis exists ([`11`](11-speech.md)).
- **Typing Japanese**: a romaji → kana converter built into the answer field,
  so the system's input method is not needed.

## Before coding this module

**Research how vocabulary is best memorised**: retrieval practice,
interleaving, production versus recognition, spacing, mnemonics such as the
keyword method, context and listening. Then propose a set of practice formats
to the learner, before building them. The list above is the starting point, not
the result.

## Open

- The formats the research suggests, and which ones to build first.
- Whether rapid series are timed, and whether speed counts in the grade.
- Near misses in typed answers (a wrong long vowel, a missing small つ): how
  they are graded and shown.
- Listening formats (audio → meaning, audio → written word).
