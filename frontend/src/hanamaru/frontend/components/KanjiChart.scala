package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.ReferenceDataState
import hanamaru.shared.kanji.Kanji

import com.raquo.laminar.api.L.*

/** The kanji of the school grades, one grade at a time, the most frequent
  * first; each a link to its page.
  */
class KanjiChart(bus: AppBus) extends ReactiveElement {
  private val grades = 1 to 6
  private val grade = Var(1)

  private val ready: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.ServerChecked(status) =>
      status.kanji == ReferenceDataState.Ready
    }
    .startWith(false)

  private val shown: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.PageShown(page) => page == Page.KanjiChart }
    .startWith(false)

  private val kanji: Signal[List[Kanji]] = shown
    .combineWith(ready, grade.signal)
    .changes
    .distinct
    .flatMapSwitch {
      case (true, true, wanted) =>
        bus
          .request(AppCommand.LoadKanjiGrade(wanted)) {
            case AppEvent.KanjiGradeLoaded(`wanted`, kanji) => kanji
          }
          .collect { case Right(kanji) => kanji }
      case _ => EventStream.empty
    }
    .startWith(Nil)

  private def cell(each: Kanji): HtmlElement =
    PageLink(
      bus,
      Page.KanjiCharacter(each.literal),
      cls := "kanji-cell",
      span(cls := "kanji", lang := "ja", each.literal),
      span(cls := "kanji-meaning", each.meanings.headOption.getOrElse(""))
    ).node

  override lazy val node: HtmlElement = div(
    cls := "kanji-chart",
    h1(Tr("Kanji", "Kanji")),
    p(
      cls := "in-english",
      Tr(
        "The kanji taught in Japanese schools, year by year, the most frequent first. They are not followed yet: these pages are to discover them.",
        "Les kanji appris à l'école au Japon, année par année, les plus fréquents d'abord. Ils ne sont pas encore suivis : ces pages servent à les découvrir."
      )
    ),
    div(
      cls := "step-tabs",
      grades.map(each =>
        button(
          cls("is-selected") <-- grade.signal.map(_ == each),
          Tr(s"Year $each", s"Année $each"),
          onClick.mapTo(each) --> grade.writer
        )
      )
    ),
    p(
      cls := "in-english",
      child.text <-- kanji.map(all =>
        Tr(s"${all.size} kanji", s"${all.size} kanji")
      )
    ),
    div(cls := "kanji-grid", children <-- kanji.map(_.map(cell)))
  )
}
