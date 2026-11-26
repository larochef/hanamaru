package hanamaru.shared.kanji

import hanamaru.shared.characters.{AnswerKept, CharacterAsked}
import hanamaru.shared.dictionary.DictionaryWord

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** A kanji: its school grade (1 to 6; 8 for the rest of the jōyō list), its
  * stroke count, its frequency rank in newspapers, its readings (on, in
  * katakana; kun, in hiragana, the okurigana after a dot), and its meanings, in
  * French when KANJIDIC2 has them (`inEnglish` otherwise).
  */
final case class Kanji(
    literal: String,
    grade: Option[Int],
    strokes: Int,
    frequency: Option[Int],
    on: List[String],
    kun: List[String],
    meanings: List[String],
    inEnglish: Boolean
)

/** A kanji with words of the core vocabulary written with it, the most basic
  * first.
  */
final case class KanjiDetail(kanji: Kanji, words: List[DictionaryWord])

object Kanji {
  given JsonValueCodec[List[Kanji]] = JsonCodecMaker.make
  given Schema[Kanji] = Schema.derived
}

object KanjiDetail {
  given JsonValueCodec[KanjiDetail] = JsonCodecMaker.make
  given Schema[KanjiDetail] = Schema.derived
}

/** A kanji exercise of a lesson session: the kanji, the word it is asked in (a
  * word the learner knows), what is asked, and whether it is asked for the
  * first time (the kanji then shown first, and written guided first).
  */
final case class KanjiExercise(
    kanji: Kanji,
    word: DictionaryWord,
    asked: CharacterAsked,
    isNew: Boolean
)

/** A kanji's reading in a word, as typed (in kana). */
final case class KanjiReadingAnswer(
    kanji: String,
    word: Long,
    typed: String,
    kept: AnswerKept = AnswerKept.AsReview
)

/** A reading judged: right or not, the word's reading, and, the word wrong but
  * the kanji's own part of it right, that part (`kanjiRead`): the kanji is read
  * right.
  */
final case class KanjiReadingVerdict(
    right: Boolean,
    reading: String,
    kanjiRead: Option[String] = None
)

object KanjiExercise {
  given JsonValueCodec[List[KanjiExercise]] = JsonCodecMaker.make
  given Schema[KanjiExercise] = Schema.derived
}

object KanjiReadingAnswer {
  given JsonValueCodec[KanjiReadingAnswer] = JsonCodecMaker.make
  given Schema[KanjiReadingAnswer] = Schema.derived
}

object KanjiReadingVerdict {
  given JsonValueCodec[KanjiReadingVerdict] = JsonCodecMaker.make
  given Schema[KanjiReadingVerdict] = Schema.derived
}
