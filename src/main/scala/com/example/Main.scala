package com.example

import cats.effect.IOApp
import cats.effect.IO
import com.example.db.PostgresContainer
import scala.concurrent.duration._

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    PostgresContainer.resource.use { _ =>
      for {
        _ <- IO.println(
          "PostgreSQL container is running. Application logic would go here."
        )
        _ <- IO.sleep(5.seconds) // Simulate work
        _ <- IO.println("Work finished. Container will now be stopped.")
      } yield ()
    }
}
