package hanamaru.shared.writing

/** What a drawn stroke is judged to be, against the stroke expected next. */
enum StrokeVerdict {
  case Accepted

  /** The right stroke, drawn from its end to its start. */
  case WrongDirection

  /** Another stroke of the character, one meant to come later (counted from 1).
    */
  case WrongOrder(drawnStroke: Int)

  /** None of the character's strokes. */
  case Misdrawn
}

/** A drawn stroke's verdict, and how far it was from the expected stroke: the
  * mean distance between their points, in units of the 109-unit box.
  */
final case class StrokeJudgement(verdict: StrokeVerdict, distance: Double)

/** Judges drawn strokes against KanjiVG's, deterministically: both are
  * resampled to the same number of points evenly spaced along them, and
  * compared point by point, in place in the box. Position, size, shape and
  * direction all count.
  */
object StrokeMatcher {
  private val samples = 32

  /** How far a stroke may be from the expected one: a fixed part, for where it
    * is placed, and a part growing with the stroke's length, for its shape.
    * First guesses, to be tuned on real stylus input.
    */
  val baseTolerance: Double = 8
  val lengthTolerance: Double = 0.12

  /** Judges `drawn` as the stroke at `expected` (counted from 0) of a character
    * whose strokes are `strokes`.
    */
  def judge(
      drawn: List[Point],
      strokes: List[List[Point]],
      expected: Int
  ): StrokeJudgement = {
    val shape = resampled(drawn)
    def distanceTo(stroke: List[Point]) = meanDistance(shape, resampled(stroke))
    val target = strokes(expected)
    val forward = distanceTo(target)
    val backward = meanDistance(shape.reverse, resampled(target))
    val nearest = forward.min(backward)
    // a later stroke closer than the expected one: it is that one, too early
    val nearestLater = strokes.zipWithIndex
      .drop(expected + 1)
      .map((stroke, index) => (index, distanceTo(stroke), toleranceOf(stroke)))
      .filter((_, distance, tolerance) => distance <= tolerance)
      .minByOption(_._2)
      .filter(_._2 < nearest)
    // Short strokes, a dakuten's, stay within the tolerance even reversed: the
    // direction is the one that matches best, not the one within it.
    val verdict = nearestLater match {
      case Some((index, _, _)) => StrokeVerdict.WrongOrder(index + 1)
      case None if nearest > toleranceOf(target) => StrokeVerdict.Misdrawn
      case None if backward < forward            => StrokeVerdict.WrongDirection
      case None                                  => StrokeVerdict.Accepted
    }
    StrokeJudgement(verdict, forward)
  }

  private def toleranceOf(stroke: List[Point]): Double =
    baseTolerance + lengthTolerance * length(stroke)

  def length(points: List[Point]): Double =
    points.zip(points.drop(1)).map(distance).sum

  /** `count` points evenly spaced along the line through `points`. */
  def resampled(points: List[Point], count: Int = samples): Vector[Point] = {
    val vertices = points.toVector
    val total = length(points)
    if (vertices.size < 2 || total == 0) {
      Vector.fill(count)(vertices.headOption.getOrElse(Point(0, 0)))
    } else {
      val step = total / (count - 1)
      val result = Vector.newBuilder[Point]
      result += vertices.head
      var segment = 0
      // how far along the current segment the next point lies
      var walked = 0.0
      for (_ <- 1 until count - 1) {
        var remaining = step
        var found = false
        while (!found) {
          val from = vertices(segment)
          val to = vertices(segment + 1)
          val segmentLength = distance(from, to)
          if (
            walked + remaining <= segmentLength || segment == vertices.size - 2
          ) {
            walked += remaining
            val t =
              if (segmentLength == 0) 0.0 else (walked / segmentLength).min(1)
            result += Point(
              from.x + t * (to.x - from.x),
              from.y + t * (to.y - from.y)
            )
            found = true
          } else {
            remaining -= segmentLength - walked
            walked = 0
            segment += 1
          }
        }
      }
      result += vertices.last
      result.result()
    }
  }

  private def meanDistance(a: Vector[Point], b: Vector[Point]): Double =
    a.zip(b).map(distance).sum / a.size

  private def distance(a: Point, b: Point): Double =
    math.hypot(a.x - b.x, a.y - b.y)
}
