package hanamaru.backend.learner

import java.time.Duration

import utest.*

object WaitingTests extends TestSuite {

  private def days(count: Long) = Some(Duration.ofDays(count))

  val tests: Tests = Tests {
    test("room left by the reviews: new items take it, as many as allowed") {
      assert(
        Waiting.room(due = 4, capacity = 10, most = 3, waited = days(0)) == 3,
        Waiting.room(due = 8, capacity = 10, most = 3, waited = days(0)) == 2
      )
    }
    test("no room left: one new item once they have waited past the patience") {
      assert(
        Waiting.room(due = 10, capacity = 10, most = 3, waited = days(1)) == 0,
        Waiting.room(due = 10, capacity = 10, most = 3, waited = days(4)) == 1,
        Waiting.room(due = 10, capacity = 10, most = 3, waited = None) == 0
      )
    }
    test(
      "the patience grows with the backlog, for the reviews to come on time"
    ) {
      // twice the capacity due: three times the patience
      assert(
        Waiting.room(due = 20, capacity = 10, most = 3, waited = days(4)) == 0,
        Waiting.room(due = 20, capacity = 10, most = 3, waited = days(6)) == 1
      )
    }
  }
}
