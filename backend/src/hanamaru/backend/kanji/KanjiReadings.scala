package hanamaru.backend.kanji

import hanamaru.shared.kana.{Kana, Romaji}
import hanamaru.shared.kanji.Kanji

/** A word's reading split along its writing, kanji by kanji, so that a kanji's
  * reading is judged on its own part of the word: a slip elsewhere in the word
  * is not a slip on it.
  */
object KanjiReadings {

  // the kana a reading's end may be cut to before another (がく in がっこう)
  private val cutShort = Set("つ", "ち", "く", "き")

  /** The readings a kanji may take in a word, in hiragana: its on and kun
    * readings (a kun one without what follows it in kana: まな.ぶ is まな), each
    * also voiced at its start (さき in 先, ざき after another kanji) or cut short at
    * its end (がく as がっ).
    */
  def formsOf(kanji: Kanji): List[String] = {
    val plain = (kanji.on ++ kanji.kun)
      .map(reading =>
        Romaji.asHiragana(reading.takeWhile(_ != '.').filterNot(_ == '-'))
      )
      .filter(_.nonEmpty)
    val voiced =
      plain.map(reading => Kana.withDakuten(reading.take(1)) + reading.drop(1))
    val short = (plain ++ voiced)
      .filter(reading => reading.length > 1 && cutShort(reading.takeRight(1)))
      .map(_.dropRight(1) + "っ")
    (plain ++ voiced ++ short).distinct.sortBy(-_.length)
  }

  /** Each character of `writing` with where its reading lies in `reading`
    * (from, until): its kana and its kanji's forms ([[formsOf]], by `forms`,
    * none for a kana) laid along the reading; none when they cannot be.
    */
  def laidOut(
      writing: String,
      reading: String,
      forms: String => List[String]
  ): Option[List[(String, (Int, Int))]] = {
    val said = Romaji.asHiragana(reading)
    val characters = writing.map(_.toString).toList
    def laid(at: Int, rest: List[String]): Option[List[(Int, Int)]] =
      rest match {
        case Nil                 => Option.when(at == said.length)(Nil)
        case character :: others =>
          // a kanji by its forms; a kana, or a kanji not known, as itself
          val candidates = Some(forms(character))
            .filter(_.nonEmpty)
            .getOrElse(List(Romaji.asHiragana(character)))
          candidates.iterator
            .filter(form => said.startsWith(form, at))
            .flatMap(form =>
              laid(at + form.length, others).map((at, at + form.length) :: _)
            )
            .nextOption()
      }
    laid(0, characters).map(characters.zip(_))
  }

  /** Where the reading of `literal` lies in `reading`, the word's written
    * `writing` ([[laidOut]]).
    */
  def spanOf(
      writing: String,
      reading: String,
      literal: String,
      forms: String => List[String]
  ): Option[(Int, Int)] =
    laidOut(writing, reading, forms).flatMap(_.collectFirst {
      case (character, span) if character == literal => span
    })

  /** Whether `typed` has the part `span` of `expected` right: no letter of it
    * missed, changed, or added inside it, the mistakes elsewhere aside.
    */
  def partRight(expected: String, typed: String, span: (Int, Int)): Boolean = {
    val (want, got) = (Romaji.asHiragana(expected), Romaji.asHiragana(typed))
    val distance = Array.tabulate(want.length + 1, got.length + 1)((i, j) =>
      if (i == 0) j else if (j == 0) i else 0
    )
    for {
      i <- 1 to want.length
      j <- 1 to got.length
    } {
      distance(i)(j) = List(
        distance(i - 1)(j - 1) + (if (want(i - 1) == got(j - 1)) 0 else 1),
        distance(i - 1)(j) + 1,
        distance(i)(j - 1) + 1
      ).min
    }
    val (from, until) = span
    // walked back from the end: each edit, and where in `expected` it falls
    def touched(i: Int, j: Int): Boolean =
      if (i == 0 && j == 0) false
      else if (
        i > 0 && j > 0 && want(i - 1) == got(j - 1) &&
        distance(i)(j) == distance(i - 1)(j - 1)
      ) touched(i - 1, j - 1)
      else if (i > 0 && j > 0 && distance(i)(j) == distance(i - 1)(j - 1) + 1)
        (i - 1 >= from && i - 1 < until) || touched(i - 1, j - 1)
      else if (i > 0 && distance(i)(j) == distance(i - 1)(j) + 1)
        (i - 1 >= from && i - 1 < until) || touched(i - 1, j)
      else (i > from && i < until) || touched(i, j - 1)
    got.nonEmpty && !touched(want.length, got.length)
  }
}
