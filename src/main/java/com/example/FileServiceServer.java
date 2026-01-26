package com.example;

import com.example.grpc.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import io.vertx.grpc.server.GrpcServerResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class FileServiceServer extends VerticleBase {

  private static final String UPLOAD_DIR = "uploads";
  private static final DateTimeFormatter DATE_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSSS");
  private static final int MAX_FILE_SIZE = 800 * 1024; // 400KB
  private static final int MAX_MESSAGE_SIZE = 10 * 1024 * 1024; // 10MB для gRPC сообщений
  private static int GRPC_PORT;

  @Override
  public Future<?> start() throws Exception {

    GRPC_PORT = config().getInteger("GRPC_PORT", 50051);
    // Создаем директорию для загрузок, если она не существует
    Path uploadPath = Paths.get(UPLOAD_DIR);
    if (!Files.exists(uploadPath)) {
      Files.createDirectories(uploadPath);
    }

    // Настраиваем опции для увеличения лимитов
    GrpcServerOptions options = new GrpcServerOptions()
      .setMaxMessageSize(MAX_MESSAGE_SIZE);
    //  .setInitialFlowControlWindow(1048576) // 1MB flow control window
     // .setCompressionSupported(true);

    // Создаем gRPC сервер
    GrpcServer grpcServer = GrpcServer.server(vertx, options);

    // 1. Унарный RPC метод
    grpcServer.callHandler(FileServiceGrpcService.SaveFileUnary, unaryHandler -> {
      unaryHandler.handler(request -> {
        try {
          SaveFileResponse response =
            processSaveFileRequest((SaveFileRequest) request);
          unaryHandler.response().end(response);
        } catch (Exception e) {
          unaryHandler.response().end(createErrorResponse(e.getMessage()));
        }
      });
    });

    // Стриминговый метод (серверный поток)
    grpcServer.callHandler(FileServiceGrpcService.SaveFileStream, request -> {
      GrpcServerResponse<SaveFileRequest, SaveFileResponse> response = request.response();

      request.handler(saveFileRequest -> {
        try {
          SaveFileResponse fileResponse =
            processSaveFileRequest(saveFileRequest);
          response.write(fileResponse);
        } catch (Exception e) {
          response.write(createErrorResponse(e.getMessage()));
        }
      });

      request.endHandler(v -> {
        response.end();
      });
    });

    // Двунаправленный стриминг
    grpcServer.callHandler(FileServiceGrpcService.SaveFileBidirectional, request -> {
      GrpcServerResponse<SaveFileRequest,SaveFileResponse> response = request.response();

      AtomicInteger fileCount = new AtomicInteger(0);

      request.handler(saveFileRequest -> {
        try {
          fileCount.incrementAndGet();
          SaveFileResponse fileResponse =
            processSaveFileRequest((SaveFileRequest) saveFileRequest);

          // Добавляем информацию о порядке получения
          SaveFileResponse enhancedResponse =
            SaveFileResponse.newBuilder(fileResponse)
              .setMessage("File " + fileCount.get() + ": " + fileResponse.getMessage())
              .build();

          response.write(enhancedResponse);
        } catch (Exception e) {
          response.write(createErrorResponse("File " + fileCount.get() + ": " + e.getMessage()));
        }
      });

      request.endHandler(v -> {
        String summary = String.format("Processed %d files successfully", fileCount.get());
        SaveFileResponse finalResponse =
          SaveFileResponse.newBuilder()
            .setSuccess(true)
            .setMessage(summary)
            .setSavedAt(LocalDateTime.now().format(DATE_FORMATTER))
            .build();

        response.write(finalResponse);
        response.end();
      });
    });

    // Запускаем сервер
    return vertx.createHttpServer()
      .requestHandler(grpcServer)
      .listen(GRPC_PORT)
      .mapEmpty()
      .andThen(v -> {
        System.out.println("gRPC Server listening on port " + GRPC_PORT);
      });
  }

  private SaveFileResponse processSaveFileRequest(
    SaveFileRequest request) throws IOException {

    // Валидация размера файла
    String contentBase64 = request.getContentBase64();
    if (contentBase64 == null || contentBase64.isEmpty()) {
      throw new IllegalArgumentException("File content cannot be empty");
    }

    byte[] content = Base64.getDecoder().decode(contentBase64);

    if (content.length > MAX_FILE_SIZE) {
      throw new IllegalArgumentException(
        String.format("File size %d bytes exceeds maximum %d bytes",
          content.length, MAX_FILE_SIZE));
    }

    // Генерация имени файла
    String filename = request.getFilename();
    if (filename == null || filename.isEmpty()) {
      filename = UUID.randomUUID().toString();
    }

    // Добавляем timestamp к имени файла для уникальности
    String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
    String safeFilename = sanitizeFilename(filename);
    String finalFilename = timestamp + "_" + safeFilename;

    // Полный путь к файлу
    Path filePath = Paths.get(UPLOAD_DIR, finalFilename);

    // Сохраняем файл
    Files.write(filePath, content, StandardOpenOption.CREATE_NEW);

    // Сохраняем метаданные (если есть)
    if (request.getMetadataJson() != null && !request.getMetadataJson().isEmpty()) {
      saveMetadata(finalFilename, request.getMetadataJson());
    }

    // Создаем ответ
    return SaveFileResponse.newBuilder()
      .setSuccess(true)
      .setMessage("File saved successfully")
      .setSavedFilename(finalFilename)
      .setFilePath(filePath.toAbsolutePath().toString())
      .setFileSize(content.length)
      .setSavedAt(LocalDateTime.now().format(DATE_FORMATTER))
      .build();
  }

  private void saveMetadata(String filename, String metadataJson) throws IOException {
    String metadataFilename = filename + ".meta.json";
    Path metadataPath = Paths.get(UPLOAD_DIR, metadataFilename);

    // Парсим и форматируем JSON для читаемости
    JsonObject json = new JsonObject(metadataJson);
    String formattedJson = json.encodePrettily();

    Files.write(metadataPath, formattedJson.getBytes(), StandardOpenOption.CREATE_NEW);
  }

  private String sanitizeFilename(String filename) {
    // Удаляем небезопасные символы из имени файла
    return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private SaveFileResponse createErrorResponse(String errorMessage) {
    return SaveFileResponse.newBuilder()
      .setSuccess(false)
      .setMessage("Error: " + errorMessage)
      .setSavedAt(LocalDateTime.now().format(DATE_FORMATTER))
      .build();
  }

}
