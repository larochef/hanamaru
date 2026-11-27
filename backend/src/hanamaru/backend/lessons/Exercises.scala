package hanamaru.backend.lessons

import hanamaru.backend.kanji.*
import hanamaru.backend.learner.*
import hanamaru.backend.reference.*
import hanamaru.shared.characters.CharacterAsked
import hanamaru.shared.kana.Romaji
import hanamaru.shared.kanji.KanjiExercise
import hanamaru.shared.learner.Grade
import hanamaru.shared.lessons.*
import hanamaru.shared.text.{KanjiShown, SentencePart}

import java.time.Instant
import scala.util.Random

/** The exercises of the curriculum's lessons, made in the sentences of a
  * point's examples and of a new reading text ([[Lessons]]): particles typed,
  * forms chosen, translations both ways, kanji written; and, once done, how
  * they went, recorded as one review of the point.
  */
final class Exercises(
    lessons: Lessons,
    analyser: () => Option[Analyser],
    dictionary: () => Option[Dictionary],
    learner: LearnerStore,
    kanjiBook: () => Option[KanjiBook]
) {
  private val points = Curriculum.points

  /** Exercises on a lesson's point, in the sentences of its examples and of a
    * new reading text that show it: its particles typed, when the point is a
    * particle (every particle learned so far blanked, in those sentences and in
    * a few of the particles learned before), then sentences translated from
    * French, a few of each, shuffled.
    */
  def exercises(id: String): Either[String, List[LessonExercise]] =
    for {
      index <- lessons.indexOf(id)
      analysis <- analyser().toRight("The Japanese analyser is not ready")
      words <- dictionary().toRight("The dictionary is not ready")
    } yield {
      val point = points(index)
      val glosses = Glosses(words)
      val sight = KanjiSight.of(learner)
      // the kanji the learner writes, recognised by the stylus
      val kanjiWritten = KanjiHand.of(kanjiBook, learner).writes().toList.sorted
      val ofExamples = point.examples.map(example =>
        (example.japanese, example.french, analysis.tokens(example.japanese))
      )
      val ofText = Option
        .when(point.text)(lessons.written(index, analysis, words))
        .flatten
        .toList
        .flatten
        .map((sentence, tokens) => (sentence.japanese, sentence.french, tokens))
        .filter((_, _, tokens) => point.shownIn(tokens).nonEmpty)
      // a sentence of the text may be an example again: asked once
      val sentences = Random.shuffle(
        (ofExamples ++ ofText).distinctBy((_, _, tokens) =>
          tokens.map(_.surface).mkString
        )
      )
      // every particle learned so far a sentence has blanked, the point's and
      // the others, each typed: one alone, the answer would be known before
      // the sentence is read
      def particleIn(
          japanese: String,
          french: String,
          tokens: List[Token]
      ): Option[LessonExercise] = {
        val blanks = points
          .take(index + 1)
          .filter(Exercises.ofAParticle)
          .flatMap(_.shownIn(tokens))
          .filter(tokens(_).partOfSpeech.headOption.contains("助詞"))
          .toSet
        Option.when(blanks.nonEmpty) {
          val parts = sight.shown(
            lessons.marking(blanks, tokens, french, glosses).parts
          )
          LessonExercise(
            point.id,
            ExerciseKind.Particle,
            parts,
            japanese,
            french,
            Nil,
            parts.filter(_.marked).map(_.text).mkString(" ")
          )
        }
      }
      val particles =
        if (!Exercises.ofAParticle(point)) Nil
        else {
          val own = sentences.flatMap(particleIn)
          val seen = sentences.map(_._1).toSet
          val before = Random
            .shuffle(
              points
                .take(index)
                .filter(Exercises.ofAParticle)
                .flatMap(_.examples)
                .filterNot(example => seen(example.japanese))
            )
            .flatMap(example =>
              particleIn(
                example.japanese,
                example.french,
                analysis.tokens(example.japanese)
              )
            )
            .take(particlesBefore)
          val taken = own.take(exercisesOfAKind - before.size)
          Random.shuffle(taken ++ before)
        }
      val forms = sentences.flatMap { (japanese, french, tokens) =>
        val at = point.shownIn(tokens).toList.sorted
        val ending = at.map(tokens(_).surface).mkString
        Option.when(
          at.nonEmpty && at == (at.head to at.last).toList && at.head > 0 &&
            Exercises.endings.exists(_._1 == ending) &&
            formDistractors(index, ending).nonEmpty &&
            tokens(at.head - 1).partOfSpeech.headOption.contains("動詞")
        ) {
          LessonExercise(
            point.id,
            ExerciseKind.Form,
            Exercises.markedTogether(
              sight.shown(lessons.shown(point, tokens, french, glosses).parts)
            ),
            japanese,
            french,
            Random.shuffle(ending :: formDistractors(index, ending)),
            ending
          )
        }
      }
      val translations = sentences.map { (japanese, french, tokens) =>
        Exercises.translation(
          point.id,
          sight.shown(lessons.shown(point, tokens, french, glosses).parts),
          japanese,
          french,
          kanjiWritten
        )
      }
      val choices = (particles ++ forms).take(exercisesOfAKind)
      val translated = translations.take(translationsAtMost)
      // the meanings asked in sentences not shown with their French yet in
      // the series (chosen or translated), which would give them away
      val shownInFrench = (choices ++ translated).map(_.japanese).toSet
      val meanings = sentences
        .filterNot((japanese, _, _) => shownInFrench(japanese))
        .take(meaningsAtMost)
        .map { (japanese, french, tokens) =>
          Exercises.meaning(
            point.id,
            sight.shown(lessons.shown(point, tokens, french, glosses).parts),
            japanese,
            french
          )
        }
      val kanjiToWrite = kanjiWritings(sentences, sight, words, glosses, point)
      choices ++ translated ++ meanings ++ kanjiToWrite
    }

  /** What a free practice is on, apart from the lessons: the index of the last
    * point learned, and `count` targets drawn evenly on everything learned so
    * far ([[FreePractice.targets]]). Nothing learned yet, none.
    */
  def freeTargets(
      count: Int
  ): Either[String, (Int, List[TextWriter.Target])] =
    for {
      analysis <- analyser().toRight("The Japanese analyser is not ready")
      words <- dictionary().toRight("The dictionary is not ready")
      upTo <- learner
        .metKeys(ItemKind.Grammar)
        .map(id => points.indexWhere(_.id == id))
        .maxOption
        .filter(_ >= 0)
        .toRight("No lesson learned yet")
    } yield {
      val met = words
        .words(learner.metKeys(ItemKind.Word).flatMap(_.toLongOption).toList)
        // as usually written first: そう, not 然う
        .map(word =>
          if (word.usuallyKana) word.readings ++ word.writings
          else word.writings ++ word.readings
        )
        .filter(_.nonEmpty)
      val learned = points.take(upTo + 1)
      val sayable = learned.map(_.id).toSet
      // verbs and adjectives drawn apart, the nouns would drown them; none
      // before the point that says them
      val needs = met.map(forms =>
        forms -> analysis
          .tokens(forms.head)
          .headOption
          .flatMap(_.partOfSpeech.headOption)
          .flatMap(FreePractice.doingFrom.get)
      )
      val doing = needs.collect {
        case (forms, Some(point)) if sayable(point) => forms
      }
      val others = needs.collect { case (forms, None) => forms }
      upTo -> FreePractice.targets(
        learned,
        doing,
        others,
        count,
        Random
      )
    }

  /** A free practice's exercise, the one at `index`, on a sentence the tutor
    * writes on its target, other than those written `before`: translated from
    * French or its meaning given, in turn; every point learned up to `upTo`
    * marked in it, so that a slip is forgiven only in its nouns. None when the
    * tutor wrote none that fits.
    */
  def freeExercise(
      target: TextWriter.Target,
      upTo: Int,
      index: Int,
      before: List[String]
  ): Option[LessonExercise] =
    for {
      analysis <- analyser()
      words <- dictionary()
      (_, sentence, tokens) <- lessons
        .freeSentences(List(target), upTo, analysis, words, before)
        .headOption
    } yield {
      val parts = KanjiSight
        .of(learner)
        .shown(
          lessons
            .marking(
              points.take(upTo + 1).flatMap(_.shownIn(tokens)).toSet,
              tokens,
              sentence.french,
              Glosses(words)
            )
            .parts
        )
      if (index % 2 == 0)
        Exercises.translation(
          target.point.id,
          parts,
          sentence.japanese,
          sentence.french,
          KanjiHand.of(kanjiBook, learner).writes().toList.sorted
        )
      else
        Exercises.meaning(
          target.point.id,
          parts,
          sentence.japanese,
          sentence.french
        )
    }

  private val translationsAtMost = 3
  private val particlesBefore = 2
  private val meaningsAtMost = 2
  private val kanjiWritingsAtMost = 2

  // A few words of the sentences whose kanji's writing is due: one kanji of
  // each written in its word, a review of the kanji.
  private def kanjiWritings(
      sentences: List[(String, String, List[Token])],
      sight: KanjiSight,
      words: Dictionary,
      glosses: Glosses,
      point: CurriculumPoint
  ): List[LessonExercise] =
    kanjiBook().toList.flatMap { book =>
      // only those due: a review out of time would reschedule them early
      val now = Instant.now().toEpochMilli
      val writing = learner
        .cards(ItemKind.Kanji)
        .filter(card => card.kind == CardKind.Writing && card.due <= now)
        .map(_.key)
        .toSet
      sentences
        .flatMap { (japanese, french, tokens) =>
          val parts =
            sight.shown(lessons.shown(point, tokens, french, glosses).parts)
          parts.flatMap(part =>
            for {
              written <- part.written
              literal <- written
                .map(_.toString)
                .find(character => writing(character))
              shownWord <- part.word
              word <- Units.chosen(
                words.withForm(shownWord.form),
                shownWord.form,
                None
              )
              kanji <- book.kanji(literal)
            } yield LessonExercise(
              point.id,
              ExerciseKind.KanjiWriting,
              // the word to write in kana, marked: in kanji, it would give
              // its kanji away
              parts.map(each =>
                if (each eq part)
                  each.copy(marked = true, shownAs = KanjiShown.Kana)
                else each.copy(marked = false)
              ),
              japanese,
              french,
              Nil,
              literal,
              Some(
                KanjiExercise(
                  kanji,
                  word,
                  KanjiHand.shown(
                    word,
                    literal =>
                      book.kanji(literal).fold(Nil)(KanjiReadings.formsOf),
                    lessons.units.allKanji
                  ),
                  CharacterAsked.Writing,
                  false
                )
              )
            )
          )
        }
        .distinctBy(_.expected)
        .take(kanjiWritingsAtMost)
    }

  // other endings for a form, among those learned by this point: none until
  // there are enough to choose from (ます alone, nothing to tell apart)
  private def formDistractors(index: Int, expected: String): List[String] = {
    val learned = points.take(index + 1).map(_.id).toSet
    val known = Exercises.endings.collect {
      case (ending, point) if learned(point) => ending
    }
    if (known.size < choicesOfAForm) Nil
    else Random.shuffle(known.filter(_ != expected)).take(choicesOfAForm - 1)
  }

  private val choicesOfAForm = 4

  /** A lesson's exercises done, recorded as one review of its point: Good with
    * four answers in five right, Hard with half, else Again. A kanji written is
    * the kanji's review, not counted here. How it went, as counted.
    */
  def done(result: ExercisesDone, at: Instant): Either[String, ExercisesTally] =
    lessons.indexOf(result.point).map { _ =>
      val counted =
        result.outcomes.filter(_.kind != ExerciseKind.KanjiWriting)
      val tally = ExercisesTally(counted.count(_.right), counted.size)
      val share =
        if (tally.total == 0) 0.0 else tally.right.toDouble / tally.total
      val grade =
        if (share >= 0.8) Grade.Good
        else if (share >= 0.5) Grade.Hard
        else Grade.Again
      learner.reviewed(
        ItemKind.Grammar,
        result.point,
        CardKind.Usage,
        grade,
        s"${tally.right}/${tally.total}",
        at
      )
      tally
    }

  private val exercisesOfAKind = 4
}

