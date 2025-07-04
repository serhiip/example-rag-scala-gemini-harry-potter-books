package com.example.data

import cats.effect.IO
import cats.implicits._
import fs2.io.file.{Files => Fs2Files}
import java.io.{BufferedOutputStream, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Path, Paths}
import java.util.zip.ZipInputStream
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{Method, Request, Uri}
import scala.jdk.StreamConverters._

/** Downloads the Harry Potter books dataset.
  */
object Downloader {

  // Use a data directory in the current working directory or user home
  // This is only for initial download during development
  private val dataDir: Path = {
    val devResourcesPath = Paths.get("src/main/resources/books")
    if (Files.exists(devResourcesPath.getParent)) {
      // Development environment - download to src/main/resources/books
      devResourcesPath
    } else {
      // Production environment - download to user home directory
      Paths
        .get(System.getProperty("user.home"))
        .resolve(".harry-potter-rag/books")
    }
  }

  private val zipFilePath: Path = dataDir.resolve("harry-potter-books.zip")
  private val datasetUrl =
    "https://www.kaggle.com/api/v1/datasets/download/shubhammaindola/harry-potter-books"

  private def booksExist: IO[Boolean] = IO.blocking {
    // Check if we already have books in resources
    val resourceCheck = Option(
      getClass.getClassLoader.getResource(
        "books/01 Harry Potter and the Sorcerers Stone.txt"
      )
    ).isDefined

    // Also check filesystem for development
    val filesystemCheck =
      if (Files.exists(dataDir) && Files.isDirectory(dataDir)) {
        Files
          .list(dataDir)
          .toScala(List)
          .exists(_.getFileName.toString.endsWith(".txt"))
      } else {
        false
      }

    resourceCheck || filesystemCheck
  }

  private def unzip(): IO[Unit] = IO.blocking {
    val buffer = new Array[Byte](1024)
    val zis = new ZipInputStream(new FileInputStream(zipFilePath.toFile))
    var zipEntry = zis.getNextEntry
    while (zipEntry != null) {
      val newFile = dataDir.resolve(zipEntry.getName)
      if (!newFile.normalize().startsWith(dataDir.normalize())) {
        throw new RuntimeException(s"Bad zip entry: ${zipEntry.getName}")
      }
      if (zipEntry.isDirectory) {
        Files.createDirectories(newFile)
      } else {
        val parent = newFile.getParent
        if (!Files.exists(parent)) {
          val _ = Files.createDirectories(parent)
        }
        val fos = new FileOutputStream(newFile.toFile)
        val bos = new BufferedOutputStream(fos)
        var len: Int = 0
        while ({ len = zis.read(buffer); len > 0 }) {
          bos.write(buffer, 0, len)
        }
        bos.close()
      }
      zis.closeEntry()
      zipEntry = zis.getNextEntry
    }
    zis.close()
  }

  private def downloadAndUnzip(client: Client[IO]): IO[Unit] = {
    for {
      _ <- IO.println(
        s"Downloading dataset from '$datasetUrl' to '$zipFilePath'."
      )
      _ <- IO.blocking(Files.createDirectories(dataDir))
      uri <- IO.fromEither(Uri.fromString(datasetUrl))
      request = Request[IO](Method.GET, uri)
      _ <- client
        .stream(request)
        .flatMap { response =>
          if (response.status.isSuccess) {
            response.body.through(
              Fs2Files[IO].writeAll(fs2.io.file.Path.fromNioPath(zipFilePath))
            )
          } else {
            fs2.Stream.raiseError[IO](
              new RuntimeException(
                s"Failed to download dataset. Status: ${response.status}"
              )
            )
          }
        }
        .compile
        .drain
      _ <- IO.println("Download complete. Unzipping archive...")
      _ <- unzip()
      _ <- IO.println("Unzipping complete.")
      _ <- IO.blocking(Files.delete(zipFilePath))
      _ <- IO.println("Cleaned up zip file.")
    } yield ()
  }

  def downloadBooks(): IO[Unit] = {
    booksExist.ifM(
      IO.println(
        "Harry Potter books dataset already exists. Skipping download."
      ),
      EmberClientBuilder
        .default[IO]
        .withCheckEndpointAuthentication(false)
        .withMaxResponseHeaderSize(8192)
        .build
        .use { client =>
          org.http4s.client.middleware
            .FollowRedirect(maxRedirects = 10)(client)
            .pure[IO]
            .flatMap { redirectClient =>
              downloadAndUnzip(redirectClient)
            }
        }
    )
  }
}
