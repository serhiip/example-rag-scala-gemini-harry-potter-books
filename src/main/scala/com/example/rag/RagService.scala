package com.example.rag

import cats.effect.Sync
import cats.implicits.*
import com.example.ai.{GenerativeAI, VertexAI, VertexAIConfig}
import com.example.db.EmbeddingRepository
import com.example.processing.{ContextExtractor, Line}
import com.pgvector.PGvector

class RagService[F[_]: Sync](
    embeddingRepository: EmbeddingRepository[F],
    generativeAI: GenerativeAI[F],
    vertexAI: VertexAI[F],
    contextExtractor: ContextExtractor[F]
) {

  def ask(query: String, config: VertexAIConfig): F[String] = {
    for {
      queryEmbedding <- vertexAI
                          .getEmbeddings(List(Line(query, 0, "query")), config)
                          .map(_.head.embedding)
      queryVector     = new PGvector(queryEmbedding.toArray)

      searchResults <- embeddingRepository.search(queryVector, limit = 15)
      contexts      <- searchResults.traverse { result =>
                         contextExtractor.getContext(
                           result.line.source,
                           result.line.number,
                           contextSize = 20
                         )
                       }

      contextString = contexts.mkString("\n\n---\n\n")
      prompt        = s"""
        Based on the following context, please answer the question.

        Context:
        ---
        $contextString
        ---

        Question: $query
      """.stripMargin

      answer <- generativeAI.generateAnswer(prompt)
    } yield answer
  }
}
