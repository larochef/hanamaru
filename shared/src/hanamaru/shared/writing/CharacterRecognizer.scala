package hanamaru.shared.writing

import hanamaru.shared.kana.{Kana, Script}

/** A character written by hand, and how far it is from a model (lower is
  * closer).
  */
final case class Recognised(character: String, distance: Double)

/** Which of `models` (characters and their strokes, in the 109-unit box) a
  * handwritten character is, whatever the character expected: the strokes and
  * the models are both brought to the same size and centre, then compared
  * stroke by stroke when they have as many strokes, and as one line, the pen's
  * moves between strokes included, so that two strokes written as one still
  * compare. Where and how large the character is drawn tells a small kana (ゃ)
  * from its large one (や).
  */
final class CharacterRecognizer(
    models: List[(String, List[List[Point]])],
    weights: CharacterRecognizer.Weights = CharacterRecognizer.Weights(),
    marked: Map[String, (String, Int)] = Map.empty
) {
  import CharacterRecognizer.*

  private val prepared = models
    .filter((_, strokes) => strokes.nonEmpty)
    .map((character, strokes) => character -> Shape(strokes))
  private val shapes = prepared.toMap

  // A character with marks (が, a dakuten's strokes last) is also compared as
  // its base and marks apart: the base against the base's model, the marks
  // only as marks, short and towards the base's upper right, wherever exactly
  // they are, as hands put them (で's dakuten above its corner).
  private def asMarked(
      strokes: List[List[Point]],
      character: String
  ): Option[Double] =
    marked.get(character).flatMap { (base, count) =>
      shapes
        .get(base)
        .filter(_ => strokes.size > count)
        .map { model =>
          val baseStrokes = strokes.dropRight(count)
          distance(Shape(baseStrokes), model, weights) +
            marksDistance(baseStrokes, strokes.takeRight(count))
        }
    }

  /** The models, closest first. */
  def ranked(drawn: List[List[Point]]): List[Recognised] = {
    val strokes = drawn.filter(_.nonEmpty)
    if (strokes.isEmpty) {
      Nil
    } else {
      val shape = Shape(strokes)
      prepared
        .map { (character, model) =>
          val whole = distance(shape, model, weights)
          Recognised(
            character,
            asMarked(strokes, character).fold(whole)(_.min(whole))
          )
        }
        .sortBy(_.distance)
    }
  }
}

object CharacterRecognizer {

  /** The kana a learner writes: those taught, and ー. */
  val kana: String = (Kana.all.map(_.character) :+ "ー").mkString

  /** A recogniser of the kana of `script` (ー with katakana), among `strokes`.
    */
  def ofKana(
      strokes: List[CharacterStrokes],
      script: Script
  ): CharacterRecognizer =
    CharacterRecognizer(
      strokes
        .filter(character =>
          if (character.character == "ー") script == Script.Katakana
          else Kana.find(character.character).exists(_.script == script)
        )
        .map(character =>
          character.character -> character.strokes
            .map(stroke => SvgPath.points(stroke.path))
        ),
      marked = kana.toList
        .map(_.toString)
        .flatMap(character => Kana.marksOf(character).map(character -> _))
        .toMap
    )

  /** How close the closest model must be for a drawing to be a character at
    * all: the distance does not tell a right recognition from a wrong one (both
    * are mostly between 0.1 and 0.2 on kana written from memory), but a
    * scribble is far from any.
    */
  val recognisedWithin: Double = 0.3

  private val strokeSamples = 16
  private val lineSamples = 48

  /** How much each difference counts: the strokes compared one by one, against
    * the line they make (`byStroke`, the rest); a stroke more or fewer than the
    * model's; the character drawn larger or smaller, or elsewhere. Tuned on
    * characters written from memory.
    */
  final case class Weights(
      byStroke: Double = 0.85,
      strokeCount: Double = 0.08,
      size: Double = 0.2,
      place: Double = 0.15,
      direction: Double = 0.2,
      byLength: Double = 0.5
  )

