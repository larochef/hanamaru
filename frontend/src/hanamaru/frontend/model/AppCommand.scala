package hanamaru.frontend.model

import hanamaru.shared.characters.{CharactersOf, KanaReadingAnswer}
import hanamaru.shared.kanji.KanjiReadingAnswer
import hanamaru.shared.lessons.*
import hanamaru.shared.placement.*
import hanamaru.shared.session.{SessionAnswer, SessionStart}
import hanamaru.shared.vocabulary.*
import hanamaru.shared.writing.{PadCharacter, WritingAttempt}

sealed trait AppCommand

object AppCommand {

  /** Asks whether the server is ready, and again while its reference data is
    * being prepared.
    */
  case object CheckServer extends AppCommand

  final case class ShowPage(page: Page) extends AppCommand

  /** Asks for a character's strokes. */
  final case class LoadStrokes(character: String) extends AppCommand
  final case class LoadManyStrokes(characters: String) extends AppCommand
  final case class RecordPadCharacters(characters: List[PadCharacter])
      extends AppCommand

  /** Asks where the learner stands on every kana. */
  case object LoadKanaProgress extends AppCommand

  /** Asks for the next kana to review, another than `after`. */
  final case class PickKanaToReview(after: Option[String]) extends AppCommand

  /** Asks where the kana placement test stands. */
  case object LoadPlacement extends AppCommand

  /** Starts the kana placement test, again if it was taken. */
  case object StartPlacement extends AppCommand

  final case class AnswerPlacement(answer: PlacementAnswer) extends AppCommand

  /** Keeps a character written once. */
  final case class RecordWriting(attempt: WritingAttempt) extends AppCommand

  /** Starts an Express session with the time and context declared. */
  final case class StartSession(start: SessionStart) extends AppCommand

  final case class AnswerSession(session: Long, answer: SessionAnswer)
      extends AppCommand

  /** Ends a session before its time. */
  final case class EndSession(session: Long) extends AppCommand

  /** Asks for the core vocabulary, `count` words from `from`. */
  final case class LoadCoreWords(from: Int, count: Int) extends AppCommand

  /** Asks for the words of a rapid series. */
  final case class LoadSeries(of: SeriesOf) extends AppCommand

  /** Has an answer judged and kept. */
  final case class AnswerVocabulary(answer: VocabularyAnswer) extends AppCommand

  /** Keeps the learner's own word on an answer the code could not judge. */
  final case class SelfGradeVocabulary(grade: SelfGrade) extends AppCommand

  /** Asks the tutor for a sentence with a word. */
  final case class LoadSentence(word: Long) extends AppCommand

  /** Asks for the grammar curriculum. */
  case object LoadCurriculum extends AppCommand

  /** Asks for a grammar point's lesson. */
  final case class LoadLesson(point: String) extends AppCommand

  /** Asks the tutor for a new reading text for a lesson. */
  final case class WriteLessonText(point: String) extends AppCommand

  /** Asks for exercises on a lesson's point. */
  final case class LoadExercises(point: String) extends AppCommand

  /** Has an exercise's answer judged. */
  final case class AnswerExercise(answer: ExerciseAnswer) extends AppCommand

  /** Records a lesson's exercises done. */
  final case class FinishExercises(done: ExercisesDone) extends AppCommand

  case object LoadGrammarPlacement extends AppCommand

  case object StartGrammarPlacement extends AppCommand

  final case class AnswerGrammarPlacement(answer: GrammarPlacementAnswer)
      extends AppCommand

  /** Asks for a lesson session's plan. */
  case object LoadLessonPlan extends AppCommand

  /** Asks for the unit under way. */
  case object LoadUnit extends AppCommand

  /** Asks for a unit gathered to be gone over. */
  final case class LoadUnitRecap(point: String) extends AppCommand

  /** Moves on from the unit under way, acquired. */
  case object MoveOnUnit extends AppCommand

  /** Asks for a school grade's kanji. */
  final case class LoadKanjiGrade(grade: Int) extends AppCommand

  /** Asks for a kanji, with its words. */
  final case class LoadKanji(literal: String) extends AppCommand

  /** Asks for a lesson session's kana and kanji exercises. */
  final case class LoadCharacterPractice(of: CharactersOf) extends AppCommand

  /** A kana's reading, to judge and keep. */
  final case class AnswerKanaReading(answer: KanaReadingAnswer)
      extends AppCommand

  /** Asks for today: what to do now. */
  case object LoadToday extends AppCommand

  /** Asks for what the menu offers. */
  case object LoadMenu extends AppCommand

  /** A kanji's reading in a word, to judge and keep. */
  final case class AnswerKanjiReading(answer: KanjiReadingAnswer)
      extends AppCommand

  /** A kanji written, to keep (a review when written from memory). */
  final case class RecordKanjiWriting(attempt: WritingAttempt)
      extends AppCommand
}
