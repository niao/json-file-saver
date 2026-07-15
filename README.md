# json-file-saver

Vert.x-based microservice for saving JSON data via REST and gRPC, with admin endpoints for monitoring, file management, and dynamic configuration of a downloadable package (`pack.zip`). Designed for reliability, configurability, and operational visibility.

## Features

- ✅ **REST JSON Saving**: Save incoming JSON to files with UUID names.
- ✅ **gRPC FileService**: Unary, server-streaming, bidirectional methods.
- ✅ **Configurable Endpoints**:
  - Multiple POST paths via `HTTP_ENDPOINT`
  - Admin prefix via `PREFIX`
- ✅ **Admin API**:
  - `GET /{prefix}/info` — service status, file count
  - `GET /{prefix}/files` — list saved `.json` files
  - `DELETE /{prefix}/files` — delete all `.json` files
  - `POST /{prefix}/setpack` — upload and replace `pack.zip` (≤200 MB)
  - `GET /{prefix}/setmd5` — calculate MD5 of `pack.zip`
  - `GET /{prefix}/setmd5/{hex}` — manually set expected MD5
  - `GET /getpack` — download `pack.zip` with correct `X-File-MD5` and `Content-MD5` headers
- ✅ **Environment-driven config** (ENV)
- ✅ **Thread-safe MD5 updates** via `volatile`
- ✅ **Clean architecture**: handlers separated by responsibility
- ✅ Simple echo server on port `8989`

---

## REST API

### POST `/...` (configurable)
Saves JSON body to a `.json` file in `UPLOAD_DIR`.

**Request**:
```json
{ "name": "John", "age": 30 }
```

**Response**:
```json
{
  "success": true,
  "message": "File saved successfully",
  "filename": "a1b2c3d4-e5f6-7890.json",
  "path": "/tmp/uploads/a1b2c3d4-e5f6-7890.json"
}
```

> **Note**: Actual endpoint(s) depend on `HTTP_ENDPOINT` (e.g., `/api/users`).

---

### GET `/{prefix}/info`
Returns service metadata and file statistics.

**Example**:
```json
{
  "service": "File Saver Service",
  "http_port": 8888,
  "grpc_port": 50051,
  "upload_dir": "/tmp/uploads",
  "files_count": 42,
  "endpoints": {
    "POST /api/users": "Save JSON to file",
    "gRPC": {
      "SaveFileUnary": "Unary RPC call",
      "SaveFileStream": "Server streaming",
      "SaveFileBidirectional": "Bidirectional streaming"
    }
  }
}
```

---

### GET `/{prefix}/files`
Lists all `.json` files in upload directory.

**Response**:
```json
{
  "directory": "/tmp/uploads",
  "total_files": 3,
  "files": [
    {
      "filename": "a1b2c3.json",
      "size": 256,
      "created": 1719845678000,
      "modified": 1719845678000
    }
  ]
}
```

---

### DELETE `/{prefix}/files`
Deletes all `.json` files from `UPLOAD_DIR`.

**Response**:
```json
{
  "success": true,
  "message": "Cleanup completed",
  "deleted_files": 3
}
```

---

### POST `/{prefix}/setpack`
Uploads a new `pack.zip` file (≤ 200 MB). Automatically recalculates and updates `PACK_FILE_MD5`.

**Request**:
```bash
curl -X POST http://localhost:8888/api/v1/setpack \
     -H "Content-Type: application/octet-stream" \
     --data-binary @new_pack.zip
```

**Response**:
```json
{
  "success": true,
  "message": "File saved successfully",
  "path": "/storage/pack.zip",
  "file_size": 104857600,
  "file_md5": "d41d8cd98f00b204e9800998ecf8427e"
}
```

---

### GET `/{prefix}/setmd5`
- **Without param**: calculates MD5 of current `pack.zip`
- **With param**: sets `PACK_FILE_MD5` manually

**Calculate**:
```bash
curl http://localhost:8888/api/v1/setmd5
```
```json
{ "file_md5": "d41d8cd98f00b204e9800998ecf8427e" }
```

**Set manually**:
```bash
curl http://localhost:8888/api/v1/setmd5/7bbce66cdc6de3bd07f0798e27dfa262
```
```json
{
  "success": true,
  "message": "MD5 set manually",
  "pack_file_md5": "7bbce66cdc6de3bd07f0798e27dfa262"
}
```

