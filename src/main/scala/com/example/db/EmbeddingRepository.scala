package com.example.db

import com.example.processing.LineWithEmbedding
import com.pgvector.PGvector
import java.sql.Connection
import cats.effect.kernel.Sync

trait EmbeddingRepository[F[_]] {
  def saveAll(embeddings: List[LineWithEmbedding]): F[Unit]
  def search(query: PGvector, limit: Int = 5): F[List[LineWithEmbedding]]
}

class EmbeddingRepositoryImpl[F[_]: Sync](conn: Connection) extends EmbeddingRepository[F] {
  override def saveAll(embeddings: List[LineWithEmbedding]): F[Unit] =
    Sync[F].blocking {
      PGvector.addVectorType(conn)
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
  ): F[List[LineWithEmbedding]] =
    Sync[F].blocking {
      val sql       =
        "SELECT source, line_number, text, embedding FROM embeddings ORDER BY embedding <-> ? LIMIT ?"
      val statement = conn.prepareStatement(sql)
      try {
        statement.setObject(1, query)
        statement.setInt(2, limit)
        val rs      = statement.executeQuery()
        val results =
          new scala.collection.mutable.ListBuffer[LineWithEmbedding]
        while (rs.next()) {
          val source     = rs.getString("source")
          val lineNumber = rs.getInt("line_number")
          val text       = rs.getString("text")
          val embedding  = rs.getObject("embedding").asInstanceOf[PGvector]
          val line       = com.example.processing.Line(text, lineNumber, source)
          results += LineWithEmbedding(line, embedding.toArray.toVector)
        }
        results.toList
      } finally {
        statement.close()
      }
    }
}

object EmbeddingRepository {
  def make[F[_]: Sync](conn: Connection): EmbeddingRepository[F] = new EmbeddingRepositoryImpl[F](conn)
}
