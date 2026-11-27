package hanamaru.shared.vocabulary

import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.learner.Grade
import hanamaru.shared.text.SentencePart
import hanamaru.shared.writing.WritingAttempt

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** The ways a word is asked. */
enum Direction {

  /** Its meaning in French given, the word typed in Japanese. */
  case Production

  /** The word given, its meaning typed in French. */
  case Meaning

  /** Its meaning and reading given, the word written in kanji from memory, as
    * [[SeriesWord.inKanji]] says: a known word whose kanji the learner now
    * writes.
    */
  case InKanji
}

/** What a series asks of a word: itself and its meaning, the kanji it is
  * written with encouraged; or, a known word whose kanji the learner writes,
  * only to be written in kanji.
  */
enum WordAsked {
  case BothWays, InKanji
}

/** The words a series is on: My lesson's (those due, then the unit's new ones);
  * a series the learner asks for, filled up with words met; one unit's, from
  * its page.
  */
enum SeriesOf {
  case MyLesson, Free
  case OfUnit(point: String)
}

object SeriesOf {
  given Schema[SeriesOf] = Schema.derived
}

/** A word of a series: new to the learner (or, `asked` in kanji, new to be
  * written in kanji), or due for review; asked for itself in a sentence once it
  * is stable enough (`inSentence`); written by hand as `inKanji` when the
  * learner writes some of its kanji (the others in kana: 会しゃ), else in kana.
  */
final case class SeriesWord(
    word: DictionaryWord,
    isNew: Boolean,
    inSentence: Boolean = false,
    inKanji: Option[String] = None,
    asked: WordAsked = WordAsked.BothWays
)

/** A step of a series, as the server planned it: a new word shown, or a word
  * asked one way; `again` when a new word is asked for itself a second time, at
  * the end (left out when the learner already knows it); `attempt`, how many
  * times it came back after being missed.
  */
enum SeriesTask {
  case Intro(word: SeriesWord)
  case Ask(
      word: SeriesWord,
      direction: Direction,
      again: Boolean = false,
      attempt: Int = 0
  )
}

/** A sentence written for a word by the tutor and checked by the code: its
  * pieces, as written, and in French.
  */
final case class WordSentence(
    word: Long,
    parts: List[SentencePart],
    japanese: String,
    french: String
)

/** An answer: the word, the way it was asked, what was typed (or, written with
  * the stylus, the characters written), how long it took, whether the learner
  * said they already knew this new word, written with the stylus, each
  * character as it was written, in order, and the sentence it was asked in.
  */
final case class VocabularyAnswer(
    word: Long,
    direction: Direction,
    typed: String,
    millis: Double,
    alreadyKnown: Boolean,
    written: List[WritingAttempt] = Nil,
    sentence: Option[String] = None,
    attempt: Int = 0
)

/** How an answer was judged. */
enum Verdict {
  case Right

  /** Nearly the word: a long vowel, a small つ, ず for づ… */
  case NearMiss

  /** Another word with this meaning, not the one asked. */
  case OtherWord(word: String)

  case Wrong

  /** Not found among the meanings: the learner says whether it meant the same.
    */
  case Unsure
}

/** An answer judged: its verdict, the grade it was given (none while the
  * learner is to say), whether the tutor recognised it as one of the meanings,
  * which it names, missed, how many questions later it is asked again, and,
  * answered in kana where the learner writes some of the word's kanji, the word
  * as they write it (`inKanji`), to remind them.
  */
final case class VocabularyVerdict(
    verdict: Verdict,
    grade: Option[Grade],
    tutorMatched: Option[String],
    comesBack: Option[Int] = None,
    inKanji: Option[String] = None
)

/** The learner's own word on an answer the code could not judge. */
final case class SelfGrade(
    word: Long,
    direction: Direction,
    typed: String,
    right: Boolean,
    attempt: Int = 0
)

/** Missed, how many questions later a word is asked again; none, not again. */
final case class ComesBack(after: Option[Int])

object Direction {
  given Schema[Direction] = Schema.derived
}

object WordAsked {
  given Schema[WordAsked] = Schema.derived
}

object WordSentence {
  given JsonValueCodec[Option[WordSentence]] = JsonCodecMaker.make
  given Schema[WordSentence] = Schema.derived
}

object SeriesWord {
  given Schema[SeriesWord] = Schema.derived
}

object SeriesTask {
  given JsonValueCodec[List[SeriesTask]] = JsonCodecMaker.make
  given Schema[SeriesTask] = Schema.derived
}

object ComesBack {
  given JsonValueCodec[ComesBack] = JsonCodecMaker.make
  given Schema[ComesBack] = Schema.derived
}

object VocabularyAnswer {
  given JsonValueCodec[VocabularyAnswer] = JsonCodecMaker.make
  given Schema[VocabularyAnswer] = Schema.derived
}

object Verdict {
  given Schema[Verdict] = Schema.derived
}

object VocabularyVerdict {
  given JsonValueCodec[VocabularyVerdict] = JsonCodecMaker.make
  given Schema[VocabularyVerdict] = Schema.derived
}

object SelfGrade {
  given JsonValueCodec[SelfGrade] = JsonCodecMaker.make
  given Schema[SelfGrade] = Schema.derived
}
