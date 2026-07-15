package com.example.handler.rest;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class JsonSaveHandler implements Handler<RoutingContext> {
  private final String uploadDir;

  public JsonSaveHandler(String uploadDir) {
    this.uploadDir = uploadDir;
  }

  @Override
  public void handle(RoutingContext ctx) {
    HttpServerRequest request = ctx.request();

    // Только POST и application/json
    if (!"POST".equalsIgnoreCase(request.method().name())) {
      sendError(ctx, 405, "Method not allowed");
      return;
    }

    Buffer body = ctx.body().buffer();
    if (body == null || body.length() == 0) {
      sendError(ctx, 400, "Empty request body");
      return;
    }

    JsonObject json;
    try {
      json = new JsonObject(body.toString(StandardCharsets.UTF_8));
    } catch (Exception e) {
      sendError(ctx, 400, "Invalid JSON");
      return;
    }

    try {
      String fileName = UUID.randomUUID() + ".json";
      Path filePath = Paths.get(uploadDir, fileName);
      Files.createDirectories(filePath.getParent());
      Files.writeString(filePath, json.encodePrettily(), StandardCharsets.UTF_8);

      JsonObject response = new JsonObject()
        .put("success", true)
        .put("message", "File saved successfully")
        .put("filename", fileName)
        .put("path", filePath.toAbsolutePath().toString());

      ctx.response()
        .putHeader("content-type", "application/json")
        .end(response.encode());
    } catch (Exception e) {
      System.err.println("Save error: " + e.getMessage());
      sendError(ctx, 500, "Failed to save file");
    }
  }

  private void sendError(RoutingContext ctx, int code, String msg) {
    ctx.response()
      .setStatusCode(code)
      .putHeader("content-type", "application/json")
      .end(new JsonObject().put("error", msg).encode());
  }
}