---

### GET `/getpack`
Downloads the current `pack.zip` with correct headers.

**Headers**:
```http
X-File-MD5: 7bbce66cdc6de3bd07f0798e27dfa262
Content-MD5: N2JiY2U2NmNkYzZkZTNiZDA3ZjA3OThlMjdkZmEyNjI=
```

> `Content-MD5` — Base64-encoded MD5

---

## gRPC API

See [file_service.proto](src/main/proto/file_service.proto)

### Methods:
- `SaveFileUnary`
- `SaveFileStream`
- `SaveFileBidirectional`

---

## Quick Start

1. Build:
   ```bash
   mvn clean package
   ```

2. Run:
   ```bash
   java -jar target/json-file-saver-1.0.0-SNAPSHOT-fat.jar
   ```
   or
   ```bash
   mvn exec:java
   ```

3. Services:
  - **HTTP/REST**: `http://localhost:8888`
  - **gRPC**: `localhost:50051`
  - **Simple HTTP**: `http://localhost:8989`

4. Default upload dir: `./uploads/`

---

## Configuration

All settings via **environment variables**:

| Env Var | Default | Description |
|--------|--------|-------------|
| `UPLOAD_DIR` | `./uploads/` | Directory for JSON files |
| `STORAGE_DIR` | `./storage/` | Directory for `pack.zip` |
| `HTTP_PORT` | `8888` | REST server port |
| `GRPC_PORT` | `50051` | gRPC server port |
| `HTTP_ENDPOINT` | `/save` | Comma-separated POST endpoints |
| `PREFIX` | `/` | Prefix for admin endpoints |
| `PACK_FILE_PATH` | `pack.zip` | Name of downloadable file |
| `PACK_FILE_MD5` | `7bbce...` | Expected MD5 (used in headers) |

### Example:
```bash
export UPLOAD_DIR=/tmp/my-uploads/
export STORAGE_DIR=/opt/storage/
export HTTP_PORT=8080
export HTTP_ENDPOINT=/save,/upload
export PREFIX=/api/v1
export GRPC_PORT=9090

mvn exec:java
```

> Now accessible at:
> - `POST http://localhost:8080/save`
> - `GET http://localhost:8080/api/v1/info`
> - `POST http://localhost:8080/api/v1/setpack`
> - `GET http://localhost:8080/getpack`

---

## Architecture

- **Modular handlers**: each endpoint has its own class
- **Thread-safe config**: `packFileMd5` is `volatile`
- **Safe I/O**: streams closed, size checks, error handling
- **No global BodyHandler**: avoids conflicts with binary uploads
- **Supplier/Consumer pattern**: dynamic MD5 updates

---

## Project Structure

```
src/main/java/com/example/
├── RestRequestJsonFileSaver.java       → main router
├── MainVerticle.java                   → deploys all services
├── config/ServiceConfig.java           → config holder
├── handler/rest/JsonSaveHandler.java   → saves JSON
├── handler/admin/InfoHandler.java      → /info
├── handler/admin/FilesListHandler.java → /files (GET)
├── handler/admin/FilesDeleteHandler.java → /files (DELETE)
├── handler/storage/GetPackHandler.java → /getpack
├── handler/storage/SetPackHandler.java → /setpack
├── handler/storage/SetMd5Handler.java  → /setmd5
└── util/Md5Util.java                   → MD5 calculation
```

---

## Admin Endpoints Summary

| Method | Path | Purpose |
|-------|------|--------|
| `GET` | `/{prefix}/info` | Service info + file count |
| `GET` | `/{prefix}/files` | List uploaded JSON files |
| `DELETE` | `/{prefix}/files` | Delete all JSON files |
| `POST` | `/{prefix}/setpack` | Upload new `pack.zip` |
| `GET` | `/{prefix}/setmd5` | Calculate current MD5 |
| `GET` | `/{prefix}/setmd5/{hex}` | Set MD5 manually |

> Replace `{prefix}` with `PREFIX` value

---

## Production Tips

- ✅ Use `Docker` for deployment
- ✅ Add health check: `GET /health` → `{"status":"UP"}`
- ✅ Enable logging with `logback.xml`
- ✅ Monitor file count and disk usage
- ✅ Protect admin endpoints in production (future enhancement)

---

## License

MIT
