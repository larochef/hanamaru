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
    words, the grammar's lessons, the vocabulary, the kanji, the kana chart.

  The menu: Today and My lesson, where the learner goes on; then, quieter,
  "Look up": the kana (until they are settled: 90% known, their memory
  holding three weeks; the server says), vocabulary, grammar, kanji. The page
  shown is marked. The credits have a page of their own, linked from the foot
  of every page.

- **Free review** (built, the chart's "Review" button): kana after kana, each
  shown, written guided, then from memory, as long as the learner wishes; the
  learner moves to the next one when ready. The next kana is picked among those
  already met: first those due, those being learned first; then those the
  placement test found probably known, to confirm them; then those being
  learned, the one due soonest first, then any the same way. At random among
  those due and those to confirm, never the same twice in a row. Every kana
  once practised is "being learned" until its memory holds three weeks, so
  picking among them at random kept the same few coming back while those to
  confirm never came; in turn, each comes back once the others have. Writing from
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
  sessions. It is planned from where the learner stands, each time it opens:
  1. the grammar points due for review, two at most, in the curriculum's
     order, each practised in its exercises (or skipped);
  2. a **vocabulary series** ([`08`](08-vocabulary-practice.md)): the words
     due, then the new words of the unit under way, or those of its words not
     stable yet; none when there is nothing to practise;
  3. the **lesson of the unit under way**, when its point was never practised
     nor placed: its explanation, examples, reading text and exercises, after
     its first words, so that its text can use them. Its exercises done are its
     first review; missed, it comes back as a review the next time. Past the
     last unit, the first point never practised;
  4. the **kana and kanji**, the units' symbols
     ([`06`](06-curriculum-and-lessons.md)): first the kana due, read or
     written, the most overdue first, ten at most, never before they are due
     (what is left waits, or goes to an Express session); while the first two
     units are under way, their new kana, five a session in the chart's order,
     each shown stroke by stroke, written over its model, then from memory,
     then read; a kana met but never read is read, and one never written (the
     placement test reads more than it writes) is written as a new one is. A katakana due for its
     reading is read in a loanword written with it (テ in テレビ), one the
     learner has met if any, else one of the core vocabulary's most basic, its
     reading typed in kana (long vowels as ー or not) and its meaning shown
     after: katakana live in words, which lessons in hiragana seldom give.
     ヌ, ヲ, ヂ and ヅ, in no such word, are read alone. Then the kanji, each in a word of
     the units the learner knows: those due, read or written, the most overdue
     first (six at most); then the writing of those read well enough (their
     reading past its first steps; two at most), over the model first, then
     from memory; then new ones (two at most), only once a word written with
     them is stable: shown (meanings, readings, strokes), then read, the
     word's reading typed in kana, by keyboard or stylus, its meaning shown
     only once answered (given, it gives the word away). Reading comes
     before writing, and a word before its kanji. The word a kanji is asked
     in has the fewest other kanji not taught so far, then not met yet (学 in
     学生, not in 大学生, whose 大 comes later).

  It ends with where the unit stands (its words stable, its point practised,
  its symbols learned) and what opens the next one, with another series to go on. A unit is
  acquired once its point is practised, most of its words stable and its
  kanji learned; it then says so, at the session's end and on Today, and
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
