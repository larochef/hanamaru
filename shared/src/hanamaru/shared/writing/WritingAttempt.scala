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
  * accepted or not; `completed` when the character was written to its end, with
  * as many strokes as it has; `expectedStrokes`, how many it has (0 in attempts
  * kept before it was recorded); `forgotten` when the learner said they did not
  * remember it, the strokes drawn till then kept, none of them a try at its
  * shape; `looksLike`, the characters it was recognised as, closest first, when
  * it looks like other characters rather than itself (ん drawn as て, in a word
  * written box by box), else none.
  */
final case class WritingAttempt(
    character: String,
    step: WritingStep,
    strokes: List[JudgedStroke],
    completed: Boolean,
    expectedStrokes: Int = 0,
    forgotten: Boolean = false,
    looksLike: List[String] = Nil
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
