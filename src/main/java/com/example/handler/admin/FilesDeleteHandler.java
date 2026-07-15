package com.example.handler.admin;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class FilesDeleteHandler implements Handler<RoutingContext> {
  private final Path uploadDir;

  public FilesDeleteHandler(String uploadDir) {
    this.uploadDir = Paths.get(uploadDir);
  }

  @Override
  public void handle(RoutingContext ctx) {
    if (!Files.exists(uploadDir)) {
      sendError(ctx, 404, "Upload directory not found");
      return;
    }

    if (!Files.isDirectory(uploadDir)) {
      sendError(ctx, 500, "Upload path is not a directory");
      return;
    }

    try {
      List<Path> filesToDelete = Files.list(uploadDir)
        .filter(path -> path.toString().toLowerCase().endsWith(".json"))
        .collect(Collectors.toList());

      int deletedCount = 0;
      for (Path file : filesToDelete) {
        Files.delete(file);
        deletedCount++;
      }

      ctx.json(new JsonObject()
        .put("success", true)
        .put("message", "Cleanup completed")
        .put("deleted_files", deletedCount));

    } catch (IOException e) {
      System.err.println("Delete error: " + e.getMessage());
      sendError(ctx, 500, "Failed to delete files: " + e.getMessage());
    }
  }

  private void sendError(RoutingContext ctx, int code, String msg) {
    ctx.response().setStatusCode(code).end(new JsonObject().put("error", msg).encode());
  }
}
