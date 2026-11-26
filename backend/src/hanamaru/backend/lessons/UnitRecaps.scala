package hanamaru.backend.lessons

import hanamaru.backend.learner.CardKind
import hanamaru.backend.reference.KanjiBook
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.lessons.*

/** A unit gathered to be gone over ([[UnitRecap]]), one of the units so far:
  * its words and its symbols, each where it stands.
  */
final class UnitRecaps(units: Units, book: () => Option[KanjiBook]) {

  def recap(point: String): Either[String, UnitRecap] = {
    val soFar = units.soFar
    soFar
      .find(_.id == point)
      .toRight(s"No unit $point so far")
      .map { unit =>
        val now = units.standing()
        val progress = units.progressOf(unit, now)
        UnitRecap(
          unit.summary,
          unit.explanation.headOption.getOrElse(""),
          unit.theme,
          progress.words.map(word =>
            RecapWord(
              word,
              if (now.stable(word.id)) WordStanding.Acquired
              else if (now.wordMet(word.id)) WordStanding.Learning
              else WordStanding.New,
              UnitRecaps.meaningOf(word)
            )
          ),
          units.kanaScriptOf(unit),
          units
            .kanjiOf(unit)
            .getOrElse(Nil)
            .flatMap(literal =>
              book()
                .flatMap(_.kanji(literal))
                .map(kanji =>
                  RecapKanji(
                    kanji,
                    if (now.symbolCardPassed(literal, CardKind.Writing))
                      KanjiStanding.Written
                    else if (now.symbolCardPassed(literal, CardKind.Reading))
                      KanjiStanding.Read
                    else if (now.symbolMet(literal)) KanjiStanding.Reading
                    else KanjiStanding.NotMet
                  )
                )
            ),
          progress.acquired,
          soFar.map(_.summary)
        )
      }
  }
}

object UnitRecaps {

  // how long a short meaning may grow, glosses added while it fits
  private val shortLength = 24

  private val aside = """\s*\([^()]*\)""".r

  /** A word's meaning in a few words: the first glosses of its first sense
    * (French first), what they say in parentheses left out, as many as fit in a
    * short line, at least one (a gloss all in parentheses kept whole); its
    * senses whole when that leaves some out.
    */
  def meaningOf(word: DictionaryWord): ShortMeaning = {
    val senses = word.frenchFirst
    val glosses = senses.headOption.map(_.glosses).getOrElse(Nil)
    val bare = glosses.map(aside.replaceAllIn(_, "").trim).filter(_.nonEmpty)
    val kept = bare.headOption.toList ++ bare
      .drop(1)
      .scanLeft(bare.headOption.fold(0)(_.length))(_ + 2 + _.length)
      .drop(1)
      .zip(bare.drop(1))
      .takeWhile((length, _) => length <= shortLength)
      .map(_._2)
    val short =
      if (kept.isEmpty) glosses.headOption.getOrElse("")
      else kept.mkString(", ")
    val whole = senses.map(_.glosses.mkString(", "))
    ShortMeaning(
      short,
      if (whole.sizeIs > 1 || whole.headOption.exists(_ != short)) whole
      else Nil
    )
  }
}
