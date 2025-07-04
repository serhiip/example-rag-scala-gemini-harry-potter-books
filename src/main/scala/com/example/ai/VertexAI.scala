package com.example.ai

import cats.effect.*
import cats.implicits.*
import com.example.processing.{Line, LineWithEmbedding}
import com.google.cloud.aiplatform.v1.{
  PredictRequest,
  PredictionServiceClient,
  PredictionServiceSettings
}
import com.google.protobuf.{Struct, Value}

import scala.jdk.CollectionConverters.*

case class VertexAIConfig(projectId: String, region: String)

object VertexAI {

  private val model = "text-embedding-004"

  private def clientResource(
      config: VertexAIConfig
  ): Resource[IO, PredictionServiceClient] = {
    val endpoint = s"${config.region}-aiplatform.googleapis.com:443"
    val settings =
      PredictionServiceSettings.newBuilder().setEndpoint(endpoint).build()
    Resource.make(
      IO.blocking(PredictionServiceClient.create(settings))
    )(client => IO.blocking(client.close()))
  }

  private def valueOf(s: String): Value =
    Value.newBuilder().setStringValue(s).build()

  def getEmbeddings(
      lines: List[Line],
      config: VertexAIConfig
  ): IO[List[LineWithEmbedding]] = {

    val batchSize = 250

    clientResource(config).use { client =>
      val endpointPath =
        s"projects/${config.projectId}/locations/${config.region}/publishers/google/models/$model"

      lines
        .grouped(batchSize)
        .toList
        .traverse { batch =>
          IO.blocking {
            val instances = batch.map { line =>
              val instanceStruct = Struct
                .newBuilder()
                .putFields("content", valueOf(line.text))
                .putFields("task_type", valueOf("RETRIEVAL_DOCUMENT"))
                .build()
              Value.newBuilder().setStructValue(instanceStruct).build()
            }

            val request = PredictRequest
              .newBuilder()
              .setEndpoint(endpointPath)
              .addAllInstances(instances.asJava)
              .build()

            val response = client.predict(request)

            val embeddings = response.getPredictionsList.asScala.map {
              prediction =>
                val embeddingsValue =
                  prediction.getStructValue.getFieldsOrThrow("embeddings")
                val valuesList = embeddingsValue.getStructValue
                  .getFieldsOrThrow("values")
                  .getListValue
                valuesList.getValuesList.asScala
                  .map(_.getNumberValue.toFloat)
                  .toVector
            }

            batch.zip(embeddings).map { case (line, embedding) =>
              LineWithEmbedding(line, embedding)
            }
          }
        }
        .map(_.flatten)
    }
  }
}
