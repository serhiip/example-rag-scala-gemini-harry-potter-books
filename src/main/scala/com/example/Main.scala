package com.example

import cats.effect.*
import com.example.ai.VertexAIConfig
import com.example.db.{Database, EmbeddingRepository, PostgresContainer}
import com.example.rag.RagService
import scala.sys.process.*
import com.example.processing.EmbeddingStore
import cats.syntax.all.*

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

  def runQuery(
      query: String,
      config: VertexAIConfig,
      ragService: RagService
  ): IO[Unit] = {
    for {
      _ <- IO.println("Executing Query")
      answer <- ragService.ask(query, config)
      _ <- IO.println(s"\nAnswer:\n$answer")
    } yield ()
  }

  def run: IO[Unit] = {
    PostgresContainer.resource.use { dbConfig =>
      Database.resource(dbConfig).use { dataSource =>
        val repository = EmbeddingRepository.make(dataSource)
        val ragService = new RagService(repository)

        for {
          projectId <- getGoogleProject
          config = VertexAIConfig(projectId, "europe-west2")
          _ <- IO.println(s"Using Google Cloud project: $projectId")

          embeddingFiles <- EmbeddingStore.listEmbeddingFiles()
          _ <- IO.println(
            s"Found ${embeddingFiles.length} embedding files to process."
          )
          _ <- embeddingFiles.traverse_ { file =>
            for {
              _ <- IO.println(s"Loading embeddings from '$file'...")
              embeddings <- EmbeddingStore.readEmbeddings(file).compile.toList
              _ <- IO.println(
                s"Saving ${embeddings.length} embeddings to the database..."
              )
              _ <- repository.saveAll(embeddings)
              _ <- IO.println(s"Finished processing '$file'.")
            } yield ()
          }

          _ <- IO.println("\n--- Init done ---")

          loop = for {
            _ <- IO.println("Enter your query and press <ENTER>")
            query <- IO.readLine
            _ <- runQuery(query, config, ragService)
          } yield ()

          _ <- loop.foreverM
        } yield ()
      }
    }
  }
}
