package hanamaru.backend.learner

import hanamaru.shared.routes.LearnerRoutes

import java.time.Duration
import scala.util.Random

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class LearnerApi(learner: LearnerStore) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    LearnerRoutes.kanaProgress.handleSuccess(_ =>
      learner.kanaProgress(System.currentTimeMillis())
    ),
    LearnerRoutes.nextKana.handleSuccess { after =>
      val now = System.currentTimeMillis()
      KanaReview.next(
        learner.kanaProgress(now),
        now,
        after,
        // the kana reviewed lately, which the rotation takes last
        learner.reviewedSince(ItemKind.Kana, now - Duration.ofDays(1).toMillis),
        Random()
      )
    }
  )
}
