package com.example.ai

import cats.effect.*
import com.google.genai.Client
import com.google.genai.types.HttpOptions
import com.google.genai.types.Part
import com.google.genai.types.Content

object GenerativeAI {

  private def genAiClientResource(
      config: VertexAIConfig
  ): Resource[IO, Client] =
    Resource.fromAutoCloseable(
      IO.blocking(
        Client
          .builder()
          .httpOptions(HttpOptions.builder().apiVersion("v1").build())
          .project(config.projectId)
          .location(config.region)
          .vertexAI(true)
          .build()
      )
    )

  def generateAnswer(prompt: String, config: VertexAIConfig): IO[String] =
    genAiClientResource(config).use { client =>
      IO.blocking {
        val response = client.models.generateContent(
          "gemini-1.5-flash-002",
          Content.fromParts(Part.fromText(prompt)),
          null
        )
        println(response)
        response.text()
      }
    }
}
