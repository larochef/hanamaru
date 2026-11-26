package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams

import com.raquo.laminar.api.L.*

class CharacterService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events.foreach {
    case command @ AppCommand.LoadCharacterPractice(of) =>
      AppStreams.runOnce(apiClient.characterPractice(of)) {
        case Right(exercises) =>
          bus.publish(AppEvent.CharacterPracticeLoaded(of, exercises))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.AnswerKanaReading(answer) =>
      AppStreams.runOnce(apiClient.answerKanaReading(answer)) {
        case Right(result) =>
          bus.publish(AppEvent.KanaReadingJudged(answer, result))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case _ => ()
  }(using unsafeWindowOwner)
}
