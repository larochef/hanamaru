package hanamaru.backend.vocabulary

import hanamaru.backend.models.LlamaClient
import hanamaru.shared.dictionary.DictionaryWord

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

/** The tutor asked whether a meaning typed for a word says the same as one of
  * its glosses. It answers with the number of that gloss, which the code checks
  * is one of those it was given: it can recognise a right answer, and never
  * condemn one.
  */
final class MeaningJudge(client: LlamaClient) {
  import MeaningJudge.*

  /** The gloss the tutor recognises in `answer`, if it does. */
  def recognised(word: DictionaryWord, answer: String): Option[String] = {
    val glosses = word.meanings.flatMap(_.glosses).distinct
    val numbered = glosses.zipWithIndex
      .map((gloss, index) => s"${index + 1}. $gloss")
      .mkString("\n")
    val forms = (word.writings ++ word.readings).distinct.mkString(", ")
    client
      .structured[Judgement](
        system =
          "You check the answers of a learner of Japanese. You are given a Japanese word, its meanings numbered, and the meaning the learner typed, in French or English. Say whether the learner's answer means the same as one of the numbered meanings, allowing synonyms, other wordings and small spelling mistakes, but not a different or vaguer meaning.",
        user = s"Word: $forms\nMeanings:\n$numbered\nLearner's answer: $answer",
        schema = schema
      )
      .toOption
      .flatMap(judgement =>
        glosses.lift(judgement.meaning - 1).filter(_ => judgement.same)
      )
  }
}

object MeaningJudge {

  /** Whether the answer means the same as a meaning, and which, by number. */
  final case class Judgement(same: Boolean, meaning: Int)

  given JsonValueCodec[Judgement] = JsonCodecMaker.make

  private val schema =
    """{"type":"object","properties":{"same":{"type":"boolean"},"meaning":{"type":"integer"}},"required":["same","meaning"]}"""
}
