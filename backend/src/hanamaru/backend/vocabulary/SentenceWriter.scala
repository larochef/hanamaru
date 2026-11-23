package hanamaru.backend.vocabulary

import hanamaru.backend.models.LlamaClient
import hanamaru.backend.reference.Analyser
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.vocabulary.WordSentence

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

/** The tutor asked for a short sentence using a word and only the words the
  * learner knows; the code checks it (`SentenceCheck`), and asks again, naming
  * the words it did not accept, a few times at most.
  */
final class SentenceWriter(
    client: LlamaClient,
    analyser: () => Option[Analyser]
) {
  import SentenceWriter.*

  private val attempts = 3
  private val wordsGiven = 150

  def written(
      asked: DictionaryWord,
      known: List[DictionaryWord]
  ): Option[WordSentence] =
    analyser().flatMap { analysis =>
      val vocabulary = (asked :: known.take(wordsGiven))
        .map(word =>
          (word.writings.headOption.toList ++ word.readings.headOption).distinct
            .mkString(" / ")
        )
        .mkString(", ")
      val meaning = asked.meanings
        .find(_.language == "eng")
        .orElse(asked.meanings.headOption)
        .fold("")(_.glosses.take(3).mkString(", "))
      val form = asked.writings.headOption.getOrElse(
        asked.readings.headOption.getOrElse("")
      )
      val reading = asked.readings.headOption.getOrElse("")
      def attempt(left: Int, refused: List[String]): Option[WordSentence] =
        if (left == 0) {
          None
        } else {
          val again = Option
            .when(refused.nonEmpty)(
              s"\nYour last sentence used words the learner does not know: ${refused.mkString(", ")}. Do not use them."
            )
            .getOrElse("")
          client
            .structured[Written](
              system =
                "You write example sentences for a beginner learning Japanese. The learner knows only the words they are given, and basic grammar: particles, です, ます and their forms, the て form. Write one short sentence in polite Japanese that a Japanese person would really say, and its French translation. A very short sentence, even X です or X があります, is better than a long or odd one: never combine words in a way that makes little sense.",
              user =
                s"Use the word $form ($reading, \"$meaning\") in its dictionary form, exactly once.\nUse no other word than these: $vocabulary; and する, いる, ある, なる, これ, それ, この, その, ここ, そこ, numbers.$again",
              schema = schema,
              temperature = 0.7
            )
            .toOption
            .flatMap { written =>
              SentenceCheck.checked(
                analysis.tokens(written.japanese),
                asked,
                known
              ) match {
                case Right(parts) =>
                  Some(
                    WordSentence(
                      asked.id,
                      parts,
                      written.japanese,
                      written.french
                    )
                  )
                case Left(problems) => attempt(left - 1, problems)
              }
            }
        }
      attempt(attempts, Nil)
    }
}

object SentenceWriter {

  /** A sentence as the tutor writes it: in Japanese, and in French. */
  final case class Written(japanese: String, french: String)

  given JsonValueCodec[Written] = JsonCodecMaker.make

  private val schema =
    """{"type":"object","properties":{"japanese":{"type":"string"},"french":{"type":"string"}},"required":["japanese","french"]}"""
}
