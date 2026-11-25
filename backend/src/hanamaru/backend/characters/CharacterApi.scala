package hanamaru.backend.characters

import hanamaru.shared.routes.CharacterRoutes

import java.time.Instant

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class CharacterApi(practice: CharacterPractice) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    CharacterRoutes.practice.handleSuccess(_ =>
      practice.exercises(Instant.now())
    ),
    CharacterRoutes.kanaReading.handle(practice.readKana(_, Instant.now()))
  )
}
