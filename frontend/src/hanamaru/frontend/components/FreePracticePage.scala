package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.lessons.ExercisesOf

import com.raquo.laminar.api.L.*

/** Free practice, apart from the lessons: sentences the tutor writes on
  * everything learned so far, drawn evenly, each translated one way or the
  * other; recorded as nothing.
  */
class FreePracticePage(bus: AppBus) extends ReactiveElement {

  override lazy val node: HtmlElement = div(
    cls := "free-practice-page",
    div(cls := "review-bar", h1(Tr("Free practice", "Entraînement libre"))),
    p(
      Tr(
        "Sentences on all the grammar and words you have learned so far, each as likely as the others: the unit under way no more than the rest. Translate them from French, or give their meaning. Nothing is recorded: it does not change your reviews.",
        "Des phrases sur toute la grammaire et tous les mots appris jusqu'ici, chacun autant que les autres : l'unité en cours pas plus que le reste. Traduis-les depuis le français, ou donne leur sens. Rien n'est enregistré : tes révisions ne changent pas."
      )
    ),
    LessonExercises(bus, ExercisesOf.Free).node
  )
}
