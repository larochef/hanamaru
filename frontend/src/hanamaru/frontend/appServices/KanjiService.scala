package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams

import com.raquo.laminar.api.L.*

class KanjiService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events.foreach {
    case command @ AppCommand.LoadKanjiGrade(grade) =>
      AppStreams.runOnce(apiClient.kanjiOfGrade(grade)) {
        case Right(kanji) =>
          bus.publish(AppEvent.KanjiGradeLoaded(grade, kanji))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.LoadKanji(literal) =>
      AppStreams.runOnce(apiClient.kanji(literal)) {
        case Right(detail) => bus.publish(AppEvent.KanjiLoaded(detail))
        case Left(reason)  =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.LoadKanjiPractice =>
      AppStreams.runOnce(apiClient.kanjiPractice()) {
        case Right(exercises) =>
          bus.publish(AppEvent.KanjiPracticeLoaded(exercises))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.AnswerKanjiReading(answer) =>
      AppStreams.runOnce(apiClient.answerKanjiReading(answer)) {
        case Right(verdict) =>
          bus.publish(AppEvent.KanjiReadingJudged(answer, verdict))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.RecordKanjiWriting(attempt) =>
      AppStreams.runOnce(apiClient.recordKanjiWriting(attempt)) {
        case Right(outcome) =>
          bus.publish(AppEvent.KanjiWritingRecorded(attempt, outcome))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case _ => ()
  }(using unsafeWindowOwner)
}
