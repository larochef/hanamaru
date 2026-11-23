package hanamaru.shared.vocabulary

import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.learner.Grade
import hanamaru.shared.writing.WritingAttempt

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** The two ways a word is asked. */
enum Direction {

  /** Its meaning in French given, the word typed in Japanese. */
  case Production

  /** The word given, its meaning typed in French. */
  case Meaning
}

/** A word of a series: new to the learner, or due for review; asked for itself
  * in a sentence once it is stable enough (`inSentence`).
  */
final case class SeriesWord(
    word: DictionaryWord,
    isNew: Boolean,
    inSentence: Boolean = false
)

/** A piece of a sentence as shown: its text in kana, whether it is the word
  * asked (blanked until answered), and whether a space comes before it.
  */
final case class SentencePart(
    text: String,
    blank: Boolean,
    spaceBefore: Boolean
)

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
    sentence: Option[String] = None
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
  * learner is to say), and whether the tutor recognised it as one of the
  * meanings, which it names.
  */
final case class VocabularyVerdict(
    verdict: Verdict,
    grade: Option[Grade],
    tutorMatched: Option[String]
)

/** The learner's own word on an answer the code could not judge. */
final case class SelfGrade(
    word: Long,
    direction: Direction,
    typed: String,
    right: Boolean
)

object Direction {
  given Schema[Direction] = Schema.derived
}

object SentencePart {
  given Schema[SentencePart] = Schema.derived
}

object WordSentence {
  given JsonValueCodec[Option[WordSentence]] = JsonCodecMaker.make
  given Schema[WordSentence] = Schema.derived
}

object SeriesWord {
  given JsonValueCodec[List[SeriesWord]] = JsonCodecMaker.make
  given Schema[SeriesWord] = Schema.derived
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
