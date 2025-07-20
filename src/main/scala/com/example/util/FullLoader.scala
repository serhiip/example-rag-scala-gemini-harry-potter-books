package com.example.util

import cats.effect.Sync
import cats.implicits.*
import com.example.ai.{VertexAI, VertexAIConfig}
import com.example.data.ResourceLoader
import com.example.processing.{EmbeddingStore, TextProcessor}

trait FullLoader[F[_]] {
  def load(config: VertexAIConfig): F[Unit]
}

class FullLoaderImpl[F[_]: Sync](
    resourceLoader: ResourceLoader[F],
    embeddingStore: EmbeddingStore[F],
    textProcessor: TextProcessor[F],
    vertexAI: VertexAI[F]
) extends FullLoader[F] {

  override def load(config: VertexAIConfig): F[Unit] = {
    for {
      bookFiles              <- resourceLoader.listBookFiles()
      existingEmbeddingFiles <- embeddingStore.listEmbeddingFiles()

      booksToProcess = bookFiles.filterNot(book => existingEmbeddingFiles.contains(s"$book.embeddings"))

      _ <- Sync[F].delay(println(s"Found ${booksToProcess.length} books to process."))

      _ <- booksToProcess.traverse_ { bookName =>
             for {
               _ <- Sync[F].delay(println(s"Processing book: $bookName"))

               embeddingStream = textProcessor
                                   .linesStream(s"books/$bookName", bookName)
                                   .chunkN(100)
                                   .evalMap { chunk =>
                                     for {
                                       _          <- Sync[F].delay(println(s"Processing chunk of ${chunk.size} lines..."))
                                       embeddings <- vertexAI.getEmbeddings(chunk.toList, config)
                                     } yield embeddings
                                   }
                                   .flatMap(fs2.Stream.emits)

               embeddingFile = s"${bookName}.embeddings"
               _            <- Sync[F].delay(println(s"\nWriting embeddings to '$embeddingFile'..."))
               _            <- embeddingStore.writeEmbeddings(embeddingStream, embeddingFile)
               _            <- Sync[F].delay(println(s"Finished writing embeddings for '$bookName'."))
             } yield ()
           }
    } yield ()
  }
}

object FullLoader {
  def apply[F[_]: Sync](
      resourceLoader: ResourceLoader[F],
      embeddingStore: EmbeddingStore[F],
      textProcessor: TextProcessor[F],
      vertexAI: VertexAI[F]
  ): FullLoader[F] = new FullLoaderImpl[F](resourceLoader, embeddingStore, textProcessor, vertexAI)
}
