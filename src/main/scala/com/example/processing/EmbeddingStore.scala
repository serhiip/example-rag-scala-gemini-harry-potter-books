package com.example.processing

import cats.effect.kernel.Async
import cats.syntax.all.*
import fs2.{Stream, text}
import fs2.io.file.{Files, Path}

trait EmbeddingStore[F[_]] {
  def listEmbeddingFiles(): F[List[String]]
  def writeEmbeddings(embeddingsStream: Stream[F, LineWithEmbedding], targetFileName: String): F[Unit]
  def readEmbeddings(embeddingFileName: String): Stream[F, LineWithEmbedding]
}

class EmbeddingStoreImpl[F[_]: Async: Files](textProcessor: TextProcessor[F]) extends EmbeddingStore[F] {

  private val embeddingsDir = Path("src/main/resources/embeddings")

  private def createEmbeddingsDir: F[Unit] =
    Files[F].createDirectories(embeddingsDir)

  override def listEmbeddingFiles(): F[List[String]] =
    Files[F]
      .list(embeddingsDir)
      .map(_.fileName.toString)
      .filter(_.endsWith(".embeddings"))
      .compile
      .toList

  override def writeEmbeddings(
      embeddingsStream: Stream[F, LineWithEmbedding],
      targetFileName: String
  ): F[Unit] = {
    val targetPath = embeddingsDir.resolve(targetFileName)

    val linesStream: Stream[F, String] = embeddingsStream.map { lwe =>
      val embeddingStr = lwe.embedding.mkString(",")
      s"${lwe.line.source}|${lwe.line.number}|$embeddingStr\n"
    }

    (Stream.eval(createEmbeddingsDir) ++
      linesStream
        .through(text.utf8.encode)
        .through(Files[F].writeAll(targetPath))).compile.drain
  }

  override def readEmbeddings(
      embeddingFileName: String
  ): Stream[F, LineWithEmbedding] = {
    val embeddingPath = embeddingsDir.resolve(embeddingFileName)

    val parsedEmbeddingsStream: Stream[F, (String, Int, Vector[Float])] =
      Files[F]
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
          val embedding  = parts(2).split(',').map(_.toFloat).toVector
          (sourceFile, lineNumber, embedding)
        }

    Stream.eval(parsedEmbeddingsStream.compile.toList).flatMap { allEmbeddings =>
      val groupedBySource = allEmbeddings.groupBy(_._1)

      Stream.emits(groupedBySource.toSeq).flatMap { case (sourceFile, embeddingsForSource) =>
        val bookResourcePath = s"books/$sourceFile"
        val linesMapStream   = Stream.eval(
          textProcessor
            .linesStream(bookResourcePath, sourceFile)
            .map(line => line.number -> line.text)
            .compile
            .toList
            .map(_.toMap)
        )

        linesMapStream.flatMap { linesMap =>
          Stream.emits(embeddingsForSource).map { case (_, lineNum, embeddingVector) =>
            val lineText     = linesMap.getOrElse(lineNum, "")
            val originalLine = Line(lineText, lineNum, sourceFile)
            LineWithEmbedding(originalLine, embeddingVector)
          }
        }
      }
    }
  }
}

object EmbeddingStore {
  def apply[F[_]: Async: Files](textProcessor: TextProcessor[F]): EmbeddingStore[F] = new EmbeddingStoreImpl[F](textProcessor)
}
