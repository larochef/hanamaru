package hanamaru.backend.learner

import hanamaru.shared.learner.Grade

import java.time.{Duration, Instant}

import utest.*

object FsrsTests extends TestSuite {
  private val start = Instant.parse("2026-01-01T09:00:00Z")

  private def near(actual: Double, expected: Double) =
    assert(math.abs(actual - expected) < 1e-4)

  val tests: Tests = Tests {
    test("a new card answered right: its first stability, then its steps") {
      val first = Fsrs.standard.reviewed(None, Grade.Good, start)
      // stability w2; difficulty w4 - e^(2 w5) + 1
      near(first.stability, 2.3065)
      near(first.difficulty, 6.4133 - math.exp(0.8334 * 2) + 1)
      assert(first.phase == Phase.Learning)
      assert(first.step.contains(1))
      assert(first.due == start.plusSeconds(600))
      // the last step passed the same day: its stability does not drop, and
      // the card is due when it would be recalled 9 times out of 10
      val second = Fsrs.standard.reviewed(Some(first), Grade.Good, first.due)
      near(second.stability, 2.3065)
      assert(second.phase == Phase.Review)
      assert(second.due == first.due.plus(Duration.ofDays(2)))
    }
    test("a new card failed comes back after the first step") {
      val failed = Fsrs.standard.reviewed(None, Grade.Again, start)
      near(failed.stability, 0.212)
      assert(failed.step.contains(0))
      assert(failed.due == start.plusSeconds(60))
    }
    test("a new card answered easily skips its steps") {
      val easy = Fsrs.standard.reviewed(None, Grade.Easy, start)
      near(easy.stability, 8.2956)
      assert(easy.phase == Phase.Review)
      assert(easy.due == start.plus(Duration.ofDays(8)))
    }
    test("hard on the first step waits between the two steps") {
      val hard = Fsrs.standard.reviewed(None, Grade.Hard, start)
      assert(hard.due == start.plusSeconds(330))
      assert(hard.step.contains(0))
    }
    test(
      "at 90% retention, a card is due after as many days as its stability"
    ) {
      assert(Fsrs.standard.intervalDays(2.3065) == 2)
      assert(Fsrs.standard.intervalDays(10.4) == 10)
      assert(Fsrs.standard.intervalDays(100) == 100)
      assert(Fsrs.standard.intervalDays(0.2) == 1)
      val memory = Memory(Phase.Review, None, 10, 5, start, start)
      near(
        Fsrs.standard.retrievability(memory, start.plus(Duration.ofDays(10))),
        0.9
      )
      near(Fsrs.standard.retrievability(memory, start), 1)
    }
    test("remembered when due: the memory grows stronger") {
      val memory = Memory(Phase.Review, None, 10, 5, start, start)
      val due = start.plus(Duration.ofDays(10))
      val good = Fsrs.standard.reviewed(Some(memory), Grade.Good, due)
      assert(good.stability > 20)
      assert(good.phase == Phase.Review)
      val easy = Fsrs.standard.reviewed(Some(memory), Grade.Easy, due)
      val hard = Fsrs.standard.reviewed(Some(memory), Grade.Hard, due)
      assert(easy.stability > good.stability)
      assert(hard.stability < good.stability)
      assert(
        easy.difficulty < good.difficulty && good.difficulty < hard.difficulty
      )
    }
    test("forgotten when due: relearned, its stability lower") {
      val memory = Memory(Phase.Review, None, 10, 5, start, start)
      val due = start.plus(Duration.ofDays(10))
      val lapse = Fsrs.standard.reviewed(Some(memory), Grade.Again, due)
      assert(lapse.phase == Phase.Relearning)
      assert(lapse.step.contains(0))
      assert(lapse.stability < 10)
      assert(lapse.due == due.plusSeconds(600))
      val relearned = Fsrs.standard.reviewed(Some(lapse), Grade.Good, lapse.due)
      assert(relearned.phase == Phase.Review)
    }
  }
}
