package hanamaru.shared.lessons

import hanamaru.shared.text.SentencePart

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** The ways a grammar point is practised in a sentence. */
enum ExerciseKind {

  /** The point's particle blanked, chosen among others. */
  case Particle

  /** The sentence given in French, written in Japanese. */
  case Translation
}

/** An exercise on a lesson's point: the sentence laid out in kana (its marked
  * parts are what is blanked, for a particle), as written, in French, the
  * choices offered for a particle, and the answer expected: the particle, or
  * the sentence in kana.
  */
final case class LessonExercise(
    point: String,
    kind: ExerciseKind,
    parts: List[SentencePart],
    japanese: String,
    french: String,
    choices: List[String],
    expected: String
)

/** An exercise answered: the particle chosen, or the sentence typed. */
final case class ExerciseAnswer(exercise: LessonExercise, answer: String)

/** An answer judged: whether it is right, and when the code could not tell by
  * itself, by the tutor (`byTutor`), with its correction (in kana, spaced) and
  * its explanation, in French.
  */
final case class ExerciseVerdict(
    right: Boolean,
    byTutor: Boolean,
    correction: Option[List[SentencePart]],
    explanation: Option[String]
)

/** A lesson's exercises done: how many answers were right. */
final case class ExercisesDone(point: String, right: Int, total: Int)

object ExerciseKind {
  given Schema[ExerciseKind] = Schema.derived
}

object LessonExercise {
  given JsonValueCodec[List[LessonExercise]] = JsonCodecMaker.make
  given Schema[LessonExercise] = Schema.derived
}

object ExerciseAnswer {
  given JsonValueCodec[ExerciseAnswer] = JsonCodecMaker.make
  given Schema[ExerciseAnswer] = Schema.derived
}

object ExerciseVerdict {
  given JsonValueCodec[ExerciseVerdict] = JsonCodecMaker.make
  given Schema[ExerciseVerdict] = Schema.derived
}

object ExercisesDone {
  given JsonValueCodec[ExercisesDone] = JsonCodecMaker.make
  given Schema[ExercisesDone] = Schema.derived
}
