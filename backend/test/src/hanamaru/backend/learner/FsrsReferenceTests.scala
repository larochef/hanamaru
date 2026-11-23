package hanamaru.backend.learner

import hanamaru.shared.learner.Grade

import java.time.{Duration, Instant}

import utest.*

/** py-fsrs 6.3.3's own scheduling tests (`tests/test_basic.py`), ported with
  * their expected values: those were computed by the reference implementation,
  * so they check this port against it. Its tests of serialisation, of the
  * random spread of intervals and of the optimiser have no counterpart here.
  */
object FsrsReferenceTests extends TestSuite {
  private val standard = Fsrs.standard
  private val start = Instant.parse("2022-11-29T12:30:00Z")

  private def days(from: Instant, to: Instant): Long =
    Duration.between(from, to).toDays

  // the whole days from a card's last review to its due date
  private def interval(memory: Memory): Long =
    days(memory.lastReview, memory.due)

  /** Answers a card again and again, each time when it is due. */
  private def reviewedWhenDue(
      fsrs: Fsrs,
      grades: Seq[Grade],
      from: Instant = start
  ): List[Memory] =
    grades
      .foldLeft((Option.empty[Memory], List.empty[Memory])) {
        case ((memory, history), grade) =>
          val next = fsrs.reviewed(memory, grade, memory.fold(from)(_.due))
          (Some(next), history :+ next)
      }
      ._2

  private def near(actual: Double, expected: Double) =
    assert(math.abs(actual - expected) < 1e-4)

  private def within(actual: Duration, expected: Duration) =
    assert(actual.minus(expected).abs().compareTo(Duration.ofSeconds(1)) <= 0)

