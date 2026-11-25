package hanamaru.shared.text

import sttp.tapir.Schema

/** A piece of a sentence as shown: its text in kana, whether it is what the
  * sentence is about (the word asked, blanked until answered; the grammar a
  * lesson explains), whether a space comes before it, the dictionary's word it
  * is, when it is one; and, for a word written with kanji, as it is written and
  * how it is shown, as the learner knows its kanji.
  */
final case class SentencePart(
    text: String,
    marked: Boolean,
    spaceBefore: Boolean,
    word: Option[PartWord] = None,
    written: Option[String] = None,
    shownAs: KanjiShown = KanjiShown.Kana
)

/** How a word written with kanji is shown: in kana, some of its kanji unknown;
  * in kanji with its reading above, its kanji being learned; in kanji, its
  * kanji all read.
  */
enum KanjiShown {
  case Kana, WithReading, Kanji
}

object KanjiShown {
  given Schema[KanjiShown] = Schema.derived
}

/** A word of a sentence as the dictionary gives it: its dictionary form, its
  * reading, its meanings in French (JMdict's, in no particular order, and not
  * for every word), and its first meaning in English.
  */
final case class PartWord(
    form: String,
    reading: String,
    french: String,
    english: String
)

object SentencePart {
  given Schema[SentencePart] = Schema.derived
}

object PartWord {
  given Schema[PartWord] = Schema.derived
}
