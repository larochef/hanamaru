package hanamaru.shared.routes

import sttp.model.StatusCode
import sttp.tapir.*

object Routes {

  /** Every endpoint's `/api` prefix: Vite's dev proxy forwards nothing else,
    * and the backend answers any other path with the page's shell.
    */
  val baseRoute: PublicEndpoint[Unit, Unit, Unit, Any] = endpoint.in("api")

  /** What was asked for cannot be done, and why, in words the page shows. */
  val refused: EndpointOutput[String] =
    statusCode(StatusCode.UnprocessableEntity).and(stringBody)
}
