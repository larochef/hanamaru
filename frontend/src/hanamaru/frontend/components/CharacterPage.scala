package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.kana.Kana

import com.raquo.laminar.api.L.*

/** One kana, at its own address, reached from the chart. */
class CharacterPage(bus: AppBus) extends ReactiveElement {

  private val shown: Signal[Option[Kana]] = bus.events.events
    .collect {
      case AppEvent.PageShown(Page.Character(kana)) => Some(kana)
      case AppEvent.PageShown(_)                    => None
    }
    .startWith(None)

  override lazy val node: HtmlElement = div(
    cls := "character-page",
    PageLink(
      bus,
      Page.Chart,
      cls := "back",
      Tr("← Kana chart", "← Tableau des kana")
    ),
    KanaPractice(bus, shown)
  )
}
