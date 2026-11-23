package hanamaru.shared

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** The server as the page sees it: the version of SQLite its learner data is
  * kept with, read from the open database.
  */
final case class ServerStatus(sqliteVersion: String)

object ServerStatus {
  given JsonValueCodec[ServerStatus] = JsonCodecMaker.make
  given Schema[ServerStatus] = Schema.derived
}
