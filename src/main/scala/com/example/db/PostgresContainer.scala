package com.example.db

import cats.effect.{IO, Resource}
import scala.sys.process._
import scala.concurrent.duration._
import cats.implicits._
import retry._

object PostgresContainer {

  private val containerName = "harry-potter-rag-db"
  private val dbImage = "pgvector/pgvector:pg16"
  private val dbUser = "user"
  private val dbPassword = "password"
  private val dbName = "ragdb"
  private val dbPort = 5432

  def resource: Resource[IO, Unit] = {
    val acquire = for {
      _ <- IO.println("Starting PostgreSQL container...")
      _ <- stopAndRemoveContainer() // Clean up previous runs if they exist
      _ <- startContainer()
      _ <- waitForReady()
      _ <- IO.println("PostgreSQL container started successfully.")
    } yield ()

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
      containerName,
      "-e",
      s"POSTGRES_USER=$dbUser",
      "-e",
      s"POSTGRES_PASSWORD=$dbPassword",
      "-e",
      s"POSTGRES_DB=$dbName",
      "-p",
      s"$dbPort:5432",
      dbImage
    )
    cmd.!!
  }.void

  private def stopAndRemoveContainer(): IO[Unit] = IO.blocking {
    // These commands might fail if the container doesn't exist, which is fine.
    val _ = Seq("docker", "stop", containerName).!
    val _ = Seq("docker", "rm", containerName).!
  }.void

  private def waitForReady(): IO[Unit] = {
    val check = IO
      .blocking(Seq("docker", "logs", containerName).!!)
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
        case RetryDetails.GivingUp(totalRetries, _) =>
          IO.raiseError(
            new RuntimeException(
              s"PostgreSQL container failed to start after $totalRetries attempts.",
              err
            )
          )
      }

    retryingOnAllErrors(policy, onError)(check)
  }
}
