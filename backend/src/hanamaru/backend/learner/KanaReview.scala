package hanamaru.backend.learner

import hanamaru.shared.learner.*

import scala.util.Random

/** Which kana to review next, among those the learner has met: first those
  * being learned that are due, then the others being learned (those missed in
  * the placement test, first of all), then those due, then any; at random
  * within each, and never the one just reviewed when there is another.
  */
object KanaReview {

  def next(
      progress: List[KanaProgress],
      now: Long,
      after: Option[String],
      random: Random
  ): Option[KanaToReview] = {
    def due(kana: KanaProgress) = kana.due.exists(_ <= now)
    def learning(kana: KanaProgress) = kana.status == ItemStatus.Learning
    val levels = List[KanaProgress => Boolean](
      kana => learning(kana) && due(kana),
      learning,
      due,
      _ => true
    )
    val candidates =
      progress.filter(kana => !after.contains(kana.character)) match {
        case Nil    => progress
        case others => others
      }
    levels.iterator
      .map(level => candidates.filter(level))
      .collectFirst {
        case level if level.nonEmpty => level(random.nextInt(level.size))
      }
      .map(kana => KanaToReview(kana.character, kana.status))
  }
}
