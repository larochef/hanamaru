package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.lessons.GrammarPoint

import com.raquo.laminar.api.L.*

/** The grammar curriculum, in the order it is learned, each point a link to its
  * lesson.
  */
class GrammarPage(bus: AppBus) extends ReactiveElement {

  // asked for the first time the page is shown
  private val points: Signal[List[GrammarPoint]] = bus.events.events
    .collect { case AppEvent.PageShown(Page.Grammar) => () }
    .take(1)
    .flatMapSwitch(_ =>
      bus.request(AppCommand.LoadCurriculum) {
        case AppEvent.CurriculumLoaded(points) =>
          points
      }
    )
    .collect { case Right(points) => points }
    .startWith(Nil)

  override lazy val node: HtmlElement = div(
    cls := "grammar-page",
    div(
      cls := "review-bar",
      h1(Tr("Grammar", "Grammaire")),
      PageLink(
        bus,
        Page.LessonSession,
        cls := "button primary",
        Tr("My lesson", "Ma leçon")
      ),
      PageLink(
        bus,
        Page.GrammarPlacement,
        cls := "button",
        Tr("Placement test", "Test de placement")
      )
    ),
    p(
      cls := "in-english",
      Tr(
        "The points in the order they are learned; the explanations are in French.",
        "Les points dans l'ordre où ils s'apprennent."
      )
    ),
    ol(
      cls := "grammar-points",
      children <-- points.map(
        _.map(point =>
          li(
            PageLink(
              bus,
              Page.Lesson(point.id),
              span(cls := "grammar-title", point.title)
            ),
            span(cls := "word-level", s"N${point.level}")
          )
        )
      )
    )
  )
}
