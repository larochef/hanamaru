package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams

import com.raquo.laminar.api.L.*

class VocabularyService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events.foreach {
    case command @ AppCommand.LoadSeries(of) =>
      AppStreams.runOnce(apiClient.series(of)) {
        case Right(tasks) => bus.publish(AppEvent.SeriesLoaded(of, tasks))
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
        case Right(comesBack) =>
          bus.publish(AppEvent.SelfGradeKept(grade, comesBack))
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
