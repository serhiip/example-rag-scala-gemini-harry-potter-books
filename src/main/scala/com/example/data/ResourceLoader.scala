package com.example.data

import cats.effect.Sync
import cats.implicits._
import scala.io.Source

trait ResourceLoader[F[_]] {
  def loadResourceAsString(resourcePath: String): F[String]
  def listBookFiles(): F[List[String]]
  def loadAllBooks(): F[List[(String, String)]]
}

class ResourceLoaderImpl[F[_]: Sync] extends ResourceLoader[F] {

  override def loadResourceAsString(resourcePath: String): F[String] = Sync[F].blocking {
    val resource = getClass.getClassLoader.getResourceAsStream(resourcePath)
    if (resource == null) {
      throw new RuntimeException(s"Resource not found: $resourcePath")
    }
    val source   = Source.fromInputStream(resource, "UTF-8")
    try {
      source.mkString
    } finally {
      source.close()
      resource.close()
    }
  }

  override def listBookFiles(): F[List[String]] = Sync[F].delay {
    val knownBooks = List(
      "01 Harry Potter and the Sorcerers Stone.txt",
      "02 Harry Potter and the Chamber of Secrets.txt",
      "03 Harry Potter and the Prisoner of Azkaban.txt",
      "04 Harry Potter and the Goblet of Fire.txt",
      "05 Harry Potter and the Order of the Phoenix.txt",
      "06 Harry Potter and the Half-Blood Prince.txt",
      "07 Harry Potter and the Deathly Hallows.txt"
    )

    // Filter to only include books that actually exist as resources
    knownBooks.filter { fileName =>
      Option(getClass.getClassLoader.getResource(s"books/$fileName")).isDefined
    }
  }

  override def loadAllBooks(): F[List[(String, String)]] = {
    for {
      bookFiles <- listBookFiles()
      books     <- bookFiles.traverse { fileName =>
                     loadResourceAsString(s"books/$fileName").map(content => (fileName, content))
                   }
    } yield books
  }
}

object ResourceLoader {
  def apply[F[_]: Sync](): ResourceLoader[F] = new ResourceLoaderImpl[F]()
}
