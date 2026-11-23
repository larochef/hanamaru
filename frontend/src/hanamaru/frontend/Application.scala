package hanamaru.frontend

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.appServices.*
import hanamaru.frontend.components.*
import hanamaru.frontend.i18n.Language
import hanamaru.frontend.model.*

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import sttp.model.Uri

object Application {

  private val baseUri: Uri = {
    val location = dom.window.location
    Uri.unsafeParse(s"${location.protocol}//${location.host}")
  }

  private val bus = AppBus()

  private val apiClient = ApiClient(baseUri)

  private val page: Signal[Page] =
    bus.events.events
      .collect { case AppEvent.PageShown(now) => now }
      .startWith(Page.Chart)

  // Every page stays mounted, the others hidden: what each shows follows the
  // bus's events, which a page away would miss.
  private lazy val shell: HtmlElement = div(
    cls := "layout",
    headerTag(
      cls := "top-bar",
      PageLink(
        bus,
        Page.Chart,
        cls := "title",
        span(lang := "ja", "花丸"),
        " Hanamaru"
      ),
      LanguageChoice()
    ),
    ReferenceDataNotice(bus),
    mainTag(
      div(hidden <-- page.map(_ != Page.Chart), KanaChart(bus)),
      div(
        hidden <-- page.map(!_.isInstanceOf[Page.Character]),
        CharacterPage(bus)
      ),
      div(hidden <-- page.map(_ != Page.Placement), PlacementPage(bus)),
      div(hidden <-- page.map(_ != Page.Review), ReviewPage(bus))
    ),
    Credits()
  )

  private lazy val appContainer: dom.Element =
    dom.document.querySelector("#app")

  // Services live through their subscription to the bus.
  def main(args: Array[String]): Unit = {
    dom.document.documentElement.setAttribute("lang", Language.current.code)
    StatusService(bus, apiClient)
    WritingService(bus, apiClient)
    LearnerService(bus, apiClient)
    PlacementService(bus, apiClient)
    val navigation = NavigationService(bus)
    render(appContainer, shell)
    navigation.showAddressed()
  }
}
