package hanamaru.shared.placement

import hanamaru.shared.kana.Script
import hanamaru.shared.writing.WritingAttempt

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** A question of the kana placement test. */
enum PlacementQuestion {

  /** A kana shown, its reading typed. */
  case Reading(character: String)

  /** A reading given, its kana written from memory with the stylus. */
  case Writing(character: String)
}

/** An answer to the question asked, or the whole of a script given up. */
enum PlacementAnswer {

  /** The reading typed, none for "I don't know", and how long it took. */
  case Reading(character: String, typed: Option[String], millis: Double)

  case Writing(attempt: WritingAttempt)

  /** "I don't know this script": its questions are not asked. */
  case SkipScript(script: Script)
}

/** A reading answered: whether it was right, and the kana's reading. */
final case class ReadingResult(
    character: String,
    correct: Boolean,
    romaji: String
)

/** How far the learner went in one script: kana read right out of those asked,
  * kana written right out of those asked, and the first row not mastered, named
  * by its first kana (none when every row was).
  */
final case class ScriptResult(
    script: Script,
    skipped: Boolean,
    read: Int,
    readAsked: Int,
    written: Int,
    writtenAsked: Int,
    startAt: Option[String]
)

/** Where the kana placement test stands: never taken, under way with its next
  * question, or finished; what each script showed so far, and the last reading
  * answered.
  */
final case class PlacementState(
    started: Boolean,
    question: Option[PlacementQuestion],
    scripts: List[ScriptResult],
    lastReading: Option[ReadingResult]
) {
  def finished: Boolean = started && question.isEmpty
}

object PlacementState {
  given JsonValueCodec[PlacementState] = JsonCodecMaker.make
  given Schema[PlacementQuestion] = Schema.derived
  given Schema[ReadingResult] = Schema.derived
  given Schema[ScriptResult] = Schema.derived
  given Schema[PlacementState] = Schema.derived
}

object PlacementAnswer {
  given JsonValueCodec[PlacementAnswer] = JsonCodecMaker.make
  given Schema[PlacementAnswer] = Schema.derived
}
