package hanamaru.shared.writing

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** A point of a character's box, which is 109 units wide and high. */
final case class Point(x: Double, y: Double)

/** One stroke as it is written: its SVG path, drawn from its start to its end,
  * and where its number is shown.
  */
final case class Stroke(path: String, label: Point)

/** A character's strokes, in the order they are written. */
final case class CharacterStrokes(character: String, strokes: List[Stroke])

object CharacterStrokes {

  /** The side of the box the strokes are drawn in. */
  val boxSize: Int = 109

  given JsonValueCodec[CharacterStrokes] = JsonCodecMaker.make
  given JsonValueCodec[List[CharacterStrokes]] = JsonCodecMaker.make
  given Schema[Point] = Schema.derived
  given Schema[Stroke] = Schema.derived
  given Schema[CharacterStrokes] = Schema.derived
}
