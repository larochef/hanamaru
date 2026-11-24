package hanamaru.frontend.api

import hanamaru.frontend.api.airstream.AjaxBackend
import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.AppResult
import hanamaru.shared.ServerStatus
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kanji.*
import hanamaru.shared.learner.*
import hanamaru.shared.lessons.*
import hanamaru.shared.placement.*
import hanamaru.shared.routes.*
import hanamaru.shared.session.*
import hanamaru.shared.vocabulary.*
import hanamaru.shared.writing.*

import scala.util.{Failure, Success}

import com.raquo.laminar.api.L.*
import sttp.client4.Backend
import sttp.model.Uri
import sttp.tapir.PublicEndpoint
import sttp.tapir.client.sttp4.SttpClientInterpreter

// Every result is an Either: failures travel as values, not stream errors.
// A stream keeps no result: restarting it sends the request again.
class ApiClient(baseUri: Uri) {
  private val backend: Backend[EventStream] = AjaxBackend()
  private val interpreter = SttpClientInterpreter()

  def status(): EventStream[AppResult[ServerStatus]] =
    call(StatusRoutes.status, ())

  def strokes(character: String): EventStream[AppResult[CharacterStrokes]] =
    call(WritingRoutes.strokes, character)

  def manyStrokes(
      characters: String
  ): EventStream[AppResult[List[CharacterStrokes]]] =
    call(WritingRoutes.manyStrokes, characters)

  def recordPadCharacters(
      characters: List[PadCharacter]
  ): EventStream[AppResult[Unit]] =
    call(WritingRoutes.recordPadCharacters, characters)

  def recordAttempt(
      attempt: WritingAttempt
  ): EventStream[AppResult[Option[ReviewOutcome]]] =
    call(WritingRoutes.recordAttempt, attempt)

  def kanaProgress(): EventStream[AppResult[List[KanaProgress]]] =
    call(LearnerRoutes.kanaProgress, ())

  def nextKana(
      after: Option[String]
  ): EventStream[AppResult[Option[KanaToReview]]] =
    call(LearnerRoutes.nextKana, after)

  def placement(): EventStream[AppResult[PlacementState]] =
    call(PlacementRoutes.kana, ())

  def startPlacement(): EventStream[AppResult[PlacementState]] =
    call(PlacementRoutes.startKana, ())

  def answerPlacement(
      answer: PlacementAnswer
  ): EventStream[AppResult[PlacementState]] =
    call(PlacementRoutes.answerKana, answer)

  def startSession(start: SessionStart): EventStream[AppResult[SessionState]] =
    call(SessionRoutes.start, start)

  def answerSession(
      session: Long,
      answer: SessionAnswer
  ): EventStream[AppResult[SessionState]] =
    call(SessionRoutes.answer, (session, answer))

  def endSession(session: Long): EventStream[AppResult[SessionState]] =
    call(SessionRoutes.end, session)

  def coreWords(
      from: Int,
      count: Int
  ): EventStream[AppResult[List[DictionaryWord]]] =
    call(DictionaryRoutes.coreWords, (from, count))

  def series(): EventStream[AppResult[List[SeriesWord]]] =
    call(VocabularyRoutes.series, ())

  def answerVocabulary(
      answer: VocabularyAnswer
  ): EventStream[AppResult[VocabularyVerdict]] =
    call(VocabularyRoutes.answer, answer)

  def selfGradeVocabulary(grade: SelfGrade): EventStream[AppResult[Unit]] =
    call(VocabularyRoutes.selfGrade, grade)

  def sentence(word: Long): EventStream[AppResult[Option[WordSentence]]] =
    call(VocabularyRoutes.sentence, word)

  def curriculum(): EventStream[AppResult[List[GrammarPoint]]] =
    call(LessonRoutes.curriculum, ())

  def lesson(point: String): EventStream[AppResult[GrammarLesson]] =
    call(LessonRoutes.lesson, point)

  def lessonText(point: String): EventStream[AppResult[Option[LessonText]]] =
    call(LessonRoutes.text, point)

  def exercises(point: String): EventStream[AppResult[List[LessonExercise]]] =
    call(LessonRoutes.exercises, point)

  def answerExercise(
      answer: ExerciseAnswer
  ): EventStream[AppResult[ExerciseVerdict]] =
    call(LessonRoutes.answer, answer)

  def finishExercises(done: ExercisesDone): EventStream[AppResult[Unit]] =
    call(LessonRoutes.done, done)

  def grammarPlacement(): EventStream[AppResult[GrammarPlacementState]] =
    call(PlacementRoutes.grammar, ())

  def startGrammarPlacement(): EventStream[AppResult[GrammarPlacementState]] =
    call(PlacementRoutes.startGrammar, ())

  def answerGrammarPlacement(
      answer: GrammarPlacementAnswer
  ): EventStream[AppResult[GrammarPlacementState]] =
    call(PlacementRoutes.answerGrammar, answer)

  def lessonPlan(): EventStream[AppResult[LessonPlan]] =
    call(LessonRoutes.plan, ())

  def unit(): EventStream[AppResult[Option[UnitProgress]]] =
    call(LessonRoutes.unit, ())

  def kanjiOfGrade(grade: Int): EventStream[AppResult[List[Kanji]]] =
    call(KanjiRoutes.grade, grade)

  def kanji(literal: String): EventStream[AppResult[KanjiDetail]] =
    call(KanjiRoutes.kanji, literal)

  def kanjiPractice(): EventStream[AppResult[List[KanjiExercise]]] =
    call(KanjiRoutes.practice, ())

  def answerKanjiReading(
      answer: KanjiReadingAnswer
  ): EventStream[AppResult[KanjiReadingVerdict]] =
    call(KanjiRoutes.reading, answer)

  def recordKanjiWriting(
      attempt: WritingAttempt
  ): EventStream[AppResult[Option[ReviewOutcome]]] =
    call(KanjiRoutes.writing, attempt)

  private def call[I, O](
      endpoint: PublicEndpoint[I, String, O, Any],
      input: I
  ): EventStream[AppResult[O]] = {
    val request = interpreter
      .toRequestThrowDecodeFailures(endpoint, Some(baseUri))
      .apply(input)
    backend.send(request).recoverToTry.map {
      case Success(response) => response.body
      case Failure(error)    =>
        Left(
          Tr(
            s"The server could not be reached: ${error.getMessage}",
            s"Le serveur est injoignable : ${error.getMessage}"
          )
        )
    }
  }
}
