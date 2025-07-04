package com.example

import cats.effect.*
import com.example.ai.{VertexAI, VertexAIConfig}
import com.example.data.ResourceLoader
import com.example.processing.{EmbeddingStore, TextProcessor}
import cats.implicits.*
import scala.sys.process.*

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
      config = VertexAIConfig(projectId, "europe-west2")
      _ <- IO.println(s"Using Google Cloud project: $projectId")

      bookName <- ResourceLoader.listBookFiles().map(_.head)
      _ <- IO.println(s"Processing book: $bookName")

      embeddingStream = TextProcessor
        .linesStream(s"books/$bookName", bookName)
        .chunkN(250)
        .evalMap { chunk =>
          for {
            _ <- IO.println(s"Processing chunk of ${chunk.size} lines...")
            embeddings <- VertexAI.getEmbeddings(chunk.toList, config)
          } yield embeddings
        }
        .flatMap(fs2.Stream.emits)

      embeddingFile = s"${bookName}.embeddings"
      _ <- IO.println(s"\nWriting embeddings to '$embeddingFile'...")
      _ <- EmbeddingStore.writeEmbeddings(embeddingStream, embeddingFile)
      _ <- IO.println("Embeddings written successfully.")

    } yield ()
  }
}
