package com.example.handler.admin;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class FilesListHandler implements Handler<RoutingContext> {
  private final Path uploadDir;

  public FilesListHandler(String uploadDir) {
    this.uploadDir = Paths.get(uploadDir);
  }

  @Override
  public void handle(RoutingContext ctx) {
    JsonArray files = new JsonArray();

    if (!Files.exists(uploadDir)) {
      sendError(ctx, 404, "Upload directory does not exist");
      return;
    }

    if (!Files.isDirectory(uploadDir)) {
      sendError(ctx, 500, "Upload path is not a directory");
      return;
    }

    try {
      Files.list(uploadDir)
        .filter(path -> path.toString().toLowerCase().endsWith(".json"))
        .forEach(path -> {
          try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            files.add(new JsonObject()
              .put("filename", path.getFileName().toString())
              .put("size", attrs.size())
              .put("created", attrs.creationTime().toMillis())
              .put("modified", attrs.lastModifiedTime().toMillis()));
          } catch (IOException e) {
            System.err.println("Can't read attrs: " + path.getFileName() + " → " + e.getMessage());
          }
        });

      ctx.json(new JsonObject()
        .put("directory", uploadDir.toAbsolutePath().toString())
        .put("total_files", files.size())
        .put("files", files));

    } catch (IOException e) {
      System.err.println("List error: " + e.getMessage());
      sendError(ctx, 500, "Failed to list files");
    }
  }

  private void sendError(RoutingContext ctx, int code, String msg) {
    ctx.response().setStatusCode(code).end(new JsonObject().put("error", msg).encode());
  }
}
