package com.example.db

import cats.effect.{IO, Resource}
import scala.sys.process._
import scala.concurrent.duration._
import cats.implicits._
import retry._

case class PostgresContainerConstants(
    name: String,
    image: String,
    user: String,
    password: String,
    database: String,
    port: Int
)

object PostgresContainer {

  private val constants = PostgresContainerConstants(
    name = "harry-potter-rag-db",
    image = "pgvector/pgvector:0.8.0-pg17",
    user = "user",
    password = "password",
    database = "public",
    port = 5432
  )

  def resource: Resource[IO, PostgresContainerConstants] = {
    val acquire = for {
      _ <- IO.println("Starting PostgreSQL container...")
      _ <- stopAndRemoveContainer() // Clean up previous runs if they exist
      _ <- startContainer()
      _ <- waitForReady()
      _ <- IO.println("PostgreSQL container started successfully.")
    } yield constants

    val release =
      IO.println("Stopping PostgreSQL container...") >> stopAndRemoveContainer()

    Resource.make(acquire)(_ => release)
  }

  private def startContainer(): IO[Unit] = IO.blocking {
    val cmd = Seq(
      "docker",
      "run",
      "-d",
      "--name",
      constants.name,
      "-e",
      s"POSTGRES_USER=${constants.user}",
      "-e",
      s"POSTGRES_PASSWORD=${constants.password}",
      "-e",
      s"POSTGRES_DB=${constants.database}",
      "-p",
      s"${constants.port}:5432",
      constants.image
    )
    val _   = cmd.!!
  }.void

  private def stopAndRemoveContainer(): IO[Unit] = IO.blocking {
    val _ = Seq("docker", "stop", constants.name).!
    val _ = Seq("docker", "rm", constants.name).!
  }.void

  private def waitForReady(): IO[Unit] = {
    val check = IO
      .blocking(Seq("docker", "logs", constants.name).!!)
      .flatMap { logs =>
        if (logs.contains("database system is ready to accept connections"))
          IO.unit
        else IO.raiseError(new RuntimeException("Database not ready yet"))
      }

    val policy = RetryPolicies
      .limitRetries[IO](15)
      .join(RetryPolicies.constantDelay(2.seconds))

    def onError(err: Throwable, details: RetryDetails): IO[Unit] =
      details match {
        case RetryDetails.WillDelayAndRetry(nextDelay, retries, _) =>
          IO.println(
            s"PostgreSQL not ready. Retrying in ${nextDelay.toSeconds.toInt}s... (Attempt: ${retries + 1})"
          )
        case _                                                     => IO.raiseError(err)
      }

    retryingOnAllErrors(policy, onError)(check.handleErrorWith {
      case e: RuntimeException if e.getMessage == "Database not ready yet" =>
        IO.raiseError(e)
      case e                                                               => IO.pure(()) // Ignore other errors during check
    })
  }
}
