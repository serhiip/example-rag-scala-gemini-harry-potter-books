package com.example.data

import cats.effect.Async
import cats.syntax.all.*
import fs2.io.file.{Files => Fs2Files}
import fs2.io.net.Network
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{Method, Request, Uri}

import java.io.{BufferedOutputStream, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Path, Paths}
import java.util.zip.ZipInputStream
import scala.jdk.StreamConverters.*

/** Downloads the Harry Potter books dataset.
  */
trait Downloader[F[_]] {
  def downloadBooks(): F[Unit]
}

class KaggleDownloader[F[_]: Async: Network] extends Downloader[F] {

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
  private val datasetUrl        =
    "https://www.kaggle.com/api/v1/datasets/download/shubhammaindola/harry-potter-books"

  private def booksExist: F[Boolean] = Async[F].blocking {
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

  private def unzip(): F[Unit] = Async[F].blocking {
    val buffer   = new Array[Byte](1024)
    val zis      = new ZipInputStream(new FileInputStream(zipFilePath.toFile))
    var zipEntry = zis.getNextEntry
    while (zipEntry != null) {
      val newFile = dataDir.resolve(zipEntry.getName)
      if (!newFile.normalize().startsWith(dataDir.normalize())) {
        throw new RuntimeException(s"Bad zip entry: ${zipEntry.getName}")
      }
      if (zipEntry.isDirectory) {
        Files.createDirectories(newFile)
      } else {
        val parent   = newFile.getParent
        if (!Files.exists(parent)) {
          val _ = Files.createDirectories(parent)
        }
        val fos      = new FileOutputStream(newFile.toFile)
        val bos      = new BufferedOutputStream(fos)
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

  private def downloadAndUnzip(client: Client[F]): F[Unit] = {
    for {
      _      <- Async[F].delay(
                  println(
                    s"Downloading dataset from '$datasetUrl' to '$zipFilePath'."
                  )
                )
      _      <- Async[F].blocking(Files.createDirectories(dataDir))
      uri    <- Async[F].fromEither(Uri.fromString(datasetUrl))
      request = Request[F](Method.GET, uri)
      _      <- client
                  .stream(request)
                  .flatMap { response =>
                    if (response.status.isSuccess) {
                      response.body.through(
                        Fs2Files.forAsync[F].writeAll(fs2.io.file.Path.fromNioPath(zipFilePath))
                      )
                    } else {
                      fs2.Stream.raiseError[F](
                        new RuntimeException(
                          s"Failed to download dataset. Status: ${response.status}"
                        )
                      )
                    }
                  }
                  .compile
                  .drain
      _      <- Async[F].delay(println("Download complete. Unzipping archive..."))
      _      <- unzip()
      _      <- Async[F].delay(println("Unzipping complete."))
      _      <- Async[F].blocking(Files.delete(zipFilePath))
      _      <- Async[F].delay(println("Cleaned up zip file."))
    } yield ()
  }

  override def downloadBooks(): F[Unit] = {
    booksExist.ifM(
      Async[F].delay(
        println(
          "Harry Potter books dataset already exists. Skipping download."
        )
      ),
      EmberClientBuilder
        .default[F]
        .withCheckEndpointAuthentication(false)
        .withMaxResponseHeaderSize(8192)
        .build
        .use { client =>
          org.http4s.client.middleware
            .FollowRedirect(maxRedirects = 10)(client)
            .pure[F]
            .flatMap { redirectClient =>
              downloadAndUnzip(redirectClient)
            }
        }
    )
  }
}

object Downloader {
  def apply[F[_]: Async: Network](): Downloader[F] = KaggleDownloader[F]()
}
