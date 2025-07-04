package com.example.processing

import cats.effect.IO
import fs2.{Stream, text}
import fs2.io.file.{Files, Path}

object EmbeddingStore {

  private val embeddingsDir = Path("src/main/resources/embeddings")

  private def createEmbeddingsDir: IO[Unit] =
    Files[IO].createDirectories(embeddingsDir)

  /** Writes a stream of LineWithEmbedding to a file in the resources folder.
    * The format for each line in the file is:
    * source_file|line_number|embedding_vector
    *
    * @param embeddingsStream
    *   The stream of LineWithEmbedding to write.
    * @param targetFileName
    *   The name of the file to save the embeddings in.
    * @return
    *   An IO operation that completes when the file is written.
    */
  def writeEmbeddings(
      embeddingsStream: Stream[IO, LineWithEmbedding],
      targetFileName: String
  ): IO[Unit] = {
    val targetPath = embeddingsDir.resolve(targetFileName)

    val linesStream: Stream[IO, String] = embeddingsStream.map { lwe =>
      val embeddingStr = lwe.embedding.mkString(",")
      s"${lwe.line.source}|${lwe.line.number}|$embeddingStr\n"
    }

    (Stream.eval(createEmbeddingsDir) ++
      linesStream
        .through(text.utf8.encode)
        .through(Files[IO].writeAll(targetPath))).compile.drain
  }

  /** Reads an embedding file and reconstructs the LineWithEmbedding stream by
    * cross-referencing with the original book files.
    *
    * @param embeddingFileName
    *   The name of the file to read from.
    * @return
    *   A stream of reconstructed LineWithEmbedding objects.
    */
  def readEmbeddings(
      embeddingFileName: String
  ): Stream[IO, LineWithEmbedding] = {
    val embeddingPath = embeddingsDir.resolve(embeddingFileName)

    val parsedEmbeddingsStream: Stream[IO, (String, Int, Vector[Float])] =
      Files[IO]
        .readAll(embeddingPath)
        .through(text.utf8.decode)
        .through(text.lines)
        .filter(_.trim.nonEmpty)
        .map { lineStr =>
          val parts = lineStr.split('|')

          if (parts.length != 3) {
            throw new RuntimeException(s"Invalid embedding format: $lineStr")
          }

          val sourceFile = parts(0)
          val lineNumber = parts(1).toInt
          val embedding = parts(2).split(',').map(_.toFloat).toVector
          (sourceFile, lineNumber, embedding)
        }

    Stream.eval(parsedEmbeddingsStream.compile.toList).flatMap {
      allEmbeddings =>
        val groupedBySource = allEmbeddings.groupBy(_._1)

        Stream.emits(groupedBySource.toSeq).flatMap {
          case (sourceFile, embeddingsForSource) =>
            val bookResourcePath = s"books/$sourceFile"
            val linesMapStream = Stream.eval(
              TextProcessor
                .linesStream(bookResourcePath, sourceFile)
                .map(line => line.number -> line.text)
                .compile
                .toList
                .map(_.toMap)
            )

            linesMapStream.flatMap { linesMap =>
              Stream.emits(embeddingsForSource).map {
                case (_, lineNum, embeddingVector) =>
                  val lineText = linesMap.getOrElse(lineNum, "")
                  val originalLine = Line(lineText, lineNum, sourceFile)
                  LineWithEmbedding(originalLine, embeddingVector)
              }
            }
        }
    }
  }
}
