# 07 — Generated texts and progressive kanji

**Status:** first piece built: single sentences checked and laid out in kana

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

- How to judge the grammar level of a generated sentence. The analyser gives
  words and inflections. Mapping them to curriculum points is to be designed.
- Naturalness: the model's Japanese can be wrong in ways validation does not
  catch. Options: a second pass where the model reviews its own text; a button
  for the learner to flag a sentence; keeping texts that read well.
- Words whose reading depends on context (and *jukujikun*, where the reading
  belongs to the whole word, not to each kanji): how furigana are placed on them.
- How a hidden review is graded: read without help, or looked up.
- Text types beyond prose: dialogues, short notices, messages.
