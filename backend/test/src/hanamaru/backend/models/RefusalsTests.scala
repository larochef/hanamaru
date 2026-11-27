package hanamaru.backend.models

import hanamaru.backend.storage.Database

import java.nio.file.Files

import utest.*

object RefusalsTests extends TestSuite {

  val tests: Tests = Tests {
    test("a refusal kept: what it was for, its sentences, its problems") {
      val directory = Files.createTempDirectory("hanamaru-refusals")
      val database = Database.open(directory.resolve("learner.db"))
      try {
        Refusals(database, "a model").kept(
          WrittenFor.LessonText,
          "wa",
          List("いみの ひとは だれですか。" -> "Qui est la personne du sens ?"),
          List("first problem", "second problem")
        )
        val kept = database.read(
          _.query("SELECT * FROM refused_sentence")(row =>
            (
              row.string("written_for"),
              row.string("asked"),
              row.string("french"),
              row.string("problems"),
              row.string("written_by")
            )
          )
        )
        assert(
          kept == List(
            (
              "lesson-text",
              "wa",
              "Qui est la personne du sens ?",
              "first problem\nsecond problem",
              "a model"
            )
          )
        )
      } finally database.close()
    }
  }
}
