package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams

import com.raquo.laminar.api.L.*

class VocabularyService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events.foreach {
    case command @ AppCommand.LoadSeries =>
      AppStreams.runOnce(apiClient.series()) {
        case Right(words) => bus.publish(AppEvent.SeriesLoaded(words))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.AnswerVocabulary(answer) =>
      AppStreams.runOnce(apiClient.answerVocabulary(answer)) {
        case Right(verdict) =>
          bus.publish(AppEvent.VocabularyJudged(answer, verdict))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.SelfGradeVocabulary(grade) =>
      AppStreams.runOnce(apiClient.selfGradeVocabulary(grade)) {
        case Right(())    => bus.publish(AppEvent.SelfGradeKept(grade))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.LoadSentence(word) =>
      AppStreams.runOnce(apiClient.sentence(word)) {
        case Right(sentence) =>
          bus.publish(AppEvent.SentenceLoaded(word, sentence))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case _ => ()
  }(using unsafeWindowOwner)
}
