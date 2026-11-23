package hanamaru.shared.dictionary

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** A sense of a word in one language (`fre`, `eng`): its parts of speech, as
  * JMdict's codes (given on English senses only), and its glosses.
  */
final case class Meaning(
    language: String,
    partsOfSpeech: List[String],
    glosses: List[String]
)

/** A word of the core vocabulary: its JMdict number, the JLPT level of the list
  * it is learned from (5, the first words, to 1), its written forms and
  * readings in JMdict's order, whether it is usually written in kana, and its
  * senses.
  */
final case class DictionaryWord(
    id: Long,
    level: Int,
    writings: List[String],
    readings: List[String],
    usuallyKana: Boolean,
    meanings: List[Meaning]
)

object Meaning {
  given Schema[Meaning] = Schema.derived
}

object DictionaryWord {
  given JsonValueCodec[List[DictionaryWord]] = JsonCodecMaker.make
  given Schema[DictionaryWord] = Schema.derived
}
