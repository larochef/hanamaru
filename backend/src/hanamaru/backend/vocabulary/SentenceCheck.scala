package hanamaru.backend.vocabulary

import hanamaru.backend.reference.Token
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kana.Romaji
import hanamaru.shared.vocabulary.SentencePart

/** A sentence checked against what the learner knows: every word in it must be
  * the word asked, a word the learner has met, or grammar (particles,
  * auxiliaries, punctuation, affixes, numbers and a few words that hold
  * sentences together). Checked, it is laid out for a learner who reads kana
  * only: each word in kana, words spaced, the word asked blanked.
  */
object SentenceCheck {

  /** Words that hold sentences together, allowed as grammar is. */
  val structureWords: Set[String] = Set(
    "する",
    "いる",
    "ある",
    "なる",
    "こと",
    "もの",
    "とき",
    "ところ",
    "これ",
    "それ",
    "あれ",
    "どれ",
    "この",
    "その",
    "あの",
    "どの",
    "ここ",
    "そこ",
    "あそこ",
    "どこ",
    "こう",
    "そう",
    "ああ",
    "どう"
  )

  /** The sentence laid out, or the words in it the learner does not know (and
    * whether the word asked is missing).
    */
  def checked(
      tokens: List[Token],
      asked: DictionaryWord,
      known: List[DictionaryWord]
  ): Either[List[String], List[SentencePart]] = {
    def formsOf(word: DictionaryWord) =
      (word.writings ++ word.readings ++ word.readings.map(
        Romaji.asHiragana
      )).toSet
    def matches(token: Token, word: DictionaryWord) = {
      val forms = formsOf(word)
      forms(token.dictionaryForm) || forms(token.surface) ||
      forms(Romaji.asHiragana(token.dictionaryForm))
    }
    def isNumber(token: Token) = token.partOfSpeech.take(2) == List("名詞", "数詞")
    val unknown = tokens
      .filterNot(token =>
        token.isGrammar || isNumber(token) || structureWords(
          token.dictionaryForm
        ) ||
          matches(token, asked) || known.exists(matches(token, _))
      )
      .map(_.dictionaryForm)
      .distinct
    val missing = Option.unless(tokens.exists(matches(_, asked)))(
      s"${formsOf(asked).head} (missing)"
    )
    if (unknown.nonEmpty || missing.nonEmpty) {
      Left(unknown ++ missing)
    } else {
      Right(tokens.zipWithIndex.map { (token, index) =>
        val blank = matches(token, asked)
        val word = (asked :: known).find(matches(token, _))
        SentencePart(
          shown(token, word),
          blank,
          spaceBefore = index > 0 && !token.isGrammar
        )
      })
    }
  }

  // A word in kana: as it is when written in katakana; a word used as it
  // stands in the dictionary, with its usual reading there (わたし, not
  // わたくし); otherwise as the analyser reads it.
  private def shown(token: Token, word: Option[DictionaryWord]): String = {
    val katakana = token.surface.nonEmpty && token.surface.forall(character =>
      (character >= 'ァ' && character <= 'ヶ') || character == 'ー'
    )
    if (token.partOfSpeech.headOption.contains("補助記号") || katakana) {
      token.surface
    } else {
      word
        .filter(_ => token.surface == token.dictionaryForm)
        .flatMap(_.readings.headOption)
        .getOrElse(token.reading)
    }
  }
}
