# 04 — Placement tests

**Status:** planned

## Intent

A placement test runs on first start and measures the learner's level in every
domain, whoever the learner is: a complete beginner stops after a few
questions, a returning learner goes further. It seeds the learner model, and
the normal reviews then confirm or correct what it seeded. Nothing in it is
tailored to a particular learner.

## Decided

- **Run on first start**, domain by domain: kana, vocabulary, grammar, kanji.
  It can be **interrupted and resumed** across several sessions, and **taken
  again** for a domain later.
- **Adaptive**: each domain goes from the easiest to the hardest and stops once
  failures dominate. A learner who knows nothing of a domain answers very few
  questions about it. "I don't know" is always an answer, and it counts as a
  failure without penalty.
- **Kana**: hiragana then katakana, read and written. Correct at once → *to
  confirm* with a long first interval; wrong or hesitant → *learning*.
- **Vocabulary**: a quick sort of the core word list, by frequency, into
  *known / unsure / unknown*. *Known* words are not trusted: they enter the
  reviews as *to confirm*, so a wrong answer brings them back to *learning*.
- **Grammar**: two or three questions per curriculum point, in curriculum
  order. Passed → *to confirm*; failed → the point becomes a lesson to do.
- **Kanji**: by school grade, reading and meaning first. Writing is only tested
  for kanji that are recognised.
- **What comes out**: a level per domain shown to the learner, and the starting
  point of each track (which kana to work on, which lesson comes first, which
  kanji grade).

## Open

- How fast *to confirm* items are spaced out, and when they count as *known*.
- How the questions are made: hand-checked, generated and validated, or a mix.
- How finely each domain is sampled: every item, or a sample per level that is
  then extrapolated, so the test stays short.
- Whether hesitation (response time) counts as a weak answer.
