# 09 — Sessions

**Status:** Express built for kana, with a free review

## Intent

The available time and conditions change from day to day. Each session is
planned from what the learner declares at its start, so a short quiet evening
and a free afternoon are both useful.

## Decided

- **Free review** (built, the chart's "Review" button): kana after kana, each
  shown, written guided, then from memory, as long as the learner wishes; the
  learner moves to the next one when ready. The next kana is picked among those
  already met: first those being learned that are due, then the others being
  learned (those missed in the placement test, first of all), then those due,
  then any; at random within each, never the same twice in a row. Writing from
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
  - the learner picks 10, 15 or 20 minutes and says whether the stylus is at
    hand (speaking and sound do not matter to kana yet, so they are not
    asked);
  - the server plans each exercise as the previous one is answered
    (`SessionPlanner`), from the cards as they stand: the cards due, those being
    learned or relearned first (a card missed comes back a few minutes later,
    within the session), then the most overdue; then the first reading of kana
    met but never read; then **new kana**, in the chart's order, one per three
    minutes of the session, and **only once nothing is due**; then the cards
    being learned, before they are due, rather than ending early;
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
