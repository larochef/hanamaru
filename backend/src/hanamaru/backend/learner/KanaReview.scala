package hanamaru.backend.learner

import hanamaru.shared.learner.*

import scala.util.Random

/** Which kana to review next, among those the learner has met: first those due,
  * those being learned first; then those the placement test found probably
  * known, to confirm them; then those being learned, the one due soonest first,
  * so that each comes in turn rather than the same few at random; then any, the
  * same way. At random among those due and those to confirm, and never the one
  * just reviewed when there is another.
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
    def atRandom(level: List[KanaProgress]) = level(random.nextInt(level.size))
    def soonest(level: List[KanaProgress]) =
      level.minBy(_.due.getOrElse(Long.MaxValue))
    val levels =
      List[(KanaProgress => Boolean, List[KanaProgress] => KanaProgress)](
        (kana => learning(kana) && due(kana), atRandom),
        (due, atRandom),
        (_.status == ItemStatus.ToConfirm, atRandom),
        (learning, soonest),
        (_ => true, soonest)
      )
    val candidates =
      progress.filter(kana => !after.contains(kana.character)) match {
        case Nil    => progress
        case others => others
      }
    levels.iterator
      .map((level, pick) => (candidates.filter(level), pick))
      .collectFirst {
        case (level, pick) if level.nonEmpty => pick(level)
      }
      .map(kana => KanaToReview(kana.character, kana.status))
  }
}
