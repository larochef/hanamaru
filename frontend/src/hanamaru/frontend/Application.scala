package hanamaru.frontend

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.appServices.StatusService
import hanamaru.frontend.components.HomePage
import hanamaru.frontend.i18n.Language
import hanamaru.frontend.model.AppBus

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

  private lazy val appContainer: dom.Element =
    dom.document.querySelector("#app")

  // Services live through their subscription to the bus.
  def main(args: Array[String]): Unit = {
    dom.document.documentElement.setAttribute("lang", Language.current.code)
    StatusService(bus, apiClient)
    render(appContainer, HomePage(bus).node)
  }
}
