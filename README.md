# json-file-saver

Vert.x-based microservice for saving JSON data and files via REST and gRPC APIs, with support for unary, server-streaming, and bidirectional streaming. Files save to an `uploads` directory with automatic naming, metadata support, and size limits.

## Features

- REST endpoint at `/api/users` (POST) to save JSON requests as `.json` files with UUID names.
- gRPC FileService with three RPC methods: `SaveFileUnary`, `SaveFileStream` (server streaming), `SaveFileBidirectional` (bidirectional).
- Automatic directory creation, filename sanitization, and optional metadata JSON saving.
- Simple HTTP echo server on port 8989 for testing.


## gRPC API

Defined in [file_service.proto](src/main/proto/file_service.proto):

- **SaveFileUnary(SaveFileRequest) returns (SaveFileResponse)**: Single file save.
- **SaveFileStream(stream SaveFileRequest) returns (stream SaveFileResponse)**: Server streams responses for client-sent stream.
- **SaveFileBidirectional(stream SaveFileRequest) returns (stream SaveFileResponse)**: Full duplex streaming with file count summary.

**SaveFileRequest fields**:

- `id` (string, optional)
- `filename` (string, optional; defaults to UUID)
- `content_base64` (string, required, ≤400KB)
- `metadata_json` (string, optional)
- `timestamp` (int64, optional)

**SaveFileResponse fields**:

- `success` (bool)
- `message` (string)
- `saved_filename`, `file_path`, `file_size`, `saved_at`


## Quick Start

1. Run `mvn clean package` to build the fat JAR.
2. Start with `java -jar target/json-file-saver-1.0.0-SNAPSHOT-fat.jar` or `mvn exec:java`.
3. Servers start on:
    - HTTP/REST: `http://localhost:8888` (test: GET `/`, POST `/api/users`)
    - gRPC: `localhost:50051`
    - Simple HTTP: `http://localhost:8989`
4. Files save to `./uploads/`.

## Project Structure

| File | Description                                                |
| :-- |:-----------------------------------------------------------|
|  [MainVerticle.java](src/main/java/com/example/MainVerticle.java)| Deploys all verticles and configs ports/directories. |
|[RestRequestJsonFileSaver.java](src/main/java/com/example/RestRequestJsonFileSaver.java) | Handles REST JSON saving. |
|[FileServiceServer.java](src/main/java/com/example/FileServiceServer.java) | gRPC server implementation. |
| [SimpleHttpServer.java](src/main/java/com/example/SimpleHttpServer.java) | Basic HTTP test server.|
|[file_service.proto](src/main/proto/file_service.proto) | gRPC service definition. |

## Configuration

Ports and paths via deployment config (defaults: HTTP 8888, gRPC 50051, uploads `./uploads`).

