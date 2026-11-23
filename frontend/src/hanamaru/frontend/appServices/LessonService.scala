package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams
import hanamaru.shared.placement.GrammarPlacementState

import com.raquo.laminar.api.L.*

class LessonService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events.foreach {
    case command @ AppCommand.LoadCurriculum =>
      AppStreams.runOnce(apiClient.curriculum()) {
        case Right(points) => bus.publish(AppEvent.CurriculumLoaded(points))
        case Left(reason)  =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.LoadLesson(point) =>
      AppStreams.runOnce(apiClient.lesson(point)) {
        case Right(lesson) => bus.publish(AppEvent.LessonLoaded(lesson))
        case Left(reason)  =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.WriteLessonText(point) =>
      AppStreams.runOnce(apiClient.lessonText(point)) {
        case Right(text) => bus.publish(AppEvent.LessonTextWritten(point, text))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.LoadExercises(point) =>
      AppStreams.runOnce(apiClient.exercises(point)) {
        case Right(exercises) =>
          bus.publish(AppEvent.ExercisesLoaded(point, exercises))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.AnswerExercise(answer) =>
      AppStreams.runOnce(apiClient.answerExercise(answer)) {
        case Right(verdict) =>
          bus.publish(AppEvent.ExerciseJudged(answer, verdict))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.FinishExercises(done) =>
      AppStreams.runOnce(apiClient.finishExercises(done)) {
        case Right(_)     => ()
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.LoadLessonPlan =>
      AppStreams.runOnce(apiClient.lessonPlan()) {
        case Right(plan)  => bus.publish(AppEvent.LessonPlanLoaded(plan))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.LoadGrammarPlacement =>
      placement(command, apiClient.grammarPlacement())
    case command @ AppCommand.StartGrammarPlacement =>
      placement(command, apiClient.startGrammarPlacement())
    case command @ AppCommand.AnswerGrammarPlacement(answer) =>
      placement(command, apiClient.answerGrammarPlacement(answer))
    case _ => ()
  }(using unsafeWindowOwner)

  private def placement(
      command: AppCommand,
      call: EventStream[AppResult[GrammarPlacementState]]
  ): Unit =
    AppStreams.runOnce(call) {
      case Right(state) => bus.publish(AppEvent.GrammarPlacementUpdated(state))
      case Left(reason) => bus.publish(AppEvent.RequestFailed(command, reason))
    }
}
