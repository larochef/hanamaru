package hanamaru.backend.lessons

import hanamaru.backend.kanji.{KanjiHand, KanjiReadings}
import hanamaru.backend.models.LlamaClient
import hanamaru.backend.reference.{Analyser, SentenceLayout}
import hanamaru.shared.kana.Romaji
import hanamaru.shared.lessons.*
import hanamaru.shared.text.SentencePart

import java.text.Normalizer

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

/** An exercise's answer judged: by the code when it is the one expected; else
  * by the tutor, who says whether it is right all the same, corrects it and
  * explains, in French.
  */
final class ExerciseJudge(
    client: LlamaClient,
    analyser: () => Option[Analyser],
    kanji: KanjiHand
) {
  import ExerciseJudge.*

  def judged(answer: ExerciseAnswer): ExerciseVerdict = {
    val exercise = answer.exercise
    exercise.kind match {
      case ExerciseKind.Particle => particles(exercise, answer.answer)
      case ExerciseKind.Form if answer.answer == exercise.expected =>
        ExerciseVerdict(right = true, byTutor = false, None, None)
      case ExerciseKind.Form =>
        val filled = exercise.parts
          .map(part => if (part.marked) answer.answer else part.text)
          .mkString
        val what = "form"
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
        // the kanji written read back as the kana they stand for
        val inKana = asKana(answer.answer, exercise.parts)
        val typed = normalised(inKana)
        val expected = normalised(exercise.expected)
        if (written(inKana) == written(exercise.expected)) {
          ExerciseVerdict(
            right = true,
            byTutor = false,
            None,
            reminder(answer.answer, exercise.parts)
          )
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
        } else if (pointRight(typed, nounsOf(exercise))) {
          val point = exercise.parts.filter(_.marked).map(_.text).distinct
          ExerciseVerdict(
            right = true,
            byTutor = false,
            None,
            Some(
              s"Juste pour la grammaire travaillée (${point.mkString("、")}). Ailleurs, une petite faute : compare avec la phrase ci-dessous."
            )
          )
        } else {
          client
            .structured[TranslationJudgement](
              system,
              s"""French sentence: ${exercise.french}
                 |Expected Japanese: ${exercise.japanese}
                 |The learner wrote: $inKana
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

  // each part of an exercise's sentence, and whether it is a noun (にほんご,
  // たなか, さん), as the analyser reads the sentence again, part for token;
  // without it, a word as the dictionary has it, in its reading, not
  // conjugated
  private def nounsOf(
      exercise: LessonExercise
  ): List[(SentencePart, Boolean)] = {
    val tokens = analyser()
      .map(_.tokens(exercise.japanese))
      .filter(_.size == exercise.parts.size)
    exercise.parts.zipWithIndex.map((part, index) =>
      part -> tokens.fold(
        part.word.exists(word =>
          Romaji.asHiragana(word.reading) == normalised(part.text)
        )
      )(each => isNoun(each(index).partOfSpeech))
    )
  }

  /** Particles typed, one per blank: right when each is the one expected; else,
    * each said as written (は said わ, wrong, its spelling explained), or judged
    * by the tutor, the sentence filled with them.
    */
  private def particles(
      exercise: LessonExercise,
      answer: String
  ): ExerciseVerdict = {
    val expected = exercise.expected.split(" ", -1).toList
    val typed = answer
      .split(" ", -1)
      .toList
      .map(each => Romaji.asHiragana(Romaji.toKana(each.trim, finished = true)))
      .padTo(expected.size, "")
    val unjudged = ExerciseVerdict(right = false, byTutor = false, None, None)
    if (typed == expected) {
      ExerciseVerdict(right = true, byTutor = false, None, None)
    } else if (typed.exists(_.isEmpty)) {
      unjudged
    } else if (typed.map(asSpoken) == expected.map(asSpoken)) {
      unjudged.copy(explanation =
        Some(
          "Bien entendu, mais mal écrit : は, へ et を se prononcent « wa », « é » et « o » et s'écrivent ainsi (en romaji : ha, he, wo)."
        )
      )
    } else {
      val filled = exercise.parts
        .foldLeft((List.empty[String], typed)) {
          case ((done, next :: rest), part) if part.marked =>
            (done :+ next, rest)
          case ((done, left), part) => (done :+ part.text, left)
        }
        ._1
        .mkString
      client
        .structured[ParticleJudgement](
          system,
          s"""Sentence: $filled
             |Meaning: ${exercise.french}
             |The blanks were filled with ${typed
              .map(each => s"「$each」")
              .mkString(", ")}; the particles expected are ${expected
              .map(each => s"「$each」")
              .mkString(", ")}.
             |Is the sentence, filled so, also correct, with this meaning? Explain why or why not.""".stripMargin,
          particleSchema
        )
        .fold(
          _ => unjudged,
          judgement =>
            ExerciseVerdict(
              judgement.alsoCorrect,
              byTutor = true,
              None,
              Some(judgement.explanation)
            )
        )
    }
  }

  /** A sentence's words written with kanji, each way of writing them by hand
    * (all or some of their kanji in kana, [[KanjiHand.ways]]), put back in
    * kana, the part's text, the longest first.
    */
  private def asKana(answer: String, parts: List[SentencePart]): String =
    parts
      .flatMap(part =>
        part.written.toList.flatMap(written =>
          kanji
            .ways(written, part.text)
            .filter(_.exists(SentenceLayout.isKanji))
            .map(_ -> part.text)
        )
      )
      .sortBy(-_._1.length)
      .foldLeft(answer) { case (sentence, (way, kana)) =>
        sentence.replace(way, kana)
      }

  // answered all in kana, the words the learner writes with kanji, as they
  // write them
  private def reminder(
      answer: String,
      parts: List[SentencePart]
  ): Option[String] = {
    val written = kanji.writes()
    val words = parts.flatMap(part =>
      part.written.flatMap(kanji.asWritten(_, part.text, written))
    )
    Option.when(words.nonEmpty && !answer.exists(SentenceLayout.isKanji))(
      s"Juste. Avec les kanji que tu sais écrire : ${words.distinct.mkString("、")}."
    )
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

  // the slips a sentence may have in its words, a letter missed or changed
  // here and there, for its point to be right in it
  private val slipsAtMost = 2

  /** A sentence (`typed`, [[normalised]]) right but for a slip or two within
    * its nouns, each part said one or not (にほん for にほんご, たなかさ for たなかさん): its
    * point, the parts marked, untouched, and so its particles, endings and
    * conjugated words, where a slip is a mistake of grammar (が for は, ません for
    * ます). The lesson is on its point, not on the words around it.
    */
  def pointRight(
      typed: String,
      parts: List[(SentencePart, Boolean)]
  ): Boolean = {
    // the spans of the expected sentence no slip may touch
    val (expected, kept, open) =
      parts.foldLeft(("", List.empty[(Int, Int)], Option.empty[Int])) {
        case ((before, kept, open), (part, noun)) =>
          val text = normalised(part.text)
          val after = before + text
          if (text.isEmpty) (after, kept, open)
          else if (noun && !part.marked)
            (after, kept ++ open.map(_ -> before.length), None)
          else (after, kept, open.orElse(Some(before.length)))
      }
    val spans = kept ++ open.map(_ -> expected.length)
    parts.exists(_._1.marked) &&
    editDistance(expected, typed) <= slipsAtMost &&
    spans.forall(KanjiReadings.partRight(expected, typed, _))
  }

  /** A noun as the analyser says (UniDic): a common or proper one, a pronoun, a
    * suffix making one (さん).
    */
  def isNoun(partOfSpeech: List[String]): Boolean = partOfSpeech match {
    case ("名詞" | "代名詞") :: _ => true
    case "接尾辞" :: "名詞的" :: _ => true
    case _                   => false
  }

  private def editDistance(a: String, b: String): Int =
    a.foldLeft((0 to b.length).toList) { (previous, character) =>
      b.zip(previous.zip(previous.tail))
        .scanLeft(previous.head + 1) { case (left, (other, (diagonal, up))) =>
          List(
            left + 1,
            up + 1,
            diagonal + (if (character == other) 0 else 1)
          ).min
        }
        .toList
    }.last

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
