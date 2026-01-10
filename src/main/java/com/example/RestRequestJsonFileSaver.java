package com.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class RestRequestJsonFileSaver extends VerticleBase {

  private String UPLOAD_DIR; //= config().getString("UPLOAD_DIR");
  private int HTTP_PORT; //= config().getInteger("HTTP_PORT", 8080);
  private int GRPC_PORT; // = config().getInteger("GRPC_PORT");
  private String HTTP_ENDPOINT;


  @Override
  public Future<?> start() throws Exception {

    UPLOAD_DIR = config().getString("UPLOAD_DIR");
    HTTP_ENDPOINT = config().getString("HTTP_ENDPOINT", "/");
    HTTP_PORT = config().getInteger("HTTP_PORT", 8080);
    GRPC_PORT = config().getInteger("GRPC_PORT");

    // Создаем HTTP сервер и роутер
    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);

    // Добавляем обработчик тела запроса
    router.route().handler(BodyHandler.create());

    // Обработчик POST запросов на корневой путь
    router.post(HTTP_ENDPOINT)
      .handler(routingContext -> {
        try {
          // Получаем тело запроса как JSON
          JsonObject requestBody = routingContext.body().asJsonObject();

          if (requestBody == null) {
            routingContext.response()
              .setStatusCode(400)
              .putHeader("content-type", "application/json")
              .end(new JsonObject()
                .put("error", "Request body must be JSON")
                .encode());
            return;
          }

          // Генерируем UUID для имени файла
          String fileName = UUID.randomUUID().toString() + ".json";
          Path filePath = Paths.get(UPLOAD_DIR + fileName);

          // Сохраняем JSON в файл
          String jsonString = requestBody.encodePrettily(); // Красивое форматирование
          Files.writeString(filePath, jsonString);

          // Отправляем ответ клиенту
          JsonObject response = new JsonObject()
            .put("message", "File saved successfully")
            .put("filename", fileName)
            .put("path", filePath.toString());

          routingContext.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(response.encode());

        } catch (IOException e) {
          System.err.println("Error saving file: " + e.getMessage());
          routingContext.response()
            .setStatusCode(500)
            .putHeader("content-type", "application/json")
            .end(new JsonObject()
              .put("error", "Failed to save file")
              .encode());
        }
      });

    // Информационная страница
    router.get("/")
      .handler(routingContext -> {
        JsonObject response = new JsonObject()
          .put("service", "File Saver Service")
          .put("http_port", HTTP_PORT)
          .put("grpc_port", GRPC_PORT)
          .put("endpoints", new JsonObject()
            .put("POST "+HTTP_ENDPOINT, "Save JSON to file")
            .put("gRPC", new JsonObject()
              .put("SaveFileUnary", "Unary RPC call")
              .put("SaveFileStream", "Server streaming")
              .put("SaveFileBidirectional", "Bidirectional streaming")));

        routingContext.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
      });

    // Запускаем сервер
    return server.requestHandler(router)
      .listen(HTTP_PORT)
      .onSuccess(handle->{
        System.out.println("HTTP/REST Server started on port " + HTTP_PORT);
        System.out.println("Upload directory: " + Paths.get(UPLOAD_DIR).toAbsolutePath());
      }).onFailure(handle-> {
        System.out.println("HTTP/REST Sever fail with: " + handle.getMessage());
      });
  }


//  public static void main(String[] args) {
//    Vertx vertx = Vertx.vertx();
//    vertx.deployVerticle(new JsonFileSaver(), res -> {
//      if (res.succeeded()) {
//        System.out.println("Application deployed successfully!");
//      } else {
//        System.err.println("Failed to deploy application: " + res.cause());
//      }
//    });
//  }
}
