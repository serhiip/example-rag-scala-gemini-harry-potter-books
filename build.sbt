import org.typelevel.sbt.tpolecat.*

ThisBuild / scalaVersion := "3.7.1"
ThisBuild / version      := "0.1.0-SNAPSHOT"

val LogbackVersion      = "1.5.18"
val Fs2Version          = "3.12.0"
val CatsRetryVersion    = "3.1.0"
val AiPlatformVersion   = "3.69.0"
val GcsVersion          = "2.53.3"
val GenaiVersion        = "1.9.0"
val PostgresJdbcVersion = "42.7.7"
val CirceVersion        = "0.14.14"
val Http4sVersion       = "0.23.30"
val CatsEffectVersion   = "3.6.2"
val PgVectorVersion     = "0.1.6"

lazy val root = (project in file("."))
  .settings(
    name := "harry-potter-books-rag-using-scala",
    libraryDependencies ++= Seq(
      "org.typelevel"    %% "cats-effect"             % CatsEffectVersion,
      "co.fs2"           %% "fs2-io"                  % Fs2Version,
      "com.github.cb372" %% "cats-retry"              % CatsRetryVersion,
      "com.google.cloud"  % "google-cloud-aiplatform" % AiPlatformVersion,
      "com.google.cloud"  % "google-cloud-storage"    % GcsVersion,
      "com.google.genai"  % "google-genai"            % GenaiVersion,

      // For PostgreSQL with pgvector
      "org.postgresql" % "postgresql" % PostgresJdbcVersion,
      "com.pgvector"   % "pgvector"   % PgVectorVersion,

      // For http4s client (downloader) and circe
      "org.http4s" %% "http4s-ember-client" % Http4sVersion,
      "org.http4s" %% "http4s-dsl"          % Http4sVersion,
      "org.http4s" %% "http4s-circe"        % Http4sVersion,
      "io.circe"   %% "circe-generic"       % CirceVersion,
      "io.circe"   %% "circe-parser"        % CirceVersion,

      // Logging
      "ch.qos.logback" % "logback-classic" % LogbackVersion % Runtime
    )
  )
