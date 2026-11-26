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

/** A drawn stroke's verdict, and how far it was from the expected stroke, in
  * units of the 109-unit box: in shape (the mean distance between their points,
  * once their centres are put together), and in place (the distance between
  * their centres).
  */
final case class StrokeJudgement(
    verdict: StrokeVerdict,
    distance: Double,
    offset: Double = 0
)

/** Judges drawn strokes against KanjiVG's, deterministically: both are
  * resampled to the same number of points evenly spaced along them, and
  * compared point by point. Shape and place are judged apart, the place more
  * leniently: a stroke drawn right but a little off is a stroke drawn right.
  */
object StrokeMatcher {
  private val samples = 32

  /** How far a stroke's shape may be from the expected one's: a fixed part, and
    * a part growing with the stroke's length. Tuned on real stylus attempts
    * written from memory.
    */
  val baseTolerance: Double = 8
  val lengthTolerance: Double = 0.12

  /** How far a stroke's centre may be from the expected one's. */
  val placeTolerance: Double = 22

  /** Judges `drawn` as the stroke at `expected` (counted from 0) of a character
    * whose strokes are `strokes`; `next`, the stroke drawn after it, when the
    * character is judged once written.
    */
  def judge(
      drawn: List[Point],
      strokes: List[List[Point]],
      expected: Int,
      next: Option[List[Point]] = None
  ): StrokeJudgement = {
    val line = resampled(drawn)
    val target = strokes(expected)
    val shapeForward = shapeDistance(line, target)
    val shapeBackward = shapeDistance(line.reverse, target)
    val offset = distance(centre(line), centre(resampled(target)))
    val fits = shapeForward.min(shapeBackward) <= toleranceOf(target) &&
      offset <= placeTolerance
    // A later stroke fitting better than the expected one, shape and place
    // together, is that one drawn too early. When the expected stroke fits
    // too, the later one must fit far better: two strokes alike (ほ's two
    // bars) are told apart by place alone, and a bar a little low is still
    // the bar expected.
    val inPlace = inPlaceDistance(line, target)
    val laterMargin = if (fits) 0.5 else 1.0
    val nearestLater = strokes.zipWithIndex
      .drop(expected + 1)
      .filter((stroke, _) =>
        shapeDistance(line, stroke) <= toleranceOf(stroke) &&
          distance(centre(line), centre(resampled(stroke))) <= placeTolerance
      )
      .map((stroke, index) => index -> inPlaceDistance(line, stroke))
      .minByOption(_._2)
      .filter(_._2 < laterMargin * inPlace)
      // Taken for the stroke right after it, while the next one drawn is
      // there: the two are judged together, by where they are one from the
      // other. Hands put a dakuten's two strokes, or ジ's ticks, a little off
      // together, the first then nearer the second's place.
      .filterNot((index, _) =>
        index == expected + 1 &&
          next.exists(inOrder(line, _, target, strokes(index)))
      )
    // Short strokes, a dakuten's, stay within the tolerance even reversed: the
    // direction is the one that matches best, not the one within it. A loop
    // has no direction to judge.
    val verdict = nearestLater match {
      case Some((index, _)) => StrokeVerdict.WrongOrder(index + 1)
      case None if !fits    => StrokeVerdict.Misdrawn
      case None if !isLoop(target) && shapeBackward < shapeForward =>
        StrokeVerdict.WrongDirection
      case None => StrokeVerdict.Accepted
    }
    StrokeJudgement(verdict, shapeForward.min(shapeBackward), offset)
  }

  // Whether `first` and `second`, drawn one after the other, are `a` then `b`
  // rather than `b` then `a`, the shift they share taken away.
  private def inOrder(
      first: Vector[Point],
      second: List[Point],
      a: List[Point],
      b: List[Point]
  ): Boolean = {
    val (to, from) = (resampled(a) ++ resampled(b), first ++ resampled(second))
    val (dx, dy) = (
      to.map(_.x).sum / to.size - from.map(_.x).sum / from.size,
      to.map(_.y).sum / to.size - from.map(_.y).sum / from.size
    )
    def moved(line: Vector[Point]) =
      line.map(point => Point(point.x + dx, point.y + dy))
    val (one, two) = (moved(first), moved(resampled(second)))
    inPlaceDistance(one, a) + inPlaceDistance(two, b) <=
      inPlaceDistance(one, b) + inPlaceDistance(two, a)
  }

  private def toleranceOf(stroke: List[Point]): Double =
    baseTolerance + lengthTolerance * length(stroke)

  /** A stroke that ends about where it starts, as a handakuten's circle. */
  private def isLoop(stroke: List[Point]): Boolean =
    distance(stroke.head, stroke.last) < 0.25 * length(stroke)

  // The shape alone, the drawn line moved onto the expected stroke's centre;
  // a loop compared from any start, both ways round.
  private def shapeDistance(
      line: Vector[Point],
      stroke: List[Point]
  ): Double = {
    val target = resampled(stroke)
    val (from, to) = (centre(line), centre(target))
    val moved =
      line.map(point => Point(point.x + to.x - from.x, point.y + to.y - from.y))
    if (isLoop(stroke)) {
      val open = moved.dropRight(1)
      val ring = target.dropRight(1)
      open.indices
        .flatMap { start =>
          val turned = open.drop(start) ++ open.take(start)
          Seq(turned, turned.reverse)
        }
        .map(meanDistance(_, ring))
        .min
    } else {
      meanDistance(moved, target)
    }
  }

  // shape and place together, either way along the stroke
  private def inPlaceDistance(
      line: Vector[Point],
      stroke: List[Point]
  ): Double = {
    val target = resampled(stroke)
    meanDistance(line, target).min(meanDistance(line.reverse, target))
  }

  private def centre(points: Vector[Point]): Point =
    Point(points.map(_.x).sum / points.size, points.map(_.y).sum / points.size)

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
