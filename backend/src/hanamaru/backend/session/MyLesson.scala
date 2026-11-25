package hanamaru.backend.session

import hanamaru.backend.characters.CharacterPractice
import hanamaru.backend.lessons.Lessons
import hanamaru.backend.vocabulary.VocabularyPractice
import hanamaru.shared.lessons.LessonPlan

import java.time.Instant

/** My lesson, the session on the unit under way: its plan, from the grammar
  * due, the words to practise, the unit's lesson and its kana and kanji.
  */
final class MyLesson(
    lessons: Lessons,
    vocabulary: VocabularyPractice,
    characters: CharacterPractice
) {

  def plan(now: Instant): LessonPlan =
    lessons.plan(
      now,
      wordsToPractise = vocabulary.series(now).exists(_.nonEmpty),
      charactersToPractise = characters.exercises(now).nonEmpty
    )
}
