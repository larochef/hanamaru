package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*

import com.raquo.laminar.api.L.*

/** The first page: the application's name, and whether its server is ready. */
class HomePage(bus: AppBus) extends ReactiveElement {

  private val serverStatus: HtmlElement = p(
    cls := "server-status",
    child.text <-- bus
      .request(AppCommand.CheckServer) { case AppEvent.ServerChecked(status) =>
        status
      }
      .map {
        case Right(status) =>
          Tr(
            s"The server is ready (SQLite ${status.sqliteVersion}).",
            s"Le serveur est prêt (SQLite ${status.sqliteVersion})."
          )
        case Left(reason) => reason
      }
      .startWith(Tr("Reaching the server…", "Connexion au serveur…")),
    cls("is-failed") <-- bus.events.events
      .collect { case AppEvent.RequestFailed(AppCommand.CheckServer, _) =>
        true
      }
      .startWith(false)
  )

  override lazy val node: HtmlElement = div(
    cls := "home",
    h1(lang := "ja", "花丸 Hanamaru"),
    serverStatus,
    LanguageChoice()
  )
}
