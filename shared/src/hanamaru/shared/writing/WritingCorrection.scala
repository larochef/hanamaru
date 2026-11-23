package hanamaru.shared.writing

/** Something wrong in a character written from memory; strokes counted from 1,
  * in the order they were drawn.
  */
enum WritingIssue {
  case WrongDirection(stroke: Int)

  /** The stroke drawn at this place is the character's stroke `isStroke`. */
  case WrongOrder(stroke: Int, isStroke: Int)

  /** Not the stroke expected here, in shape or in place. */
  case Misdrawn(stroke: Int)

  /** A stroke beyond the character's last. */
  case Extra(stroke: Int)

  /** Strokes left out: `count` of the character's `total`. */
  case Missing(count: Int, total: Int)
}

/** How a character written from memory went: right, right but for the order or
  * direction of some strokes, or wrong.
  */
enum WritingOutcome {
  case Right, StrokeOrder, Wrong
}

/** A character written from memory, judged once written: every stroke drawn
  * against the stroke expected at its place, then what is wrong in it.
  */
object WritingCorrection {

  /** Every stroke drawn judged, in the order drawn; a stroke beyond the
    * character's last is one too many.
    */
  def judged(
      drawn: List[List[DrawnPoint]],
      model: List[List[Point]]
  ): List[JudgedStroke] =
    drawn.zipWithIndex.map { (points, index) =>
      val judgement =
        if (index < model.size)
          StrokeMatcher.judge(points.map(_.point), model, index)
        else StrokeJudgement(StrokeVerdict.Misdrawn, 0)
      JudgedStroke(points, index, judgement)
    }

  /** The attempt of a character of `model`, written to its end. */
  def attempt(
      character: String,
      drawn: List[List[DrawnPoint]],
      model: List[List[Point]]
  ): WritingAttempt =
    WritingAttempt(
      character,
      WritingStep.FromMemory,
      judged(drawn, model),
      completed = drawn.size == model.size,
      expectedStrokes = model.size
    )

  def issues(attempt: WritingAttempt): List[WritingIssue] = {
    val expected = expectedOf(attempt)
    val perStroke = attempt.strokes.zipWithIndex.collect {
      case (stroke, index) if index >= expected => WritingIssue.Extra(index + 1)
      case (stroke, index)
          if stroke.judgement.verdict != StrokeVerdict.Accepted =>
        stroke.judgement.verdict match {
          case StrokeVerdict.WrongDirection =>
            WritingIssue.WrongDirection(index + 1)
          case StrokeVerdict.WrongOrder(isIt) =>
            WritingIssue.WrongOrder(index + 1, isIt)
          case _ => WritingIssue.Misdrawn(index + 1)
        }
    }
    val missing = expected - attempt.strokes.size
    perStroke ++ Option.when(missing > 0)(
      WritingIssue.Missing(missing, expected)
    )
  }

  def outcome(attempt: WritingAttempt): WritingOutcome = {
    val found = issues(attempt)
    val whole = attempt.completed && attempt.strokes.size == expectedOf(attempt)
    if (whole && found.isEmpty) {
      WritingOutcome.Right
    } else if (
      whole && found.forall {
        case WritingIssue.WrongDirection(_) | WritingIssue.WrongOrder(_, _) =>
          true
        case _ => false
      }
    ) {
      WritingOutcome.StrokeOrder
    } else {
      WritingOutcome.Wrong
    }
  }

  // attempts kept before the expected count was recorded were stopped at
  // their first wrong stroke: written to their end, they had every stroke
  private def expectedOf(attempt: WritingAttempt): Int =
    if (attempt.expectedStrokes > 0) attempt.expectedStrokes
    else attempt.strokes.size
}
