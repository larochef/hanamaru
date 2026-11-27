package hanamaru.shared.questions

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** Where a question is asked from. */
enum AskedFrom {

  /** The questions page: a question of the learner's own. */
  case Questions

  /** A grammar point's lesson, read. */
  case Lesson

  /** An exercise being answered: its answer not to be given away. */
  case Exercise

  /** An exercise answered, its correction shown. */
  case Correction
}

/** What the learner had before them when they asked: where, the grammar point
  * it was on, the sentence, with its French, and the word (as written), if any;
  * on an exercise, what was asked of them, in words, then what they answered
  * and the verdict shown, in words.
  */
final case class QuestionContext(
    from: AskedFrom,
    point: Option[String] = None,
    sentence: Option[String] = None,
    french: Option[String] = None,
    word: Option[String] = None,
    task: Option[String] = None,
    answer: Option[String] = None,
    verdict: Option[String] = None
)

/** A question to the tutor, asked from `context`; `follows`, the question it
  * goes on from, in a conversation.
  */
final case class TutorQuestion(
    context: QuestionContext,
    question: String,
    follows: Option[Long] = None
)

/** A question kept, and the tutor's answer: none when it could not give one
  * (away, or its answer unreadable), the question kept all the same.
  */
final case class AskedQuestion(
    id: Long,
    askedAt: Long,
    context: QuestionContext,
    question: String,
    follows: Option[Long],
    answer: Option[String]
)

object AskedFrom {
  given Schema[AskedFrom] = Schema.derived
}

object QuestionContext {
  given JsonValueCodec[QuestionContext] = JsonCodecMaker.make
  given Schema[QuestionContext] = Schema.derived
}

object TutorQuestion {
  given JsonValueCodec[TutorQuestion] = JsonCodecMaker.make
  given Schema[TutorQuestion] = Schema.derived
}

object AskedQuestion {
  given JsonValueCodec[AskedQuestion] = JsonCodecMaker.make
  given JsonValueCodec[List[AskedQuestion]] = JsonCodecMaker.make
  given Schema[AskedQuestion] = Schema.derived
}
