package hanamaru.backend.learner

import hanamaru.shared.learner.*

import scala.util.Random

/** Which kana to review next, among those the learner has met: first those due,
  * those being learned first; then those the placement test found probably
  * known, to confirm them; at random among them. Then, nothing left to review,
  * any kana met ([[Rotation]]), those not reviewed `lately` first: every kana
  * comes back, not the same few being learned in turn. Never the one just
  * reviewed when there is another.
  */
object KanaReview {

  def next(
      progress: List[KanaProgress],
      now: Long,
      after: Option[String],
      lately: Set[String],
      random: Random
  ): Option[KanaToReview] = {
    def due(kana: KanaProgress) = kana.due.exists(_ <= now)
    def learning(kana: KanaProgress) = kana.status == ItemStatus.Learning
    val levels = List[KanaProgress => Boolean](
      kana => learning(kana) && due(kana),
      due,
      _.status == ItemStatus.ToConfirm
    )
    val candidates =
      progress.filter(kana => !after.contains(kana.character)) match {
        case Nil    => progress
        case others => others
      }
    levels.iterator
      .map(candidates.filter)
      .collectFirst {
        case level if level.nonEmpty => level(random.nextInt(level.size))
      }
      .orElse(
        Rotation
          .drawn(candidates, 1, kana => lately(kana.character), random)
          .headOption
      )
      .map(kana => KanaToReview(kana.character, kana.status))
  }
}
