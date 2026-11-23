package hanamaru.backend.placement

import hanamaru.backend.lessons.Curriculum

import utest.*

object GrammarPlacementTests extends TestSuite {
  private val points = Curriculum.points
  private val placement = GrammarPlacement(points)

  private def right(point: Int, number: Int) =
    Answered(AnswerKind.Grammar, s"${points(point).id}#$number", correct = true)
  private def wrong(point: Int, number: Int) =
    Answered(
      AnswerKind.Grammar,
      s"${points(point).id}#$number",
      correct = false
    )

  val tests: Tests = Tests {
    test("the first point's first question comes first") {
      assert(placement.progress(Nil) == (Some(points.head -> 0), Nil))
    }
    test(
      "a point is passed with both questions right, then the next is asked"
    ) {
      val (next, decided) = placement.progress(List(right(0, 0), right(0, 1)))
      assert(next == Some(points(1) -> 0), decided == List(points.head -> true))
    }
    test("a first question missed fails the point without its second") {
      val (next, decided) = placement.progress(List(wrong(0, 0)))
      assert(
        next == Some(points(1) -> 0),
        decided == List(points.head -> false)
      )
    }
    test("two points failed in a row end the test") {
      val (next, decided) = placement.progress(
        List(right(0, 0), right(0, 1), wrong(1, 0), right(2, 0), wrong(2, 1))
      )
      assert(next.isEmpty, decided.map(_._2) == List(true, false, false))
    }
    test("a failed point then a passed one: the test goes on") {
      val (next, _) =
        placement.progress(List(wrong(0, 0), right(1, 0), right(1, 1)))
      assert(next == Some(points(2) -> 0))
    }
    test("given up, nothing more is asked") {
      assert(
        placement.progress(
          List(Answered(AnswerKind.Skip, "grammar", correct = false))
        ) == (None, Nil)
      )
    }
  }
}
