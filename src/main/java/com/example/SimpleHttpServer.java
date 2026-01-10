package com.example;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;

public class SimpleHttpServer extends VerticleBase{
  @Override
  public Future<?> start() {
    return vertx.createHttpServer().requestHandler(req -> {
      req.response()
        .putHeader("content-type", "text/plain")
        .end("Hello from Vert.x!");
    }).listen(8989).onSuccess(http -> {
      System.out.println("HTTP server started on port 8989");
    });
  }
}
