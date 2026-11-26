package hanamaru.backend.writing

import hanamaru.shared.writing.*

import utest.*

object WritingCorrectionTests extends TestSuite {

  // an invented character: a long bar, a vertical crossing it, a short tick
  private val model = List(
    SvgPath.points("M20,50c20,0,50,0,70,0"),
    SvgPath.points("M55,15c0,20,0,50,0,75"),
    SvgPath.points("M80,15l6,8")
  )

  private def drawn(points: List[Point]*): List[List[DrawnPoint]] =
    points.toList.map(_.map(point => DrawnPoint(point.x, point.y, 0.5, 0)))

  private def written(strokes: List[Point]*) =
    WritingCorrection.attempt("x", drawn(strokes*), model)

  val tests: Tests = Tests {
    test("written right: no issue") {
      val attempt = written(model*)
      assert(WritingCorrection.issues(attempt).isEmpty)
      assert(WritingCorrection.outcome(attempt) == WritingOutcome.Right)
      assert(attempt.completed, attempt.expectedStrokes == 3)
    }
    test("not remembered: wrong, whatever was drawn before") {
      val attempt = written(model*).copy(forgotten = true)
      assert(WritingCorrection.outcome(attempt) == WritingOutcome.Wrong)
      assert(
        WritingCorrection.outcome(written().copy(forgotten = true)) ==
          WritingOutcome.Wrong
      )
    }
    test("a stroke the other way: right but for its direction") {
      val attempt = written(model(0), model(1).reverse, model(2))
      assert(
        WritingCorrection.issues(attempt) == List(
          WritingIssue.WrongDirection(2)
        )
      )
      assert(WritingCorrection.outcome(attempt) == WritingOutcome.StrokeOrder)
    }
    test("two strokes swapped") {
      val attempt = written(model(1), model(0), model(2))
      assert(
        WritingCorrection.issues(attempt).head == WritingIssue.WrongOrder(1, 2)
      )
      assert(WritingCorrection.outcome(attempt) != WritingOutcome.Right)
    }
    test("a stroke left out, or one too many") {
      val short = written(model(0), model(1))
      assert(
        WritingCorrection.issues(short) == List(WritingIssue.Missing(1, 3))
      )
      assert(WritingCorrection.outcome(short) == WritingOutcome.Wrong)
      val long = written(model(0), model(1), model(2), model(2))
      assert(WritingCorrection.issues(long) == List(WritingIssue.Extra(4)))
      assert(WritingCorrection.outcome(long) == WritingOutcome.Wrong)
    }
    test("a stroke not the one expected") {
      val attempt =
        written(model(0), List(Point(10, 90), Point(30, 70)), model(2))
      assert(
        WritingCorrection.issues(attempt) == List(WritingIssue.Misdrawn(2))
      )
      assert(WritingCorrection.outcome(attempt) == WritingOutcome.Wrong)
    }
  }
}
