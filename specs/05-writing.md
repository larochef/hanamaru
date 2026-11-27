# 05 — Writing with the stylus, stroke order

**Status:** built for kana; tolerances to tune

## Intent

Learning to write kana and then kanji by hand, with correct stroke order and
direction, on a touchscreen with a stylus. This is the long-term part: the goal
is every kyōiku kanji.

## Decided

- **The matcher on kanji**, checked by a test on the 1,026 school kanji (with
  KanjiVG and KANJIDIC2 in the cache): each written as its model, every point
  shaken a little, is right; its first two strokes swapped are found, but for
  3 kanji whose first strokes are alike (妹, 然, 属). Real handwriting on kanji
  is still to try: My lesson ([`09`](09-sessions.md)) records each kanji
  written from memory as a review of its writing, graded as a kana is, its
  tolerances not tuned on kanji yet; the kanji pages are free practice,
  nothing recorded.
- **ぢ and づ told apart**: they are read as じ and ず are, so a kana asked by
  its reading would be ambiguous. Wherever a reading is shown (the chart, the
  prompts, the answers), ぢ and づ show the romaji they are typed with too:
  "ji (di)", "zu (du)"; "di" and "du" are accepted as their readings only.
- **Input**: an SVG box taking Pointer Events from the pen, or a mouse, but
  never touch (`pointerType == "touch"`), so the palm and fingers are ignored:
  a stroke follows the one pointer that began it, so a palm's events, the
  cancel a browser sends as the pen comes near among them, never end it; a
  stroke the browser cancels is kept as drawn.
  The box takes no browser gesture (`touch-action: none`), so the pen never
  scrolls the page. Each point is kept with its pressure and time, including
  the positions a fast pen passes between two events (coalesced events). The
  screen is vertical for now, so the writing area is large and the gestures
  forgiving. A flat tablet may come later and must need no redesign.
- **Three steps per character**, as tabs of the character's page, and of a
  new kana or kanji shown in a lesson or a session (`WritingPractice`), there
  practice only, nothing kept as a review, before the lesson's own steps:
  1. **Demonstration**: strokes animated one by one from KanjiVG, each drawn
     from its start to its end over the faint model, numbered as it is drawn,
     on a box with the dashed cross of practice paper; it can be replayed.
  2. **Guided**: the learner writes over the faint model. A wrong stroke is
     named, erased, and drawn again.
  3. **From memory**: an empty box, the character hidden, its reading as the
     prompt. In a review (My lesson, an Express session), the learner may
     say **they do not remember it**: the review is kept as missed (`forgotten`
     in the attempt, so that the attempts tuning the matcher leave it out),
     the character is shown again stroke by stroke and written over its
     model, as practice, and the session goes on; its card comes back soon.
     The learner **writes the whole character as they think it goes**
     (the last stroke can be erased), says it is done, and **only then is it
     corrected** (`WritingCorrection`, in `shared`): every stroke drawn is
     judged against the stroke expected at its place, and the correction shows
     the learner's strokes over the faint model, green when right, orange when
     only their direction or order is wrong, red otherwise, numbered in the
     order drawn, with each fault in words: a stroke the other way, a stroke
     that is another one of the character (the order), a stroke not the one
     expected, a stroke too many, strokes missing. The stroke order can be
     watched again. The character is right when it has every stroke and each
     is right; right but for the order or direction of some strokes; or wrong.
     Graded *good*, *hard* and *again*.
- **Kana written one after the other in one box** (`KanaPad`): once the pen
  rests 0.9 s, what is drawn is recognised (`CharacterRecognizer`) among the
  kana of the script in use and taken as a character, the box cleared for the
  next; a drawing far from any kana stays, to be finished, erased or taken all
  the same ("Next character"). The recogniser brings the drawing and each
  model to the same size and centre, and compares them stroke by stroke when
  they have as many strokes, and as one line (the pen's moves between strokes
  included, so strokes written as one still compare, a stroke more or fewer
  costing enough that て's one stroke is not こ's two joined, nor で a ご), in
  place and in
  direction along the strokes (a corner, て, against a curve, つ), a long
  stroke counting more than a short one, each point matched to one a little
  further on or back on the model (dynamic time warping, within a tenth of
  the line: a ん with a long descent and a small hump is not a し); where and how large the character
  is drawn tells ゃ from や, poorly. A kana with a dakuten or a handakuten is
  also compared as its base and its marks apart: the base against the base's
  model, the marks only as short strokes towards its upper right, wherever
  exactly (a hand puts で's dakuten above its corner, KanjiVG inside its
  curve, where it was taken for づ). On the kana written right from memory
  kept so far, 1136 in 1191 are recognised first (1121 with a stroke more or
  fewer costing half as much), 1186 among the first three; in the pad, 490 in
  506 (486, で taken for ご 9 times of 32, now once), measured in
  `backend.calibration`. The kana of an answer written wholly in the pad are
  kept, each with the kana it is in the answer as the learner left it, in
  `~/.local/share/hanamaru/pad-characters.jsonl`: what the recogniser is tuned
  on next (a test measures it on them). The distance does not tell a right recognition from a wrong one, so
  the pause, not a threshold, decides when a character is taken; only a
  scribble is refused.
