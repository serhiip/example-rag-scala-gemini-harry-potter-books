package com.example

import cats.effect.IOApp
import cats.effect.IO
import cats.implicits._
import com.example.data.{Downloader, ResourceLoader}

object Main extends IOApp.Simple {

  // This is your new "main"!
  def run: IO[Unit] =
    for {
      _ <- Downloader.downloadBooks()
      _ <- IO.println("\nTesting resource loader...")
      books <- ResourceLoader.loadAllBooks()
      _ <- IO.println(s"Successfully loaded ${books.length} books:")
      _ <- books.traverse { case (fileName, content) =>
        IO.println(s"  - $fileName (${content.length} characters)")
      }
    } yield ()
}
