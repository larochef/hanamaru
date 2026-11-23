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
  ends, against the stroke expected next:
  - both are resampled to 32 points evenly spaced along them and compared point
    by point, in place in the box: the mean distance measures position, size
    and shape at once;
  - a stroke is within tolerance when that distance is at most 8 units (of the
    109-unit box) plus 12% of the expected stroke's length;
  - its **direction** is the one, forward or backward, that matches best, and
    its **order** is wrong when a later stroke matches better than the
    expected one: comparing, rather than testing a threshold, is what tells a
    dakuten's two short strokes apart;
  - the verdicts are *accepted*, *wrong direction*, *wrong order* (naming the
    stroke drawn) and *misdrawn*. A stroke under 2 units is a slip of the pen
    and is ignored.
  On KanjiVG's own 437 kana strokes, every stroke is accepted, every reversed
  one and every later one drawn too early is caught. With strokes shifted by up
  to 4 units and noisy, 99.8% are accepted; the misses are dakuten strokes taken
  for each other.
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
- Whether the learner can choose strict or lenient matching, and whether
  strictness grows with the item's stability.
- Judging *tome / hane / harai* from the end of the stroke's trajectory and
  pressure, later.
- Whether the attempts move into the learner model's database once it exists
  ([`03`](03-learner-model.md)).
- Writing words (several characters in boxes) as a dictation exercise.
