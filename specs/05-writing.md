# 05 — Writing with the stylus, stroke order

**Status:** planned

## Intent

Learning to write kana and then kanji by hand, with correct stroke order and
direction, on a touchscreen with a stylus. This is the long-term part: the goal
is every kyōiku kanji.

## Decided

- **Input**: a canvas taking Pointer Events from the pen only
  (`pointerType == "pen"`), so the palm and fingers are ignored. Pressure is
  recorded. The screen is vertical for now, so the writing area is large and the
  gestures forgiving. A flat tablet may come later and must need no redesign.
- **Three steps per character**:
  1. **Demonstration**: strokes animated one by one from KanjiVG, numbered,
     with their direction shown.
  2. **Guided**: the learner writes over a faint model.
  3. **From memory**: an empty box (with the meaning or reading as the prompt).
- **Correction is deterministic, not by the AI.** Each drawn stroke is compared
  with the expected KanjiVG stroke: stroke count, order, direction, start and
  end points, rough shape, and position and proportion within the box. Feedback
  is immediate and precise ("stroke 3: wrong direction"). The matcher lives in
  `shared` and runs in the browser.
- **Kana come first.** They are a small set to tune the matcher and the
  interaction on before kanji.
- Stroke endings (*tome*, *hane*, *harai*) are not judged at first.

## Open

- The matching method (resampling and point distances, dynamic time warping,
  Fréchet distance), and its tolerances. Tune them on real stylus input.
- Whether the learner can choose strict or lenient matching, and whether
  strictness grows with the item's stability.
- Judging *tome / hane / harai* from the end of the stroke's trajectory and
  pressure, later.
- How a wrong stroke is handled during *from memory*: stop at once and show, or
  let the character finish and review it.
- Writing words (several characters in boxes) as a dictation exercise.
