package hanamaru.backend.vocabulary

import hanamaru.shared.routes.VocabularyRoutes

import java.time.Instant

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class VocabularyApi(practice: VocabularyPractice) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    VocabularyRoutes.sentence.handle(practice.sentence),
    VocabularyRoutes.series.handle(of =>
      practice.series(Instant.now(), of).map(VocabularyPractice.planned)
    ),
    VocabularyRoutes.answer.handle(answer =>
      practice.answered(answer, Instant.now())
    ),
    VocabularyRoutes.selfGrade.handleSuccess(grade =>
      practice.selfGraded(grade, Instant.now())
    )
  )
}
