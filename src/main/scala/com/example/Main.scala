package com.example

import cats.effect.*
import com.example.ai.{VertexAI, VertexAIConfig}
import com.example.data.ResourceLoader
import com.example.processing.{EmbeddingStore, TextProcessor}
import cats.implicits.*
import fs2.Stream
import scala.sys.process._

object Main extends IOApp.Simple {

  private def getGoogleProject: IO[String] = IO.blocking {
    val projectId = "gcloud config get-value project".!!.trim
    if (projectId.isEmpty) {
      throw new RuntimeException(
        "No Google Cloud project is configured. Please run `gcloud config set project YOUR_PROJECT_ID`."
      )
    }
    projectId
  }

  def run: IO[Unit] = {
    for {
      projectId <- getGoogleProject
      config = VertexAIConfig(projectId, "europe-west1")
      _ <- IO.println(s"Using Google Cloud project: ${config.projectId}")

      bookName <- ResourceLoader.listBookFiles().map(_.head)
      lines <- TextProcessor
        .linesStream(s"books/$bookName", bookName)
        .take(5)
        .compile
        .toList

      _ <- IO.println(
        s"\nGetting embeddings for ${lines.length} lines from '$bookName'..."
      )
      linesWithEmbeddings <- VertexAI.getEmbeddings(lines, config)

      embeddingFile = "test-embeddings.txt"
      _ <- IO.println(s"\nWriting embeddings to '$embeddingFile'...")
      _ <- EmbeddingStore.writeEmbeddings(
        Stream.emits(linesWithEmbeddings),
        embeddingFile
      )

      _ <- IO.println(s"\nReading embeddings from '$embeddingFile'...")
      readEmbeddings <- EmbeddingStore
        .readEmbeddings(embeddingFile)
        .compile
        .toList

      _ <- IO.println("\nSuccessfully read and reconstructed embeddings:")
      _ <- readEmbeddings.traverse_ { lwe =>
        IO.println(s"  - [L${lwe.line.number}] '${lwe.line.text
            .take(50)}...' -> Embedding(size=${lwe.embedding.length})")
      }
    } yield ()
  }
}
