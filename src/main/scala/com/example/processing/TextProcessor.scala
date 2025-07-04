package com.example.processing

import cats.effect.IO
import fs2.Stream
import fs2.text

/** Represents a single line of text from a source file.
  * @param text
  *   The content of the line.
  * @param number
  *   The line number in the source file (1-based).
  * @param source
  *   The name of the source file.
  */
case class Line(text: String, number: Int, source: String)

object TextProcessor {

  /** Reads a resource file and streams its content as Lines, each with its
    * corresponding line number.
    *
    * @param resourcePath
    *   The path to the resource file (e.g., "books/01 Harry Potter and the
    *   Sorcerers Stone.txt").
    * @return
    *   A stream of Line objects.
    */
  def linesStream(
      resourcePath: String,
      sourceName: String
  ): Stream[IO, Line] = {
    val stream = fs2.io.readInputStream(
      IO.blocking(
        Option(getClass.getClassLoader.getResourceAsStream(resourcePath))
          .getOrElse(
            throw new RuntimeException(s"Resource not found: $resourcePath")
          )
      ),
      chunkSize = 4096
    )

    stream
      .through(text.utf8.decode)
      .through(text.lines)
      .filter(_.trim.nonEmpty)
      .zipWithIndex
      .map { case (lineText, index) =>
        Line(lineText, (index + 1).toInt, sourceName)
      }
  }
}
