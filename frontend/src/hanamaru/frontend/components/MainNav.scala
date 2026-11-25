package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*

import com.raquo.laminar.api.L.*

/** The menu: today's page and My lesson, where the learner goes on; then,
  * quieter, what is looked up or practised freely, the kana there while the
  * server offers them, until they are settled (asked again each time a page is
  * shown). The page shown is marked.
  */
class MainNav(bus: AppBus, page: Signal[Page]) extends ReactiveElement {

  private val kanaInMenu: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.PageShown(_) => () }
    .flatMapSwitch(_ =>
      bus.request(AppCommand.LoadMenu) { case AppEvent.MenuLoaded(menu) =>
        menu.kana
      }
    )
    .collect { case Right(kana) => kana }
    .startWith(true)

  private def link(target: Page => Boolean, to: Page, name: String) =
    PageLink(
      bus,
      to,
      cls("is-current") <-- page.map(target),
      name
    )

  override lazy val node: HtmlElement = navTag(
    cls := "main-nav",
    link(_ == Page.Home, Page.Home, Tr("Today", "Aujourd'hui")),
    link(
      _ == Page.LessonSession,
      Page.LessonSession,
      Tr("My lesson", "Ma leçon")
    ),
    span(
      cls := "nav-more",
      span(cls := "nav-label", Tr("Look up:", "Consulter :")),
      child.maybe <-- kanaInMenu.map(shown =>
        Option.when(shown)(
          link(
            {
              case Page.Chart | Page.Character(_) | Page.Review | Page.Session |
                  Page.Placement =>
                true
              case _ => false
            },
            Page.Chart,
            Tr("Kana", "Kana")
          ).node
        )
      ),
      link(
        {
          case Page.Vocabulary | Page.Series => true
          case _                             => false
        },
        Page.Vocabulary,
        Tr("Vocabulary", "Vocabulaire")
      ),
      link(
        {
          case Page.Grammar | Page.Lesson(_) | Page.GrammarPlacement => true
          case _                                                     => false
        },
        Page.Grammar,
        Tr("Grammar", "Grammaire")
      ),
      link(
        {
          case Page.KanjiChart | Page.KanjiCharacter(_) => true
          case _                                        => false
        },
        Page.KanjiChart,
        Tr("Kanji", "Kanji")
      )
    )
  )
}
