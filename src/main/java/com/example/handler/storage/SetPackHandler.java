package com.example.handler.storage;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class SetPackHandler implements Handler<RoutingContext> {
  private static final long MAX_FILE_SIZE = 200 * 1024 * 1024; // 200 MB
  private final Path targetPath;
  private final Consumer<String> onMd5Update; // Callback: когда MD5 обновляется

  public SetPackHandler(String storageDir, String packFilePath, Consumer<String> onMd5Update) {
    this.targetPath = Paths.get(storageDir).resolve(packFilePath);
    this.onMd5Update = onMd5Update;
  }

  @Override
  public void handle(RoutingContext ctx) {
    HttpServerRequest request = ctx.request();

    // Проверяем Content-Length
    String contentLengthStr = request.getHeader("Content-Length");
    if (contentLengthStr == null) {
      sendError(ctx, 411, "Content-Length header is required");
      return;
    }

    long contentLength;
    try {
      contentLength = Long.parseLong(contentLengthStr);
    } catch (NumberFormatException e) {
      sendError(ctx, 400, "Invalid Content-Length header");
      return;
    }

    if (contentLength > MAX_FILE_SIZE) {
      sendError(ctx, 413, "File too large: " + contentLength + " bytes. Max allowed: " + MAX_FILE_SIZE);
      return;
    }

    // Читаем тело
    ctx.request().bodyHandler(buffer -> saveFile(ctx, buffer));
  }

  private void saveFile(RoutingContext ctx, Buffer buffer) {
    byte[] data = buffer.getBytes();
    if (data.length > MAX_FILE_SIZE) {
      sendError(ctx, 413, "Received data exceeds 200 MB limit");
      return;
    }

    try {
      Files.createDirectories(targetPath.getParent());
      Files.write(targetPath, data);

      // Пересчитываем MD5
      String newMd5 = com.example.util.Md5Util.calculate(targetPath);
      onMd5Update.accept(newMd5); // Обновляем конфиг

      JsonObject response = new JsonObject()
        .put("success", true)
        .put("message", "File saved successfully")
        .put("path", targetPath.toAbsolutePath().toString())
        .put("file_size", data.length)
        .put("file_md5", newMd5);

      ctx.response()
        .putHeader("content-type", "application/json")
        .end(response.encode());

    } catch (IOException e) {
      System.err.println("Write error: " + e.getMessage());
      sendError(ctx, 500, "Failed to save file: " + e.getMessage());
    } catch (Exception e) {
      System.err.println("MD5 error: " + e.getMessage());
      sendError(ctx, 500, "Failed to calculate MD5");
    }
  }

  private void sendError(RoutingContext ctx, int statusCode, String message) {
    if (!ctx.response().ended()) {
      ctx.response()
        .setStatusCode(statusCode)
        .putHeader("content-type", "application/json")
        .end(new JsonObject().put("error", message).encode());
    }
  }
}
