package hanamaru.shared.lessons

import hanamaru.shared.kanji.KanjiExercise
import hanamaru.shared.text.SentencePart

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** The ways a grammar point is practised in a sentence. */
enum ExerciseKind {

  /** Every particle learned so far blanked in the sentence, each typed. */
  case Particle

  /** The point's form (an ending: ます, ました, たい…) blanked, chosen among the
    * others of its kind, the French saying which.
    */
  case Form

  /** The sentence given in French, written in Japanese. */
  case Translation

  /** The sentence given in Japanese, its meaning written in French: judged by
    * the tutor, or, without it, by the learner.
    */
  case Meaning

  /** A word of the sentence whose kanji is being learned, the kanji written
    * with the stylus: a review of the kanji's writing, not of the point.
    */
  case KanjiWriting
}

/** An exercise on a lesson's point: the sentence laid out in kana (its marked
  * parts are what is blanked, for particles or a form), as written, in French,
  * the choices offered for a form, and the answer expected: the particles, one
  * per blank, spaced, the form, or the sentence in kana; for a translation, the
  * kanji the learner writes, which the stylus also recognises.
  */
final case class LessonExercise(
    point: String,
    kind: ExerciseKind,
    parts: List[SentencePart],
    japanese: String,
    french: String,
    choices: List[String],
    expected: String,
    kanji: Option[KanjiExercise] = None,
    wordsInKatakana: Int = 0,
    kanjiWritten: List[String] = Nil
)

/** An exercise answered: the particles typed, one per blank, spaced (in kana or
  * romaji, a blank left empty empty), the form chosen, or the sentence typed.
  */
final case class ExerciseAnswer(exercise: LessonExercise, answer: String)

/** An answer judged: whether it is right, and when the code could not tell by
  * itself, by the tutor (`byTutor`), with its correction (in kana, spaced) and
  * its explanation, in French; or left to the learner (`toSelfGrade`: a
  * meaning, the tutor away), shown the expected one.
  */
final case class ExerciseVerdict(
    right: Boolean,
    byTutor: Boolean,
    correction: Option[List[SentencePart]],
    explanation: Option[String],
    toSelfGrade: Boolean = false
)

/** What exercises are on: a lesson's point, recorded as its review once done;
  * or everything learned so far, freely, recorded as nothing.
  */
enum ExercisesOf {
  case Lesson(point: String)
  case Free
}

/** An exercise of a free practice: the one at `index` of `total`, written by
  * the tutor while the learner answered those before.
  */
final case class FreeExercise(
    practice: Long,
    index: Int,
    total: Int,
    exercise: LessonExercise
)

/** A lesson's exercises done: each one's kind, and whether it was right. */
final case class ExercisesDone(point: String, outcomes: List[ExerciseOutcome])

/** An exercise done: its kind, and whether it was answered right. */
final case class ExerciseOutcome(kind: ExerciseKind, right: Boolean)

/** How a lesson's exercises went, as counted in the point's review: right out
  * of total (a kanji written is the kanji's review, not counted here).
  */
final case class ExercisesTally(right: Int, total: Int)

object ExercisesOf {
  given Schema[ExercisesOf] = Schema.derived
}

object ExerciseKind {
  given Schema[ExerciseKind] = Schema.derived
}

object LessonExercise {
  given JsonValueCodec[List[LessonExercise]] = JsonCodecMaker.make
  given Schema[LessonExercise] = Schema.derived
}

object FreeExercise {
  given JsonValueCodec[FreeExercise] = JsonCodecMaker.make
  given Schema[FreeExercise] = Schema.derived
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

object ExerciseOutcome {
  given Schema[ExerciseOutcome] = Schema.derived
}

object ExercisesTally {
  given JsonValueCodec[ExercisesTally] = JsonCodecMaker.make
  given Schema[ExercisesTally] = Schema.derived
}
