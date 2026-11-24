package hanamaru.backend.lessons

import hanamaru.backend.kanji.KanjiPractice
import hanamaru.backend.vocabulary.VocabularyPractice
import hanamaru.shared.routes.LessonRoutes

import java.time.Instant

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class LessonApi(
    lessons: Lessons,
    judge: ExerciseJudge,
    vocabulary: VocabularyPractice,
    kanji: KanjiPractice
) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    LessonRoutes.curriculum.handle(_ => Right(lessons.curriculum)),
    LessonRoutes.lesson.handle(lessons.lesson),
    LessonRoutes.plan.handleSuccess { _ =>
      val now = Instant.now()
      lessons.plan(
        now,
        wordsToPractise = vocabulary.series(now).exists(_.nonEmpty),
        kanjiToPractise = kanji.exercises(now).exists(_.nonEmpty)
      )
    },
    LessonRoutes.unit.handleSuccess(_ => lessons.unit),
    LessonRoutes.text.handle(lessons.text),
    LessonRoutes.exercises.handle(lessons.exercises),
    LessonRoutes.answer.handleSuccess(judge.judged),
    LessonRoutes.done.handle(result => lessons.done(result, Instant.now()))
  )
}
