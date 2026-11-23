package hanamaru.shared.writing

/** The points along an SVG path made of moves, lines and cubic Béziers, as
  * KanjiVG writes its strokes: absolute or relative, smooth or not.
  */
object SvgPath {
  private val segmentsPerCurve = 8
  private val token =
    """[MmLlCcSsHhVvZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE]-?\d+)?""".r

  def points(path: String): List[Point] = {
    val tokens = token.findAllIn(path).toVector
    val points = Vector.newBuilder[Point]
    var at = Point(0, 0)
    var start = at
    // the second control point of the last curve, which a smooth one mirrors
    var lastControl: Option[Point] = None
    var command = 'M'
    var index = 0

    def number(): Double = {
      val value = tokens(index).toDouble
      index += 1
      value
    }
    def point(relative: Boolean): Point = {
      val x = number()
      val y = number()
      if (relative) Point(at.x + x, at.y + y) else Point(x, y)
    }
    def curve(control1: Point, control2: Point, end: Point): Unit = {
      val from = at
      for (step <- 1 to segmentsPerCurve) {
        points += cubic(
          from,
          control1,
          control2,
          end,
          step.toDouble / segmentsPerCurve
        )
      }
      lastControl = Some(control2)
      at = end
    }

    while (index < tokens.size) {
      if (tokens(index).head.isLetter) {
        command = tokens(index).head
        index += 1
      }
      val relative = command.isLower
      command.toUpper match {
        case 'M' =>
          at = point(relative)
          start = at
          points += at
          lastControl = None
          // coordinates after a move are lines
          command = if (relative) 'l' else 'L'
        case 'L' =>
          at = point(relative)
          points += at
          lastControl = None
        case 'H' =>
          val x = number()
          at = Point(if (relative) at.x + x else x, at.y)
          points += at
          lastControl = None
        case 'V' =>
          val y = number()
          at = Point(at.x, if (relative) at.y + y else y)
          points += at
          lastControl = None
        case 'C' =>
          val control1 = point(relative)
          val control2 = point(relative)
          curve(control1, control2, point(relative))
        case 'S' =>
          val control1 = lastControl
            .map(control => Point(2 * at.x - control.x, 2 * at.y - control.y))
            .getOrElse(at)
          val control2 = point(relative)
          curve(control1, control2, point(relative))
        case 'Z' =>
          at = start
          points += at
          lastControl = None
        case other =>
          throw IllegalArgumentException(s"Unsupported path command $other")
      }
    }
    points.result().toList
  }

  private def cubic(
      from: Point,
      control1: Point,
      control2: Point,
      to: Point,
      t: Double
  ): Point = {
    val u = 1 - t
    val a = u * u * u
    val b = 3 * u * u * t
    val c = 3 * u * t * t
    val d = t * t * t
    Point(
      a * from.x + b * control1.x + c * control2.x + d * to.x,
      a * from.y + b * control1.y + c * control2.y + d * to.y
    )
  }
}
