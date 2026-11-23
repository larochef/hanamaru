package hanamaru.frontend.model

import hanamaru.shared.placement.PlacementAnswer
import hanamaru.shared.session.{SessionAnswer, SessionStart}
import hanamaru.shared.vocabulary.{SelfGrade, VocabularyAnswer}
import hanamaru.shared.writing.WritingAttempt

sealed trait AppCommand

object AppCommand {

  /** Asks whether the server is ready, and again while its reference data is
    * being prepared.
    */
  case object CheckServer extends AppCommand

  final case class ShowPage(page: Page) extends AppCommand

  /** Asks for a character's strokes. */
  final case class LoadStrokes(character: String) extends AppCommand

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
  case object LoadSeries extends AppCommand

  /** Has an answer judged and kept. */
  final case class AnswerVocabulary(answer: VocabularyAnswer) extends AppCommand

  /** Keeps the learner's own word on an answer the code could not judge. */
  final case class SelfGradeVocabulary(grade: SelfGrade) extends AppCommand
}
