package hanamaru.shared.placement

import hanamaru.shared.lessons.GrammarPoint
import hanamaru.shared.text.SentencePart

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** A grammar placement question: the point, the question's number, the sentence
  * laid out in kana (its marked parts are the blank), its French, and the
  * choices for the blank.
  */
final case class GrammarQuestion(
    point: GrammarPoint,
    number: Int,
    parts: List[SentencePart],
    french: String,
    choices: List[String]
)

/** An answer to the grammar placement test. */
enum GrammarPlacementAnswer {

  /** A choice for a question, none for "I don't know". */
  case Chosen(point: String, number: Int, choice: Option[String])

  /** The test given up: no grammar known. */
  case GiveUp
}

/** A point the test decided: passed (both its questions right) or not. */
final case class GrammarPointResult(point: GrammarPoint, passed: Boolean)

/** Where the grammar placement test stands: whether it was started and is over,
  * the question asked, the points decided, and the lesson to start with (none
  * once every point is passed).
  */
final case class GrammarPlacementState(
    started: Boolean,
    finished: Boolean,
    question: Option[GrammarQuestion],
    results: List[GrammarPointResult],
    firstLesson: Option[GrammarPoint]
)

object GrammarQuestion {
  given Schema[GrammarQuestion] = Schema.derived
}

object GrammarPlacementAnswer {
  given JsonValueCodec[GrammarPlacementAnswer] = JsonCodecMaker.make
  given Schema[GrammarPlacementAnswer] = Schema.derived
}

object GrammarPointResult {
  given Schema[GrammarPointResult] = Schema.derived
}

object GrammarPlacementState {
  given JsonValueCodec[GrammarPlacementState] = JsonCodecMaker.make
  given Schema[GrammarPlacementState] = Schema.derived
}
