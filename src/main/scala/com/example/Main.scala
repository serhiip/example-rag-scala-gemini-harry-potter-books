package com.example

import cats.effect.*
import com.example.ai.{VertexAI, VertexAIConfig}
import com.example.data.ResourceLoader
import com.example.processing.TextProcessor
import cats.implicits.*
import scala.sys.env

object Main extends IOApp.Simple {

  def run: IO[Unit] = {
    for {
      projectId <- env.get("GOOGLE_PROJECT_ID") match {
        case Some(id) => IO.pure(id)
        case None =>
          IO.raiseError(
            new RuntimeException(
              "GOOGLE_PROJECT_ID environment variable not set. Please set it and run `gcloud auth application-default login`."
            )
          )
      }
      config = VertexAIConfig(projectId, "europe-west1")
      _ <- IO.println(s"Using Google Cloud project: ${config.projectId}")

      bookName <- ResourceLoader.listBookFiles().map(_.head) // Take first book
      lines <- TextProcessor
        .linesStream(s"books/$bookName", bookName)
        .take(5)
        .compile
        .toList

      _ <- IO.println(
        s"\nGetting embeddings for ${lines.length} lines from '$bookName'..."
      )

      linesWithEmbeddings <- VertexAI.getEmbeddings(lines, config)

      _ <- IO.println("\nSuccessfully generated embeddings:")
      _ <- linesWithEmbeddings.traverse_ { lwe =>
        IO.println(
          s"  - [L${lwe.line.number}] '${lwe.line.text
              .take(50)}...' -> Embedding(size=${lwe.embedding.length}, data=[${lwe.embedding.take(3).mkString(", ")}...])"
        )
      }
    } yield ()
  }
}
