package hanamaru.backend.placement

import hanamaru.backend.learner.*
import hanamaru.backend.writing.WritingAttempts
import hanamaru.shared.kana.Kana
import hanamaru.shared.learner.Grade
import hanamaru.shared.placement.*
import hanamaru.shared.writing.*

import java.time.Instant

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString

/** The kana placement test as it is taken: started, answered question by
  * question, resumed where it was left, taken again. Each answer seeds the
  * learner model at once: a kana read or written right is to confirm, one
  * missed is to learn.
  */
final class KanaPlacementTest(
    placements: PlacementStore,
    learner: LearnerStore,
    attempts: WritingAttempts
) {
  private val domain = "kana"

  def state(): PlacementState =
    placements
      .lastRun(domain)
      .fold(PlacementState(false, None, Nil, None))(stateOf)

  def started(at: Instant): PlacementState = stateOf(
    placements.started(domain, at)
  )

  /** Answers the question asked; refused when it answers another one. */
  def answered(
      answer: PlacementAnswer,
      at: Instant
  ): Either[String, PlacementState] =
    for {
      run <- placements
        .lastRun(domain)
        .filter(!_.finished)
        .toRight("No kana placement test is under way")
      question = KanaPlacement.next(placements.answers(run.id), run.id)
      answered <- judged(answer, question)
    } yield {
      placements.record(run.id, answered, writeToString(answer), at)
      seeded(answer, answered, at)
      stateOf(run)
    }

  private def judged(
      answer: PlacementAnswer,
      question: Option[PlacementQuestion]
  ): Either[String, Answered] =
    (answer, question) match {
      case (
            PlacementAnswer.Reading(character, typed, _),
            Some(PlacementQuestion.Reading(asked))
          ) if character == asked =>
        val kana =
          Kana.find(character).getOrElse(throw IllegalStateException(character))
        Right(
          Answered(
            AnswerKind.Reading,
            character,
            typed.exists(Kana.reads(kana, _))
          )
        )
      case (
            PlacementAnswer.Writing(attempt),
            Some(PlacementQuestion.Writing(asked))
          )
          if attempt.character == asked && attempt.step == WritingStep.FromMemory =>
        val right = WritingCorrection.outcome(attempt) == WritingOutcome.Right
        Right(Answered(AnswerKind.Writing, asked, right))
      case (PlacementAnswer.SkipScript(script), Some(asked))
          if Kana.find(subjectOf(asked)).exists(_.script == script) =>
        Right(Answered(AnswerKind.Skip, script.toString, correct = false))
      case _ => Left("That is not the question asked")
    }

  private def subjectOf(question: PlacementQuestion): String = question match {
    case PlacementQuestion.Reading(character) => character
    case PlacementQuestion.Writing(character) => character
  }

  // A right answer is a first review answered easily, to be confirmed by the
  // next one; a missed one is a first review failed. A kana written right
  // vouches for the writing of the others of its row read right.
  private def seeded(
      answer: PlacementAnswer,
      answered: Answered,
      at: Instant
  ): Unit = {
    def review(character: String, card: CardKind) = learner.reviewed(
      ItemKind.Kana,
      character,
      card,
      if (answered.correct) Grade.Easy else Grade.Again,
      writeToString(answer),
      at,
      ReviewSource.Placement,
      toConfirm = answered.correct
    )
    answer match {
      case PlacementAnswer.Reading(character, _, _) =>
        review(character, CardKind.Reading)
      case PlacementAnswer.Writing(attempt) =>
        attempts.record(attempt)
        review(attempt.character, CardKind.Writing)
        for {
          kana <- Kana.find(attempt.character) if answered.correct
          row = KanaPlacement.rowOf(kana)
          other <- KanaPlacement.readRight(row, placementAnswers())
          if other != kana
        } {
          learner.seededToConfirm(
            ItemKind.Kana,
            other.character,
            CardKind.Writing,
            at
          )
        }
      case PlacementAnswer.SkipScript(_) => ()
    }
  }

  private def placementAnswers(): List[Answered] =
    placements.lastRun(domain).fold(Nil)(run => placements.answers(run.id))

  private def stateOf(run: PlacementRun): PlacementState = {
    val answers = placements.answers(run.id)
    val question =
      if (run.finished) None else KanaPlacement.next(answers, run.id)
    if (question.isEmpty && !run.finished) {
      placements.finish(run.id, Instant.now())
    }
    PlacementState(
      started = true,
      question = question,
      scripts = KanaPlacement.results(answers, run.id),
      lastReading = answers.lastOption.collect {
        case Answered(AnswerKind.Reading, character, correct) =>
          ReadingResult(
            character,
            correct,
            Kana.find(character).fold("")(Kana.shownReading)
          )
      }
    )
  }
}
