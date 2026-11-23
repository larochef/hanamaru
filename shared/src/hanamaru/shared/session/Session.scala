package hanamaru.shared.session

import hanamaru.shared.placement.ReadingResult
import hanamaru.shared.writing.WritingAttempt

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** What the learner declares as a session starts: the minutes available, and
  * whether the stylus is at hand.
  */
final case class SessionStart(minutes: Int, stylus: Boolean)

/** An exercise of a session. */
enum SessionExercise {

  /** A kana shown, its reading typed. */
  case Reading(character: String)

  /** A reading given, its kana written from memory. */
  case Writing(character: String)

  /** A kana met for the first time: shown, then written guided and from memory
    * with the stylus, or read without it.
    */
  case NewKana(character: String, stylus: Boolean)
}

/** An answer to the exercise asked. */
enum SessionAnswer {

  /** The reading typed, none for "I don't know", and how long it took. */
  case Reading(character: String, typed: Option[String], millis: Double)

  case Writing(attempt: WritingAttempt)
}

/** What a session did: reviews given, those right, and the kana it brought in,
  * in order.
  */
final case class SessionSummary(
    reviewed: Int,
    right: Int,
    introduced: List[String]
)

/** A session under way, or over when it asks no exercise: when its time ends,
  * in milliseconds since the epoch, what it did, and the last reading answered.
  */
final case class SessionState(
    id: Long,
    exercise: Option[SessionExercise],
    endsAt: Long,
    summary: SessionSummary,
    lastReading: Option[ReadingResult]
)

object SessionStart {
  given JsonValueCodec[SessionStart] = JsonCodecMaker.make
  given Schema[SessionStart] = Schema.derived
}

object SessionExercise {
  given JsonValueCodec[SessionExercise] = JsonCodecMaker.make
  given Schema[SessionExercise] = Schema.derived
}

object SessionAnswer {
  given JsonValueCodec[SessionAnswer] = JsonCodecMaker.make
  given Schema[SessionAnswer] = Schema.derived
}

object SessionSummary {
  given Schema[SessionSummary] = Schema.derived
}

object SessionState {
  given JsonValueCodec[SessionState] = JsonCodecMaker.make
  given Schema[SessionState] = Schema.derived
}