object Exercises {

  /** A sentence to translate from French, laid out (`parts`): typed in kana,
    * the script expected said (hiragana, and the words in katakana), or written
    * with the stylus with the kanji the learner writes.
    */
  def translation(
      point: String,
      parts: List[SentencePart],
      japanese: String,
      french: String,
      kanjiWritten: List[String]
  ): LessonExercise = LessonExercise(
    point,
    ExerciseKind.Translation,
    parts,
    japanese,
    french,
    Nil,
    parts.map(_.text).mkString,
    wordsInKatakana = parts.count(part => Romaji.isKatakana(part.text)),
    kanjiWritten = kanjiWritten
  )

  /** A sentence shown, laid out (`parts`), its meaning given in French. */
  def meaning(
      point: String,
      parts: List[SentencePart],
      japanese: String,
      french: String
  ): LessonExercise = LessonExercise(
    point,
    ExerciseKind.Meaning,
    parts,
    japanese,
    french,
    Nil,
    french
  )

  /** Whether a point is taught by a particle (の, を), as its signature says. */
  def ofAParticle(point: CurriculumPoint): Boolean =
    point.signature.flatten.exists(_.pos.exists(_.startsWith("助詞")))

  /** The endings a verb's polite stem takes (たべ＋ます), with the point teaching
    * each: one blanked is chosen among the others, the French saying which
    * (tense, negation, wish, invitation).
    */
  val endings: List[(String, String)] = List(
    "ます" -> "masu",
    "ません" -> "masen-mashita",
    "ました" -> "masen-mashita",
    "ませんでした" -> "masen-mashita",
    "ましょう" -> "mashou",
    "たい" -> "tai"
  )

  /** The marked parts next to each other made one, in kana, to be blanked as
    * one: ませ, ん, でし, た as ませんでした.
    */
  def markedTogether(parts: List[SentencePart]): List[SentencePart] =
    parts
      .foldLeft(Vector.empty[SentencePart])((done, part) =>
        done.lastOption match {
          case Some(last) if last.marked && part.marked && !part.spaceBefore =>
            done.init :+ last.copy(
              text = last.text + part.text,
              written = None,
              shownAs = KanjiShown.Kana
            )
          case _ => done :+ part
        }
      )
      .toList

}
