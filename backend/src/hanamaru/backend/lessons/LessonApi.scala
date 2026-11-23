package hanamaru.backend.lessons

import hanamaru.shared.routes.LessonRoutes

import java.time.Instant

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class LessonApi(lessons: Lessons, judge: ExerciseJudge) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    LessonRoutes.curriculum.handle(_ => Right(lessons.curriculum)),
    LessonRoutes.lesson.handle(lessons.lesson),
    LessonRoutes.plan.handleSuccess(_ => lessons.plan(Instant.now())),
    LessonRoutes.text.handle(lessons.text),
    LessonRoutes.exercises.handle(lessons.exercises),
    LessonRoutes.answer.handleSuccess(judge.judged),
    LessonRoutes.done.handle(result => lessons.done(result, Instant.now()))
  )
}
