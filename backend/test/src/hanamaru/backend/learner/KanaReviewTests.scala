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
      "being learned and due first, then being learned, then due, then any"
    ) {
      val learningDue = KanaProgress("あ", ItemStatus.Learning, Some(now - 1))
      val learning = KanaProgress("い", ItemStatus.Learning, Some(now + 1))
      val confirmDue = KanaProgress("う", ItemStatus.ToConfirm, Some(now - 1))
      val known = KanaProgress("え", ItemStatus.Known, Some(now + 100))
      assert(picked(List(known, confirmDue, learning, learningDue)) == Set("あ"))
      assert(picked(List(known, confirmDue, learning)) == Set("い"))
      assert(picked(List(known, confirmDue)) == Set("う"))
      assert(picked(List(known)) == Set("え"))
    }
    test("at random within a level, never the kana just reviewed") {
      val learning = List("あ", "い", "う").map(
        KanaProgress(_, ItemStatus.Learning, Some(now + 1))
      )
      assert(picked(learning) == Set("あ", "い", "う"))
      assert(picked(learning, after = Some("あ")) == Set("い", "う"))
      assert(picked(learning.take(1), after = Some("あ")) == Set("あ"))
    }
  }
}
