package com.example

import cats.effect.*
import com.example.ai.{GenerativeAI, VertexAIConfig}
import com.example.db.{Database, EmbeddingRepository, PostgresContainer}
import com.example.rag.RagService
import com.example.util.FullLoader
import scala.sys.process.*
import com.example.processing.EmbeddingStore
import cats.syntax.all.*
import com.example.data.*
import com.example.processing.*
import com.example.ai.VertexAI

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
      ragService: RagService[IO]
  ): IO[Unit] = {
    for {
      _      <- IO.println("Executing Query")
      answer <- ragService.ask(query, config)
      _      <- IO.println(s"\nAnswer:\n$answer")
    } yield ()
  }

  def run: IO[Unit] = {
    PostgresContainer.resource.flatMap { dbConfig =>
      Database.resource(dbConfig).flatMap { dataSource =>
        for {
          projectId     <- getGoogleProject.toResource
          repository     = EmbeddingRepository.make[IO](dataSource)
          resourceLoader = ResourceLoader[IO]()
          embeddingStore = EmbeddingStore[IO](TextProcessor[IO]())
          textProcessor  = TextProcessor[IO]()
          vertexAI       = VertexAI[IO]()
          fullLoader     = FullLoader[IO](resourceLoader, embeddingStore, textProcessor, vertexAI)
          generativeAI  <- GenerativeAI[IO](VertexAIConfig(projectId, "europe-west2"))
          config         = VertexAIConfig(projectId, "europe-west2")
          _             <- IO.println(s"Using Google Cloud project: $projectId").toResource

          _ <- fullLoader.load(config).toResource

          embeddingFiles <- embeddingStore.listEmbeddingFiles().toResource
          _              <-
            (if (embeddingFiles.isEmpty)
               IO.println("No embedding files found, skipping database load.")
             else {
               IO.println(
                 s"Found ${embeddingFiles.length} embedding files to process."
               ) >>
                 embeddingFiles.traverse_ { file =>
                   for {
                     _          <- IO.println(s"Loading embeddings from '$file'...")
                     embeddings <- embeddingStore.readEmbeddings(file).compile.toList
                     _          <- IO.println(
                                     s"Saving ${embeddings.length} embeddings to the database..."
                                   )
                     _          <- repository.saveAll(embeddings)
                     _          <- IO.println(s"Finished processing '$file'.")
                   } yield ()
                 }
             }).toResource

          _ <- IO.println("\n--- Init done ---").toResource

          contextExtractor = ContextExtractor[IO](textProcessor)
          ragService       = RagService[IO](repository, generativeAI, vertexAI, contextExtractor)

          loop = for {
                   _     <- IO.println("Enter your query and press <ENTER>")
                   query <- IO.readLine
                   _     <- runQuery(query, config, ragService)
                 } yield ()

          _ <- loop.foreverM.toResource
        } yield ()
      }
    }.use_
  }
}
