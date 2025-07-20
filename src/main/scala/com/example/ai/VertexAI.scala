package com.example.ai

import cats.effect.*
import cats.syntax.all.*
import com.example.processing.{Line, LineWithEmbedding}
import com.google.cloud.aiplatform.v1.{PredictRequest, PredictionServiceClient, PredictionServiceSettings}
import com.google.protobuf.{Struct, Value}

import scala.jdk.CollectionConverters.*

case class VertexAIConfig(projectId: String, region: String)

trait VertexAI[F[_]] {
  def getEmbeddings(lines: List[Line], config: VertexAIConfig): F[List[LineWithEmbedding]]
}

class VertexAIImpl[F[_]: Sync] extends VertexAI[F] {

  private val model = "text-embedding-004"

  private def clientResource(
      config: VertexAIConfig
  ): Resource[F, PredictionServiceClient] = {
    val endpoint = s"${config.region}-aiplatform.googleapis.com:443"
    val settings =
      PredictionServiceSettings.newBuilder().setEndpoint(endpoint).build()
    Resource.make(
      Sync[F].blocking(PredictionServiceClient.create(settings))
    )(client => Sync[F].blocking(client.close()))
  }

  private def valueOf(s: String): Value =
    Value.newBuilder().setStringValue(s).build()

  override def getEmbeddings(
      lines: List[Line],
      config: VertexAIConfig
  ): F[List[LineWithEmbedding]] = {

    val batchSize = 250

    clientResource(config).use { client =>
      val endpointPath =
        s"projects/${config.projectId}/locations/${config.region}/publishers/google/models/$model"

      lines
        .grouped(batchSize)
        .toList
        .traverse { batch =>
          Sync[F].blocking {
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

            val embeddings = response.getPredictionsList.asScala.map { prediction =>
              val embeddingsValue =
                prediction.getStructValue.getFieldsOrThrow("embeddings")
              val valuesList      = embeddingsValue.getStructValue
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

object VertexAI {
  def apply[F[_]: Sync](): VertexAI[F] = new VertexAIImpl[F]()
}
