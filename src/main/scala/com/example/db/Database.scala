package com.example.db

import cats.effect.*
import com.pgvector.PGvector
import org.postgresql.Driver

import java.sql.{Connection, DriverManager}

object Database {

  def resource(c: PostgresContainerConstants): Resource[IO, Connection] =
    Resource
      .make(
        IO.blocking {
          DriverManager.registerDriver(new Driver())
          val conn = DriverManager.getConnection(
            s"jdbc:postgresql://localhost:${c.port}/${c.database}",
            c.user,
            c.password
          )
          // PGvector.addVectorType(conn)
          conn
        }
      )(conn => IO.blocking(conn.close()))
      .evalTap(setupDatabase)

  private def setupDatabase(conn: Connection): IO[Unit] =
    IO.blocking {
      val statement = conn.createStatement()
      try {
        statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector")
        statement.executeUpdate("DROP TABLE IF EXISTS embeddings")
        statement.executeUpdate("""
          CREATE TABLE IF NOT EXISTS embeddings (
            id SERIAL PRIMARY KEY,
            source TEXT NOT NULL,
            line_number INT NOT NULL,
            text TEXT NOT NULL,
            embedding vector(768)
          );
        """)
        val rs0 = statement.executeQuery(
          s"SELECT nspname FROM pg_namespace WHERE nspname NOT LIKE 'pg_%';"
        )
        while (rs0.next()) {
          val nspname = rs0.getString("nspname")
          println(s"Namespace: $nspname")
        }
        val rs1 = statement.executeQuery("SHOW search_path")
        while (rs1.next()) {
          val searchPath = rs1.getString("search_path")
          println(s"Search path: $searchPath")
        }
        val rs  = statement.executeQuery(
          "SELECT extname, extversion, nspname FROM pg_extension LEFT JOIN pg_namespace ON pg_namespace.oid = pg_extension.extnamespace WHERE extname = 'vector';"
        )
        while (rs.next()) {
          val extname    = rs.getString("extname")
          val extversion = rs.getString("extversion")
          val nspname    = rs.getString("nspname")
          println(
            s"Extension $extname version $extversion in namespace $nspname"
          )
        }
        statement.executeUpdate("ALTER EXTENSION vector UPDATE;")
        PGvector.addVectorType(conn)
        println("Ensured 'vector' extension and 'embeddings' table exist.")
      } finally {
        statement.close()
      }
    }
}
