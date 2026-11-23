package hanamaru.backend.reference

import hanamaru.shared.text.{PartWord, SentencePart}

/** A sentence laid out for a learner who reads kana only: each word in kana,
  * words spaced as in books for beginners (particles, auxiliaries, suffixes,
  * the する of a verb made of a noun and the ある of じゃありません kept with the word
  * before them, prefixes with the word after).
  */
object SentenceLayout {

  // the readings the analyser gives in a formal register, for the everyday ones
  private val everyday = Map("私" -> "わたし", "明日" -> "あした", "日本" -> "にほん")

  /** A word's everyday reading, where the analyser gives a formal one. */
  def everydayReading(token: Token): Option[String] =
    Option
      .when(token.surface == token.dictionaryForm)(everyday.get(token.surface))
      .flatten

  /** The tokens laid out; `marked` says which tokens (by their index) the
    * sentence is about, `reading` may give a word's reading where the
    * analyser's is not the one wanted, and `word` the dictionary's word a token
    * is.
    */
  def laidOut(
      tokens: List[Token],
      marked: Int => Boolean,
      reading: Token => Option[String] = _ => None,
      word: Token => Option[PartWord] = _ => None
  ): List[SentencePart] =
    tokens.zipWithIndex.map { (token, index) =>
      val previous = tokens.lift(index - 1)
      SentencePart(
        shown(token, reading(token)),
        marked(index),
        word = word(token),
        spaceBefore = index > 0 && !token.isGrammar &&
          !previous.exists(before =>
            before.partOfSpeech.headOption.exists(Set("接頭辞", "補助記号")) ||
              // する after a noun made a verb: べんきょうします
              (token.dictionaryForm == "する" && before.partOfSpeech.contains(
                "サ変可能"
              )) ||
              // ある after じゃ: じゃありません
              (token.dictionaryForm == "ある" && before.dictionaryForm == "だ")
          )
      )
    }

  // As written when written in kana; otherwise the reading given, the
  // everyday one, or the analyser's.
  private def shown(token: Token, chosen: Option[String]): String = {
    val inKana = token.surface.forall(character =>
      (character >= 'ぁ' && character <= 'ゖ') ||
        (character >= 'ァ' && character <= 'ヶ') || character == 'ー'
    )
    if (inKana || token.partOfSpeech.headOption.contains("補助記号")) {
      token.surface
    } else {
      chosen
        .orElse(everyday.get(token.surface))
        .getOrElse(token.reading)
    }
  }
}
