# 08 — Vocabulary practice

**Status:** formats chosen; to build

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

## Chosen first

- **Typed production**, **rapid series** and **meaning recall** (formats 1 to
  3 below), built first; the others later.
- Words are learned in **frequency order**, mixed, not by theme: no
  interference between related words, and the most useful first. Scenes come
  with the grammar lessons.

## Research (done)

What the evidence says about adult second-language vocabulary, checked at the
level of abstracts and reviews, the strength of each finding given:

- **Retrieval beats restudy** (strong: Rowland 2014, g = 0.50, 0.69 past a
  day). **Feedback after a miss is essential**: showing the correct form after
  an error multiplied retention in Pashler et al. 2005, a bare "wrong" did
  nothing. Every miss shows the full right answer.
- **Spacing** (strong: Kim & Webb 2022), but expanding or equal intervals
  hardly differ; FSRS covers it. Within a session, a new word's first
  retrievals are spread out, not in a row.
- **Several retrievals the first day**: five to seven within a session beat one
  or three, immediately and later (Nakata 2017).
- **Practice transfers to the direction practised** (Steinel et al. 2007;
  Webb 2009); the harder direction, French → Japanese, forgets less (Schneider,
  Healy & Bourne 2002); form recall is the hardest skill (Laufer & Goldstein
  2004). Production weighted most is right; meaning recall is still needed
  for reading.
- **Words related in meaning interfere** when learned together (Tinkham 1993;
  Nakata & Suzuki 2019); sets around one scene do better than categories.
- **Generating** an answer beats reading it (Bertsch et al. 2007, d = 0.40).
- **Mnemonics** (the keyword method) help at once but their edge fades within
  days unless retrieval follows (Wang & Thomas 1995): a rescue for words that
  keep failing, not a default.
- **Context**: early on, a word in a list or one short sentence does as well
  as or better than in a text, which can hide it (Laufer & Shmueli 1997);
  writing a sentence with a new word hurts learning its form (Barcroft 2004).
  Varied contexts help once the word is known.
- **Handwriting** helps learning characters (Wiley & Rapp 2021), not word
  meanings: the stylus is for kana and kanji forms.
- **Saying a word, or mouthing it**, helps memory (MacLeod et al. 2010), which
  fits quiet evenings. Pitch accent is rarely retained by learners; reading in
  kanji goes through sound, so reading is a card of its own.

## Formats proposed

Ranked by expected benefit; the learner chooses which are built first:

1. **Typed production** (French → Japanese): the French meaning, with a hint
   when it is ambiguous; the word typed in kana through the romaji converter;
   any JMdict form accepted, the intended one shown after.
2. **Rapid series**: a set of 8 to 12 words in random order, both directions
   mixed; a missed word comes back 3 to 5 items later, until each new word has
   been retrieved several times.
3. **Meaning recall** (Japanese → French): the word shown, its meaning typed;
   checked against the glosses, the tutor judging near matches only, its
   verdict checked by code.
4. **Kanji form written with the stylus**, for words whose kanji are learned:
   the existing stroke matcher.
5. **Cloze in a generated sentence** (made of known words, validated): the
   word blanked, its meaning given, typed; only once the word is stable.
6. **Reading in a sentence**: a fresh sentence, the word underlined, its
   reading typed.
7. **Dictation**, once speech synthesis exists: heard, typed.
8. **Rescue of words that keep failing**: after three misses, the tutor
   proposes a mnemonic, then retrieval at once.

Not worth much investment: mnemonics by default, kana words written with the
stylus, writing whole sentences early, sets by category, multiple choice as the
main review, pitch drills.

## Open

- The formats the research suggests, and which ones to build first.
- Whether rapid series are timed, and whether speed counts in the grade: no
  evidence either way; the time can be kept, and a right but slow answer
  graded *hard*.
- Near misses in typed answers (a wrong long vowel, a missing small つ, ず for
  づ): graded *hard* or as misses; the right form shown either way.
- Words with several right answers (synonyms, homophones in kana): any JMdict
  word accepted, the intended one named; the kanji form needs its own card.
- French glosses: present for 98% of the JLPT N5 to N2 words, but their senses
  are sometimes not in the English order (足: "allure (de la marche)" before
  "pied"), with repeats, and some entries overlap (いい and 良い). Which gloss a
  production prompt shows, and whether the tutor reorders or translates them,
  checked and kept.
- Whether sets by theme or at random retain better, measured on this learner.
- Listening formats (audio → meaning, audio → written word).
