# 07 — Generated texts and progressive kanji

**Status:** sentences for words, and reading texts for lessons, built; their kanji shown as the learner reads them

## Intent

Reading texts that use only what the learner knows (plus what the lesson
introduces). They start almost entirely in hiragana and gain kanji as kanji are
learned, without anyone writing them by hand.

## Decided

- **Built first, for vocabulary** ([`08`](08-vocabulary-practice.md)): one
  sentence for one word (`SentenceWriter`). The tutor is given the word, in its
  dictionary form, and the words the learner has met; it writes one short
  polite sentence and its French translation (a very short sentence rather
  than an odd one). The analyser splits it, and the code checks every word
  (`SentenceCheck`): the word asked, a word met, or grammar (particles,
  auxiliaries, punctuation, affixes, numbers, and words that hold sentences
  together: する, いる, ある, なる, これ, この, ここ…). Refused, the tutor is
  asked again with the words it may not use, three times at most; otherwise no
  sentence. Checked, it is laid out for a learner who reads kana only: each word
  in kana, words spaced as in books for beginners, a word used as it stands in
  the dictionary read as JMdict reads it first. With 40 words met, 9 sentences
  in 10 were written and checked, in 1 to 3 s each.
- **Reading texts for lessons** (built, `TextWriter`): the tutor is given the
  lesson's point, the points up to it (pattern and title), the particles not
  learned yet, never to be used (those of the points to come, and が, と, て,
  ね…), and the words known: those of the lessons' examples and their words
  up to this one, and the words the learner has met. It writes 3 to 5 short
  sentences with their translation. The code checks each sentence
  (`SentenceGrammar`): a point after the lesson's, named by what is written
  (へ, not the point's id); a particle or auxiliary no point learned covers;
  a word neither known nor grammar (names of people and numbers allowed; a
  word a learned point names, as the potential verbs; a piece of a word the
  analyser splits, known whole with its neighbour: 茶 of お茶, ずつ of
  少しずつ); and the lesson's point must be in two sentences. The tutor is told
  the lesson's own words to use, and the particles and forms not learned yet
  (ため, ように, のに, けど…), never to be used. Refused, the tutor is asked
  again with the problems, three times at most. On the 66 points, 62 had a
  text at the first request (about 5 s; up to 13 s with retries); the others
  usually at the next; ます never does, and has none.
- **Kanji as they are learned** (built): each word laid out keeps how it is
  written beside its kana (`SentencePart.written`), and as a sentence is
  served (`KanjiSight`, not as it is laid out and cached: the learner's kanji
  change between two requests) the server says how to show it: in kanji when
  its kanji are all read (their reading past its first steps), in kanji with
  its reading above (furigana) when they are all met, being learned, in kana
  otherwise. Only the units' kanji (the first two grades) are taught, so a
  word with any other kanji stays in kana. Applied to the lessons' examples,
  reading texts, exercises and the series' sentences; the placement questions
  stay in kana. The kana stay what exercises are judged on.
- **Pipeline**:
  1. The backend asks the model for a text built on a grammar point and a set
     of words, with constraints: length, level, the vocabulary allowed, the
     words that should appear.
  2. The text is analysed by the morphological analyser into words with their
     readings and dictionary forms.
  3. It is validated against the learner model: unknown words, grammar beyond
     the curriculum's current point, unknown kanji. Too many problems → it is
     regenerated with the problems named. Minor ones are fixed (an unknown word
     glossed).
  4. It is rendered word by word:
     - all the word's kanji known → written in kanji;
     - kanji seen but not yet stable → kanji with furigana;
     - kanji unknown → written in kana.
- **Hidden reviews**: the generator is asked to reuse words whose reviews are
  due, so reading also reviews them.
- **Tap a word** for its reading, meaning (from JMdict, not the model), and the
  kanji it contains.
- **Model output is structured** (JSON matching a schema), so the backend can
  read it without guessing.

## Open

- Variety: with few points and words, texts repeat the same person and
  sentences (たなかさんは…); hidden reviews of due words are not asked yet.
- Naturalness: the model's Japanese can be wrong in ways validation does not
  catch. Options: a second pass where the model reviews its own text; a button
  for the learner to flag a sentence; keeping texts that read well.
- Words whose reading depends on context (and *jukujikun*, where the reading
  belongs to the whole word, not to each kanji): how furigana are placed on them.
- How a hidden review is graded: read without help, or looked up.
- Text types beyond prose: dialogues, short notices, messages.
