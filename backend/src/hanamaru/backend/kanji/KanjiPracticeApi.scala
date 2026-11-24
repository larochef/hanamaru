package hanamaru.backend.kanji

import hanamaru.shared.routes.KanjiRoutes

import java.time.Instant

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class KanjiPracticeApi(practice: KanjiPractice) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    KanjiRoutes.practice.handle(_ => practice.exercises(Instant.now())),
    KanjiRoutes.reading.handle(practice.read(_, Instant.now())),
    KanjiRoutes.writing.handleSuccess(practice.written(_, Instant.now()))
  )
}
