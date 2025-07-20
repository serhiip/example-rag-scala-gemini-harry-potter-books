package com.example.ai

import cats.effect.*
import com.google.genai.Client
import com.google.genai.types.{Content, HttpOptions, Part}

trait GenerativeAI[F[_]] {
  def generateAnswer(prompt: String): F[String]
}

class GenerativeAIImpl[F[_]: Sync](client: Client) extends GenerativeAI[F] {
  override def generateAnswer(prompt: String): F[String] =
    Sync[F].blocking {
      val response = client.models.generateContent(
        "gemini-1.5-flash-002",
        Content.fromParts(Part.fromText(prompt)),
        null
      )
      response.text()
    }
}

object GenerativeAI {

  private def genAiClientResource[F[_]: Sync](
      config: VertexAIConfig
  ): Resource[F, Client] =
    Resource.fromAutoCloseable(
      Sync[F].blocking(
        Client
          .builder()
          .httpOptions(HttpOptions.builder().apiVersion("v1").build())
          .project(config.projectId)
          .location(config.region)
          .vertexAI(true)
          .build()
      )
    )

  def apply[F[_]: Sync](config: VertexAIConfig): Resource[F, GenerativeAI[F]] =
    for {
      client <- genAiClientResource(config)
    } yield new GenerativeAIImpl[F](client)
}
