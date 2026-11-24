package hanamaru.shared

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** Where the reference data stands: downloaded and checked on first start,
  * ready, or failed and why.
  */
enum ReferenceDataState {
  case Preparing
  case Ready
  case Failed(reason: String)
}

/** The server as the page sees it: its reference data (the strokes, the
  * dictionary, the analyser's dictionary), and its tutor's model.
  */
final case class ServerStatus(
    strokes: ReferenceDataState,
    dictionary: ReferenceDataState,
    analyser: ReferenceDataState,
    kanji: ReferenceDataState,
    model: ModelState
)

object ServerStatus {
  given JsonValueCodec[ServerStatus] = JsonCodecMaker.make
  given Schema[ReferenceDataState] = Schema.derived
  given Schema[ServerStatus] = Schema.derived
}
