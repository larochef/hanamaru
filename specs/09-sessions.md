# 09 — Sessions

**Status:** Express built for kana, with a free review; My lesson built (grammar, vocabulary and kanji by unit)

## Intent

The available time and conditions change from day to day. Each session is
planned from what the learner declares at its start, so a short quiet evening
and a free afternoon are both useful.

## Decided

- **Today** (built, the home page, `/`): what to do now, as the learner
  stands, so that nothing has to be looked for in the menus. The server
  decides it all (`Today`, one request); the page shows it as it is.
  - **The kana**, besides My lesson, which teaches and reviews them: the
    placement test while no kana is met, and to resume it until it is over
    (My lesson teaches them from nothing otherwise); an Express session when more kana are due than My lesson
    takes (ten).
  - **My lesson**: the unit under way, where it stands (its words, its
    lesson, its symbols, and what it waits for), what today's session holds
    (grammar reviews, a series, the lesson, kana and kanji), and the grammar
    placement test while it was never taken and the unit's lesson is to do.
    From it, **the unit's page** (`/unit/<point>`, any unit so far): its
    lesson read again with its exercises; its words, each new, being learned
    or acquired, and a series on them alone (due first, a few new, then the
    others in turn); its symbols: for the first two units the chart of their
    kana and an Express session, else its kanji, each where it stands (not
    met, being read, read, read and written), written freely, and a series
    on those met (each read, and written once read, kept as reviews). Each
    practice opens in the page; the kanji chart, by school grade, is not the
    units' order.
  - Then, smaller, what can be practised or looked up freely: a series of
    words, **free practice** (below), the grammar's lessons, the vocabulary,
    the kanji, the kana chart.
- **Free practice** (built, `/practice`): apart from the lessons, six
  sentences the tutor writes on everything learned so far, each asked one
  way, translated from French and its meaning given in turn, judged as the
  lessons' are. So that the unit under way is no more there than the rest,
  the code draws what each sentence is on ([`07`](07-text-generation.md)):
  a point among those up to the last one met, each as often as the others,
  and two words met, one that says what is done or how (a verb, an
  adjective) and one other, none twice. Every learned point in a sentence is
  marked, so a slip is forgiven only in its nouns. Recorded as nothing: it
  changes no review. Even only in expectation, one practice after another:
  nothing remembers what earlier ones were on.

  The menu: Today and My lesson, where the learner goes on; then, quieter,
  "Look up": the kana (until they are settled: 90% known, their memory
  holding three weeks; the server says), vocabulary, grammar, kanji. The page
  shown is marked. The credits have a page of their own, linked from the foot
  of every page.

