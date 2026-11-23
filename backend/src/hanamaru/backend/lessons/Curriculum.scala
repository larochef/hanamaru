package hanamaru.backend.lessons

import hanamaru.backend.reference.Token
import hanamaru.shared.lessons.GrammarPoint

import scala.util.Using

import com.github.plokhotnyuk.jsoniter_scala.core.{
  readFromStream,
  JsonValueCodec
}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

/** A token as a grammar point's signature describes it: by its dictionary form
  * (one, or one of several), its part of speech as the analyser names it, from
  * the most general (`助詞,格助詞`), and its conjugated form (`終止形`, `連用形-融合`…);
  * what is not said is not checked.
  */
final case class TokenPattern(
    form: Option[String] = None,
    forms: List[String] = Nil,
    pos: Option[String] = None,
    conjugation: Option[String] = None
) {
  def matches(token: Token): Boolean =
    form.forall(_ == token.dictionaryForm) &&
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
  * the choices offered for the blank (in kana), and the right one.
  */
final case class PlacementItem(
    sentence: String,
    french: String,
    choices: List[String],
    answer: String
) {

  /** The sentence with its right answer. */
  def filled: String = sentence.replace("＿", answer)

  /** Where the blank is in the filled sentence: from, until. */
  def blank: (Int, Int) = {
    val from = sentence.indexOf('＿')
    (from, from + answer.length)
  }
}

/** A point of the curriculum as written in `curriculum/grammar.json`: what the
  * learner reads (title, pattern, explanation, examples), the points it relies
  * on, the words it introduces besides those of its examples (in their
  * dictionary form), its placement questions, its signature, and whether it has
  * reading texts (not when what is learned up to it cannot make a text: ます
  * without を or へ). The signature is the sequences of tokens that show it in a
  * sentence, any one of them enough.
  */
final case class CurriculumPoint(
    id: String,
    level: Int,
    title: String,
    pattern: String,
    requires: List[String],
    explanation: List[String],
    examples: List[Example],
    words: List[String],
    placement: List[PlacementItem],
    signature: List[List[TokenPattern]],
    text: Boolean = true
) {
  def summary: GrammarPoint = GrammarPoint(id, level, title, pattern)

  /** The tokens of a sentence that show this point: none when it is not in it.
    */
  def shownIn(tokens: List[Token]): Set[Int] =
    signature.toSet.flatMap { sequence =>
      tokens.indices
        .filter(start =>
          sequence.zipWithIndex.forall((pattern, offset) =>
            tokens.lift(start + offset).exists(pattern.matches)
          )
        )
        .flatMap(start => start until start + sequence.length)
    }
}

/** The grammar curriculum: its points in the order they are learned, as written
  * and reviewed in the repository; the tutor fills lessons, it never decides
  * the order.
  */
object Curriculum {
  given JsonValueCodec[List[CurriculumPoint]] = JsonCodecMaker.make

  lazy val points: List[CurriculumPoint] =
    Using.resource(getClass.getResourceAsStream("/curriculum/grammar.json"))(
      readFromStream[List[CurriculumPoint]](_)
    )
}
