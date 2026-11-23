package hanamaru.shared.text

import sttp.tapir.Schema

/** A piece of a sentence as shown: its text in kana, whether it is what the
  * sentence is about (the word asked, blanked until answered; the grammar a
  * lesson explains), whether a space comes before it, and the dictionary's word
  * it is, when it is one.
  */
final case class SentencePart(
    text: String,
    marked: Boolean,
    spaceBefore: Boolean,
    word: Option[PartWord] = None
)

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
