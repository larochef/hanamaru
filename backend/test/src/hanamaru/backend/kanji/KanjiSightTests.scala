package hanamaru.backend.kanji

import hanamaru.shared.text.{KanjiShown, SentencePart}

import utest.*

object KanjiSightTests extends TestSuite {

  private def part(text: String, written: Option[String]) =
    SentencePart(text, marked = false, spaceBefore = true, written = written)

  private val sentence = List(
    part("がくせい", Some("学生")),
    part("です", None),
    part("たべ", Some("食べ"))
  )

  val tests: Tests = Tests {
    test("a word in kanji once its kanji are all read") {
      val shown = KanjiSight(Set("学", "生"), Set("学", "生")).shown(sentence)
      assert(
        shown.map(_.shownAs) ==
          List(KanjiShown.Kanji, KanjiShown.Kana, KanjiShown.Kana)
      )
    }
    test("with its reading above while one of them is being learned") {
      val shown = KanjiSight(Set("学"), Set("学", "生")).shown(sentence)
      assert(shown.head.shownAs == KanjiShown.WithReading)
    }
    test("々 repeats a kanji: the word goes by that kanji") {
      val people = List(part("ひとびと", Some("人々")))
      assert(
        KanjiSight(Set("人"), Set("人")).shown(people).head.shownAs ==
          KanjiShown.Kanji
      )
    }
    test("in kana while one of them was never met") {
      val shown = KanjiSight(Set("学"), Set("学")).shown(sentence)
      assert(shown.head.shownAs == KanjiShown.Kana, shown.head.text == "がくせい")
    }
  }
}
