# Harry Potter Books RAG using Scala

This project is a demonstration of the Retrieval-Augmented Generation (RAG) technique for building Generative AI applications. It is written in Scala 3 and uses the Cats Effect ecosystem for managing effects and concurrency.

The application processes the full text of the Harry Potter book series, generates vector embeddings for each line using Google's Gemini models via the Vertex AI API, and stores them in a PostgreSQL database with the `pgvector` extension. Finally, it demonstrates how to perform a vector similarity search to find the most relevant text passages for a given query.

## Features

- **Data Downloading**: Automatically downloads the Harry Potter book series dataset.
- **Text Processing**: Reads and processes the book text line-by-line in a streaming, memory-efficient manner using FS2.
- **Embedding Generation**: Generates vector embeddings for each line of text using the Google Vertex AI API. It supports both synchronous and asynchronous (batch) API calls.
- **Database Storage**: Sets up and manages a PostgreSQL database with the `pgvector` extension using Docker.
- **Vector Search**: Performs a k-NN similarity search on the stored vectors to find the most relevant text passages for a given query.

## Technology Stack

- **Language**: Scala 3
- **Concurrency**: Cats Effect 3
- **Database**:
  - PostgreSQL with `pgvector` extension
  - Plain JDBC for database interaction
- **AI/ML**:
  - Google Vertex AI (Gemini) for embedding generation
- **Build Tool**: sbt
- **Containerization**: Docker

## Prerequisites

Before you can run the application, you will need the following installed:

- **Java Development Kit (JDK)**: Version 11 or higher.
- **sbt**: The Scala Build Tool.
- **Docker**: To run the PostgreSQL database container.
- **Google Cloud SDK**: The `gcloud` command-line tool.

## Setup

1.  **Authenticate with Google Cloud**:
    You need to authenticate your local environment to use the Vertex AI API. Run the following commands:

    ```shell
    gcloud auth login
    gcloud auth application-default login
    ```

2.  **Configure Your Google Cloud Project**:
    Set the active `gcloud` project to the one you want to use for this application:

    ```shell
    gcloud config set project YOUR_PROJECT_ID
    ```

    The application will automatically pick up this project ID.

3.  **Set Environment Variables** (Optional, for batch processing):
    If you intend to use the batch processing functionality (`VertexAIBatch.scala`), you will need to set the following environment variable:
    ```shell
    export GCS_BUCKET_NAME="your-gcs-bucket-name"
    ```

## How to Run

1.  **Compile the project**:

    ```shell
    sbt compile
    ```

2.  **Run the application**:
    The main application will perform the full end-to-end workflow: start the database, load embeddings from the `resources` folder, save them to the database, generate an embedding for a sample query, and perform a similarity search.
    ```shell
    sbt run
    ```

## Project Structure

- `src/main/scala/com/example/Main.scala`: The main entry point of the application.
- `src/main/scala/com/example/ai/`: Contains the logic for interacting with the Vertex AI API.
  - `VertexAI.scala`: Synchronous API client.
  - `VertexAIBatch.scala`: Asynchronous batch processing client.
- `src/main/scala/com/example/data/`: Components for data loading and processing.
  - `Downloader.scala`: Downloads the Harry Potter dataset.
  - `ResourceLoader.scala`: Loads files from the classpath.
- `src/main/scala/com/example/db/`: Contains all database-related components.
  - `PostgresContainer.scala`: Manages the PostgreSQL Docker container.
  - `Database.scala`: Handles the database connection and schema setup.
  - `EmbeddingRepository.scala`: Provides an interface for database operations.
- `src/main/scala/com/example/processing/`: Contains text processing and embedding storage logic.
  - `TextProcessor.scala`: Reads and processes text files.
  - `EmbeddingStore.scala`: Saves and loads embeddings to/from the filesystem.

## sbt-tpolecat

This template uses the `sbt-tpolecat` sbt plugin to set Scala compiler options to recommended defaults. If you want to change these defaults or find out about the different modes the plugin can operate in you can find out [here](https://github.com/typelevel/sbt-tpolecat/).
