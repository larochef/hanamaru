package hanamaru.backend.status

import hanamaru.backend.models.LlamaServer
import hanamaru.backend.reference.ReferenceData
import hanamaru.shared.ServerStatus
import hanamaru.shared.routes.StatusRoutes

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class StatusApi(reference: ReferenceData, model: LlamaServer) {

  def status(): ServerStatus =
    ServerStatus(
      reference.strokes,
      reference.dictionaryData,
      reference.analyserData,
      reference.kanjiData,
      model.state
    )

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    StatusRoutes.status.handleSuccess(_ => status())
  )
}
