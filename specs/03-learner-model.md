# 03 — Learner model and spaced repetition

**Status:** built for kana, words and grammar points

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
  - grammar point: one card, `usage`, its exercises in a lesson graded together
    (built): four answers in five right is Good, half is Hard, less is Again
- **FSRS** schedules every card. It is the current algorithm used by Anki:
  FSRS-6, ported to Scala (`Fsrs`) from py-fsrs 6.3.3, the reference
  implementation, with its default parameters, a desired retention of 0.9, its
  learning steps (1 and 10 minutes) and relearning step (10 minutes). The
  random spread py-fsrs can add to intervals is left out, so scheduling is
  deterministic. py-fsrs's own scheduling tests are ported with their
  expected values (`FsrsReferenceTests`), which checks the port against the
  reference implementation: the intervals of a run of answers, the memory
  after a history, and the learning and relearning steps. Its settings are
  those of py-fsrs and can be changed, for the day they are fitted to the
  learner's own history. Its state per card is a phase (learning, review,
  relearning) and step, stability, difficulty, the due date and the last
  review.
- **Grades come from the answer, not from self-assessment**, wherever the
  answer can be checked: typed answers are compared, strokes are matched,
  multiple choices are scored. Self-grading ("I knew it") is the fallback for
  what cannot be checked.
- **Item status** derived from its cards: *unknown* (never reviewed),
  *learning*, *known* (every card in review with a stability of 21 days or
  more), plus *to confirm* for what the placement test found probably known
  ([`04`](04-placement.md)): its next review confirms it, or sends it back to
  learning. An item is only as far as its least known card.
- **Storage**: tables `item` (kind and key: the character, for a kana),
  `card` (its kind and FSRS state, a row once first reviewed) and `review`
  (time, grade 1 to 4, the answer as JSON, and its source: practice or
  placement, whose reviews FSRS is not fitted on), in
  `~/.local/share/hanamaru/learner.db`.
- **Kana** have two cards: **reading** (the kana shown, its reading typed) and
  **writing**, both reviewed in Express sessions ([`09`](09-sessions.md)), where
  a reading is graded *good* when typed right and *again* otherwise. Writing is
  also reviewed anywhere a kana is written from memory: a kana written from memory
  ([`05`](05-writing.md)) is a review of it, graded *good* when every stroke
  was accepted and *again* when a wrong stroke stopped it. Guided writing is
  practice, and an attempt given up is no answer: neither is a review. The
  chart of kana shows each one's status and which are due.
- **Words** are items keyed by their JMdict number, with a *production* and a
  *meaning* card ([`08`](08-vocabulary-practice.md)).
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
- What progress views are worth showing (kanji known per grade, words, streaks).
