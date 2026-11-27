package hanamaru.backend.lessons

import hanamaru.backend.models.*
import hanamaru.backend.reference.{Analyser, Token}
import hanamaru.shared.kana.Romaji

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import com.typesafe.scalalogging.Logger

/** A short reading text for a lesson, written by the tutor on the lesson's
  * point with the points before it and the words the learner knows, checked by
  * the code (`SentenceGrammar`) sentence by sentence, and asked again with the
  * problems named, a few times at most; what is refused is kept.
  */
final class TextWriter(client: LlamaClient, refusals: Refusals) {
  import TextWriter.*

  private val logger = Logger("hanamaru.backend.lessons")

  private val attempts = 3
  private val wordsGiven = 200

  /** The text's sentences, each with its tokens; none when no text the code
    * accepts could be written.
    */
  def written(
      upTo: Int,
      analyser: Analyser,
      known: List[String],
      knownForms: Set[String],
      points: List[CurriculumPoint] = Curriculum.points
  ): Option[List[(Written, List[Token])]] = {
    val point = points(upTo)
    val grammar = grammarUpTo(upTo, points)
    val notYet = notYetAt(upTo, points)
    def attempt(
        left: Int,
        refused: List[String]
    ): Option[List[(Written, List[Token])]] =
      if (left == 0) {
        None
      } else {
        val again = Option
          .when(refused.nonEmpty)(
            s"\nYour last text had these problems; write a new one without them:\n${refused.map("- " + _).mkString("\n")}"
          )
          .getOrElse("")
        client
          .structured[Text](
            system =
              "You write short reading texts for a beginner learning Japanese, in polite Japanese, with their French translation. The learner knows only the grammar and the words they are given: use nothing else, not even common words or particles they are not given. Short, simple, natural, complete sentences (never a list of words) that follow one another, about one person or one scene.",
            user =
              s"""Write a text of 3 to 5 sentences that uses, in at least two sentences, the grammar point "${point.pattern}: ${point.title}".
              |Grammar the learner knows (use no other particle, ending or conjugation):
              |$grammar
              |Particles and forms the learner does not know yet, never to be used: ${notYet
                  .mkString(", ")}.
              |The lesson's own words, to use as many as fit naturally: ${point.wordForms
                  .mkString(", ")}.
              |Words the learner knows (and names of people, numbers): ${known
                  .take(wordsGiven)
                  .mkString(", ")}.$again""".stripMargin,
            schema = schema,
            maxTokens = 1024,
            temperature = 0.7
          )
          .toOption
          .flatMap { text =>
            val analysed = text.sentences
              .map(sentence => sentence -> analyser.tokens(sentence.japanese))
            val demands = analysed.map((_, tokens) =>
              SentenceGrammar.demands(tokens, upTo, knownForms, points)
            )
            val showing = demands.count(_.points.contains(point.id))
            val problems = demands.flatMap(_.problems).distinct ++
              Option.when(showing < 2)(
                s"the point ${point.pattern} is in $showing sentence(s), not two"
              )
            if (problems.isEmpty && analysed.nonEmpty) {
              Some(analysed)
            } else {
              logger.debug(
                s"Text for ${point.id} refused: ${problems.mkString("; ")}"
              )
              refusals.kept(
                WrittenFor.LessonText,
                point.id,
                text.sentences.map(each => each.japanese -> each.french),
                problems
              )
              attempt(left - 1, problems)
            }
          }
      }
    attempt(attempts, Nil)
  }

