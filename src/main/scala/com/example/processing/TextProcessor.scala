package com.example.processing

import cats.effect.Sync
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

case class LineWithEmbedding(line: Line, embedding: Vector[Float])

trait TextProcessor[F[_]] {
  def linesStream(resourcePath: String, sourceName: String): Stream[F, Line]
}

class TextProcessorImpl[F[_]: Sync] extends TextProcessor[F] {
  override def linesStream(resourcePath: String, sourceName: String): Stream[F, Line] = {
    val stream = fs2.io.readInputStream(
      Sync[F].blocking(
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

object TextProcessor {
  def apply[F[_]: Sync](): TextProcessor[F] = new TextProcessorImpl[F]()
}
