package hanamaru.shared.routes

import hanamaru.shared.dictionary.DictionaryWord

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object DictionaryRoutes {

  /** The core vocabulary, the most frequent words first, `count` of them from
    * `from`.
    */
  val coreWords: PublicEndpoint[(Int, Int), String, List[DictionaryWord], Any] =
    Routes.baseRoute.get
      .in("dictionary" / "core")
      .in(query[Int]("from"))
      .in(query[Int]("count"))
      .errorOut(Routes.refused)
      .out(jsonBody[List[DictionaryWord]])
}
