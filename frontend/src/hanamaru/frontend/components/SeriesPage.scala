package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*

import com.raquo.laminar.api.L.*

/** A rapid series of words, with the unit they come from: asked each time the
  * page is shown.
  */
class SeriesPage(bus: AppBus) extends ReactiveElement {

  private val pageShown: EventStream[Unit] =
    bus.events.events.collect { case AppEvent.PageShown(Page.Series) => () }

  private val series = VocabularySeries(bus, pageShown)

  override lazy val node: HtmlElement = div(
    cls := "series-page",
    // where the unit stands, and again once a series is over
    UnitBanner(bus, EventStream.merge(pageShown, series.finished)),
    div(cls := "review-bar", h1(Tr("Rapid series", "Série rapide"))),
    series
  )
}
