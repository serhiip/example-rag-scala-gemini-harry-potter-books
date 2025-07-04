package com.example.data

import cats.effect.IO
import cats.implicits._
import scala.io.Source

object ResourceLoader {

  // Load a resource file as a string
  def loadResourceAsString(resourcePath: String): IO[String] = IO.blocking {
    val resource = getClass.getClassLoader.getResourceAsStream(resourcePath)
    if (resource == null) {
      throw new RuntimeException(s"Resource not found: $resourcePath")
    }
    val source = Source.fromInputStream(resource, "UTF-8")
    try {
      source.mkString
    } finally {
      source.close()
      resource.close()
    }
  }

  // List all book files - since we can't dynamically list resources in a JAR,
  // we need to maintain a list of known book files
  def listBookFiles(): IO[List[String]] = IO {
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

  // Load all books as a list of (filename, content) tuples
  def loadAllBooks(): IO[List[(String, String)]] = {
    for {
      bookFiles <- listBookFiles()
      books <- bookFiles.traverse { fileName =>
        loadResourceAsString(s"books/$fileName").map(content =>
          (fileName, content)
        )
      }
    } yield books
  }
}
