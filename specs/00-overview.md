# 00 — Overview

**Status:** reference

## Goal

A personal, local application to learn Japanese at one's own pace: reading,
writing, grammar and vocabulary, with a local AI model providing the
interactivity (generated texts and exercises, corrections, explanations, and
eventually spoken conversation).

The long-term writing goal is to know **all the kanji taught in Japanese
primary school** (the *kyōiku kanji*, grades 1 to 6), after a review of
hiragana and katakana.

## The learner it is designed for

- **Any starting level**, from a beginner to a returning learner whose
  knowledge is partly solid and partly faded. Nothing is assumed about the
  learner: a placement test run on first start measures the level in every
  domain ([`04`](04-placement.md)), and what is confirmed moves fast.
- **Vocabulary** is the hardest part to retain for most learners: it gets the
  most varied practice ([`08`](08-vocabulary-practice.md)).
- **Time varies from day to day**: some evenings are short and quiet (no
  speaking aloud, headphones fine), other days allow long sessions. The learner
  states at the start of each session how much time they have and in which
  context ([`09`](09-sessions.md)).
- Explanations are given in **French**.
- Writing is done with a **stylus on a vertical touchscreen** for now; a flat
  tablet may come later.

## Principles

1. **The AI is not the source of truth.** A local model is good at generating
   text, conversing, correcting a free sentence and explaining a mistake. It is
   unreliable for stroke order, exact kanji readings, kanji grade lists, and for
   reading handwriting. Those come from open reference data
   ([`02`](02-reference-data.md)) and deterministic code.
2. **Generate, then validate.** Everything the model produces for the learner
   (texts, exercises) is checked by code against the learner model and the
   reference data: unknown kanji, unknown vocabulary, grammar not yet seen. What
   fails is regenerated or adjusted ([`07`](07-text-generation.md)).
3. **The learner model drives everything.** What is known, at which stability,
   decides what is reviewed, what is new, how a text is written (kanji, furigana
   or kana) and which exercises are chosen ([`03`](03-learner-model.md)).
4. **Confirmed means fast.** What is answered right is spaced out quickly; what
   is failed comes back. A failure that reveals a missing prerequisite reopens
   that prerequisite: the tutor goes back to it instead of pushing on.
5. **The curriculum is fixed, the content varies.** The order of grammar points
   and kanji is a reviewed list; the model fills it with fresh texts and
   exercises each time, so a lesson can be repeated without repeating itself
   ([`06`](06-curriculum-and-lessons.md)).
6. **Everything runs locally.** Models run on the learner's machine (a Strix
   Halo with 128 GB of unified memory), through standalone inference servers.

## Vocabulary

- **Item**: one thing that can be known: a kana, a kanji, a word, a grammar
  point.
- **Card**: one way of testing an item (a word read → its meaning, a meaning →
  the word typed, a kanji → written with the stylus). An item has several cards;
  each card has its own spaced-repetition state.
- **Review**: one answer to a card, graded, which updates its state.
- **Lesson**: a unit of the curriculum: a grammar point, its vocabulary, its
  new kanji, a text and exercises.
- **Session**: one sitting, planned from the time budget and context the
  learner declares.
- **Exercise**: one task inside a lesson or session; it produces reviews on the
  cards it touches.
- **Reference data**: the open datasets imported once (stroke shapes, kanji
  information, dictionary, example sentences).

## Components

```
           browser (Scala.js, Laminar)
   stylus canvas · stroke matcher · furigana · sessions
                       │  HTTP / WebSocket (Tapir endpoints in shared)
           backend (Scala, Tapir, Ox)
   learner model + FSRS · curriculum · session planner
   text pipeline (analyser, validator) · reference data · SQLite
        │                     │                     │
   llama-server          speech synthesis      speech recognition
   (Gemma 4 26B A4B)     (to be compared)      (whisper.cpp, later)
```

## Roadmap

1. **Skeleton** (done): Mill build, backend and frontend, SQLite, conventions
   ([`01`](01-application-architecture.md)).
2. **Kana** (done), in five deliveries, writing first so the stroke matcher is
   tuned on real stylus input early:
   1. (done) the kana chart, KanjiVG imported, stroke order animated;
   2. (done, tolerances to tune) the stylus canvas and the stroke matcher:
      guided, then from memory;
   3. (done) the learner model in SQLite, with FSRS;
   4. (done) the kana placement test;
   5. (done) the Express session format, and a free review of the kana met.
3. **LLM and vocabulary** (done), in five deliveries:
   1. (done) llama-server launched and supervised by the backend;
   2. (done) JMdict imported, the core word list from the JLPT lists, a page
      listing it;
   3. (done) research on how vocabulary is best memorised, and practice
      formats proposed to the learner ([`08`](08-vocabulary-practice.md));
   4. (done) the formats chosen, built: typed production, meaning recall,
      rapid series;
   5. (done) words in sentences written by the tutor and checked by code,
      Sudachi as the analyser; Tatoeba left for reading texts.
4. **Lessons** (done), in five deliveries:
   1. (done) the curriculum's format and its first points, a lesson page with
      the explanation and the examples;
   2. (done) the grammar of a sentence told by code, reading texts held to
      the points learned, words tapped for their meaning;
   3. (done) exercises (a particle chosen, a sentence translated) and
      corrections by the tutor, recorded as reviews of the point;
   4. (done) grammar placement, two questions per point written by hand;
   5. (done) the Lesson session: grammar reviews due, then the next lesson.
5. **Kanji**: grade by grade, writing and recognition, progressive kanji in
   texts.
6. **Speech**: synthesis for texts and words, then recognition for reading
   aloud.
7. **Conversation**: spoken dialogue with the tutor on what has been learned.
