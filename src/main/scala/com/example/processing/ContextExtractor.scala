package com.example.processing

import cats.effect.Sync
import cats.syntax.all.*

trait ContextExtractor[F[_]] {
  def getContext(bookName: String, lineNumber: Int, contextSize: Int): F[String]
}

class ContextExtractorImpl[F[_]: Sync](textProcessor: TextProcessor[F]) extends ContextExtractor[F] {
  override def getContext(bookName: String, lineNumber: Int, contextSize: Int): F[String] = {
    val resourcePath = s"books/$bookName"

    val startLine = (lineNumber - contextSize).max(1)
    val endLine   = lineNumber + contextSize

    textProcessor
      .linesStream(resourcePath, bookName)
      .filter(line => line.number >= startLine && line.number <= endLine)
      .map(_.text)
      .compile
      .toList
      .map(_.mkString("\n"))
  }
}

object ContextExtractor {
  def apply[F[_]: Sync](textProcessor: TextProcessor[F]): ContextExtractor[F] = new ContextExtractorImpl[F](textProcessor)
}
