package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.lessons.*

import com.raquo.laminar.api.L.*

/** Where the unit under way stands, as the server measured it: its theme and
  * lesson, its words acquired (and how many are needed), its lesson, its
  * symbols (the hiragana, the katakana, or its kanji), and what it waits for.
  */
class UnitProgressView(bus: AppBus, progress: UnitProgress)
    extends ReactiveElement {

  private def symbolsLine(symbols: SymbolsProgress): HtmlElement = {
    val counted =
      Tr(
        s" ${symbols.learned} of ${symbols.total} learned.",
        s" ${symbols.learned} appris sur ${symbols.total}."
      )
    symbols.kind match {
      case SymbolKind.Hiragana => span(Tr("Hiragana:", "Hiragana :"), counted)
      case SymbolKind.Katakana => span(Tr("Katakana:", "Katakana :"), counted)
      case SymbolKind.Kanji    =>
        span(
          Tr("Its kanji: ", "Ses kanji : "),
          span(lang := "ja", symbols.listed.mkString(" ")),
          counted
        )
    }
  }

  private def waiting: String = progress.waitsFor match {
    case UnitWaitsFor.KanaToMeet =>
      Tr(
        "Its words are written in kana: they come once its kana are all met.",
        "Ses mots s'écrivent avec ses kana : ils viendront une fois tous ses kana rencontrés."
      )
    case UnitWaitsFor.Words =>
      Tr(
        "Its words, in series, open the next unit with the rest.",
        "Ses mots, en séries, ouvriront l'unité suivante avec le reste."
      )
    case UnitWaitsFor.Lesson =>
      Tr(
        "The words are acquired: do the lesson to open the next unit.",
        "Les mots sont acquis : fais la leçon pour ouvrir l'unité suivante."
      )
    case UnitWaitsFor.Symbols =>
      Tr(
        "Words and lesson done: its symbols, read and written, open the next unit.",
        "Mots et leçon faits : ses symboles, lus et écrits, ouvriront l'unité suivante."
      )
    case UnitWaitsFor.MovingOn =>
      Tr(
        "Acquired: move on to the next unit when you like.",
        "Acquise : passe à l'unité suivante quand tu veux."
      )
  }

  override lazy val node: HtmlElement = div(
    cls := "unit-progress",
    p(
      Tr("Unit under way: ", "Unité en cours : "),
      span(cls := "unit-theme", progress.theme),
      " · ",
      PageLink(bus, Page.Lesson(progress.point.id), progress.point.title)
    ),
    p(
      PageLink(
        bus,
        Page.Unit(progress.point.id),
        Tr(
          "Go over the unit: its lesson, words and symbols",
          "Revoir l'unité : sa leçon, ses mots, ses symboles"
        )
      )
    ),
    p(
      cls := "in-english",
      Tr(
        s"Words acquired: ${progress.stable} of ${progress.words.size} (${progress.wordsNeeded} needed). ",
        s"Mots acquis : ${progress.stable} sur ${progress.words.size} (il en faut ${progress.wordsNeeded}). "
      ),
      if (progress.practised) Tr("Lesson done. ", "Leçon faite. ")
      else Tr("Lesson to do. ", "Leçon à faire. "),
      progress.symbols.map(symbolsLine)
    ),
    p(cls := "unit-next", waiting)
  )
}
