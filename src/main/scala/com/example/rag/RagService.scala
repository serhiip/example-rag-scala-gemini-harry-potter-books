package com.example.rag

import cats.effect.IO
import cats.implicits.*
import com.example.ai.{GenerativeAI, VertexAI, VertexAIConfig}
import com.example.db.EmbeddingRepository
import com.example.processing.{ContextExtractor, Line}
import com.pgvector.PGvector

class RagService(
    embeddingRepository: EmbeddingRepository
) {

  def ask(query: String, config: VertexAIConfig): IO[String] = {
    for {
      queryEmbedding <- VertexAI
        .getEmbeddings(List(Line(query, 0, "query")), config)
        .map(_.head.embedding)
      queryVector = new PGvector(queryEmbedding.toArray)

      searchResults <- embeddingRepository.search(queryVector, limit = 15)
      contexts <- searchResults.traverse { result =>
        ContextExtractor.getContext(
          result.line.source,
          result.line.number,
          contextSize = 5
        )
      }

      contextString = contexts.mkString("\n\n---\n\n")
      prompt = s"""
        Based on the following context, please answer the question.

        Context:
        ---
        $contextString
        ---

        Question: $query
      """.stripMargin

      answer <- GenerativeAI.generateAnswer(prompt, config)
    } yield answer
  }
}
