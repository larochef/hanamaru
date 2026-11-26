package hanamaru.backend.lessons

import hanamaru.backend.learner.CardKind
import hanamaru.backend.reference.KanjiBook
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
          unit.theme,
          progress.words.map(word =>
            RecapWord(
              word,
              if (now.stable(word.id)) WordStanding.Acquired
              else if (now.wordMet(word.id)) WordStanding.Learning
              else WordStanding.New
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
