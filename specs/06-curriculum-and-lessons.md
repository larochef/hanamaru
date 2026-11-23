# 06 — Curriculum and lessons

**Status:** planned

## Intent

A steady progression through grammar, vocabulary and kanji, where each lesson
can be repeated with fresh content until it is acquired.

## Decided

- **A fixed curriculum skeleton**: an ordered list of grammar points, roughly
  100 to 150, from N5 towards N3. Each point has a short French explanation,
  its prerequisites, and the vocabulary and kanji its lesson introduces. It is
  written once, reviewed, and kept as a resource file in the repository. The AI
  fills lessons with content but never decides the order.
- **Kanji follow the school grades** (80 in grade 1, then the next grades),
  spread across lessons. A kanji can be pulled forward when a lesson's
  vocabulary needs it.
- **A lesson** is:
  - **Grammar**: the point explained in French, with examples.
  - **Vocabulary**: 5 to 15 new words, added to the learner model.
  - **Kanji** (once that phase starts): 2 to 5 new ones, with writing practice.
  - **Text**: a reading text built on the point and the vocabulary
    ([`07`](07-text-generation.md)). Each word can be tapped for its reading and
    meaning.
  - **Exercises**: multiple choice, fill in the blank, translation both ways,
    free answers corrected by the tutor, kanji dictation with the stylus,
    listening and reading aloud when the context allows.
- **Repeating a lesson** brings a new text and new exercises on the same point
  and vocabulary. A lesson is *acquired* when its items are stable in the
  learner model, not after a set number of repetitions.
- **Explanations are in French.** The skeleton's explanations are reviewed text;
  the tutor adds explanations on demand ("why は and not が here?").

## Open

- The skeleton itself: its sources, its granularity, how it is drafted and
  reviewed.
- Its file format.
- Whether the skeleton's explanations are hand-written, or generated once and
  reviewed.
- How exercises are chosen and ordered inside a lesson, and how many.