- **Free review** (built, the chart's "Review" button): kana after kana, each
  shown, written guided, then from memory, as long as the learner wishes; the
  learner moves to the next one when ready. The next kana is picked among those
  already met: first those due, those being learned first; then those the
  placement test found probably known, to confirm them, at random; then,
  nothing left to review, any kana met, at random, those not reviewed in the
  last day first, as Express does: taking those being learned, the soonest
  due first, went round the ten or so that a slip had kept learning (ち written
  nine times, り once). Never the same twice in a row. Writing from
  memory there is a review like anywhere else. Kana never met are not offered:
  new content belongs to the planned sessions below.

- **At the start of a session** the learner gives:
  - the **time available** (for example 10, 20, 45, 60+ minutes);
  - the **context**:
    - *can speak aloud*: off on quiet evenings;
    - *sound available*: headphones are usually fine, even on quiet evenings;
    - *stylus available*.
  - optionally a **focus**: vocabulary, lesson, writing, free choice.
- **Formats**:

  | Format | Time | Content |
  |---|---|---|
  | Express | 10–15 min | Due reviews, then a few new words. Nothing spoken |
  | Vocabulary | 20–30 min | New words and deeper vocabulary practice ([`08`](08-vocabulary-practice.md)) |
  | Lesson | 45–60 min | Grammar, text, exercises, writing, reading aloud if allowed |
  | Conversation | later | Spoken dialogue with the tutor ([`11`](11-speech.md)) |

- **The planner**:
  - excludes exercises the context rules out;
  - puts due reviews first, capped so that a busy day does not build a backlog
    the next session cannot clear;
  - fills the rest with new content according to the format.
- A session can be **stopped at any time**: every answer is saved as it is
  given.
- **Express, as built for kana** (the chart's "Express session" button):
  - the learner picks 10, 15 or 20 minutes, **the kana to work on (hiragana,
    katakana, or both)**, and says whether the stylus is at hand (speaking and
    sound do not matter to kana yet, so they are not asked); a session on one
    script asks only its kana, due or new, and keeps its choice
    (`study_session.kana_script`);
  - the server plans each exercise as the previous one is answered
    (`SessionPlanner`), from the cards as they stand: the cards due, those being
    learned or relearned first (a card missed comes back a few minutes later,
    within the session), then the most overdue; then the first reading of kana
    met but never read; then **new kana**, in the chart's order, one per three
    minutes of the session, and **only once nothing is due**; then, rather
    than ending early, **any card of the session's kana, at random**, those
    it has not asked yet first (taking the soonest due went round the few
    kana being learned, whose next steps are minutes away);
  - exercises: a kana **read** (typed in romaji), a kana **written** from memory
    from its reading, and a **new kana**: shown stroke by stroke, written over
    its model, then from memory (its writing's first review), or, without the
    stylus, shown then read (its reading's first review);
  - the same card never twice in a row when there is another; the session ends
    when its time is up, the exercise under way finished, and shows what it
    did: reviews, those right, new kana;
  - each session is kept (`study_session`) with the exercise it asks, so that
    an answer to another one is refused, and each review keeps its session, so
    that the time exercises take can be measured later.

- **My lesson** (built, first in the menu): the one place to go on with the
  curriculum, one unit ([`06`](06-curriculum-and-lessons.md)) after the other,
  grammar and vocabulary together. Kana stay apart for now, in their Express
  sessions. Its page shows the unit under way at a glance: where it stands
  (its words, its lesson, its symbols, each criterion marked met as the
  server judges it) and what it waits for; its grammar,
  the start of its lesson, read again whole or practised; its symbols, the
  kana chart or its kanji, each where it stands, written freely; its words,
  two columns of them, each with a short meaning (the first glosses of its
  first sense that fit a short line, their asides in parentheses left out),
  its senses unfolded on demand, the server deciding both. Each section has
  its own practice (a series on the unit's words or kanji, its exercises), and
  a Review button the session: each opens in a pop-in over the page, closed by
  its × alone (a stroke slipping off a pad must not close it), the unit asked
  again once it is. The units before are a click away: the one before by an arrow, any of
  them in a menu on the unit's name, on one line however many there are. A session is planned
  from where the learner stands, each time it opens:
  1. the grammar points due for review, two at most, in the curriculum's
     order, each practised in its exercises (or skipped);
  2. a **vocabulary series** ([`08`](08-vocabulary-practice.md)): the words
     due, then the new words of the unit under way, or those of its words not
     stable yet, in the room the words due leave; then the known words to be
     written in kanji, due or new (the new ones named above the series, "Mots
     à écrire en kanji"); none when there is nothing to practise;
  3. the **lesson of the unit under way**, when its point was never practised
     nor placed: its explanation, examples, reading text and exercises, after
     its first words, so that its text can use them. Its exercises done are its
     first review; missed, it comes back as a review the next time. Past the
     last unit, the first point never practised;
  4. the **kana and kanji**, the units' symbols
     ([`06`](06-curriculum-and-lessons.md)): first the kana due, read or
     written, the most overdue first, ten at most, never before they are due
     (what is left waits, or goes to an Express session); while the first two
     units are under way, their new kana, two a session in the chart's order,
     each learned in a **focus** (below); a kana met but never read is read, and one never written (the
     placement test reads more than it writes) is written as a new one is. A katakana due for its
     reading is read in a loanword written with it (テ in テレビ), one the
     learner has met if any, else one of the core vocabulary's most basic, its
     reading typed in romaji, left as typed (shown in katakana, or written
     with the stylus, the word would only be copied; long vowels lengthened or
     not), and its meaning shown after: katakana live in words, which lessons in hiragana seldom give.
     ヌ, ヲ, ヂ and ヅ, in no such word, are read alone. Then the kanji, each in a word of
     the units the learner knows: those due, read or written, the most overdue
     first (six at most); then the writing of those read well enough (their
     reading past its first steps, or read four times: a kanji written is
     recognised more easily; two at most), over the model first, then
     from memory (kanji read before focuses were); then new ones (two at
     most), only once a word written with them is stable, or met two days
     ago, each learned in a focus. Read, a kanji's word's reading is typed in
     kana, by keyboard or stylus, its meaning shown only once answered
     (given, it gives the word away). A reading, kana or
     kanji, or a word of a series, answered wrong is shown as the learner
     typed or wrote it, beside the answer expected. Reading comes
     before writing, and a word before its kanji. The word a kanji is asked
     in has the fewest other kanji not taught so far, then not met yet (学 in
     学生, not in 大学生, whose 大 comes later); its kanji no unit teaches are
     shown in kana, as Japanese schoolbooks do (友だち, 子ども: 達 and 供 are
     of grades 4 and 6), those of units to come in kanji.

  A **focus** (`CharacterFocus`) is how a new character, kana or kanji, is
  learned, before any review: about ten cards in one sitting, read and
  written in turn four times, the first of each new (shown: meanings,
  readings, strokes with the Traits / Guidé / De mémoire tabs; written over
  its model, then from memory); a kanji read in up to three words of the
  units written with it, whose other kanji are taught so far, a katakana in
  a loanword every other time. A card missed comes back once the others are
  done, the one asked longest ago first, three times at most: a focus always
  ends. Its answers are judged but not kept as reviews (its writing kept for
  the matcher); once over, the character's reading and writing are reviewed
  once each, as their last card went, and due from the next day, still in
  their first steps: focuses alone acquire no unit. A unit's page offers a
  focus on each of its kanji not met yet, ahead of My lesson.

  Nothing waits for ever (`Waiting`): an item held back, by reviews taking
  all the room or by a gate not passed yet, gets in once it has waited past a
  patience, two days with no backlog, longer as the backlog grows (twice as
  long with a full series due) so that the reviews still come on time: a new
  word once none has come for that long, though the words due fill the
  series; a kanji once a word written with it was met that long ago, though
  the word is not stable yet. Express still brings new kana only once nothing
  is due: My lesson brings them anyway.

  Each part is a selection with its fallback: what it selects (the grammar
  due, the words due or new, the kana and kanji due or new), else, nothing
  being, the unit under way gone over at random: its point's exercises (once
  its lesson is done), a series on its words, one on its kanji. Asked for, a
  session always has something to practise; Today names only what is due.

  It ends with where the unit stands (its words stable, its point practised,
  its symbols learned) and what opens the next one, with another session to go on. A unit is
  acquired once its point is practised, most of its words stable and its
  kanji learned; it then says so, at the session's end, on Today and on My
  lesson's page, and
  offers to move on to the next unit or to go on with this one (another
  session on it). The next
  unit's words and lesson come only once the learner moves on, so a unit takes a few sessions:
  words first, the lesson as soon as some are met, then series until the
  words hold. It does not keep to a time yet, and asks nothing of the
  context: nothing in it is spoken; the stylus is a choice within the series
  and exercises, and needed for the kanji. The vocabulary and grammar pages keep their own
  series and lessons, for practice out of turn.

## Open

- Time estimates per card and exercise type, measured from the reviews kept
  with their session. Express keeps to the time by the clock for now, and
  counts two minutes for a new kana.
- How a backlog after days away is spread out. Express only caps it by time:
  no new kana while reviews are due, the rest left for the next session.
- Whether the planner proposes a format from the time given, or the learner
  always picks it.
- Mixing within a session (a few kana or kanji reviews inside a vocabulary
  session).
- My lesson's next steps: the kana reviews due once the kana are settled, and
  a time given at the start.
