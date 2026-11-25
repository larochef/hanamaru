package hanamaru.shared.routes

import hanamaru.shared.session.{Menu, Today}

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object TodayRoutes {

  /** Today: what to do now, ready to be shown. */
  val today: PublicEndpoint[Unit, String, Today, Any] =
    Routes.baseRoute.get
      .in("today")
      .errorOut(Routes.refused)
      .out(jsonBody[Today])

  /** What the menu offers. */
  val menu: PublicEndpoint[Unit, String, Menu, Any] =
    Routes.baseRoute.get
      .in("menu")
      .errorOut(Routes.refused)
      .out(jsonBody[Menu])
}
