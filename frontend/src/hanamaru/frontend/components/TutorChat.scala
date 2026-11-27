package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.questions.*

import com.raquo.laminar.api.L.*

/** The tutor, a button away on every page: questions about what the learner has
  * before them, as the screens tell it ([[TutorContext]]), the latest shown
  * first; else about the page itself (a lesson), else of their own.
  */
class TutorChat(bus: AppBus) extends ReactiveElement {

  private val open = Var(false)

  private val context = Var(QuestionContext(AskedFrom.Questions))

  private def ofPage(page: Page): QuestionContext = page match {
    case Page.Lesson(point) => QuestionContext(AskedFrom.Lesson, Some(point))
    case Page.Unit(point)   => QuestionContext(AskedFrom.Lesson, Some(point))
    case _                  => QuestionContext(AskedFrom.Questions)
  }

  private val asking = AskTutor(bus, context.signal)

  override lazy val node: HtmlElement = div(
    cls := "tutor-chat",
    // the page shown, and the contexts its screens told, the latest last
    bus.events.events
      .scanLeft((Page.Home: Page, List.empty[QuestionContext])) {
        case ((_, _), AppEvent.PageShown(page))           => (page, Nil)
        case ((page, shown), AppEvent.ContextShown(each)) =>
          (page, shown.filterNot(_ == each) :+ each)
        case ((page, shown), AppEvent.ContextLeft(each)) =>
          (page, shown.filterNot(_ == each))
        case (now, _) => now
      }
      .map((page, shown) => shown.lastOption.getOrElse(ofPage(page)))
      .distinct --> context.writer,
    div(
      cls := "tutor-chat-panel",
      display <-- open.signal.map(if (_) "" else "none"),
      div(
        cls := "tutor-chat-head",
        h2(Tr("The tutor", "Le tuteur")),
        button(
          typ := "button",
          cls := "icon-button",
          aria.label := Tr("Close", "Fermer"),
          ButtonIcon(ButtonIcon.Shape.Cross).node,
          onClick --> (_ => open.set(false))
        )
      ),
      asking.node
    ),
    button(
      typ := "button",
      cls := "tutor-chat-button",
      aria.label := Tr("Ask the tutor", "Demander au tuteur"),
      title := Tr("Ask the tutor", "Demander au tuteur"),
      ButtonIcon(ButtonIcon.Shape.Chat).node,
      onClick --> { _ =>
        open.update(!_)
        if (open.now()) asking.focus()
      }
    )
  )
}
