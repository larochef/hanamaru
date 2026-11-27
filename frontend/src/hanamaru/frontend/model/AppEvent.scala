package hanamaru.frontend.model

import hanamaru.shared.ServerStatus
import hanamaru.shared.characters.*
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kanji.*
import hanamaru.shared.learner.*
import hanamaru.shared.lessons.*
import hanamaru.shared.placement.*
import hanamaru.shared.questions.*
import hanamaru.shared.session.*
import hanamaru.shared.vocabulary.*
import hanamaru.shared.writing.{CharacterStrokes, WritingAttempt}

sealed trait AppEvent

object AppEvent {

  final case class ServerChecked(status: ServerStatus) extends AppEvent

  final case class PageShown(page: Page) extends AppEvent

  final case class StrokesLoaded(strokes: CharacterStrokes) extends AppEvent
  final case class ManyStrokesLoaded(
      characters: String,
      strokes: List[CharacterStrokes]
  ) extends AppEvent

  /** A character written once was kept; written from memory, it was a review
    * too, with this outcome.
    */
  final case class WritingRecorded(
      attempt: WritingAttempt,
      review: Option[ReviewOutcome]
  ) extends AppEvent

  /** Where the kana placement test stands, after a command about it. */
  final case class PlacementUpdated(state: PlacementState) extends AppEvent

  /** Every kana reviewed at least once, where the learner stands on it. */
  final case class KanaProgressLoaded(progress: List[KanaProgress])
      extends AppEvent

  /** The next kana to review, none before any kana is met. */
  final case class KanaToReviewPicked(kana: Option[KanaToReview])
      extends AppEvent

  /** A command could not be carried out, and why, in words the page shows. */
  final case class RequestFailed(command: AppCommand, reason: String)
      extends AppEvent

  /** Where the session stands, after a command about it. */
  final case class SessionUpdated(state: SessionState) extends AppEvent

  final case class CoreWordsLoaded(from: Int, words: List[DictionaryWord])
      extends AppEvent

  final case class SeriesLoaded(of: SeriesOf, tasks: List[SeriesTask])
      extends AppEvent

  final case class VocabularyJudged(
      answer: VocabularyAnswer,
      verdict: VocabularyVerdict
  ) extends AppEvent

  final case class SelfGradeKept(grade: SelfGrade, comesBack: ComesBack)
      extends AppEvent

  /** A sentence for a word, none when none could be written. */
  final case class SentenceLoaded(word: Long, sentence: Option[WordSentence])
      extends AppEvent

  final case class CurriculumLoaded(points: List[GrammarPoint]) extends AppEvent

  final case class LessonLoaded(lesson: GrammarLesson) extends AppEvent

  /** A reading text for a lesson, none when none could be written. */
  final case class LessonTextWritten(point: String, text: Option[LessonText])
      extends AppEvent

  final case class ExercisesLoaded(
      of: ExercisesOf,
      exercises: List[LessonExercise]
  ) extends AppEvent

  final case class ExerciseJudged(
      answer: ExerciseAnswer,
      verdict: ExerciseVerdict
  ) extends AppEvent

  final case class GrammarPlacementUpdated(state: GrammarPlacementState)
      extends AppEvent

  final case class LessonPlanLoaded(plan: LessonPlan) extends AppEvent

  final case class ExercisesFinished(done: ExercisesDone, tally: ExercisesTally)
      extends AppEvent

  final case class UnitLoaded(unit: Option[UnitProgress]) extends AppEvent

  final case class UnitRecapLoaded(recap: UnitRecap) extends AppEvent

  /** Moved on from an acquired unit: the unit under way now. */
  final case class UnitMovedOn(unit: Option[UnitProgress]) extends AppEvent

  final case class KanjiGradeLoaded(grade: Int, kanji: List[Kanji])
      extends AppEvent

  final case class KanjiLoaded(detail: KanjiDetail) extends AppEvent

  final case class CharacterPracticeLoaded(
      of: CharactersOf,
      exercises: List[CharacterExercise]
  ) extends AppEvent

  final case class FocusCardLoaded(
      focus: FocusState,
      card: Option[CharacterExercise]
  ) extends AppEvent

  final case class FocusFinished(focus: FocusState) extends AppEvent

  final case class KanaReadingJudged(
      answer: KanaReadingAnswer,
      result: ReadingResult
  ) extends AppEvent

  final case class TodayLoaded(today: Today) extends AppEvent

  final case class MenuLoaded(menu: Menu) extends AppEvent

  final case class KanjiReadingJudged(
      answer: KanjiReadingAnswer,
      verdict: KanjiReadingVerdict
  ) extends AppEvent

  final case class KanjiWritingRecorded(
      attempt: WritingAttempt,
      outcome: Option[ReviewOutcome]
  ) extends AppEvent

  /** A question kept, with the tutor's answer if it gave one. */
  final case class TutorAnswered(question: TutorQuestion, asked: AskedQuestion)
      extends AppEvent

  final case class QuestionsLoaded(questions: List[AskedQuestion])
      extends AppEvent

  /** What the learner has before them now, for the tutor: shown by a screen as
    * it changes, until it is left.
    */
  final case class ContextShown(context: QuestionContext) extends AppEvent

  final case class ContextLeft(context: QuestionContext) extends AppEvent
}
