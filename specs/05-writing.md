# 05 — Writing with the stylus, stroke order

**Status:** built for kana; tolerances to tune

## Intent

Learning to write kana and then kanji by hand, with correct stroke order and
direction, on a touchscreen with a stylus. This is the long-term part: the goal
is every kyōiku kanji.

## Decided

- **Input**: an SVG box taking Pointer Events from the pen, or a mouse, but
  never touch (`pointerType == "touch"`), so the palm and fingers are ignored.
  The box takes no browser gesture (`touch-action: none`), so the pen never
  scrolls the page. Each point is kept with its pressure and time, including
  the positions a fast pen passes between two events (coalesced events). The
  screen is vertical for now, so the writing area is large and the gestures
  forgiving. A flat tablet may come later and must need no redesign.
- **Three steps per character**, as tabs of the character's page:
  1. **Demonstration**: strokes animated one by one from KanjiVG, each drawn
     from its start to its end over the faint model, numbered as it is drawn,
     on a box with the dashed cross of practice paper; it can be replayed.
  2. **Guided**: the learner writes over the faint model. A wrong stroke is
     named, erased, and drawn again.
  3. **From memory**: an empty box, the character hidden, its reading as the
     prompt. The first wrong stroke ends the attempt: the expected stroke is
     drawn in red, from its start, so its direction shows.
- **Correction is deterministic, not by the AI**: the matcher
  (`StrokeMatcher`, in `shared`, run in the browser) judges each stroke as it
  ends, against the stroke expected next. Both are resampled to 32 points
  evenly spaced along them, and **shape and place are judged apart**, the place
  more leniently: a stroke drawn right but a little off is drawn right.
  - **Shape**: the mean distance between their points once their centres are
    put together, at most 8 units (of the 109-unit box) plus 12% of the
    expected stroke's length.
  - **Place**: the distance between their centres, at most 22 units.
  - **Direction**: the one, forward or backward, whose shape matches best. A
    loop (a stroke ending about where it starts, as a handakuten's circle) has
    no direction: it is compared from any start, both ways round.
  - **Order**: wrong when a later stroke fits, shape and place together, better
    than the expected one; when the expected one fits too, the later one must
    fit twice as well, so that of two strokes alike (ほ's bars) the expected
    one drawn a little off is still taken for itself, while a dakuten's two
    strokes drawn the other way round are caught.
  - The verdicts are *accepted*, *wrong direction*, *wrong order* (naming the
    stroke drawn) and *misdrawn*. A stroke under 2 units is a slip of the pen
    and is ignored.

  These tolerances were tuned on the first real attempts written from memory
  with the stylus (113 strokes): correct strokes sit within 15 units of the
  model in shape and in place 95 times out of 100, 24 and 20 at most. Judging
  place apart accepts the 9 correct strokes the first matcher refused (begun a
  little off, circles drawn the other way) and still refuses the 6 real
  mistakes. On KanjiVG's own 437 kana strokes, every stroke is accepted, every
  reversed one and every later one drawn too early is caught; with strokes
  shifted by up to 8 units and noisy, 99.3% are accepted, the misses being
  dakuten strokes taken for each other.
- **Every attempt is kept** in `~/.local/share/hanamaru/writing-attempts.jsonl`,
  one JSON line each: the character, the step, every stroke drawn with its
  points and verdict, and whether it was completed. It is the material the
  tolerances are tuned on.
- **Kana come first.** They are a small set to tune the matcher and the
  interaction on before kanji.
- Stroke endings (*tome*, *hane*, *harai*) are not judged at first.

## Open

- The tolerances, tuned on the attempts kept; and whether point distances
  after resampling are enough, or dynamic time warping or the Fréchet distance
  do better on real strokes.
- Whether, from memory, a stroke's place should be judged against the strokes
  already drawn rather than against the box, for a character written much
  smaller or larger than the model. Judging against the box with a lenient
  place tolerance was enough on the first attempts.
- Whether the learner can choose strict or lenient matching, and whether
  strictness grows with the item's stability.
- Judging *tome / hane / harai* from the end of the stroke's trajectory and
  pressure, later.
- Whether the attempts file stays once enough are kept to tune on: those
  written from memory are also kept as reviews' answers, in the learner model
  ([`03`](03-learner-model.md)).
- Writing words (several characters in boxes) as a dictation exercise.
