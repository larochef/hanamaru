package hanamaru.backend.learner

import java.time.Duration

/** Nothing waits for ever: an item held back, by reviews taking all the room or
  * by a gate it has not passed yet, gets in once it has waited past a patience;
  * the patience grows with the backlog, so that the reviews due still come on
  * time when there are many.
  */
object Waiting {

  /** How long an item may wait with no backlog. */
  val patience: Duration = Duration.ofDays(2)

  /** The patience with `due` reviews for a `capacity`: twice as long with a
    * full backlog, three times with twice that.
    */
  def patienceWith(due: Int, capacity: Int): Duration =
    patience.multipliedBy(1 + due / capacity.max(1))

  /** Whether an item that has waited `waited` gets in. */
  def overdue(waited: Duration, due: Int = 0, capacity: Int = 1): Boolean =
    waited.compareTo(patienceWith(due, capacity)) >= 0

  /** How many new items take room among `due` reviews in a practice of
    * `capacity`: what the reviews leave, `most` at most; with no room left, one
    * still once the new ones have waited past the patience (`waited`, since the
    * last one came).
    */
  def room(
      due: Int,
      capacity: Int,
      most: Int,
      waited: Option[Duration]
  ): Int = {
    val left = (capacity - due).min(most).max(0)
    if (left == 0 && most > 0 && waited.exists(overdue(_, due, capacity))) 1
    else left
  }
}
