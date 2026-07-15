package com.example.handler.admin;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class InfoHandler implements Handler<RoutingContext> {
  private final String uploadDir;
  private final int httpPort;
  private final int grpcPort;
  private final List<String> httpEndpoints;

  public InfoHandler(String uploadDir, int httpPort, int grpcPort, List<String> httpEndpoints) {
    this.uploadDir = uploadDir;
    this.httpPort = httpPort;
    this.grpcPort = grpcPort;
    this.httpEndpoints = httpEndpoints;
  }

  @Override
  public void handle(RoutingContext ctx) {
    long fileCount = 0;
    try {
      Path dir = Paths.get(uploadDir);
      if (Files.exists(dir) && Files.isDirectory(dir)) {
        fileCount = Files.list(dir).count();
      }
    } catch (IOException e) {
      System.err.println("Count error: " + e.getMessage());
    }

    JsonObject endpoints = new JsonObject();
    httpEndpoints.forEach(ep -> endpoints.put("POST " + ep, "Save JSON to file"));

    JsonObject response = new JsonObject()
      .put("service", "File Saver Service")
      .put("http_port", httpPort)
      .put("grpc_port", grpcPort)
      .put("upload_dir", Paths.get(uploadDir).toAbsolutePath().toString())
      .put("files_count", fileCount)
      .put("endpoints", endpoints);

    ctx.response()
      .putHeader("content-type", "application/json")
      .end(response.encode());
  }
}
