package hanamaru.backend.lessons

import hanamaru.backend.models.LlamaClient
import hanamaru.backend.reference.{Analyser, SentenceLayout}
import hanamaru.shared.kana.Romaji
import hanamaru.shared.lessons.*

import java.text.Normalizer

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

/** An exercise's answer judged: by the code when it is the one expected; else
  * by the tutor, who says whether it is right all the same, corrects it and
  * explains, in French.
  */
final class ExerciseJudge(
    client: LlamaClient,
    analyser: () => Option[Analyser]
) {
  import ExerciseJudge.*

  def judged(answer: ExerciseAnswer): ExerciseVerdict = {
    val exercise = answer.exercise
    exercise.kind match {
      case ExerciseKind.Particle | ExerciseKind.Form
          if answer.answer == exercise.expected =>
        ExerciseVerdict(right = true, byTutor = false, None, None)
      case ExerciseKind.Particle | ExerciseKind.Form =>
        val filled = exercise.parts
          .map(part => if (part.marked) answer.answer else part.text)
          .mkString
        val what =
          if (exercise.kind == ExerciseKind.Form) "form" else "particle"
        client
          .structured[ParticleJudgement](
            system,
            s"""Sentence: $filled
               |Meaning: ${exercise.french}
               |The blank was filled with 「${answer.answer}」; the expected $what is 「${exercise.expected}」.
               |Is 「${answer.answer}」 also correct here, with this meaning? Explain why or why not.""".stripMargin,
            particleSchema
          )
          .fold(
            _ => ExerciseVerdict(right = false, byTutor = false, None, None),
            judgement =>
              ExerciseVerdict(
                judgement.alsoCorrect,
                byTutor = true,
                None,
                Some(judgement.explanation)
              )
          )
      case ExerciseKind.Meaning if answer.answer.isBlank =>
        ExerciseVerdict(right = false, byTutor = false, None, None)
      case ExerciseKind.Meaning
          if inFrench(answer.answer) == inFrench(exercise.expected) =>
        ExerciseVerdict(right = true, byTutor = false, None, None)
      case ExerciseKind.Meaning =>
        client
          .structured[MeaningJudgement](
            system,
            s"""Japanese sentence: ${exercise.japanese}
               |Its meaning: ${exercise.french}
               |The learner translated it into French: ${answer.answer}
               |Does the learner's French say what the Japanese says? Another wording is fine, and so are small French mistakes; a different meaning (tense, negation, who does what) is not. Explain in French.""".stripMargin,
            meaningSchema
          )
          .fold(
            // the tutor away: the learner compares with the meaning shown
            _ =>
              ExerciseVerdict(
                right = false,
                byTutor = false,
                None,
                None,
                toSelfGrade = true
              ),
            judgement =>
              ExerciseVerdict(
                judgement.correct,
                byTutor = true,
                None,
                Some(judgement.explanation)
              )
          )
      // a kanji written is kept as the kanji's review, not judged here
      case ExerciseKind.KanjiWriting =>
        ExerciseVerdict(right = true, byTutor = false, None, None)
      case ExerciseKind.Translation =>
        val typed = normalised(answer.answer)
        val expected = normalised(exercise.expected)
        if (written(answer.answer) == written(exercise.expected)) {
          ExerciseVerdict(right = true, byTutor = false, None, None)
        } else if (typed == expected) {
          // right, but in the other script: said, the words in katakana named
          val inKatakana = exercise.parts.map(_.text).filter(Romaji.isKatakana)
          ExerciseVerdict(
            right = true,
            byTutor = false,
            None,
            Some(
              if (inKatakana.isEmpty)
                "Juste, mais tout s'écrit ici en hiragana."
              else
                s"Juste, mais ${inKatakana.mkString(", ")} s'écri${
                    if (inKatakana.size == 1) "t" else "vent"
                  } en katakana, et le reste en hiragana."
            )
          )
        } else if (
          typed.nonEmpty && uncontracted(typed) == uncontracted(expected)
        ) {
          ExerciseVerdict(
            right = true,
            byTutor = false,
            None,
            Some(
              "Juste : じゃありません est la forme parlée de ではありません, qu'on écrit plutôt."
            )
          )
        } else if (typed.nonEmpty && asSpoken(typed) == asSpoken(expected)) {
          ExerciseVerdict(
            right = true,
            byTutor = false,
            None,
            Some(
              "Juste, à l'orthographe des particules près : は, へ et を se prononcent « wa », « é » et « o » mais s'écrivent ainsi (en romaji : ha, he, wo)."
            )
          )
        } else if (typed.isEmpty) {
          ExerciseVerdict(right = false, byTutor = false, None, None)
        } else {
          client
            .structured[TranslationJudgement](
              system,
              s"""French sentence: ${exercise.french}
                 |Expected Japanese: ${exercise.japanese}
                 |The learner wrote: ${answer.answer}
                 |Is the learner's sentence a correct Japanese translation? Another wording than the expected one is fine, and so are a missing subject or missing punctuation. If it is not correct, give the learner's sentence corrected as little as possible, and explain the mistakes.""".stripMargin,
              translationSchema,
              maxTokens = 768
            )
            .fold(
              _ => ExerciseVerdict(right = false, byTutor = false, None, None),
              judgement =>
                ExerciseVerdict(
                  judgement.correct,
                  byTutor = true,
                  Option
                    .unless(judgement.correct || judgement.corrected.isBlank)(
                      judgement.corrected
                    )
                    .flatMap(corrected =>
                      analyser().map(analysis =>
                        SentenceLayout
                          .laidOut(analysis.tokens(corrected), _ => false)
                      )
                    ),
                  Some(judgement.explanation)
                )
            )
        }
    }
  }
}

