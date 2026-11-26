package hanamaru.backend.learner

import scala.util.Random

/** What a practice the learner asked for draws once nothing is due, rather than
  * ending or going round the few items being learned: any of the items met, at
  * random, those not asked lately first. Every item comes back this way, the
  * rarely used as often as the others.
  */
object Rotation {

  /** `count` of `candidates` at random, those not `asked` lately first. */
  def drawn[A](
      candidates: List[A],
      count: Int,
      asked: A => Boolean,
      random: Random
  ): List[A] = {
    val (lately, others) = candidates.partition(asked)
    (random.shuffle(others) ++ random.shuffle(lately)).take(count)
  }
}
