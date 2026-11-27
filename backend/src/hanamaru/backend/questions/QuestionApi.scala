package hanamaru.backend.questions

import hanamaru.shared.routes.QuestionRoutes

import java.time.Instant

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class QuestionApi(questions: TutorQuestions) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    QuestionRoutes.ask.handleSuccess(questions.asked(_, Instant.now())),
    QuestionRoutes.all.handleSuccess(_ => questions.all)
  )
}
