package com.example.handler.storage;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.Base64;
import java.util.function.Supplier;

public class GetPackHandler implements Handler<RoutingContext> {
  private final String filePath;
  private final Supplier<String> md5Supplier; // Теперь читает актуальное значение

  public GetPackHandler(String filePath, Supplier<String> md5Supplier) {
    this.filePath = filePath;
    this.md5Supplier = md5Supplier;
  }

  @Override
  public void handle(RoutingContext ctx) {
    String currentMd5 = md5Supplier.get();
    String contentMd5 = Base64.getEncoder().encodeToString(currentMd5.getBytes());

    ctx.response()
      .putHeader("X-Response-Status", "true")
      .putHeader("Content-MD5", contentMd5)
      .putHeader("X-File-MD5", currentMd5)
      .sendFile(filePath)
      .onFailure(err -> {
        if (!ctx.response().ended()) {
          ctx.response()
            .setStatusCode(err instanceof java.nio.file.NoSuchFileException ? 404 : 500)
            .putHeader("content-type", "application/json")
            .end(new JsonObject()
              .put("success", false)
              .put("error", "File access failed: " + err.getMessage())
              .encode());
        }
      });
  }
}
