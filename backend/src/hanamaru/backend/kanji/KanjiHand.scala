package hanamaru.backend.kanji

import hanamaru.backend.learner.*
import hanamaru.backend.reference.{KanjiBook, SentenceLayout}
import hanamaru.shared.dictionary.DictionaryWord

/** The kanji the learner writes (`writes`: those whose writing they have met,
  * in a focus or since), and the ways a word may be written by hand: each of
  * its kanji as itself or in kana, so that a kanji the learner writes is
  * written, and one they do not yet is not held against them (会しゃ while 社 is
  * not learned). `forms` are a kanji's readings ([[KanjiReadings.formsOf]]),
  * along which a word's kana are laid.
  */
final class KanjiHand(
    forms: String => List[String],
    val writes: () => Set[String]
) {

  // each character of the word with the kana it reads; none when its kana
  // cannot be laid along its kanji
  private def laidOut(
      writing: String,
      reading: String
  ): Option[List[(String, String)]] =
    KanjiReadings
      .laidOut(writing, reading, forms)
      .map(_.map { case (character, (from, until)) =>
        character -> reading.substring(from, until)
      })

  private def isKanji(character: String): Boolean =
    character.length == 1 && SentenceLayout.isKanji(character.head)

  /** `writing`, read `reading`, as the learner writes it: the kanji they write
    * kept, the others in kana; none when they write none of its kanji.
    */
  def asWritten(
      writing: String,
      reading: String,
      written: Set[String]
  ): Option[String] =
    laidOut(writing, reading)
      .filter(_.exists((character, _) => written(character)))
      .map(
        _.map((character, kana) =>
          if (!isKanji(character) || written(character)) character else kana
        ).mkString
      )

  /** A word as an exercise shows it: the kanji the units teach (`taught`) as
    * themselves, the others in kana (友だち: 達 is not taught); as written when its
    * kana cannot be laid along its kanji.
    */
  def asTaught(word: DictionaryWord, taught: Set[String]): String =
    asWritten(word, taught).getOrElse(word.writings.headOption.getOrElse(""))

  /** A word as the learner writes it ([[asWritten]]), its usual writing laid
    * along its first reading; none for a word usually in kana.
    */
  def asWritten(word: DictionaryWord, written: Set[String]): Option[String] =
    for {
      writing <- word.writings.headOption
      if !word.usuallyKana
      reading <- word.readings.headOption
      shown <- asWritten(writing, reading, written)
    } yield shown

  /** Every way of writing `writing`, read `reading`, each kanji as itself or in
    * kana: all kanji, all kana, and every mix between; none laid out, only
    * `writing` itself.
    */
  def ways(writing: String, reading: String): Set[String] =
    laidOut(writing, reading).fold(Set(writing))(
      _.foldLeft(List("")) { case (before, (character, kana)) =>
        val each =
          if (isKanji(character)) List(character, kana) else List(character)
        for {
          start <- before
          next <- each
        } yield start + next
      }.toSet
    )

  /** Every way of writing a word by hand: its readings, as they are written,
    * and each of its writings along each of its readings ([[ways]]).
    */
  def ways(word: DictionaryWord): Set[String] =
    word.readings.toSet ++
      (for {
        writing <- word.writings
        reading <- word.readings
        way <- ways(writing, reading)
      } yield way)
}

object KanjiHand {

  /** [[KanjiHand.asTaught]], from a kanji's readings (`forms`). */
  def shown(
      word: DictionaryWord,
      forms: String => List[String],
      taught: Set[String]
  ): String =
    KanjiHand(forms, () => Set.empty).asTaught(word, taught)

  /** The learner's hand now: the kanji whose writing card they have. */
  def of(book: () => Option[KanjiBook], learner: LearnerStore): KanjiHand =
    KanjiHand(
      literal =>
        book().flatMap(_.kanji(literal)).fold(Nil)(KanjiReadings.formsOf),
      () =>
        learner
          .cards(ItemKind.Kanji)
          .filter(_.kind == CardKind.Writing)
          .map(_.key)
          .toSet
    )
}
