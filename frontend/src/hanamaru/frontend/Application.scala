package hanamaru.frontend

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.appServices.*
import hanamaru.frontend.components.*
import hanamaru.frontend.i18n.{Language, Tr}
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
      .startWith(Page.Home)

  // Every page stays mounted, the others hidden: what each shows follows the
  // bus's events, which a page away would miss.
  private lazy val shell: HtmlElement = div(
    cls := "layout",
    headerTag(
      cls := "top-bar",
      PageLink(
        bus,
        Page.Home,
        cls := "title",
        img(cls := "logo", src := "/assets/hanamaru.svg", alt := ""),
        span(lang := "ja", "花丸"),
        " Hanamaru"
      ),
      MainNav(bus, page),
      TutorIndicator(bus),
      LanguageChoice()
    ),
    ServerNotice(bus),
    mainTag(
      div(hidden <-- page.map(_ != Page.Home), HomePage(bus)),
      div(hidden <-- page.map(_ != Page.Chart), KanaChart(bus)),
      div(
        hidden <-- page.map(!_.isInstanceOf[Page.Character]),
        CharacterPage(bus)
      ),
      div(hidden <-- page.map(_ != Page.Placement), PlacementPage(bus)),
      div(hidden <-- page.map(_ != Page.Review), ReviewPage(bus)),
      div(hidden <-- page.map(_ != Page.Session), SessionPage(bus)),
      div(hidden <-- page.map(_ != Page.Vocabulary), VocabularyPage(bus)),
      div(hidden <-- page.map(_ != Page.Series), SeriesPage(bus)),
      div(hidden <-- page.map(_ != Page.Grammar), GrammarPage(bus)),
      div(
        hidden <-- page.map(!_.isInstanceOf[Page.Lesson]),
        LessonPage(bus)
      ),
      div(
        hidden <-- page.map(_ != Page.GrammarPlacement),
        GrammarPlacementPage(bus)
      ),
      div(
        hidden <-- page.map(_ != Page.LessonSession),
        LessonSessionPage(bus)
      ),
      div(hidden <-- page.map(_ != Page.KanjiChart), KanjiChart(bus)),
      div(
        hidden <-- page.map(!_.isInstanceOf[Page.KanjiCharacter]),
        KanjiPage(bus)
      ),
      div(hidden <-- page.map(_ != Page.Credits), Credits()),
      div(hidden <-- page.map(!_.isInstanceOf[Page.Unit]), UnitPage(bus))
    ),
    footerTag(
      cls := "page-foot",
      PageLink(bus, Page.Credits, Tr("Credits", "Crédits"))
    )
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
    SessionService(bus, apiClient)
    DictionaryService(bus, apiClient)
    VocabularyService(bus, apiClient)
    LessonService(bus, apiClient)
    KanjiService(bus, apiClient)
    CharacterService(bus, apiClient)
    TodayService(bus, apiClient)
    val navigation = NavigationService(bus)
    render(appContainer, shell)
    navigation.showAddressed()
  }
}
