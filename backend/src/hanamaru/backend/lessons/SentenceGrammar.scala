package hanamaru.backend.lessons

import hanamaru.backend.reference.Token
import hanamaru.backend.vocabulary.SentenceCheck
import hanamaru.shared.kana.Romaji

/** What a sentence asks of a learner who has learned the curriculum's points up
  * to one of them: the points it shows, the points after that one it shows, the
  * particles and auxiliaries no point learned covers, and the words that are
  * neither known nor grammar.
  */
final case class SentenceDemands(
    points: List[String],
    later: List[String],
    laterGrammar: List[String],
    uncovered: List[String],
    unknownWords: List[String]
) {

  /** What is wrong with the sentence for this learner, in words the tutor is
    * told; none when nothing is.
    */
  def problems: List[String] =
    (laterGrammar ++ uncovered).map(grammar =>
      s"grammar not learned yet: $grammar"
    ) ++
      unknownWords.map(word => s"word the learner does not know: $word")
}

object SentenceGrammar {

  /** The demands of a sentence, the points learned being the curriculum's up to
    * `upTo` (its index), the words known given by their forms.
    */
  def demands(
      tokens: List[Token],
      upTo: Int,
      knownForms: Set[String],
      points: List[CurriculumPoint] = Curriculum.points
  ): SentenceDemands = {
    val (learned, after) = points.splitAt(upTo + 1)
    val covered = learned.flatMap(_.shownIn(tokens)).toSet
    val named = learned.flatMap(_.namedIn(tokens)).toSet
    // a token of a later point is named as that point
    val ofLater = after.flatMap(_.shownIn(tokens)).toSet
    // a piece of a word the analyser splits, known whole with the token before
    // or after it: 茶 of お茶, ずつ of 少しずつ, か of 誰か
    def inKnownWord(index: Int) = {
      val token = tokens(index)
      tokens
        .lift(index - 1)
        .exists(before =>
          knownForms(before.surface + token.surface) ||
            knownForms(before.surface + token.dictionaryForm)
        ) || tokens
        .lift(index + 1)
        .exists(after => knownForms(token.surface + after.surface))
    }
    val uncovered = tokens.zipWithIndex.collect {
      case (token, index)
          if token.partOfSpeech.headOption.exists(Set("助詞", "助動詞")) &&
            !covered(index) && !ofLater(index) && !inKnownWord(index) =>
        token.surface
    }.distinct
    def known(token: Token) =
      knownForms(token.dictionaryForm) || knownForms(token.surface) ||
        knownForms(Romaji.asHiragana(token.dictionaryForm))
    val unknownWords = tokens.zipWithIndex
      // a word a point learned names is grammar: 書ける, しまう, できる
      .filterNot((_, index) => inKnownWord(index) || named(index))
      .map(_._1)
      .filterNot(token =>
        token.isGrammar || known(token) ||
          SentenceCheck.structureWords(token.dictionaryForm) ||
          token.partOfSpeech.take(2) == List("名詞", "数詞") ||
          // names, shown in kana, are not vocabulary
          token.partOfSpeech.take(3) == List("名詞", "固有名詞", "人名")
      )
      .map(_.dictionaryForm)
      .distinct
    SentenceDemands(
      learned.filter(_.shownIn(tokens).nonEmpty).map(_.id),
      after.filter(_.shownIn(tokens).nonEmpty).map(_.id),
      ofLater.toList.sorted.map(tokens(_).surface).distinct,
      uncovered,
      unknownWords
    )
  }
}
