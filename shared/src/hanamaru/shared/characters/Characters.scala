package hanamaru.shared.characters

import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kanji.KanjiExercise
import hanamaru.shared.writing.*

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** The kana and kanji practised: My lesson's (those due, then the unit's new
  * ones), or one unit's kanji gone over out of turn, from its page.
  */
enum CharactersOf {
  case MyLesson
  case OfUnit(point: String)
}

object CharactersOf {
  given Schema[CharactersOf] = Schema.derived
}

/** What a kana or kanji exercise asks: the character read, or written. */
enum CharacterAsked {

  /** The character shown (a kanji, in a word): its reading typed. */
  case Reading

  /** Its reading given (a kanji's, a word's reading and meaning): the character
    * written with the stylus.
    */
  case Writing
}

/** An exercise on the symbols of a lesson session: a kana, or a kanji. */
enum CharacterExercise {

  /** A kana read (shown, its reading typed in romaji) or written (from its
    * reading); new, it is shown first, then written over its model. A katakana
    * is read in a word written with it, when there is one (`inWord`): loanwords
    * are where katakana are met.
    */
  case Kana(
      character: String,
      asked: CharacterAsked,
      isNew: Boolean,
      inWord: Option[DictionaryWord] = None
  )

  case Kanji(exercise: KanjiExercise)

  /** A character new to the learner, a kana or a kanji, learned in a focus of
    * its own ([[FocusState]]) before it is reviewed.
    */
  case Focus(character: String)
}

/** How an answer is kept: as a review of its card; or only judged, in a focus,
  * kept as one review once the focus is over.
  */
enum AnswerKept {
  case AsReview, InFocus
}

/** How a card went: right, or missed. */
enum CardResult {
  case Right, Missed
}

/** A card of a focus as it went. */
final case class FocusAnswer(exercise: CharacterExercise, result: CardResult)

/** A focus on a new character: about ten cards in one sitting, its reading and
  * its writing in turn, a card missed coming back a few times; the cards
  * answered so far. Once over, its reading and writing are reviewed, from the
  * next day on.
  */
final case class FocusState(character: String, answers: List[FocusAnswer])

/** A kana's reading as typed in romaji, none for "I don't know"; or, read in a
  * word, the word's reading as typed (in kana).
  */
final case class KanaReadingAnswer(
    character: String,
    typed: Option[String],
    word: Option[Long] = None,
    kept: AnswerKept = AnswerKept.AsReview
)

object CharacterAsked {
  given Schema[CharacterAsked] = Schema.derived
}

object CharacterExercise {
  given JsonValueCodec[List[CharacterExercise]] = JsonCodecMaker.make
  given JsonValueCodec[Option[CharacterExercise]] = JsonCodecMaker.make
  given Schema[CharacterExercise] = Schema.derived
}

object AnswerKept {
  given Schema[AnswerKept] = Schema.derived
}

object CardResult {

  /** How a character written went: missed when wrong or not remembered; its
    * strokes out of order, it is still written, as its review grades it.
    */
  def of(attempt: WritingAttempt): CardResult =
    WritingCorrection.outcome(attempt) match {
      case WritingOutcome.Wrong                              => Missed
      case WritingOutcome.Right | WritingOutcome.StrokeOrder => Right
    }

  given Schema[CardResult] = Schema.derived
}

object FocusState {
  given JsonValueCodec[FocusState] = JsonCodecMaker.make
  given Schema[FocusAnswer] = Schema.derived
  given Schema[FocusState] = Schema.derived
}

object KanaReadingAnswer {
  given JsonValueCodec[KanaReadingAnswer] = JsonCodecMaker.make
  given Schema[KanaReadingAnswer] = Schema.derived
}
