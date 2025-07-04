package com.example.ai

import cats.effect.*
import cats.implicits.*
import com.example.processing.{Line, LineWithEmbedding}
import com.google.cloud.aiplatform.v1.*
import com.google.cloud.storage.{BlobId, BlobInfo, Storage, StorageOptions}
import io.circe.parser.parse
import io.circe.Json
import retry.*

import java.util.UUID
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

case class GCSUri(bucket: String, path: String) {
  override def toString: String = s"gs://$bucket/$path"
}

case class JobStillRunning(state: JobState)
    extends RuntimeException(s"Job is still running with state: $state")

object VertexAIBatch {

  private val model = "text-embedding-004"

  private def jobServiceClientResource(
      config: VertexAIConfig
  ): Resource[IO, JobServiceClient] = {
    val endpoint = s"${config.region}-aiplatform.googleapis.com:443"
    val settings = JobServiceSettings.newBuilder().setEndpoint(endpoint).build()
    Resource.make(IO.blocking(JobServiceClient.create(settings)))(client =>
      IO.blocking(client.close())
    )
  }

  private def gcsClientResource: Resource[IO, Storage] =
    Resource.make(IO.blocking(StorageOptions.getDefaultInstance.getService))(
      _ => IO.unit
    )

  private def uploadToGCS(
      lines: List[Line],
      bucketName: String,
      objectName: String
  ): IO[GCSUri] = {
    gcsClientResource.use { storage =>
      IO.blocking {
        val jsonLines = lines
          .map { line =>
            Json.obj("content" -> Json.fromString(line.text)).noSpaces
          }
          .mkString("\n")

        val blobId = BlobId.of(bucketName, objectName)
        val blobInfo = BlobInfo.newBuilder(blobId).build()
        storage.create(blobInfo, jsonLines.getBytes("UTF-8"))

        GCSUri(bucketName, objectName)
      }
    }
  }

  private def pollJob(
      jobName: String,
      client: JobServiceClient
  ): IO[BatchPredictionJob] = {
    val check =
      IO.blocking(client.getBatchPredictionJob(jobName)).flatMap { job =>
        job.getState match {
          case JobState.JOB_STATE_SUCCEEDED => IO.pure(job)
          case JobState.JOB_STATE_PENDING | JobState.JOB_STATE_RUNNING =>
            IO.raiseError(JobStillRunning(job.getState))
          case failedState =>
            IO.raiseError(
              new RuntimeException(
                s"Job failed with state: $failedState and error: ${job.getError.getMessage}"
              )
            )
        }
      }

    val policy = RetryPolicies
      .limitRetries[IO](30)
      .join(RetryPolicies.constantDelay(60.seconds))

    def onError(err: Throwable, details: RetryDetails): IO[Unit] =
      details match {
        case RetryDetails.WillDelayAndRetry(nextDelay, retries, _) =>
          IO.println(
            s"Job not finished. Retrying in ${nextDelay.toSeconds}s... (Attempt ${retries + 1})"
          )
        case _ => IO.raiseError(err) // Fail on other errors
      }

    retryingOnAllErrors[BatchPredictionJob](policy, onError)(
      check.handleErrorWith {
        case _: JobStillRunning =>
          IO.raiseError(new RuntimeException("Job still running"))
        case e => IO.raiseError(e)
      }
    )
  }

  private def readAndReconstructEmbeddings(
      originalLines: List[Line],
      outputUri: GCSUri,
      storage: Storage
  ): IO[List[LineWithEmbedding]] = {
    val readBlobsIO = IO.blocking {
      storage
        .list(outputUri.bucket, Storage.BlobListOption.prefix(outputUri.path))
        .iterateAll()
        .asScala
        .toList
        .flatMap(blob =>
          new String(blob.getContent(), "UTF-8").linesIterator.toList
            .filter(_.nonEmpty)
        )
    }

    readBlobsIO.flatMap { jsonLines =>
      val contentToEmbeddingMapIO = jsonLines
        .traverse { line =>
          IO.fromEither(
            parse(line)
              .leftMap(e => new RuntimeException(s"JSON parsing failed: $e", e))
          ).flatMap { json =>
            val cursor = json.hcursor
            val contentEither =
              cursor.downField("instance").downField("content").as[String]
            val embeddingEither = cursor
              .downField("prediction")
              .downField("embeddings")
              .downField("values")
              .as[Vector[Float]]

            IO.fromEither(
              (contentEither, embeddingEither).tupled
                .leftMap(e =>
                  new RuntimeException(s"Circe decoding failed: $e", e)
                )
            )
          }
        }
        .map(_.toMap)

      contentToEmbeddingMapIO.map { contentToEmbeddingMap =>
        originalLines.map { line =>
          val embedding =
            contentToEmbeddingMap.getOrElse(line.text, Vector.empty[Float])
          LineWithEmbedding(line, embedding)
        }
      }
    }
  }

  def getEmbeddings(
      lines: List[Line],
      config: VertexAIConfig,
      gcsBucket: String
  ): IO[List[LineWithEmbedding]] = {
    (jobServiceClientResource(config), gcsClientResource).tupled.use {
      (client, storage) =>
        val jobId =
          s"harry-potter-embeddings-${UUID.randomUUID().toString.take(8)}"
        val inputObjectName = s"batch-inputs/$jobId.jsonl"
        val outputPrefix = s"batch-outputs/$jobId/"

        for {
          inputUri <- uploadToGCS(lines, gcsBucket, inputObjectName)
          outputUri = GCSUri(gcsBucket, outputPrefix)

          batchJob = BatchPredictionJob
            .newBuilder()
            .setDisplayName(jobId)
            .setModel(
              s"projects/${config.projectId}/locations/${config.region}/publishers/google/models/$model"
            )
            .setInputConfig(
              BatchPredictionJob.InputConfig
                .newBuilder()
                .setInstancesFormat("jsonl")
                .setGcsSource(GcsSource.newBuilder().addUris(inputUri.toString))
            )
            .setOutputConfig(
              BatchPredictionJob.OutputConfig
                .newBuilder()
                .setPredictionsFormat("jsonl")
                .setGcsDestination(
                  GcsDestination
                    .newBuilder()
                    .setOutputUriPrefix(outputUri.toString)
                )
            )
            .build()

          locationName = LocationName.of(config.projectId, config.region)
          createdJob <- IO.blocking(
            client.createBatchPredictionJob(locationName, batchJob)
          )
          _ <- IO.println(
            s"Batch prediction job '${createdJob.getName}' created. Waiting for completion..."
          )

          completedJob <- pollJob(createdJob.getName, client)
          _ <- IO.println(
            s"Batch job '${completedJob.getName}' finished with state: ${completedJob.getState}."
          )

          linesWithEmbeddings <- readAndReconstructEmbeddings(
            lines,
            outputUri,
            storage
          )

        } yield linesWithEmbeddings
    }
  }
}
