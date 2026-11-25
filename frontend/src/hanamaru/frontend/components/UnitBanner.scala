package hanamaru.frontend.components

import hanamaru.frontend.model.*
import hanamaru.shared.lessons.UnitProgress

import com.raquo.laminar.api.L.*

/** The unit under way, where its words come from ([[UnitProgressView]]). Asked
  * again whenever `refresh` fires.
  */
class UnitBanner(bus: AppBus, refresh: EventStream[Unit])
    extends ReactiveElement {

  private val unit: Signal[Option[Option[UnitProgress]]] = refresh
    .flatMapSwitch(_ =>
      bus.request(AppCommand.LoadUnit) { case AppEvent.UnitLoaded(unit) =>
        unit
      }
    )
    .collect { case Right(unit) => Some(unit) }
    .startWith(None)

  override lazy val node: HtmlElement = div(
    child <-- unit.map {
      case Some(Some(progress)) =>
        div(cls := "unit-banner", UnitProgressView(bus, progress))
      case _ => emptyNode
    }
  )
}
