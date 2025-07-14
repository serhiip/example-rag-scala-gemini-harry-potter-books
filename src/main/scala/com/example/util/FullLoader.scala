package com.example.util

import cats.effect.IO
import cats.implicits.*
import com.example.ai.{VertexAI, VertexAIConfig}
import com.example.data.ResourceLoader
import com.example.processing.{EmbeddingStore, TextProcessor}

object FullLoader {

  def load(config: VertexAIConfig): IO[Unit] = {
    for {
      bookFiles <- ResourceLoader.listBookFiles()
      existingEmbeddingFiles <- EmbeddingStore.listEmbeddingFiles()

      booksToProcess = bookFiles.filterNot(book =>
        existingEmbeddingFiles.contains(s"$book.embeddings")
      )

      _ <- IO.println(s"Found ${booksToProcess.length} books to process.")

      _ <- booksToProcess.traverse_ { bookName =>
        for {
          _ <- IO.println(s"Processing book: $bookName")

          embeddingStream = TextProcessor
            .linesStream(s"books/$bookName", bookName)
            .chunkN(100)
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
          _ <- IO.println(s"Finished writing embeddings for '$bookName'.")
        } yield ()
      }
    } yield ()
  }
}
