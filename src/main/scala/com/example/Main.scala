package com.example

import cats.effect.*
import com.example.ai.{VertexAI, VertexAIConfig}
import com.example.db.{Database, EmbeddingRepository, PostgresContainer}
import com.example.processing.EmbeddingStore
import cats.implicits.*
import com.pgvector.PGvector
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
    PostgresContainer.resource.use { dbConfig =>
      Database.resource(dbConfig).use { conn =>
        val repository = EmbeddingRepository.make(conn)

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

          _ <- IO.println("\n--- Starting RAG Demo ---")

          query = "Who is Harry Potter?"
          _ <- IO.println(s"Query: '$query'")

          queryEmbedding <- VertexAI
            .getEmbeddings(
              List(com.example.processing.Line(query, 1, "query")),
              config
            )
            .map(_.head.embedding)
          queryVector = new PGvector(queryEmbedding.toArray)

          _ <- IO.println("\nPerforming similarity search...")
          searchResults <- repository.search(queryVector, limit = 10)

          _ <- IO.println("Search results:")
          _ <- searchResults.traverse_ { result =>
            IO.println(
              s"  - [L${result.line.number} in ${result.line.source}] ${result.line.text}"
            )
          }
        } yield ()
      }
    }
  }
}
