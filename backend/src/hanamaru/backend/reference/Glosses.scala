package hanamaru.backend.reference

import hanamaru.shared.kana.Romaji
import hanamaru.shared.text.PartWord

/** The dictionary's word a token of a sentence is: the entry with its
  * dictionary form whose reading is the analyser's, else the most common one;
  * none for grammar, punctuation and words JMdict does not have.
  */
final class Glosses(dictionary: Dictionary) {

  def of(token: Token): Option[PartWord] =
    Option
      .unless(token.isGrammar) {
        val entries = dictionary.withForm(token.dictionaryForm)
        // the analyser reads the token as written: its reading is the entry's
        // only when the token is not conjugated
        val read = SentenceLayout
          .everydayReading(token)
          .orElse(
            Option.when(token.surface == token.dictionaryForm)(token.reading)
          )
          .map(Romaji.asHiragana)
        val inKana = token.surface
          .forall(character => character >= 'ぁ' && character <= 'ヶ')
        entries
          .sortBy(entry =>
            (
              !read.exists(entry.readings.map(Romaji.asHiragana).contains),
              // a word written in kana: an entry usually written so (この, not 九)
              inKana && !entry.usuallyKana,
              // a word written in kanji: an entry written with them
              !inKana && !entry.writings.contains(token.dictionaryForm),
              entry.level == 0
            )
          )
          .headOption
          .map(entry =>
            PartWord(
              token.dictionaryForm,
              // the reading the token is read with, if it is the entry's
              read
                .filter(entry.readings.map(Romaji.asHiragana).contains)
                .orElse(entry.readings.headOption)
                .getOrElse(token.reading),
              entry.meanings
                .filter(_.language == "fre")
                .flatMap(_.glosses)
                .take(5)
                .mkString(", "),
              entry.meanings
                .find(_.language == "eng")
                .fold("")(_.glosses.take(3).mkString(", "))
            )
          )
      }
      .flatten
}
