package hanamaru.backend.lessons

import utest.*

object NextLessonTests extends TestSuite {
  private val points = Curriculum.points
  private val List(first, second, third) = points.take(3): @unchecked

  val tests: Tests = Tests {
    test("the unit under way's lesson, while its point was never met") {
      assert(
        Lessons.nextLesson(points, Some(second), Set.empty).contains(second)
      )
    }
    test("its point met (placed, or practised): no lesson, only its words") {
      assert(Lessons.nextLesson(points, Some(second), Set(second.id)).isEmpty)
    }
    test("past the last unit, the first point never met") {
      assert(
        Lessons
          .nextLesson(points, None, Set(first.id, second.id))
          .contains(third)
      )
    }
  }
}
