# 04 — Placement tests

**Status:** built for kana and grammar

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
- **Kana** (built, its own page, offered on the chart until it is over):
  hiragana then katakana, from the easiest rows to the hardest (basic sounds,
  voiced, small kana), by **blocks of three rows whose kana come in a random
  order**, different from one test and one script to the next: the chart's
  order would give the readings away.
  - Every kana of a block is **read**: shown, its reading typed in romaji, with
    the usual variants accepted (*si*, *tu*, *nn*, *xya*…).
  - A row is passed when at least half of it is read right; one of the kana
    read right, a different one from one test to the next, is then **written**
    from memory with the stylus.
  - A script stops after **two rows failed in a row**, once their block is
    answered: a beginner reads 15 kana of each script. A script can be given
    up at once ("I don't know the katakana at all"): it is then to learn from
    its start.
  - A right answer is a first review graded *easy*, *to confirm*: FSRS brings
    it back about 8 days later, and that review confirms it or sends it back to
    learning. A missed one is graded *again*, and is learned.
  - A kana written right vouches for the writing of the other kana of its row
    read right: their writing cards are made *to confirm* too, without a review
    of their own. A learner who knows the hiragana writes 17 of them, not 75.
  - The result, per script: read right out of asked, written right out of
    asked, and the row to learn from, the first one not mastered.
  - Every answer is kept as it is given (`placement_answer`), so the test
    resumes where it was left. The reviews it makes are marked as coming from
    placement, so that FSRS's parameters are not fitted on them.
- **Vocabulary**: a quick sort of the core word list, by frequency, into
  *known / unsure / unknown*. *Known* words are not trusted: they enter the
  reviews as *to confirm*, so a wrong answer brings them back to *learning*.
- **Grammar** (built, its own page, reached from the grammar page): the
  curriculum's points in order, **two questions each**, written by hand in the
  curriculum file ([`06`](06-curriculum-and-lessons.md)): a sentence with a
  blank, its French, three choices in kana, the wrong ones wrong for that
  meaning (は is not offered where も is meant). A test checks each: its
  answer among distinct choices, the sentence filled showing its point and
  none after it, the blank whole tokens.
  - A point's second question is asked only if its first was right; a point
    is **passed with both right**. The test stops after **two points failed in
    a row**, or when given up ("I know no Japanese grammar").
  - A point passed is a first review graded *easy*, *to confirm*, from
    placement; a point failed is left alone: it is a lesson to do.
  - The result: each point decided, passed or not, and the lesson to start
    with, the first point not passed. Answers are kept as the kana test's are
    (`placement_answer`, subject `wa#0`), so the test resumes.
- **Kanji**: by school grade, reading and meaning first. Writing is only tested
  for kanji that are recognised.
- **What comes out**: a level per domain shown to the learner, and the starting
  point of each track (which kana to work on, which lesson comes first, which
  kanji grade).

## Open

- How fast *to confirm* items of the other domains are spaced out, and when
  they count as *known*. For kana: a first review graded *easy*, confirmed by
  the next one, known once stable for 21 days like any card.
- How the questions are made for vocabulary and kanji: hand-checked,
  generated and validated, or a mix. Kana are all asked; grammar questions are
  written by hand and checked by the analyser.
- How finely the other domains are sampled. Kana: every kana is read, one per
  row is written, and its row's writing inferred from it.
- Whether hesitation (response time) counts as a weak answer. The time each
  reading took is kept with its answer, not used yet.
