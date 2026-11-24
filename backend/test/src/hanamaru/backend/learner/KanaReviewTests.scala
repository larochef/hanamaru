package hanamaru.backend.learner

import hanamaru.shared.learner.{ItemStatus, KanaProgress}

import scala.util.Random

import utest.*

object KanaReviewTests extends TestSuite {
  private val now = 1_000_000L

  // fifty draws from one generator
  private def picked(
      progress: List[KanaProgress],
      after: Option[String] = None
  ): Set[String] = {
    val random = Random(1)
    (1 to 50)
      .flatMap(_ => KanaReview.next(progress, now, after, random))
      .map(_.character)
      .toSet
  }

  val tests: Tests = Tests {
    test("nothing to review before any kana is met") {
      assert(KanaReview.next(Nil, now, None, Random(1)).isEmpty)
    }
    test(
      "due first, being learned first; then to confirm; then being learned, then any"
    ) {
      val learningDue = KanaProgress("あ", ItemStatus.Learning, Some(now - 1))
      val confirmDue = KanaProgress("う", ItemStatus.ToConfirm, Some(now - 1))
      val toConfirm = KanaProgress("お", ItemStatus.ToConfirm, Some(now + 50))
      val learning = KanaProgress("い", ItemStatus.Learning, Some(now + 1))
      val known = KanaProgress("え", ItemStatus.Known, Some(now + 100))
      val all = List(known, learning, toConfirm, confirmDue, learningDue)
      assert(picked(all) == Set("あ"))
      assert(picked(all.take(4)) == Set("う"))
      assert(picked(all.take(3)) == Set("お"))
      assert(picked(all.take(2)) == Set("い"))
      assert(picked(all.take(1)) == Set("え"))
    }
    test("being learned and not due: the one due soonest, each in turn") {
      val learning = List("あ" -> 3L, "い" -> 1L, "う" -> 2L).map((kana, in) =>
        KanaProgress(kana, ItemStatus.Learning, Some(now + in))
      )
      assert(picked(learning) == Set("い"))
      assert(picked(learning, after = Some("い")) == Set("う"))
    }
    test("at random among those due, never the kana just reviewed") {
      val due = List("あ", "い", "う").map(
        KanaProgress(_, ItemStatus.Learning, Some(now - 1))
      )
      assert(picked(due) == Set("あ", "い", "う"))
      assert(picked(due, after = Some("あ")) == Set("い", "う"))
      assert(picked(due.take(1), after = Some("あ")) == Set("あ"))
    }
  }
}
