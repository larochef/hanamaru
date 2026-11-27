package hanamaru.backend.lessons

import scala.util.Random

/** What free practice is on: everything learned so far, evenly, so that the
  * unit under way is no more there than the rest.
  */
object FreePractice {

  /** The sentences of a free practice. */
  val sentences = 6

  /** What the analyser calls the words that say what is done or how things are,
    * drawn apart from the others.
    */
  val doingWords: Set[String] = Set("動詞", "形容詞")

  /** What each of `count` sentences is to be on: a point among those learned,
    * each as often as the others (in a random order, once each before any comes
    * again), and two words met, each given by its forms, drawn without
    * replacement: one that says what is done or how (`doing`), when there are
    * any left, and one of the others (`others`).
    */
  def targets(
      points: List[CurriculumPoint],
      doing: List[List[String]],
      others: List[List[String]],
      count: Int,
      random: Random
  ): List[TextWriter.Target] =
    if (points.isEmpty) Nil
    else {
      val drawn = Iterator
        .continually(random.shuffle(points))
        .flatten
        .take(count)
        .toList
      val verbs = random.shuffle(doing)
      val nouns = random.shuffle(others)
      drawn.zipWithIndex.map((point, index) =>
        TextWriter.Target(
          point,
          verbs.lift(index).toList ++ nouns.lift(index).toList
        )
      )
    }
}
