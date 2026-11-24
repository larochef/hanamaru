package hanamaru.shared.writing

import hanamaru.shared.writing.WritingAttempt.given

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** A kana written in the pad, as drawn: the kana it was in the answer given (in
  * its usual size: a small kana is made with a button), and those the
  * recogniser took it for, closest first. The material the recogniser is tuned
  * on.
  */
final case class PadCharacter(
    character: String,
    strokes: List[List[DrawnPoint]],
    recognised: List[String]
)

object PadCharacter {
  given JsonValueCodec[PadCharacter] = JsonCodecMaker.make
  given JsonValueCodec[List[PadCharacter]] = JsonCodecMaker.make
  given Schema[PadCharacter] = Schema.derived
}
