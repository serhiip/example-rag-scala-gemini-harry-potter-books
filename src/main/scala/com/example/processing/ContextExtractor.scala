package com.example.processing

import cats.effect.IO

object ContextExtractor {

  def getContext(
      bookName: String,
      lineNumber: Int,
      contextSize: Int
  ): IO[String] = {
    val resourcePath = s"books/$bookName"

    val startLine = (lineNumber - contextSize).max(1)
    val endLine = lineNumber + contextSize

    TextProcessor
      .linesStream(resourcePath, bookName)
      .filter(line => line.number >= startLine && line.number <= endLine)
      .map(_.text)
      .compile
      .toList
      .map(_.mkString("\n"))
  }
}
