package hanamaru.shared.lessons

import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kana.Script
import hanamaru.shared.kanji.Kanji

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** Where a unit's word stands: never met, being learned, or acquired (its
  * production past its first steps).
  */
enum WordStanding {
  case New, Learning, Acquired
}

/** Where a unit's kanji stands: not met yet, being read, read (its reading past
  * its first steps), or written too.
  */
enum KanjiStanding {
  case NotMet, Reading, Read, Written
}

/** A word's meaning as a list shows it: `short`, a few words; `senses`, each of
  * its senses whole, when `short` leaves some out (none when it says all).
  */
final case class ShortMeaning(short: String, senses: List[String])

final case class RecapWord(
    word: DictionaryWord,
    standing: WordStanding,
    meaning: ShortMeaning
)

final case class RecapKanji(kanji: Kanji, standing: KanjiStanding)

/** A unit gathered to be gone over: its point (whose lesson is read again and
  * practised) and the first paragraph of its explanation, its theme, its words
  * and its symbols (the kana of its script, for the first two; else its kanji),
  * each where it stands; whether it is acquired; and the units that can be gone
  * over, those up to the one under way, in order.
  */
final case class UnitRecap(
    point: GrammarPoint,
    introduction: String,
    theme: String,
    words: List[RecapWord],
    kana: Option[Script],
    kanji: List[RecapKanji],
    acquired: Boolean,
    units: List[GrammarPoint]
)

object UnitRecap {
  given JsonValueCodec[UnitRecap] = JsonCodecMaker.make
  given Schema[WordStanding] = Schema.derived
  given Schema[KanjiStanding] = Schema.derived
  given Schema[ShortMeaning] = Schema.derived
  given Schema[RecapWord] = Schema.derived
  given Schema[RecapKanji] = Schema.derived
  given Schema[UnitRecap] = Schema.derived
}
