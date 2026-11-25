package hanamaru.backend.learner

import hanamaru.shared.routes.LearnerRoutes

import scala.util.Random

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class LearnerApi(learner: LearnerStore) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    LearnerRoutes.kanaProgress.handleSuccess(_ =>
      learner.kanaProgress(System.currentTimeMillis())
    ),
    LearnerRoutes.nextKana.handleSuccess(after =>
      KanaReview.next(
        learner.kanaProgress(System.currentTimeMillis()),
        System.currentTimeMillis(),
        after,
        Random()
      )
    )
  )
}
