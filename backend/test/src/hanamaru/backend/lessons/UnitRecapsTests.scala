package hanamaru.backend.lessons

import hanamaru.shared.dictionary.{DictionaryWord, Meaning}
import hanamaru.shared.lessons.ShortMeaning

import utest.*

object UnitRecapsTests extends TestSuite {

  private def word(senses: Meaning*) =
    DictionaryWord(1, 5, Nil, List("そう"), true, senses.toList)

  private def french(glosses: String*) = Meaning("fre", Nil, glosses.toList)

  val tests: Tests = Tests {
    test("a short meaning: the first glosses that fit, their asides left out") {
      val sou = word(
        french(
          "de cette façon (concernant les actions de l'auditeur ; avec une phrase négative, implique que…) ",
          "ainsi",
          "tel"
        ),
        french("oui (utilisé pour exprimer un accord)", "bien sûr")
      )
      val meaning = UnitRecaps.meaningOf(sou)
      assert(
        meaning.short == "de cette façon, ainsi",
        meaning.senses.size == 2
      )
    }
    test("a meaning said whole has no senses to unfold") {
      assert(
        UnitRecaps.meaningOf(word(french("qui"))) == ShortMeaning("qui", Nil)
      )
    }
    test("a gloss all in parentheses is kept as it is") {
      assert(UnitRecaps.meaningOf(word(french("(poli)"))).short == "(poli)")
    }
    test("no French: the English senses") {
      val meaning = UnitRecaps.meaningOf(
        word(Meaning("eng", List("pn"), List("what", "whatsit")))
      )
      assert(meaning == ShortMeaning("what, whatsit", Nil))
    }
    test("no sense at all: nothing to show") {
      assert(UnitRecaps.meaningOf(word()) == ShortMeaning("", Nil))
    }
  }
}
