package hanamaru.shared.writing

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** The steps of learning to write a character. */
enum WritingStep {
  case Demonstration, Guided, FromMemory
}

/** A point of a drawn stroke: where in the box, how hard the pen pressed (0 to
  * 1), and when, in milliseconds since the stroke began.
  */
final case class DrawnPoint(
    x: Double,
    y: Double,
    pressure: Double,
    millis: Double
) {
  def point: Point = Point(x, y)
}

/** One stroke as the learner drew it, the stroke it was meant to be (counted
  * from 0), and how it was judged.
  */
final case class JudgedStroke(
    points: List[DrawnPoint],
    expected: Int,
    judgement: StrokeJudgement
)

/** A character written once, at one step, every stroke drawn kept, whether
  * accepted or not; `completed` when the character was written to its end.
  */
final case class WritingAttempt(
    character: String,
    step: WritingStep,
    strokes: List[JudgedStroke],
    completed: Boolean
)

object WritingAttempt {
  given JsonValueCodec[WritingAttempt] = JsonCodecMaker.make
  given Schema[WritingStep] = Schema.derived
  given Schema[DrawnPoint] = Schema.derived
  given Schema[StrokeVerdict] = Schema.derived
  given Schema[StrokeJudgement] = Schema.derived
  given Schema[JudgedStroke] = Schema.derived
  given Schema[WritingAttempt] = Schema.derived
}
