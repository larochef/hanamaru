package hanamaru.shared.lessons

import hanamaru.shared.text.SentencePart

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** A grammar point of the curriculum, as listed: its JLPT level, its title (in
  * French) and the pattern it teaches.
  */
final case class GrammarPoint(
    id: String,
    level: Int,
    title: String,
    pattern: String
)

/** An example of a lesson: the sentence laid out in kana, the grammar it shows
  * marked, and its French translation.
  */
final case class LessonExample(parts: List[SentencePart], french: String)

/** A grammar point's lesson: the point, the points it relies on, its
  * explanation (in French, paragraph by paragraph), its examples, the points
  * before and after it in the curriculum, and whether reading texts are written
  * for it.
  */
final case class GrammarLesson(
    point: GrammarPoint,
    requires: List[GrammarPoint],
    explanation: List[String],
    examples: List[LessonExample],
    previous: Option[GrammarPoint],
    next: Option[GrammarPoint],
    hasText: Boolean
)

/** A reading text written for a lesson: its sentences, each laid out as an
  * example is.
  */
final case class LessonText(sentences: List[LessonExample])

/** A lesson session planned: the points whose review is due, first, then the
  * next point to learn (none once the curriculum is learned).
  */
final case class LessonPlan(
    reviews: List[GrammarPoint],
    next: Option[GrammarPoint]
)

object LessonPlan {
  given JsonValueCodec[LessonPlan] = JsonCodecMaker.make
  given Schema[LessonPlan] = Schema.derived
}

object LessonText {
  given JsonValueCodec[Option[LessonText]] = JsonCodecMaker.make
  given Schema[LessonText] = Schema.derived
}

object GrammarPoint {
  given JsonValueCodec[List[GrammarPoint]] = JsonCodecMaker.make
  given Schema[GrammarPoint] = Schema.derived
}

object LessonExample {
  given Schema[LessonExample] = Schema.derived
}

object GrammarLesson {
  given JsonValueCodec[GrammarLesson] = JsonCodecMaker.make
  given Schema[GrammarLesson] = Schema.derived
}
