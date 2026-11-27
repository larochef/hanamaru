package hanamaru.backend.lessons

import hanamaru.shared.lessons.{FreeExercise, LessonExercise}

import java.util.concurrent.{CompletableFuture, Executors}
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import scala.util.Try

/** Free practices ([[FreePractice]]), their sentences written one after the
  * other: the first as the practice starts, each next one while the learner
  * answers those before, so that the learner waits for one sentence, not for
  * all. A sentence the tutor cannot write on its target is written on the next
  * target drawn. Only the latest practice is kept, and written: there is one
  * learner, and the tutor has better to do than a practice left.
  */
final class FreePractices(exercises: Exercises) {
  import FreePractices.*

  private val ids = AtomicLong()
  private val latest = AtomicReference[Option[Practice]](None)
  // the tutor writes one sentence at a time anyway
  private val writing = Executors.newVirtualThreadPerTaskExecutor()

  /** A new practice, its first exercise once written, the others being written.
    */
  def started(): Either[String, FreeExercise] =
    exercises
      .freeTargets(FreePractice.sentences * targetsTried)
      .flatMap { (upTo, targets) =>
        val id = ids.incrementAndGet()
        val first = CompletableFuture.supplyAsync(
          () => written(id, upTo, targets, Written(None, 0, Nil), 0),
          writing
        )
        // each written once the one before is: the tutor is told them
        val slots = (1 until FreePractice.sentences).scanLeft(first) {
          (before, index) =>
            before.thenApplyAsync(
              done => written(id, upTo, targets, done, index),
              writing
            )
        }
        val practice = Practice(id, slots.toVector)
        latest.set(Some(practice))
        exercise(practice.id, 0)
      }

  /** A practice's exercise, the one at `index`, once written. */
  def exercise(practice: Long, index: Int): Either[String, FreeExercise] =
    for {
      found <- latest
        .get()
        .filter(_.id == practice)
        .toRight("This practice is over: start another")
      slot <- found.slots.lift(index).toRight("No such exercise")
      done <- Try(slot.get()).toOption.toRight(notWritten)
      exercise <- done.exercise.toRight(notWritten)
    } yield FreeExercise(practice, index, found.slots.size, exercise)

  // the exercise at `index`, on the first target from where the one before
  // stopped that the tutor writes a sentence for, a few tried at most; none
  // once another practice has started
  private def written(
      practice: Long,
      upTo: Int,
      targets: List[TextWriter.Target],
      before: Written,
      index: Int
  ): Written = {
    val tried =
      if (ids.get() == practice) targets.drop(before.next).take(targetsTried)
      else Nil
    val found = tried.iterator.zipWithIndex
      .map((target, at) =>
        exercises
          .freeExercise(target, upTo, index, before.sentences)
          .map(_ -> at)
      )
      .collectFirst { case Some(found) => found }
    found.fold(Written(None, before.next + tried.size, before.sentences)) {
      (exercise, at) =>
        Written(
          Some(exercise),
          before.next + at + 1,
          before.sentences :+ exercise.japanese
        )
    }
  }
}

object FreePractices {

  // the targets tried for one sentence before giving it up
  private val targetsTried = 3

  private val notWritten =
    "The tutor wrote no sentence that fits what you have learned: is it running?"

  final private case class Practice(
      id: Long,
      slots: Vector[CompletableFuture[Written]]
  )

  // a sentence written, if any, the next target to try, and the sentences
  // written so far
  final private case class Written(
      exercise: Option[LessonExercise],
      next: Int,
      sentences: List[String]
  )
}
