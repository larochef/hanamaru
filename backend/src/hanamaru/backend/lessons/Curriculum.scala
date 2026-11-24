package hanamaru.backend.lessons

import hanamaru.backend.reference.Token
import hanamaru.shared.lessons.GrammarPoint

import scala.util.Using

import com.github.plokhotnyuk.jsoniter_scala.core.{
  readFromStream,
  JsonValueCodec
}
import com.github.plokhotnyuk.jsoniter_scala.macros.{
  CodecMakerConfig,
  JsonCodecMaker
}

/** A token as a grammar point's signature describes it: by its dictionary form
  * (one, or one of several, or its end: 食べすぎる ends with すぎる), its part of
  * speech as the analyser names it, from the most general (`助詞,格助詞`), its
  * conjugated form (`終止形`, `連用形-融合`…), and the tokens that must not follow it
  * (the で of a place is not the で of ではありません); what is not said is not checked.
  */
final case class TokenPattern(
    form: Option[String] = None,
    forms: List[String] = Nil,
    formEndsWith: Option[String] = None,
    pos: Option[String] = None,
    conjugation: Option[String] = None,
    notFollowedBy: List[TokenPattern] = Nil
) {

  /** Whether the token at `index` is this one, the tokens after it included. */
  def matchesAt(tokens: List[Token], index: Int): Boolean =
    tokens.lift(index).exists(matches) && (notFollowedBy.isEmpty ||
      !notFollowedBy.zipWithIndex.forall((after, offset) =>
        tokens.lift(index + 1 + offset).exists(after.matches)
      ))

  def matches(token: Token): Boolean =
    form.forall(_ == token.dictionaryForm) &&
      formEndsWith.forall(token.dictionaryForm.endsWith) &&
      (forms.isEmpty || forms.contains(token.dictionaryForm)) &&
      pos.forall(expected =>
        token.partOfSpeech.startsWith(expected.split(',').toList)
      ) &&
      conjugation.forall(expected =>
        token.partOfSpeech.lift(5).exists(_.startsWith(expected))
      )
}

/** An example sentence, written as a Japanese would write it, and in French. */
final case class Example(japanese: String, french: String)

/** A placement question on a point: a sentence with its blank (＿), in French,
  * the choices offered for the blank (in kana), the right one, and that one as
  * it is written when kana alone mislead the analyser (読め for よめ).
  */
final case class PlacementItem(
    sentence: String,
    french: String,
    choices: List[String],
    answer: String,
    written: Option[String] = None
) {
  private def filling = written.getOrElse(answer)

  /** The sentence with its right answer, as written. */
  def filled: String = sentence.replace("＿", filling)

  /** Where the blank is in the filled sentence: from, until. */
  def blank: (Int, Int) = {
    val from = sentence.indexOf('＿')
    (from, from + filling.length)
  }
}

/** A point of the curriculum as written in `curriculum/grammar.json`: what the
  * learner reads (title, pattern, explanation, examples), the points it relies
  * on, its unit's theme and the words the unit teaches (as they are written,
  * `form|reading` when the form alone is ambiguous: `上|うえ`, `form#entry` when
  * not even its reading is: `いる#1577980`), its placement questions, its
  * signature, and whether it has reading texts (not when what is learned up to
  * it cannot make a text: ます without を or へ). The signature is the sequences of
  * tokens that show it in a sentence, any one of them enough.
  */
final case class CurriculumPoint(
    id: String,
    level: Int,
    title: String,
    pattern: String,
    requires: List[String],
    explanation: List[String],
    examples: List[Example],
    theme: String,
    words: List[String],
    placement: List[PlacementItem],
    signature: List[List[TokenPattern]],
    text: Boolean = true
) {
  def summary: GrammarPoint = GrammarPoint(id, level, title, pattern)

  /** The unit's words as they are written, without the readings that tell them
    * apart.
    */
  def wordForms: List[String] =
    words.map(_.takeWhile(character => character != '|' && character != '#'))

  /** The tokens of a sentence that show this point: none when it is not in it.
    */
  def shownIn(tokens: List[Token]): Set[Int] =
    matched(tokens).map(_._1)

  /** The tokens of a sentence this point names by their form (できる, しまう, 読める),
    * not by their kind (any adjective): words the point teaches.
    */
  def namedIn(tokens: List[Token]): Set[Int] =
    matched(tokens).collect {
      case (index, pattern)
          if pattern.form.nonEmpty || pattern.forms.nonEmpty ||
            pattern.formEndsWith.nonEmpty =>
        index
    }

  // each token a sequence of the signature finds, with the pattern it matched
  private def matched(tokens: List[Token]): Set[(Int, TokenPattern)] =
    signature.toSet.flatMap { sequence =>
      tokens.indices
        .filter(start =>
          sequence.zipWithIndex.forall((pattern, offset) =>
            pattern.matchesAt(tokens, start + offset)
          )
        )
        .flatMap(start =>
          sequence.zipWithIndex
            .map((pattern, offset) => (start + offset, pattern))
        )
    }
}

/** The grammar curriculum: its points in the order they are learned, as written
  * and reviewed in the repository; the tutor fills lessons, it never decides
  * the order.
  */
object Curriculum {
  // a token pattern holds the patterns that must not follow it: recursive, and
  // read only from the repository's own file
  given JsonValueCodec[List[CurriculumPoint]] =
    JsonCodecMaker.make(CodecMakerConfig.withAllowRecursiveTypes(true))

  lazy val points: List[CurriculumPoint] =
    Using.resource(getClass.getResourceAsStream("/curriculum/grammar.json"))(
      readFromStream[List[CurriculumPoint]](_)
    )
}
