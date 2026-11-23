package hanamaru.backend.reference

import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.routes.DictionaryRoutes

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class DictionaryApi(reference: ReferenceData) {
  private val largestPage = 200

  def coreWords(from: Int, count: Int): Either[String, List[DictionaryWord]] =
    reference.dictionary
      .toRight("The dictionary is not ready")
      .map(_.coreWords(from.max(0), count.max(0).min(largestPage)))

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    DictionaryRoutes.coreWords.handle(coreWords)
  )
}
