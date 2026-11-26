package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.lessons.*

import com.raquo.laminar.api.L.*

/** A unit gathered to be gone over, as the server found it ([[UnitSections]]),
  * asked again once a practice is closed. The units so far are a click away.
  */
class UnitPage(bus: AppBus) extends ReactiveElement {

  private val shown: Signal[Option[String]] = bus.events.events
    .collect {
      case AppEvent.PageShown(Page.Unit(point)) => Some(point)
      case AppEvent.PageShown(_)                => None
    }
    .startWith(None)

  private val closed = EventBus[Unit]()

  private val recap: Signal[Option[AppResult[UnitRecap]]] = EventStream
    .merge(shown.changes, closed.events.sample(shown))
    .flatMapSwitch {
      case Some(point) =>
        bus
          .request(AppCommand.LoadUnitRecap(point)) {
            case AppEvent.UnitRecapLoaded(recap) if recap.point.id == point =>
              recap
          }
          .map(Some(_))
      case None => EventStream.fromValue(None)
    }
    .startWith(None)

  private def page(recap: UnitRecap): HtmlElement =
    div(
      UnitsNav(bus, recap).node,
      h1(
        recap.theme,
        " ",
        span(cls := "unit-pattern", lang := "ja", recap.point.pattern)
      ),
      Option.when(recap.acquired)(
        p(cls := "unit-next", Tr("Acquired.", "Acquise."))
      ),
      UnitSections(bus, recap, () => closed.writer.onNext(())).node
    )

  override lazy val node: HtmlElement = div(
    cls := "unit-page",
    PageLink(
      bus,
      Page.LessonSession,
      cls := "back",
      Tr("← My lesson", "← Ma leçon")
    ),
    child <-- recap.map {
      case Some(Right(recap)) => page(recap)
      case Some(Left(reason)) => p(cls := "error", reason)
      case None => p(cls := "in-english", Tr("Loading…", "Chargement…"))
    }
  )
}
