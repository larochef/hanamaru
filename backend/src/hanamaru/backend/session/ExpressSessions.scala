package hanamaru.backend.session

import hanamaru.backend.learner.*
import hanamaru.backend.writing.{WritingAttempts, WritingReview}
import hanamaru.shared.kana.Kana
import hanamaru.shared.learner.Grade
import hanamaru.shared.placement.ReadingResult
import hanamaru.shared.session.*
import hanamaru.shared.writing.WritingStep

import java.time.Instant

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString

/** Express sessions as they are taken: started with the time and context
  * declared, answered exercise by exercise, each answer a review kept at once,
  * over when the time is up or the learner stops.
  */
final class ExpressSessions(
    sessions: SessionStore,
    learner: LearnerStore,
    writing: WritingReview,
    attempts: WritingAttempts
) {
  private val format = "express"
  private val shortest = 5
  private val longest = 60

  def started(start: SessionStart, at: Instant): Either[String, SessionState] =
    if (start.minutes < shortest || start.minutes > longest) {
      Left(s"A session lasts from $shortest to $longest minutes")
    } else {
      val session = sessions.started(format, start, at)
      Right(movedOn(session, None, None, at))
    }

  def answered(
      id: Long,
      answer: SessionAnswer,
      at: Instant
  ): Either[String, SessionState] =
    for {
      session <- sessions
        .find(id)
        .filter(!_.ended)
        .toRight("No such session under way")
      asked <- session.current.toRight("The session asks nothing")
      _ <- Either.cond(
        answers(asked, answer),
        (),
        "That is not the exercise asked"
      )
    } yield {
      val lastReading = answer match {
        case SessionAnswer.Reading(character, typed, _) =>
          val kana = Kana
            .find(character)
            .getOrElse(throw IllegalStateException(character))
          val right = typed.exists(Kana.reads(kana, _))
          learner.reviewed(
            ItemKind.Kana,
            character,
            CardKind.Reading,
            if (right) Grade.Good else Grade.Again,
            writeToString(answer),
            at,
            studySession = Some(id)
          )
          Some(ReadingResult(character, right, Kana.shownReading(kana)))
        case SessionAnswer.Writing(attempt) =>
          attempts.record(attempt)
          writing.reviewed(attempt, at, Some(id))
          None
      }
      movedOn(session, Some(asked), lastReading, at)
    }

  /** Ends a session before its time. */
  def ended(id: Long, at: Instant): Either[String, SessionState] =
    sessions.find(id).toRight("No such session").map { session =>
      sessions.asking(id, None, at)
      SessionState(id, None, session.endsAt, learner.sessionSummary(id), None)
    }

  private def answers(asked: SessionExercise, answer: SessionAnswer): Boolean =
    (asked, answer) match {
      case (
            SessionExercise.Reading(character),
            SessionAnswer.Reading(answered, _, _)
          ) =>
        character == answered
      case (
            SessionExercise.NewKana(character, false),
            SessionAnswer.Reading(answered, _, _)
          ) =>
        character == answered
      case (
            SessionExercise.Writing(character),
            SessionAnswer.Writing(attempt)
          ) =>
        character == attempt.character && attempt.step == WritingStep.FromMemory
      case (
            SessionExercise.NewKana(character, true),
            SessionAnswer.Writing(attempt)
          ) =>
        character == attempt.character && attempt.step == WritingStep.FromMemory
      case _ => false
    }

  // the next exercise asked, none once the time is up
  private def movedOn(
      session: StudySession,
      last: Option[SessionExercise],
      lastReading: Option[ReadingResult],
      at: Instant
  ): SessionState = {
    val summary = learner.sessionSummary(session.id)
    val next = SessionPlanner.next(
      learner.kanaCards(),
      at.toEpochMilli,
      session.endsAt,
      session.stylus,
      summary.introduced.size,
      SessionPlanner.newKanaFor(session.minutes),
      last,
      session.script.fold(Kana.all)(script =>
        Kana.all.filter(_.script == script)
      ),
      learner.sessionAsked(session.id)
    )
    sessions.asking(session.id, next, at)
    SessionState(session.id, next, session.endsAt, summary, lastReading)
  }
}
