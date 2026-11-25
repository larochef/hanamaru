package hanamaru.shared.characters

import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kanji.KanjiExercise

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

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
}

/** A kana's reading as typed in romaji, none for "I don't know"; or, read in a
  * word, the word's reading as typed (in kana).
  */
final case class KanaReadingAnswer(
    character: String,
    typed: Option[String],
    word: Option[Long] = None
)

object CharacterAsked {
  given Schema[CharacterAsked] = Schema.derived
}

object CharacterExercise {
  given JsonValueCodec[List[CharacterExercise]] = JsonCodecMaker.make
  given Schema[CharacterExercise] = Schema.derived
}

object KanaReadingAnswer {
  given JsonValueCodec[KanaReadingAnswer] = JsonCodecMaker.make
  given Schema[KanaReadingAnswer] = Schema.derived
}
