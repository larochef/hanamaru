package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams

import com.raquo.laminar.api.L.*

class QuestionService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events.foreach {
    case command @ AppCommand.AskTutor(question) =>
      AppStreams.runOnce(apiClient.askTutor(question)) {
        case Right(asked) =>
          bus.publish(AppEvent.TutorAnswered(question, asked))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.LoadQuestions =>
      AppStreams.runOnce(apiClient.questions()) {
        case Right(questions) =>
          bus.publish(AppEvent.QuestionsLoaded(questions))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case _ => ()
  }(using unsafeWindowOwner)
}
