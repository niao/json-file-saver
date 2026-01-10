package com.example;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainVerticle extends VerticleBase {

  private static final String UPLOAD_DIR = "uploads/";
  private static final int HTTP_PORT = 8888;
  private static final int GRPC_PORT = 50051;
  private static final String HTTP_ENDPOINT = "/api/Users";

  @Override
  public Future<?> start() throws Exception {

    DeploymentOptions httpDeploymentOptions = new DeploymentOptions().setConfig(new JsonObject()
      .put("UPLOAD_DIR",UPLOAD_DIR)
      .put("GRPC_PORT", GRPC_PORT)
      .put("HTTP_PORT", HTTP_PORT)
      .put("HTTP_ENDPOINT",HTTP_ENDPOINT));

    DeploymentOptions grpcDeploymentOptions = new DeploymentOptions().setConfig(new JsonObject()
      .put("UPLOAD_DIR",UPLOAD_DIR)
      .put("GRPC_PORT", GRPC_PORT));

    // Создаем директорию для сохранения файлов
    createUploadDirectory();

    // Promise → Future для дочерних деплоев
    Future<Void> httpFuture = startHttpServer(httpDeploymentOptions);
    Future<Void> grpcFuture = startGrpcServer(grpcDeploymentOptions);
    Future<Void> simpleHttpFuture = startSimpleHttpServer(httpDeploymentOptions);

    // Ждем все Future
    return Future.all(httpFuture, grpcFuture, simpleHttpFuture)
      .mapEmpty()
      .andThen(ar -> {
        if (ar.succeeded()) {
          System.out.println("Both HTTP and gRPC servers started successfully!");
          System.out.println("HTTP Server: http://localhost:" + HTTP_PORT);
          System.out.println("gRPC Server: localhost:" + GRPC_PORT);
        }
      });
  }

  private Future<Void> startHttpServer(DeploymentOptions httpDeploymentOptions) {
    return vertx.deployVerticle(new RestRequestJsonFileSaver(), httpDeploymentOptions)
      .compose(deploymentId -> {  // deploymentId: String
        System.out.println("HTTP/REST Server deployed with ID: " + deploymentId);
        System.out.println("HTTP/REST started on port " + HTTP_PORT);
        return Future.succeededFuture();  // -> Future<Void>
      });
  }

  private Future<Void> startSimpleHttpServer(DeploymentOptions httpDeploymentOptions) {
    return vertx.deployVerticle(new SimpleHttpServer())
      .mapEmpty();
  }

  private Future<Void> startGrpcServer(DeploymentOptions grpcDeploymentOptions) {

    return vertx.deployVerticle(new FileServiceServer(), grpcDeploymentOptions)
      .compose(deploymentId -> {  // deploymentId: String
        System.out.println("gRPC Server deployed with ID: " + deploymentId);
        System.out.println("gRPC Server started on port " + GRPC_PORT);
        return Future.succeededFuture();  // -> Future<Void>
      });
  }


  private void createUploadDirectory() throws IOException {
    Path uploadPath = Paths.get(UPLOAD_DIR);
    if (!Files.exists(uploadPath)) {
      Files.createDirectories(uploadPath);
      System.out.println("Created upload directory: " + uploadPath.toAbsolutePath());
    }
  }

}
