package hanamaru.backend.kanji

import hanamaru.shared.kanji.Kanji

import utest.*

object KanjiReadingsTests extends TestSuite {

  private def kanji(literal: String, on: List[String], kun: List[String]) =
    Kanji(literal, Some(1), 1, None, on, kun, Nil, false)

  private val book = Map(
    "先" -> kanji("先", List("セン"), List("さき", "ま.ず")),
    "生" -> kanji("生", List("セイ", "ショウ"), List("い.きる", "う.まれる")),
    "学" -> kanji("学", List("ガク"), List("まな.ぶ")),
    "校" -> kanji("校", List("コウ"), Nil),
    "会" -> kanji("会", List("カイ", "エ"), List("あ.う")),
    "社" -> kanji("社", List("シャ"), List("やしろ")),
    "人" -> kanji("人", List("ジン", "ニン"), List("ひと"))
  )

  private def forms(literal: String) =
    book.get(literal).fold(Nil)(KanjiReadings.formsOf)

  val tests: Tests = Tests {
    test("a word's reading split along its kanji") {
      assert(
        KanjiReadings.spanOf("先生", "せんせい", "先", forms).contains((0, 2)),
        KanjiReadings.spanOf("先生", "せんせい", "生", forms).contains((2, 4)),
        // cut short before the next kanji
        KanjiReadings.spanOf("学校", "がっこう", "学", forms).contains((0, 2)),
        // voiced after another
        KanjiReadings.spanOf("会社", "かいしゃ", "社", forms).contains((2, 4)),
        // kana in the writing are themselves
        KanjiReadings.spanOf("会う", "あう", "会", forms).contains((0, 1)),
        KanjiReadings.spanOf("先生", "あいうえ", "先", forms).isEmpty
      )
    }
    test("a slip elsewhere in the word is not a slip on the kanji") {
      assert(
        KanjiReadings.partRight("せんせい", "せんせり", (0, 2)),
        !KanjiReadings.partRight("せんせい", "せしせい", (0, 2)),
        !KanjiReadings.partRight("せんせい", "せんせり", (2, 4)),
        // a letter added inside the kanji's part
        !KanjiReadings.partRight("せんせい", "せえんせい", (0, 2)),
        // a letter missing after it
        KanjiReadings.partRight("せんせい", "せんせ", (0, 2)),
        // nothing typed
        !KanjiReadings.partRight("せんせい", "", (0, 2)),
        // in either script
        KanjiReadings.partRight("せんせい", "センセリ", (0, 2))
      )
    }
  }
}
