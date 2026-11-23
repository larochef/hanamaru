package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.ReferenceDataState
import hanamaru.shared.lessons.*

import com.raquo.laminar.api.L.*

/** A grammar point's lesson: its explanation, in French, and its examples in
  * kana, the grammar they show marked, each with its translation.
  */
class LessonPage(bus: AppBus) extends ReactiveElement {

  private val shown: Signal[Option[String]] = bus.events.events
    .collect {
      case AppEvent.PageShown(Page.Lesson(point)) => Some(point)
      case AppEvent.PageShown(_)                  => None
    }
    .startWith(None)

  // the examples are laid out by the analyser, ready once its dictionary is
  private val ready: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.ServerChecked(status) =>
      status.analyser == ReferenceDataState.Ready
    }
    .startWith(false)

  private val lesson: Signal[Option[AppResult[GrammarLesson]]] =
    shown
      .combineWith(ready)
      .changes
      .distinct
      .flatMapSwitch {
        case (Some(point), true) =>
          bus
            .request(AppCommand.LoadLesson(point)) {
              case AppEvent.LessonLoaded(lesson) if lesson.point.id == point =>
                lesson
            }
            .map(Some(_))
        case _ => EventStream.fromValue(None)
      }
      .startWith(None)

  override lazy val node: HtmlElement = div(
    cls := "lesson-page",
    PageLink(bus, Page.Grammar, cls := "back", Tr("← Grammar", "← Grammaire")),
    child <-- lesson.map {
      case Some(Right(shown)) => LessonView(bus, shown).node
      case Some(Left(reason)) => p(cls := "error", reason)
      case None => p(cls := "in-english", Tr("Loading…", "Chargement…"))
    }
  )
}
