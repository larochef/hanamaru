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

/** The server as the page sees it. */
final case class ServerStatus(referenceData: ReferenceDataState)

object ServerStatus {
  given JsonValueCodec[ServerStatus] = JsonCodecMaker.make
  given Schema[ReferenceDataState] = Schema.derived
  given Schema[ServerStatus] = Schema.derived
}
