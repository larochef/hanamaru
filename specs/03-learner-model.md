# 03 — Learner model and spaced repetition

**Status:** planned

## Intent

One record of what the learner knows, kept in SQLite, which every other module
reads and updates: the planner picks reviews and new items from it, the text
pipeline decides how to write each word from it, the tutor reads it to know
what it may use.

## Decided

- **Items** are kana, kanji, words and grammar points. Each has several
  **cards**, one per way of testing it, for example:
  - word: reading → meaning, meaning → word (typed), audio → meaning
  - kanji: kanji → reading and meaning, meaning → kanji written with the stylus
  - kana: kana → reading, sound → kana written
  - grammar point: exercises tagged with it
- **FSRS** schedules every card. It is the current algorithm used by Anki,
  implemented in Scala. Its state per card is stability, difficulty and the due
  date.
- **Grades come from the answer, not from self-assessment**, wherever the
  answer can be checked: typed answers are compared, strokes are matched,
  multiple choices are scored. Self-grading ("I knew it") is the fallback for
  what cannot be checked.
- **Item status** derived from its cards: *unknown*, *learning*, *known*
  (stable), plus *to confirm* for what the placement test marked as probably
  known.
- **Prerequisites**: grammar points and words can name the points they rely on.
  Repeated failures on exercises tagged with a point lower that point's
  stability. When the tutor's diagnosis of a mistake names a missing
  prerequisite ([`10`](10-llm-runtime.md)), that prerequisite is reopened and
  scheduled for review.
- **Every review is kept** (card, time, answer, grade), not only the current
  state. FSRS parameters can then be re-fitted later, and progress shown.

## Open

- When to fit FSRS's parameters to the learner's own history, rather than the
  defaults.
- How a review hidden in a reading text counts (a word looked up versus read
  without help), see [`07`](07-text-generation.md).
- How grammar points are graded: per exercise, or as an aggregate over a
  session.
- What progress views are worth showing (kanji known per grade, words, streaks).