  /** Free sentences, one per target ([[Target]]): each must show its point, use
    * one of its words, and ask nothing the learner has not learned
    * (`SentenceGrammar`); those refused asked again with their problems named,
    * a few times at most, the others kept. Each kept with its target and
    * tokens, in the targets' order. The sentences written before (`before`) are
    * shown to the tutor, so that it does not write them again.
    */
  def onTargets(
      targets: List[Target],
      upTo: Int,
      analyser: Analyser,
      known: List[String],
      knownForms: Set[String],
      before: List[String],
      points: List[CurriculumPoint] = Curriculum.points
  ): List[(Target, Written, List[Token])] = {
    val grammar = grammarUpTo(upTo, points)
    val notYet = notYetAt(upTo, points)
    def attempt(
        left: Int,
        asked: List[(Target, List[String])]
    ): List[(Target, Written, List[Token])] =
      if (left == 0 || asked.isEmpty) Nil
      else {
        val lines = asked.zipWithIndex
          .map { case ((target, refused), index) =>
            s"${index + 1}. grammar point \"${target.point.pattern}: ${target.point.title}\"; word(s): ${target.words
                .map(_.head)
                .mkString(", ")}" + Option
              .when(refused.nonEmpty)(
                s" (your last sentence for it had these problems: ${refused.mkString("; ")})"
              )
              .getOrElse("")
          }
          .mkString("\n")
        val written = client
          .structured[Text](
            system =
              "You write single, independent sentences for a beginner learning Japanese to translate, in polite Japanese, with their French translation. The learner knows only the grammar and the words they are given: use nothing else, not even common words or particles they are not given. Each sentence short, simple, natural and complete, about its own situation; vary the people, places and situations from one sentence to the next.",
            // what is the same from one request to the next comes first: the
            // tutor reads it once, kept from the request before
            user = s"""Grammar the learner knows (use no other particle, ending or conjugation):
              |$grammar
              |Particles and forms the learner does not know yet, never to be used: ${notYet
                       .mkString(", ")}.
              |Other words the learner knows, to use as needed (and names of people, numbers): ${known
                       .take(wordsGiven)
                       .mkString(", ")}.""".stripMargin + Option
              .when(before.nonEmpty)(
                s"\nSentences already written, not to be written again (take other people, places and situations): ${before
                    .mkString(" ")}"
              )
              .getOrElse("") +
              s"""
              |Write ${asked.size} sentence(s), in this order, each using its grammar point and its word(s):
              |$lines""".stripMargin,
            schema = exactly(asked.size),
            maxTokens = 2048,
            temperature = 0.8
          )
          .toOption
          .fold(List.empty[Written])(_.sentences)
        val judged = asked.zipWithIndex.map { case ((target, _), index) =>
          val sentence = written.lift(index)
          val tokens = sentence.fold(List.empty[Token])(each =>
            analyser.tokens(each.japanese)
          )
          val problems = sentence.fold(List("no sentence written"))(_ =>
            TextWriter.problems(target, tokens, upTo, knownForms, points)
          )
          (target, sentence, tokens, problems)
        }
        val kept = judged.collect {
          case (target, Some(sentence), tokens, Nil) =>
            (target, sentence, tokens)
        }
        val again = judged.collect {
          case (target, sentence, _, problems) if problems.nonEmpty =>
            logger.debug(
              s"Sentence on ${target.point.id} refused: ${problems.mkString("; ")}"
            )
            refusals.kept(
              WrittenFor.FreeSentence,
              s"${target.point.id}: ${target.words.map(_.head).mkString(", ")}",
              sentence.toList.map(each => each.japanese -> each.french),
              problems
            )
            target -> problems
        }
        kept ++ attempt(left - 1, again)
      }
    val done = attempt(attempts, targets.map(_ -> Nil))
    targets.flatMap(target => done.find(_._1 eq target))
  }

  // the points learned up to one, for the tutor
  private def grammarUpTo(upTo: Int, points: List[CurriculumPoint]) =
    points
      .take(upTo + 1)
      .map(learned => s"- ${learned.pattern}: ${learned.title}")
      .mkString("\n")

  // the particles a beginner's sentence reaches for that are not learned yet:
  // those of the points to come, and the usual ones no point teaches yet
  private def notYetAt(upTo: Int, points: List[CurriculumPoint]) = {
    val learnedForms =
      points.take(upTo + 1).flatMap(_.signature.flatten).flatMap(_.form).toSet
    (points
      .drop(upTo + 1)
      .flatMap(_.signature.flatten)
      .filter(_.pos.exists(_.startsWith("助詞")))
      .flatMap(_.form) ++ usualParticles).distinct.filterNot(learnedForms)
  }
}

object TextWriter {

  /** What a free sentence is to be on: a point, and words, each given by its
    * forms, the first as the tutor is told it.
    */
  final case class Target(point: CurriculumPoint, words: List[List[String]])

  /** What is wrong with a free sentence for its target: its point not shown,
    * none of its words used, or something not learned yet.
    */
  def problems(
      target: Target,
      tokens: List[Token],
      upTo: Int,
      knownForms: Set[String],
      points: List[CurriculumPoint] = Curriculum.points
  ): List[String] = {
    val forms = target.words.flatten.toSet
    val used = tokens.exists(token =>
      forms(token.dictionaryForm) || forms(token.surface) ||
        forms(Romaji.asHiragana(token.dictionaryForm))
    )
    Option
      .when(target.point.shownIn(tokens).isEmpty)(
        s"the grammar point ${target.point.pattern} is not used"
      )
      .toList ++
      Option
        .when(forms.nonEmpty && !used)(
          s"none of the words ${target.words.map(_.head).mkString(", ")} is used"
        )
        .toList ++
      SentenceGrammar.demands(tokens, upTo, knownForms, points).problems
  }

  private def exactly(count: Int) =
    s"""{"type":"object","properties":{"sentences":{"type":"array","minItems":$count,"maxItems":$count,"items":{"type":"object","properties":{"japanese":{"type":"string"},"french":{"type":"string"}},"required":["japanese","french"]}}},"required":["sentences"]}"""

  /** A sentence as the tutor writes it: in Japanese, and in French. */
  final case class Written(japanese: String, french: String)

  final case class Text(sentences: List[Written])

  given JsonValueCodec[Text] = JsonCodecMaker.make

  private val usualParticles =
    List(
      "が",
      "と",
      "や",
      "て",
      "から",
      "まで",
      "ね",
      "よ",
      "ため",
      "ように",
      "のに",
      "けど"
    )

  private val schema =
    """{"type":"object","properties":{"sentences":{"type":"array","minItems":3,"maxItems":5,"items":{"type":"object","properties":{"japanese":{"type":"string"},"french":{"type":"string"}},"required":["japanese","french"]}}},"required":["sentences"]}"""
}
