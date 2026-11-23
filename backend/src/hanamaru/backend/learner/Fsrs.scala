package hanamaru.backend.learner

import hanamaru.shared.learner.Grade

import java.time.{Duration, Instant}

/** Where a card is in FSRS's schedule: still being learned (by short steps),
  * reviewed at intervals in days, or relearned after a lapse.
  */
enum Phase {
  case Learning, Review, Relearning
}

/** A card's memory as FSRS models it: its phase and step, its stability (the
  * days after which it is recalled 9 times out of 10), its difficulty (1 to
  * 10), when it is due, and when it was last reviewed.
  */
final case class Memory(
    phase: Phase,
    step: Option[Int],
    stability: Double,
    difficulty: Double,
    due: Instant,
    lastReview: Instant
)

/** FSRS-6, the scheduler Anki uses: py-fsrs 6.3.3 (open-spaced-repetition)
  * ported as it is, without the random spread it can add to intervals. Its
  * settings are py-fsrs's defaults unless given: the 21 parameters of its
  * memory model, the share of cards to recall when due, the short steps of
  * learning and relearning, and the longest interval in days.
  */
final case class Fsrs(
    parameters: Vector[Double] = Fsrs.defaultParameters,
    desiredRetention: Double = 0.9,
    learningSteps: Vector[Duration] =
      Vector(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    relearningSteps: Vector[Duration] = Vector(Duration.ofMinutes(10)),
    maximumInterval: Int = 36500
) {

  private val minimumStability = 0.001
  private val decay = -parameters(20)
  private val factor = math.pow(0.9, 1 / decay) - 1
  private def w(index: Int) = parameters(index)

  /** The card's memory after an answer graded `grade` at `at`; `memory` is
    * empty for a card never reviewed.
    */
  def reviewed(memory: Option[Memory], grade: Grade, at: Instant): Memory =
    memory match {
      case None =>
        stepped(
          Phase.Learning,
          Some(0),
          initialStability(grade),
          initialDifficulty(grade, clamp = true),
          grade,
          at
        )
      case Some(memory) =>
        val sameDay = elapsedDays(memory.lastReview, at) < 1
        val stability =
          if (sameDay) shortTermStability(memory.stability, grade)
          else {
            nextStability(
              memory.difficulty,
              memory.stability,
              retrievability(memory, at),
              grade
            )
          }
        val difficulty = nextDifficulty(memory.difficulty, grade)
        memory.phase match {
          case Phase.Review
              if grade == Grade.Again && relearningSteps.nonEmpty =>
            Memory(
              Phase.Relearning,
              Some(0),
              stability,
              difficulty,
              at.plus(relearningSteps(0)),
              at
            )
          case Phase.Review =>
            inReview(stability, difficulty, at)
          case phase =>
            stepped(phase, memory.step, stability, difficulty, grade, at)
        }
    }

  /** The chance the card is recalled at `at`: 0.9 when as many days have gone
    * by as its stability.
    */
  def retrievability(memory: Memory, at: Instant): Double =
    math.pow(
      1 + factor * elapsedDays(memory.lastReview, at) / memory.stability,
      decay
    )

  /** The interval, in whole days, after which a card of that stability is due.
    */
  def intervalDays(stability: Double): Int = {
    val interval =
      stability / factor * (math.pow(desiredRetention, 1 / decay) - 1)
    // rounded half to even, as Python rounds
    math.rint(interval).toInt.max(1).min(maximumInterval)
  }

  // A learning or relearning card goes through its steps before its days.
  private def stepped(
      phase: Phase,
      step: Option[Int],
      stability: Double,
      difficulty: Double,
      grade: Grade,
      at: Instant
  ): Memory = {
    val steps = if (phase == Phase.Learning) learningSteps else relearningSteps
    val current = step.getOrElse(0)
    def stay(next: Int, wait: Duration) =
      Memory(phase, Some(next), stability, difficulty, at.plus(wait), at)
    if (steps.isEmpty || (current >= steps.size && grade != Grade.Again)) {
      inReview(stability, difficulty, at)
    } else {
      grade match {
        case Grade.Again                                   => stay(0, steps(0))
        case Grade.Hard if current == 0 && steps.size == 1 =>
          stay(current, steps(0).multipliedBy(3).dividedBy(2))
        case Grade.Hard if current == 0 =>
          stay(current, steps(0).plus(steps(1)).dividedBy(2))
        case Grade.Hard => stay(current, steps(current))
        case Grade.Good if current + 1 == steps.size =>
          inReview(stability, difficulty, at)
        case Grade.Good => stay(current + 1, steps(current + 1))
        case Grade.Easy => inReview(stability, difficulty, at)
      }
    }
  }

  private def inReview(stability: Double, difficulty: Double, at: Instant) =
    Memory(
      Phase.Review,
      None,
      stability,
      difficulty,
      at.plus(Duration.ofDays(intervalDays(stability))),
      at
    )

  // whole days, as py-fsrs counts them
  private def elapsedDays(from: Instant, to: Instant): Long =
    Duration.between(from, to).toDays.max(0)

  private def clampDifficulty(difficulty: Double) = difficulty.max(1).min(10)

  private def clampStability(stability: Double) =
    stability.max(minimumStability)

  private def initialStability(grade: Grade) =
    clampStability(w(grade.value - 1))

  private def initialDifficulty(grade: Grade, clamp: Boolean) = {
    val difficulty = w(4) - math.exp(w(5) * (grade.value - 1)) + 1
    if (clamp) clampDifficulty(difficulty) else difficulty
  }

  private def shortTermStability(stability: Double, grade: Grade) = {
    val increase =
      math.exp(w(17) * (grade.value - 3 + w(18))) * math.pow(stability, -w(19))
    val bounded = if (grade == Grade.Again) increase else increase.max(1)
    clampStability(stability * bounded)
  }

  private def nextDifficulty(difficulty: Double, grade: Grade) = {
    val delta = -(w(6) * (grade.value - 3))
    val damped = difficulty + (10 - difficulty) * delta / 9
    val reverted =
      w(7) * initialDifficulty(Grade.Easy, clamp = false) + (1 - w(7)) * damped
    clampDifficulty(reverted)
  }

  private def nextStability(
      difficulty: Double,
      stability: Double,
      retrievability: Double,
      grade: Grade
  ) = clampStability(grade match {
    case Grade.Again =>
      val longTerm = w(11) * math.pow(difficulty, -w(12)) *
        (math.pow(stability + 1, w(13)) - 1) *
        math.exp((1 - retrievability) * w(14))
      val shortTerm = stability / math.exp(w(17) * w(18))
      longTerm.min(shortTerm)
    case _ =>
      val hardPenalty = if (grade == Grade.Hard) w(15) else 1
      val easyBonus = if (grade == Grade.Easy) w(16) else 1
      stability * (1 + math.exp(w(8)) * (11 - difficulty) *
        math.pow(stability, -w(9)) *
        (math.exp((1 - retrievability) * w(10)) - 1) * hardPenalty * easyBonus)
  })
}

object Fsrs {

  val defaultParameters: Vector[Double] = Vector(0.212, 1.2931, 2.3065, 8.2956,
    6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614,
    0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542)

  /** The scheduler the learner's cards are scheduled with. */
  val standard: Fsrs = Fsrs()
}
