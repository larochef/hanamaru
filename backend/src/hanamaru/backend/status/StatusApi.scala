package hanamaru.backend.status

import hanamaru.backend.storage.Database
import hanamaru.shared.ServerStatus
import hanamaru.shared.routes.StatusRoutes

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class StatusApi(database: Database) {

  def status(): ServerStatus = {
    val version = database.read(
      _.queryOne("SELECT sqlite_version() AS version")(_.string("version"))
    )
    ServerStatus(version.getOrElse(""))
  }

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    StatusRoutes.status.handleSuccess(_ => status())
  )
}
