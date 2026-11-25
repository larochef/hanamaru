package hanamaru.backend.lessons

import hanamaru.backend.session.MyLesson
import hanamaru.shared.routes.LessonRoutes

import java.time.Instant

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class LessonApi(
    lessons: Lessons,
    exercises: Exercises,
    judge: ExerciseJudge,
    myLesson: MyLesson
) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    LessonRoutes.curriculum.handle(_ => Right(lessons.curriculum)),
    LessonRoutes.lesson.handle(lessons.lesson),
    LessonRoutes.plan.handleSuccess(_ => myLesson.plan(Instant.now())),
    LessonRoutes.unit.handleSuccess(_ => lessons.unit),
    LessonRoutes.text.handle(lessons.text),
    LessonRoutes.exercises.handle(exercises.exercises),
    LessonRoutes.answer.handleSuccess(judge.judged),
    LessonRoutes.done.handle(result => exercises.done(result, Instant.now()))
  )
}
