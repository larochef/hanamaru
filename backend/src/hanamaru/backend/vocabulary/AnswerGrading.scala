package hanamaru.backend.vocabulary

import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kana.*
import hanamaru.shared.vocabulary.Verdict
import hanamaru.shared.writing.*

import java.text.Normalizer

/** Judges typed answers by code alone: a word typed in Japanese against its
  * forms, a meaning typed in French (or English) against its glosses.
  */
object AnswerGrading {

  /** A word typed for its meaning: right when it is one of the word's forms (in
    * either kana script), nearly right when only a long vowel, a small つ or a
    * voiced つ/ち tell it apart, another word when it is one sharing a meaning
    * with it.
    */
  def production(
      typed: String,
      word: DictionaryWord,
      withForm: String => List[DictionaryWord]
  ): Verdict = {
    val answer = Romaji
      .asHiragana(Romaji.toKana(typed.trim, finished = true))
      .filterNot(_.isWhitespace)
    val readings = word.readings.map(Romaji.asHiragana)
    if (answer.isEmpty) {
      Verdict.Wrong
    } else if (readings.contains(answer) || word.writings.contains(answer)) {
      Verdict.Right
    } else if (readings.exists(reading => loose(reading) == loose(answer))) {
      Verdict.NearMiss
    } else {
      val meanings = glosses(word).toSet
      withForm(answer)
        .find(other => other.id != word.id && glosses(other).exists(meanings))
        .fold(Verdict.Wrong)(_ => Verdict.OtherWord(answer))
    }
  }

  /** A word written with the stylus, character by character, as its first
    * reading: right when every character was written to its end with every
    * stroke accepted; nearly, when only a stroke's order or direction was
    * wrong; missed when a character was not written, or a stroke was not the
    * one expected.
    */
  def handwritten(
      written: List[WritingAttempt],
      word: DictionaryWord
  ): Verdict = {
    val expected = word.readings.headOption.toList.flatMap(characters)
    val outcomes = written.map(WritingCorrection.outcome)
    if (
      written
        .map(_.character) != expected || outcomes.contains(WritingOutcome.Wrong)
    ) {
      Verdict.Wrong
    } else if (outcomes.forall(_ == WritingOutcome.Right)) {
      Verdict.Right
    } else {
      Verdict.NearMiss
    }
  }

  /** A text's characters, each a whole code point. */
  def characters(text: String): List[String] =
    text.codePoints().toArray.toList.map(Character.toString)

  /** A meaning typed for a word: right when it is one of its glosses, French or
    * English, whatever the accents, articles and words in brackets.
    */
  def meaning(typed: String, word: DictionaryWord): Boolean = {
    val answer = french(typed)
    answer.nonEmpty && glosses(word).contains(answer)
  }

  /** A word's glosses, each part between commas apart, made comparable. */
  def glosses(word: DictionaryWord): List[String] =
    word.meanings
      .flatMap(_.glosses)
      .flatMap(_.split("[,;/]"))
      .map(french)
      .filter(_.nonEmpty)

  private val articles =
    """^(?:(?:le|la|les|un|une|des|du|de la|de|se|to|be|a|an|the|etre|être) +|l'|d'|s')+""".r

  /** A meaning made comparable: lower case, without accents, without what is in
    * brackets, without a leading article or "to".
    */
  def french(text: String): String = {
    val bare = Normalizer
      .normalize(text.toLowerCase.trim, Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .replaceAll("\\([^)]*\\)", " ")
      .replaceAll("[^\\p{L}\\p{N}' -]", " ")
      .replaceAll("\\s+", " ")
      .trim
    articles.replaceFirstIn(bare, "").trim
  }

  // the vowel each hiragana ends in
  private val vowelOf: Map[Char, Char] =
    Kana.all
      .filter(kana =>
        kana.script == Script.Hiragana && kana.character.length == 1
      )
      .map(kana => kana.character.head -> kana.romaji.last)
      .toMap ++ Map('ぁ' -> 'a', 'ぃ' -> 'i', 'ぅ' -> 'u', 'ぇ' -> 'e', 'ぉ' -> 'o')

  private val vowels =
    Map('あ' -> 'a', 'い' -> 'i', 'う' -> 'u', 'え' -> 'e', 'お' -> 'o')

  // a word without what a near miss gets wrong: small つ, long vowels, ー,
  // づ and ぢ for ず and じ
  private def loose(text: String): String = {
    val out = StringBuilder()
    var previous = Option.empty[Char]
    for (character <- text) {
      val kept = character match {
        case 'っ' | 'ー'                       => None
        case 'づ'                             => Some('ず')
        case 'ぢ'                             => Some('じ')
        case 'を'                             => Some('お')
        case vowel if vowels.contains(vowel) =>
          val sound = vowels(vowel)
          val lengthens = previous.contains(sound) ||
            (previous.contains('o') && sound == 'u') ||
            (previous.contains('e') && sound == 'i')
          Option.unless(lengthens)(vowel)
        case other => Some(other)
      }
      kept.foreach { kana =>
        out += kana
        previous = vowelOf.get(kana)
      }
    }
    out.result()
  }
}
