package hanamaru.backend.placement

import hanamaru.backend.lessons.CurriculumPoint

/** The grammar placement test, as a function of the answers given so far: the
  * curriculum's points in order, each asked its first question, then its second
  * if the first was right; a point is passed with both right. The test stops
  * after two points failed in a row, when it is given up, or at the end of the
  * curriculum.
  */
final class GrammarPlacement(points: List[CurriculumPoint]) {

  /** The next question (point, number), and the points decided: passed or not.
    */
  def progress(
      answers: List[Answered]
  ): (Option[(CurriculumPoint, Int)], List[(CurriculumPoint, Boolean)]) = {
    def answer(point: CurriculumPoint, number: Int) =
      answers.find(answered =>
        answered.kind == AnswerKind.Grammar && answered.subject == s"${point.id}#$number"
      )
    val givenUp = answers.exists(_.kind == AnswerKind.Skip)
    @annotation.tailrec
    def walk(
        left: List[CurriculumPoint],
        decided: List[(CurriculumPoint, Boolean)],
        failedInARow: Int
    ): (Option[(CurriculumPoint, Int)], List[(CurriculumPoint, Boolean)]) =
      left match {
        case _ if givenUp || failedInARow >= 2 => (None, decided.reverse)
        case Nil                               => (None, decided.reverse)
        case point :: rest                     =>
          val numbers = point.placement.indices.toList
          val answered = numbers.map(answer(point, _))
          answered.indexWhere(_.isEmpty) match {
            // a question to ask, unless one before it was missed
            case next
                if next >= 0 && answered
                  .take(next)
                  .forall(_.exists(_.correct)) =>
              (Some(point -> next), decided.reverse)
            case _ =>
              val passed = answered.forall(_.exists(_.correct))
              walk(
                rest,
                (point -> passed) :: decided,
                if (passed) 0 else failedInARow + 1
              )
          }
      }
    walk(points, Nil, 0)
  }
}
