import org.typelevel.sbt.tpolecat.*

ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "3.4.2"
ThisBuild / version := "0.1.0-SNAPSHOT"

val CatsEffectVersion = "3.5.4"
val SkunkVersion = "0.6.3"
val Http4sVersion = "0.23.26"
val CirceVersion = "0.14.7"
val LogbackVersion = "1.5.6"
val Fs2Version = "3.10.2"
val CatsRetryVersion = "3.1.0"
val AiPlatformVersion = "3.11.0"
val GcsVersion = "2.38.0"

lazy val root = (project in file("."))
  .settings(
    name := "harry-potter-books-rag-using-scala",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % CatsEffectVersion,
      "co.fs2" %% "fs2-io" % Fs2Version,
      "com.github.cb372" %% "cats-retry" % CatsRetryVersion,
      "com.google.cloud" % "google-cloud-aiplatform" % AiPlatformVersion,
      "com.google.cloud" % "google-cloud-storage" % GcsVersion,

      // For PostgreSQL with pgvector
      "org.tpolecat" %% "skunk-core" % SkunkVersion,

      // For Google Vertex AI Gemini API (http4s client is still used for downloader)
      "org.http4s" %% "http4s-ember-client" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "org.http4s" %% "http4s-circe" % Http4sVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
      "io.circe" %% "circe-parser" % CirceVersion,

      // Logging
      "ch.qos.logback" % "logback-classic" % LogbackVersion % Runtime
    )
  )
