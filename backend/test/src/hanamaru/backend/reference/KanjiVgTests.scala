package hanamaru.backend.reference

import hanamaru.shared.writing.Point

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import utest.*

object KanjiVgTests extends TestSuite {

  // Laid out as KanjiVG's files are, with invented strokes: a DTD on w3.org
  // with an internal subset, strokes in a nested group, out of order.
  private val svg =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.0//EN" "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd" [
      |<!ATTLIST g
      |xmlns:kvg CDATA #FIXED "http://kanjivg.tagaini.net"
      |kvg:element CDATA #IMPLIED >
      |<!ATTLIST path
      |xmlns:kvg CDATA #FIXED "http://kanjivg.tagaini.net"
      |kvg:type CDATA #IMPLIED >
      |]>
      |<svg xmlns="http://www.w3.org/2000/svg" width="109" height="109" viewBox="0 0 109 109" xmlns:kvg="https://kanjivg.tagaini.net/">
      |<g id="kvg:StrokePaths_0304c" style="fill:none;stroke:#000000;">
      |<g id="kvg:0304c" kvg:element="が">
      |  <path id="kvg:0304c-s2" d="M2,2c1,1,2,2,3,3"/>
      |  <g id="kvg:0304c-g1">
      |    <path id="kvg:0304c-s1" d="M1,1c1,1,2,2,3,3"/>
      |  </g>
      |  <path id="kvg:0304c-s10" d="M10,10c1,1,2,2,3,3"/>
      |</g>
      |</g>
      |<g id="kvg:StrokeNumbers_0304c" style="font-size:8;fill:#808080">
      |  <text transform="matrix(1 0 0 1 16.75 37.38)">1</text>
      |  <text transform="matrix(1 0 0 1 39.75 16)">2</text>
      |  <text transform="matrix(1 0 0 1 70 29.75)">10</text>
      |</g>
      |</svg>
      |""".stripMargin

  val tests: Tests = Tests {
    test("the strokes in the order they are written, with their numbers") {
      val strokes = KanjiVg.strokes(
        ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))
      )
      assert(
        strokes.map(_.path) == List(
          "M1,1c1,1,2,2,3,3",
          "M2,2c1,1,2,2,3,3",
          "M10,10c1,1,2,2,3,3"
        )
      )
      assert(strokes.head.label == Point(16.75, 37.38))
      assert(strokes.last.label == Point(70, 29.75))
    }
    test("a file per single character, named by its code point") {
      assert(KanjiVg.entryOf("が").contains("kanji/0304c.svg"))
      assert(KanjiVg.entryOf("𠮟").contains("kanji/20b9f.svg"))
      assert(KanjiVg.entryOf("がが").isEmpty)
      assert(KanjiVg.entryOf("").isEmpty)
      assert(KanjiVg.entryOf("../x").isEmpty)
    }
  }
}
