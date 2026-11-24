package hanamaru.backend.lessons

import hanamaru.backend.models.LlamaClient
import hanamaru.backend.reference.{Analyser, Token}

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import com.typesafe.scalalogging.Logger

/** A short reading text for a lesson, written by the tutor on the lesson's
  * point with the points before it and the words the learner knows, checked by
  * the code (`SentenceGrammar`) sentence by sentence, and asked again with the
  * problems named, a few times at most.
  */
final class TextWriter(client: LlamaClient) {
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
    val grammar = points
      .take(upTo + 1)
      .map(learned => s"- ${learned.pattern}: ${learned.title}")
      .mkString("\n")
    // the particles a beginner's sentence reaches for that are not learned yet:
    // those of the points to come, and the usual ones no point teaches yet
    val learnedForms =
      points.take(upTo + 1).flatMap(_.signature.flatten).flatMap(_.form).toSet
    val notYet = (points
      .drop(upTo + 1)
      .flatMap(_.signature.flatten)
      .filter(_.pos.exists(_.startsWith("助詞")))
      .flatMap(_.form) ++ usualParticles).distinct.filterNot(learnedForms)
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
              attempt(left - 1, problems)
            }
          }
      }
    attempt(attempts, Nil)
  }
}

object TextWriter {

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