  val tests: Tests = Tests {
    test("test_review_card: the intervals of a run of answers") {
      val grades = Seq.fill(6)(Grade.Good) ++ Seq.fill(2)(Grade.Again) ++
        Seq.fill(5)(Grade.Good)
      val intervals = reviewedWhenDue(standard, grades).map(interval)
      assert(intervals == List(0, 2, 11, 46, 163, 498, 0, 0, 2, 4, 7, 12, 21))
    }
    test("test_repeated_correct_reviews: easy again and again, easiest card") {
      val memory = (0 until 10).foldLeft(Option.empty[Memory]) {
        (memory, micros) =>
          Some(
            standard.reviewed(
              memory,
              Grade.Easy,
              start.plusNanos(micros * 1000L)
            )
          )
      }
      assert(memory.map(_.difficulty).contains(1.0))
    }
    test("test_memo_state: stability and difficulty after a history") {
      val history = Seq(
        Grade.Again -> 0,
        Grade.Good -> 0,
        Grade.Good -> 1,
        Grade.Good -> 3,
        Grade.Good -> 8,
        Grade.Good -> 21
      )
      val (memory, _) = history.foldLeft((Option.empty[Memory], start)) {
        case ((memory, at), (grade, wait)) =>
          val when = at.plus(Duration.ofDays(wait))
          (Some(standard.reviewed(memory, grade, when)), when)
      }
      near(memory.get.stability, 53.62691)
      near(memory.get.difficulty, 6.3574867)
    }
    test("test_retrievability: between 0 and 1 in every phase") {
      val learning = standard.reviewed(None, Grade.Good, start)
      val review = standard.reviewed(Some(learning), Grade.Good, learning.due)
      val relearning = standard.reviewed(Some(review), Grade.Again, review.due)
      assert(
        learning.phase == Phase.Learning,
        review.phase == Phase.Review,
        relearning.phase == Phase.Relearning
      )
      for (memory <- Seq(learning, review, relearning)) {
        val retrievability = standard.retrievability(memory, memory.due)
        assert(retrievability >= 0, retrievability <= 1)
      }
    }
    test("test_good_learning_steps") {
      val first = standard.reviewed(None, Grade.Good, start)
      assert(first.phase == Phase.Learning, first.step.contains(1))
      assert(
        math.round(Duration.between(start, first.due).toSeconds / 100.0) == 6
      )
      val second = standard.reviewed(Some(first), Grade.Good, first.due)
      assert(second.phase == Phase.Review, second.step.isEmpty)
      assert(
        math.round(Duration.between(start, second.due).toSeconds / 3600.0) >= 24
      )
    }
    test("test_again_learning_steps") {
      val memory = standard.reviewed(None, Grade.Again, start)
      assert(memory.phase == Phase.Learning, memory.step.contains(0))
      assert(
        math.round(Duration.between(start, memory.due).toSeconds / 10.0) == 6
      )
    }
    test("test_hard_learning_steps") {
      val memory = standard.reviewed(None, Grade.Hard, start)
      assert(memory.phase == Phase.Learning, memory.step.contains(0))
      assert(
        math.round(Duration.between(start, memory.due).toSeconds / 10.0) == 33
      )
    }
    test("test_easy_learning_steps") {
      val memory = standard.reviewed(None, Grade.Easy, start)
      assert(memory.phase == Phase.Review, memory.step.isEmpty)
      assert(
        math.round(Duration.between(start, memory.due).toSeconds / 86400.0) >= 1
      )
    }
    test("test_review_state") {
      val history = reviewedWhenDue(
        standard,
        Seq(Grade.Good, Grade.Good, Grade.Good, Grade.Again)
      )
      val List(_, inReview, again, lapse) = history: @unchecked
      assert(inReview.phase == Phase.Review, inReview.step.isEmpty)
      assert(again.phase == Phase.Review)
      assert(
        math.round(
          Duration.between(inReview.due, again.due).toSeconds / 3600.0
        ) >= 24
      )
      assert(lapse.phase == Phase.Relearning)
      assert(
        math.round(
          Duration.between(again.due, lapse.due).toSeconds / 60.0
        ) == 10
      )
    }
    test("test_relearning") {
      val history = reviewedWhenDue(
        standard,
        Seq(
          Grade.Good,
          Grade.Good,
          Grade.Good,
          Grade.Again,
          Grade.Again,
          Grade.Good
        )
      )
      val List(_, _, third, lapse, again, relearned) = history: @unchecked
      assert(lapse.phase == Phase.Relearning, lapse.step.contains(0))
      assert(
        math.round(
          Duration.between(third.due, lapse.due).toSeconds / 60.0
        ) == 10
      )
      assert(again.phase == Phase.Relearning, again.step.contains(0))
      assert(
        math.round(
          Duration.between(lapse.due, again.due).toSeconds / 60.0
        ) == 10
      )
      assert(relearned.phase == Phase.Review, relearned.step.isEmpty)
      assert(
        math.round(
          Duration.between(again.due, relearned.due).toSeconds / 3600.0
        ) >= 24
      )
    }
    test("test_no_learning_steps") {
      val memory =
        Fsrs(learningSteps = Vector()).reviewed(None, Grade.Again, start)
      assert(memory.phase == Phase.Review, interval(memory) >= 1)
    }
    test("test_no_relearning_steps") {
      val history = reviewedWhenDue(
        Fsrs(relearningSteps = Vector()),
        Seq(Grade.Good, Grade.Good, Grade.Again)
      )
      assert(
        history.map(_.phase) == List(Phase.Learning, Phase.Review, Phase.Review)
      )
      assert(interval(history.last) >= 1)
    }
    test("test_maximum_interval") {
      val fsrs = Fsrs(maximumInterval = 100)
      val history =
        reviewedWhenDue(
          fsrs,
          Seq(Grade.Easy, Grade.Good, Grade.Easy, Grade.Good)
        )
      assert(history.forall(interval(_) <= 100))
    }
    test("test_stability_lower_bound") {
      (1 to 1000).foldLeft(Option.empty[Memory]) { (memory, _) =>
        val at = memory.fold(start)(_.due.plus(Duration.ofDays(1)))
        val next = standard.reviewed(memory, Grade.Again, at)
        assert(next.stability >= 0.001)
        Some(next)
      }
    }
    test("test_same_day_hard_review_does_not_decrease_stability") {
      val fsrs = Fsrs(learningSteps = Vector())
      val at = Instant.parse("2026-01-01T00:00:00Z")
      val good = fsrs.reviewed(None, Grade.Good, at)
      val hard = fsrs.reviewed(Some(good), Grade.Hard, at.plusSeconds(60))
      assert(hard.stability == good.stability)
    }
    test("test_learning_card_rate_hard_one_learning_step") {
      val memory = Fsrs(learningSteps = Vector(Duration.ofMinutes(10)))
        .reviewed(None, Grade.Hard, start)
      assert(memory.phase == Phase.Learning)
      within(Duration.between(start, memory.due), Duration.ofMinutes(15))
    }
    test("test_learning_card_rate_hard_second_learning_step") {
      val fsrs = Fsrs(learningSteps =
        Vector(Duration.ofMinutes(1), Duration.ofMinutes(10))
      )
      val good = fsrs.reviewed(None, Grade.Good, start)
      assert(good.phase == Phase.Learning, good.step.contains(1))
      val hard = fsrs.reviewed(Some(good), Grade.Hard, good.due)
      assert(hard.phase == Phase.Learning, hard.step.contains(1))
      within(Duration.between(good.due, hard.due), Duration.ofMinutes(10))
    }
    test("test_long_term_stability_learning_state") {
      val easy = standard.reviewed(None, Grade.Easy, start)
      assert(easy.phase == Phase.Review)
      val lapse = standard.reviewed(Some(easy), Grade.Again, easy.due)
      assert(lapse.phase == Phase.Relearning)
      val late = standard.reviewed(
        Some(lapse),
        Grade.Good,
        lapse.due.plus(Duration.ofDays(1))
      )
      assert(late.phase == Phase.Review)
    }
    test("test_relearning_card_rate_hard_one_relearning_step") {
      val fsrs = Fsrs(relearningSteps = Vector(Duration.ofMinutes(10)))
      val easy = fsrs.reviewed(None, Grade.Easy, start)
      val lapse = fsrs.reviewed(Some(easy), Grade.Again, easy.due)
      assert(lapse.phase == Phase.Relearning, lapse.step.contains(0))
      val hard = fsrs.reviewed(Some(lapse), Grade.Hard, lapse.due)
      assert(hard.phase == Phase.Relearning, hard.step.contains(0))
      within(Duration.between(lapse.due, hard.due), Duration.ofMinutes(15))
    }
    test("test_relearning_card_rate_hard_two_relearning_steps") {
      val fsrs = Fsrs(relearningSteps =
        Vector(Duration.ofMinutes(1), Duration.ofMinutes(10))
      )
      val easy = fsrs.reviewed(None, Grade.Easy, start)
      val lapse = fsrs.reviewed(Some(easy), Grade.Again, easy.due)
      assert(lapse.phase == Phase.Relearning, lapse.step.contains(0))
      val hard = fsrs.reviewed(Some(lapse), Grade.Hard, lapse.due)
      assert(hard.phase == Phase.Relearning, hard.step.contains(0))
      within(Duration.between(lapse.due, hard.due), Duration.ofSeconds(330))
      val good = fsrs.reviewed(Some(hard), Grade.Good, hard.due)
      assert(good.phase == Phase.Relearning, good.step.contains(1))
      val hardAgain = fsrs.reviewed(Some(good), Grade.Hard, good.due)
      assert(hardAgain.phase == Phase.Relearning, hardAgain.step.contains(1))
      within(Duration.between(good.due, hardAgain.due), Duration.ofMinutes(10))
      val easyAgain = fsrs.reviewed(Some(hardAgain), Grade.Easy, good.due)
      assert(easyAgain.phase == Phase.Review, easyAgain.step.isEmpty)
    }
  }
}
