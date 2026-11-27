package hanamaru.backend.session

import hanamaru.backend.characters.CharacterPractice
import hanamaru.backend.lessons.Lessons
import hanamaru.backend.vocabulary.VocabularyPractice
import hanamaru.shared.characters.CharactersOf
import hanamaru.shared.lessons.LessonPlan
import hanamaru.shared.vocabulary.{SeriesOf, WordAsked}

import java.time.Instant

/** My lesson, the session on the unit under way: its plan, from the grammar
  * due, the words to practise, the unit's lesson and its kana and kanji.
  */
final class MyLesson(
    lessons: Lessons,
    vocabulary: VocabularyPractice,
    characters: CharacterPractice
) {

  /** What is due, or new: none of it when nothing is; the words new to be
    * written in kanji named.
    */
  def plan(now: Instant): LessonPlan = {
    val words = vocabulary.series(now, SeriesOf.MyLesson).getOrElse(Nil)
    lessons
      .plan(
        now,
        Option.when(words.nonEmpty)(SeriesOf.MyLesson),
        Option.when(characters.exercises(now).nonEmpty)(CharactersOf.MyLesson)
      )
      .copy(inKanji =
        words
          .filter(word => word.isNew && word.asked == WordAsked.InKanji)
          .flatMap(_.inKanji)
      )
  }

  /** A session the learner asks for, to make what is learned hold better: each
    * part what is due, else, nothing being, the unit under way gone over again:
    * the grammar due, else its point's exercises (once its lesson is done); the
    * words due or new, else a series on its words; the kana and kanji due or
    * new, else a series on its kanji.
    */
  def session(now: Instant): LessonPlan = {
    val due = plan(now)
    lessons.unit.fold(due) { unit =>
      val of = unit.point.id
      due.copy(
        reviews =
          if (due.reviews.nonEmpty || !unit.practised) due.reviews
          else List(unit.point),
        series = due.series.orElse(
          Option.when(
            vocabulary.series(now, SeriesOf.OfUnit(of)).exists(_.nonEmpty)
          )(SeriesOf.OfUnit(of))
        ),
        characters = due.characters.orElse(
          Option.when(characters.ofUnit(of, now).exists(_.nonEmpty))(
            CharactersOf.OfUnit(of)
          )
        )
      )
    }
  }
}
