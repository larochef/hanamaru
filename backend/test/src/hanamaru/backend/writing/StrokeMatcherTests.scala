package hanamaru.backend.writing

import hanamaru.shared.writing.*

import utest.*

object StrokeMatcherTests extends TestSuite {

  // an invented character: a long horizontal stroke, then a vertical one
  // crossing it, then a short tick at the top right
  private val strokes = List(
    SvgPath.points("M20,50c20,0,50,0,70,0"),
    SvgPath.points("M55,15c0,20,0,50,0,75"),
    SvgPath.points("M80,15l6,8")
  )

  private def judged(drawn: List[Point], expected: Int): StrokeVerdict =
    StrokeMatcher.judge(drawn, strokes, expected).verdict

  private def shifted(points: List[Point], dx: Double, dy: Double) =
    points.map(point => Point(point.x + dx, point.y + dy))

  val tests: Tests = Tests {
    test("paths: relative curves, lines and moves") {
      val points = SvgPath.points("M10,10c0,0,10,0,10,0l0,10m5,5L30,30")
      assert(points.head == Point(10, 10))
      assert(points.contains(Point(20, 10)))
      assert(points.contains(Point(20, 20)))
      assert(points.contains(Point(25, 25)))
      assert(points.last == Point(30, 30))
    }
    test("paths: a smooth curve mirrors the last control point") {
      val points = SvgPath.points("M0,0C0,10,10,10,10,0S20,-10,20,0")
      assert(points.last == Point(20, 0))
      assert(points.exists(point => point.y < -1))
    }
    test("paths: numbers run together, as KanjiVG writes them") {
      val points = SvgPath.points(
        "M31.01,33c0.88,0.88,2.75,1.82,5.25,1.75c8.62-0.25,20-2.12,29.5-4.25"
      )
      assert(math.abs(points.last.x - (31.01 + 5.25 + 29.5)) < 1e-9)
      assert(math.abs(points.last.y - (33 + 1.75 - 4.25)) < 1e-9)
    }
    test("resampling: evenly spaced, from the start to the end") {
      val points = StrokeMatcher.resampled(
        List(Point(0, 0), Point(10, 0), Point(10, 10)),
        5
      )
      assert(
        points == Vector(
          Point(0, 0),
          Point(5, 0),
          Point(10, 0),
          Point(10, 5),
          Point(10, 10)
        )
      )
    }
    test("the stroke as expected, or nearly") {
      assert(judged(strokes(0), 0) == StrokeVerdict.Accepted)
      assert(judged(shifted(strokes(0), 4, -3), 0) == StrokeVerdict.Accepted)
      assert(judged(strokes(2), 2) == StrokeVerdict.Accepted)
    }
    test("the stroke drawn backwards") {
      assert(judged(strokes(0).reverse, 0) == StrokeVerdict.WrongDirection)
      assert(judged(strokes(1).reverse, 1) == StrokeVerdict.WrongDirection)
    }
    test("a later stroke drawn first") {
      assert(judged(strokes(1), 0) == StrokeVerdict.WrongOrder(2))
      assert(judged(strokes(2), 0) == StrokeVerdict.WrongOrder(3))
    }
    test("a stroke the character does not have") {
      assert(judged(shifted(strokes(0), 0, 30), 0) == StrokeVerdict.Misdrawn)
      assert(
        judged(List(Point(10, 90), Point(30, 70)), 1) == StrokeVerdict.Misdrawn
      )
    }
    test("a short stroke drawn backwards, as a dakuten's") {
      val dakuten =
        List(SvgPath.points("M80,15l6,8"), SvgPath.points("M86,12l6,8"))
      assert(
        StrokeMatcher
          .judge(dakuten(0).reverse, dakuten, 0)
          .verdict == StrokeVerdict.WrongDirection
      )
    }
    test("two close strokes drawn in the wrong order, as a dakuten's") {
      val dakuten =
        List(SvgPath.points("M80,15l6,8"), SvgPath.points("M86,12l6,8"))
      assert(
        StrokeMatcher.judge(dakuten(1), dakuten, 0).verdict == StrokeVerdict
          .WrongOrder(2)
      )
      assert(
        StrokeMatcher
          .judge(dakuten(0), dakuten, 0)
          .verdict == StrokeVerdict.Accepted
      )
    }
    test("a stroke drawn right but a little off its place") {
      assert(judged(shifted(strokes(0), 12, 14), 0) == StrokeVerdict.Accepted)
    }
    test("a circle drawn the other way round, from another start") {
      val circle = SvgPath.points("M50,40c-10,0,-10,20,0,20c10,0,10,-20,0,-20")
      val withCircle = List(strokes(0), circle)
      val turned = StrokeMatcher.resampled(circle).toList
      val otherStart = (turned.drop(10).dropRight(1) ++ turned.take(11)).reverse
      assert(
        StrokeMatcher
          .judge(otherStart, withCircle, 1)
          .verdict == StrokeVerdict.Accepted
      )
    }
    test("of two bars alike, the expected one drawn a little low is still it") {
      val bars =
        List(SvgPath.points("M30,40l40,0"), SvgPath.points("M30,60l40,0"))
      val low = shifted(bars(0), 0, 12)
      assert(
        StrokeMatcher.judge(low, bars, 0).verdict == StrokeVerdict.Accepted
      )
      assert(
        StrokeMatcher.judge(bars(1), bars, 0).verdict == StrokeVerdict
          .WrongOrder(2)
      )
    }
    test("a dot, drawn without moving") {
      assert(judged(List(Point(50, 50)), 0) == StrokeVerdict.Misdrawn)
    }
  }
}