- **One way to write an answer** (built, `AnswerPanel`, `HandwrittenWord`,
  `WritingBox`), the same in every exercise, each button where it always is,
  in the frame of what it changes:
  - **the answer**, under the choice of stylus or keyboard (`InputChoice`), in
    a frame with what changes its text: 小, ゛ and ゜ on the characters
    selected, else the last one, and ⌫ taking them away; under it, the kana
    the last one may be instead;
  - **the box written in**, in a frame with what changes its drawing: the last
    stroke taken away, the whole drawing rubbed out (the answer untouched),
    the drawing taken as it is; beside it, what it recognises, あ hiragana,
    ア katakana, 漢 kanji, chosen in one touch, the frame in that script's
    colour (blue, brown, green); a script that does not serve stays in its
    place, faded;
  - **typed**, the scripts in a row under the field, kanji left to the
    stylus;
  - at the end, "I don't know", then the answer beside it (on the right, it
    was under the tutor's button).

  With the stylus, the fields never open the screen's keyboard (it moved the
  page at each change of script), and no tool takes the focus.
- **Writing a word** from memory (a vocabulary answer, [`08`](08-vocabulary-practice.md)):
  its characters one after the other in the box, each shown small, as drawn,
  in its place, one place for each character expected; the learner may take
  the last one away, and says when the word is written; the script is
  theirs to choose, in kana each character recognised in the one chosen, so
  that a word written in the other is wrong; its dakuten and small kana are
  drawn, being what is corrected; no correction in between, all are corrected together then
  against the characters expected. A kanji's place is a larger box; a kanji
  is taken when the pen rests only once all its strokes are drawn: the pen
  pauses between its many strokes.
- **Writing a sentence** (a lesson's translation exercise,
  [`06`](06-curriculum-and-lessons.md)): each kana recognised goes at the end
  of the answer field, which can still be typed in; the other kana it may be
  are offered, to put in its place. The script chosen beside the box says
  what it recognises among: あ hiragana, ア katakana, 漢 the kanji the
  learner writes (only when there are some). Kana and kanji are never recognised together: a kanji begun looks
  like a kana (本 begun is ナ). A kanji is taken when the pen rests once it
  has as many strokes as the kanji it is closest to.
  Small kana are not recognised there: told from their large ones by size
  alone, they were taken for each other; a button of the answer, 小, makes
  the kana selected, else the last one, small (しや to しゃ), or large again.
  Two more add or take away a dakuten (゛, か to が) or a handakuten (゜, は to
  ぱ), for a kana taken before its marks were drawn. In a word, the small kana is written
  small, as it is judged against the character expected. A box whose kana
  is not among the three the recogniser finds closest is another kana (て
  written for ん, its one stroke within ん's tolerance): the word written is
  another, wrong, and the correction says what the box looks like; it is no
  review of the kana expected. A kana written
  wrong (or with strokes out of order) within a word is kept as a review of
  that kana's writing, so it comes back to be practised; one written right
  only when its writing was due, not to crowd its schedule.
- **Correction is deterministic, not by the AI**: the matcher
  (`StrokeMatcher`, in `shared`, run in the browser) judges each stroke as it
  ends, against the stroke expected next. Both are resampled to 32 points
  evenly spaced along them, and **shape and place are judged apart**, the place
  more leniently: a stroke drawn right but a little off is drawn right.
  - **Shape**: the mean distance between their points once their centres are
    put together, at most 8 units (of the 109-unit box) plus 12% of the
    expected stroke's length.
  - **Place**: the distance between their centres, at most 22 units.
  - **Direction**: backward only when its shape matches clearly best, closer
    than 0.8 times the forward one: a stroke near the same both ways (り's
    first, its hook drawn as long as its descent) is drawn right. On 3,689
    strokes written from memory, those really reversed are at most 0.55; two
    at about 0.95 (り, な) no longer are. A loop (a stroke ending about where it starts, as a handakuten's circle) has
    no direction: it is compared from any start, both ways round.
  - **Order**: wrong when a later stroke fits, shape and place together, better
    than the expected one; when the expected one fits too, the later one must
    fit twice as well, so that of two strokes alike (ほ's bars) the expected
    one drawn a little off is still taken for itself, while a dakuten's two
    strokes drawn the other way round are caught. Once the character is
    written, a stroke taken for the one right after it is judged with the
    next stroke drawn: the pair, their shared shift taken away, decides by
    where each is from the other. Hands put a dakuten a little off as a whole,
    its first stroke then nearer the second's place: on 266 voiced kana
    written from memory, this took the strokes wrongly out of order from 33
    to 5, those 5 really reversed (ジ's ticks).
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

- The tolerances, tuned on the attempts kept; and whether the stroke matcher
  too does better with dynamic time warping (the recogniser does) or the
  Fréchet distance on real strokes.
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
- How long the pen rests before a kana is taken (0.9 s), to tune in use: too
  short cuts a kana written slowly, too long slows a fluent hand.
