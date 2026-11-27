package hanamaru.shared.lessons

import hanamaru.shared.characters.CharactersOf
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kana.Script
import hanamaru.shared.text.SentencePart
import hanamaru.shared.vocabulary.SeriesOf

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
  * before and after it in the curriculum, whether reading texts are written for
  * it, and its unit's theme and words.
  */
final case class GrammarLesson(
    point: GrammarPoint,
    requires: List[GrammarPoint],
    explanation: List[String],
    examples: List[LessonExample],
    previous: Option[GrammarPoint],
    next: Option[GrammarPoint],
    hasText: Boolean,
    theme: String,
    vocabulary: List[DictionaryWord]
)

/** The symbols a unit teaches: the hiragana, the katakana, or kanji. */
enum SymbolKind {
  case Hiragana, Katakana, Kanji
}

object SymbolKind {
  def fromScript(script: Script): SymbolKind = script match {
    case Script.Hiragana => Hiragana
    case Script.Katakana => Katakana
  }

  given Schema[SymbolKind] = Schema.derived
}

/** A unit's symbols as shown: their kind, those listed (kanji; the kana are too
  * many to list), how many are learned, out of how many, and whether all are.
  */
final case class SymbolsProgress(
    kind: SymbolKind,
    listed: List[String],
    learned: Int,
    total: Int,
    done: Boolean
)

object SymbolsProgress {
  given Schema[SymbolsProgress] = Schema.derived
}

/** What the unit under way waits for, first: its kana to be met (its words are
  * written with them), its words, its lesson, or its symbols to be learned;
  * acquired, the learner to move on from it.
  */
enum UnitWaitsFor {
  case KanaToMeet, Words, Lesson, Symbols, MovingOn
}

object UnitWaitsFor {
  given Schema[UnitWaitsFor] = Schema.derived
}

/** An acquired unit the learner may move on from: it and the units right after
  * it acquired too, left together, and the unit that comes next (none past the
  * last).
  */
final case class UnitOnward(
    acquired: List[GrammarPoint],
    next: Option[GrammarPoint]
)

object UnitOnward {
  given Schema[UnitOnward] = Schema.derived
}

/** The unit under way: its point, its theme, its words, how many of them are
  * stable and how many must be (and whether enough are), whether its point is
  * practised, its symbols, and what it waits for; it is acquired once the point
  * is practised, enough words are stable and every symbol is learned, and then
  * stays the unit under way, with where the learner may go on to (`onward`),
  * until the learner moves on.
  */
final case class UnitProgress(
    point: GrammarPoint,
    theme: String,
    words: List[DictionaryWord],
    stable: Int,
    wordsNeeded: Int,
    wordsAcquired: Boolean,
    practised: Boolean,
    symbols: Option[SymbolsProgress],
    waitsFor: UnitWaitsFor,
    onward: Option[UnitOnward]
)

object UnitProgress {
  given JsonValueCodec[Option[UnitProgress]] = JsonCodecMaker.make
  given Schema[UnitProgress] = Schema.derived
}

/** A reading text written for a lesson: its sentences, each laid out as an
  * example is.
  */
final case class LessonText(sentences: List[LessonExample])

/** A lesson session planned: the points to review, first; then the words to
  * practise in a series, if any; then the lesson to learn, the unit under way's
  * when its point was never practised (none once the curriculum is learned);
  * then the kana or kanji to practise, if any. `inKanji`: the known words the
  * series asks to be written in kanji for the first time, as asked.
  */
final case class LessonPlan(
    reviews: List[GrammarPoint],
    series: Option[SeriesOf],
    next: Option[GrammarPoint],
    characters: Option[CharactersOf],
    inKanji: List[String] = Nil
) {
  def isEmpty: Boolean =
    reviews.isEmpty && series.isEmpty && next.isEmpty && characters.isEmpty
}

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