  // A character's strokes brought to a size of 1 around its centre: each
  // resampled, and all as one line; with its size and centre in the box.
  final private class Shape(strokes: List[List[Point]]) {
    private val points = strokes.flatten
    private val (left, right) = (points.map(_.x).min, points.map(_.x).max)
    private val (top, bottom) = (points.map(_.y).min, points.map(_.y).max)
    val size: Double = (right - left).max(bottom - top).max(1)
    val centre: Point = Point((left + right) / 2, (top + bottom) / 2)

    private def scaled(stroke: List[Point]) = stroke.map(point =>
      Point((point.x - centre.x) / size, (point.y - centre.y) / size)
    )

    val count: Int = strokes.size
    val each: List[Vector[Point]] =
      strokes.map(stroke =>
        StrokeMatcher.resampled(scaled(stroke), strokeSamples)
      )
    val line: Vector[Point] =
      StrokeMatcher.resampled(scaled(strokes.flatten), lineSamples)
    val lengths: List[Double] =
      strokes.map(stroke => StrokeMatcher.length(scaled(stroke)))
  }

  private def distance(drawn: Shape, model: Shape, weights: Weights): Double = {
    val asOneLine = meanDistance(drawn.line, model.line)
    val byStroke =
      if (drawn.count == model.count)
        drawn.each
          .zip(model.each)
          .zip(model.lengths.map(math.pow(_, weights.byLength)))
          .map { case ((stroke, target), weight) =>
            weight * (meanDistance(stroke, target) +
              weights.direction * turnDistance(stroke, target))
              .min(
                (meanDistance(stroke.reverse, target) +
                  weights.direction * turnDistance(stroke.reverse, target)) *
                  1.2
              )
          }
          .sum / model.lengths.map(math.pow(_, weights.byLength)).sum
      else asOneLine + weights.strokeCount * (drawn.count - model.count).abs
    val size = math.log(drawn.size / model.size).abs
    val place = math.hypot(
      drawn.centre.x - model.centre.x,
      drawn.centre.y - model.centre.y
    ) / CharacterStrokes.boxSize
    weights.byStroke * byStroke + (1 - weights.byStroke) * asOneLine +
      weights.size * size + weights.place * place
  }

  // how far the lines' directions differ along them, from 0 (alike) to 1
  // (opposite): a sharp corner (て) against a steady curve (つ)
  private def turnDistance(a: Vector[Point], b: Vector[Point]): Double = {
    def directions(line: Vector[Point]) = line
      .zip(line.drop(1))
      .map((p, q) => math.atan2(q.y - p.y, q.x - p.x))
    directions(a)
      .zip(directions(b))
      .map { (x, y) =>
        val turn = (x - y).abs % (2 * math.Pi)
        turn.min(2 * math.Pi - turn) / math.Pi
      }
      .sum / (a.size - 1)
  }

  // how far drawn strokes are from being marks on a base: each short, beside
  // the base's upper right rather than its lower left
  private def marksDistance(
      base: List[List[Point]],
      marks: List[List[Point]]
  ): Double = {
    val points = base.flatten
    val (left, right) = (points.map(_.x).min, points.map(_.x).max)
    val (top, bottom) = (points.map(_.y).min, points.map(_.y).max)
    val size = (right - left).max(bottom - top).max(1)
    marks.map { mark =>
      val x = mark.map(_.x).sum / mark.size
      val y = mark.map(_.y).sum / mark.size
      val tooLong = (StrokeMatcher.length(mark) / size - 0.4).max(0)
      val tooLeft = ((left + right) / 2 - x).max(0) / size
      val tooLow = (y - (top + bottom) / 2).max(0) / size
      tooLong + tooLeft + tooLow
    }.sum / marks.size
  }

  private def meanDistance(a: Vector[Point], b: Vector[Point]): Double =
    a.zip(b).map((p, q) => math.hypot(p.x - q.x, p.y - q.y)).sum / a.size
}
