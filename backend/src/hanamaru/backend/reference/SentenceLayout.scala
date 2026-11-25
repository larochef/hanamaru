package hanamaru.backend.reference

import hanamaru.shared.kana.Romaji
import hanamaru.shared.text.{PartWord, SentencePart}

/** A sentence laid out for a learner who reads kana: each word in kana (and as
  * written, when written with kanji: [[KanjiSight]] says how it is shown),
  * words spaced as in books for beginners (particles, auxiliaries, suffixes,
  * the する of a verb made of a noun and the ある of ではありません kept with the word
  * before them, prefixes with the word after).
  */
object SentenceLayout {

  // the readings the analyser gives in a formal register, or rarely meant (何時
  // is いつ in kana, and なんじ in kanji), for the everyday ones
  private val everyday =
    Map("私" -> "わたし", "明日" -> "あした", "日本" -> "にほん", "何時" -> "なんじ")

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
        shown(
          token,
          readIn(token, tokens.lift(index + 1)).orElse(reading(token))
        ),
        marked(index),
        word = word(token),
        written = Option.when(token.surface.exists(isKanji))(token.surface),
        spaceBefore = index > 0 && !token.isGrammar &&
          !previous.exists(before =>
            before.partOfSpeech.headOption.exists(Set("接頭辞", "補助記号")) ||
              // する after a noun made a verb: べんきょうします
              (token.dictionaryForm == "する" && before.partOfSpeech.contains(
                "サ変可能"
              )) ||
              // a counter after its number: しちじ
              (before.partOfSpeech.take(2) == List("名詞", "数詞") &&
                token.partOfSpeech.contains("助数詞可能")) ||
              // ある after では or じゃ: ではありません, じゃありません
              (token.dictionaryForm == "ある" &&
                (before.dictionaryForm == "だ" ||
                  (before.dictionaryForm == "は" &&
                    tokens
                      .lift(index - 2)
                      .exists(token => Set("で", "だ")(token.dictionaryForm)))))
          )
      )
    }

  /** A kanji (or 々, which repeats one). */
  def isKanji(character: Char): Boolean =
    (character >= '\u4e00' && character <= '\u9fff') || character == '々'

  // A reading that depends on the word after: 何 is なん before a t, d or n
  // sound (なんですか, なんじ, なんの), なに otherwise (なにを, なにも).
  private def readIn(token: Token, next: Option[Token]): Option[String] =
    Option.when(token.surface == "何") {
      val after =
        next.map(word => Romaji.asHiragana(word.reading)).getOrElse("")
      if (after.headOption.exists("たちつてとだぢづでどなにぬねの".contains(_))) "なん"
      else "なに"
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