object ExerciseJudge {

  final case class ParticleJudgement(alsoCorrect: Boolean, explanation: String)

  final case class TranslationJudgement(
      correct: Boolean,
      corrected: String,
      explanation: String
  )

  final case class MeaningJudgement(correct: Boolean, explanation: String)

  given JsonValueCodec[ParticleJudgement] = JsonCodecMaker.make
  given JsonValueCodec[MeaningJudgement] = JsonCodecMaker.make
  given JsonValueCodec[TranslationJudgement] = JsonCodecMaker.make

  private val system =
    "You are a Japanese teacher correcting a beginner's exercise. Be fair: accept what a Japanese teacher would accept. Explanations are in French, short (one to three sentences), for a beginner who reads only kana: write any Japanese in them in kana, never in kanji."

  private val particleSchema =
    """{"type":"object","properties":{"alsoCorrect":{"type":"boolean"},"explanation":{"type":"string"}},"required":["alsoCorrect","explanation"]}"""

  private val meaningSchema =
    """{"type":"object","properties":{"correct":{"type":"boolean"},"explanation":{"type":"string"}},"required":["correct","explanation"]}"""

  private val translationSchema =
    """{"type":"object","properties":{"correct":{"type":"boolean"},"corrected":{"type":"string"},"explanation":{"type":"string"}},"required":["correct","corrected","explanation"]}"""

  /** A sentence in kana, as compared: in hiragana, without spaces nor
    * punctuation.
    */
  def normalised(text: String): String = Romaji.asHiragana(written(text))

  // a sentence as written, without spaces nor punctuation
  private def written(text: String): String =
    text.filterNot(character =>
      character.isWhitespace || "。、？?！!.,　".contains(character)
    )

  /** A French sentence as compared: in small letters, without accents, spaces
    * nor punctuation.
    */
  def inFrench(text: String): String =
    Normalizer
      .normalize(text.toLowerCase, Normalizer.Form.NFD)
      .filter(_.isLetterOrDigit)

  // じゃ, spoken, as the では it stands for
  private def uncontracted(text: String): String = text.replace("じゃあり", "ではあり")

  // the particles written as they sound: は as わ, へ as え, を as お
  private def asSpoken(text: String): String =
    text.map {
      case 'は'   => 'わ'
      case 'へ'   => 'え'
      case 'を'   => 'お'
      case other => other
    }
}
