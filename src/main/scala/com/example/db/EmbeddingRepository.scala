package com.example.db

import cats.effect.IO
import com.example.processing.LineWithEmbedding
import com.pgvector.PGvector
import java.sql.Connection

trait EmbeddingRepository {
  def saveAll(embeddings: List[LineWithEmbedding]): IO[Unit]
  def search(query: PGvector, limit: Int = 5): IO[List[LineWithEmbedding]]
}

object EmbeddingRepository {
  def make(conn: Connection): EmbeddingRepository = new EmbeddingRepository {
    override def saveAll(embeddings: List[LineWithEmbedding]): IO[Unit] =
      IO.blocking {
        PGvector.addVectorType(conn)
        // val sql =
        //  "INSERT INTO embeddings (source, line_number, text, embedding) VALUES (?, ?, ?, ?)"
//        val statement = conn.prepareStatement(sql)

        val statement = conn.prepareStatement(
          "INSERT INTO embeddings (source, line_number, text, embedding) VALUES (?, ?, ?, ?)"
        )
        try {
          conn.setAutoCommit(false)
          embeddings.foreach { lwe =>
            statement.setString(1, lwe.line.source)
            statement.setInt(2, lwe.line.number)
            statement.setString(3, lwe.line.text)
            statement.setObject(4, new PGvector(lwe.embedding.toArray))
            statement.addBatch()
          }
          statement.executeBatch()
          conn.commit()
        } finally {
          statement.close()
          conn.setAutoCommit(true)
        }
      }

    override def search(
        query: PGvector,
        limit: Int
    ): IO[List[LineWithEmbedding]] =
      IO.blocking {
        val sql =
          "SELECT source, line_number, text, embedding FROM embeddings ORDER BY embedding <-> ? LIMIT ?"
        val statement = conn.prepareStatement(sql)
        try {
          statement.setObject(1, query)
          statement.setInt(2, limit)
          val rs = statement.executeQuery()
          val results =
            new scala.collection.mutable.ListBuffer[LineWithEmbedding]
          while (rs.next()) {
            val source = rs.getString("source")
            val lineNumber = rs.getInt("line_number")
            val text = rs.getString("text")
            val embedding = rs.getObject("embedding").asInstanceOf[PGvector]
            val line = com.example.processing.Line(text, lineNumber, source)
            results += LineWithEmbedding(line, embedding.toArray.toVector)
          }
          results.toList
        } finally {
          statement.close()
        }
      }
  }
}
