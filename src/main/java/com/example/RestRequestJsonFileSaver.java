package com.example;

import com.example.config.ServiceConfig;
import com.example.handler.admin.FilesDeleteHandler;
import com.example.handler.admin.FilesListHandler;
import com.example.handler.admin.InfoHandler;
import com.example.handler.rest.JsonSaveHandler;
import com.example.handler.storage.GetPackHandler;
import com.example.handler.storage.SetMd5Handler;
import com.example.handler.storage.SetPackHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class RestRequestJsonFileSaver extends VerticleBase {

  private ServiceConfig config;
  private SetMd5Handler setMd5Handler;

  @Override
  public Future<?> start() throws Exception {
    config = new ServiceConfig(config());


    // Создаем обработчик MD5 с возможностью обновления
    setMd5Handler = new SetMd5Handler(
      config.getStorageDir(),
      config.getPackFilePath(),
      config.getPackFileMd5(),
      config::setPackFileMd5
    );

    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);

    GetPackHandler getPackHandler = new GetPackHandler(config.getAbsolutePackPath(), config::getPackFileMd5);


    // Добавляем обработчик тела запроса
//    router.route().consumes("application/json").handler(BodyHandler.create());
    // Только для нужных POST
    for (String ep : config.getHttpEndpoints()) {
      router.route(HttpMethod.POST, ep).handler(BodyHandler.create());
      router.post(ep).handler(new JsonSaveHandler(config.getUploadDir()));
    }

    String p = config.getPrefix();
    if (!p.endsWith("/")) p += "/";

    // Admin
    router.get(p).handler(new InfoHandler(config.getUploadDir(), config.getHttpPort(), config.getGrpcPort(), config.getHttpEndpoints()));
    // Admin: управление файлами
    router.get(p + "files").handler(new FilesListHandler(config.getUploadDir()));
    router.delete(p + "files").handler(new FilesDeleteHandler(config.getUploadDir()));
    // Storage
    router.get(p + "getpack").handler(getPackHandler);
    router.get(p + "setmd5").handler(setMd5Handler);
    router.get(p + "setmd5/:md5").handler(setMd5Handler);

    // Storage: загрузка pack.zip
    router.post(p + "setpack").handler(new SetPackHandler(
      config.getStorageDir(),
      config.getPackFilePath(),
      config::setPackFileMd5  // ← теперь обновляет центральное состояние
    ));


    return server.requestHandler(router)
      .listen(config.getHttpPort())
      .onSuccess(s -> System.out.println("HTTP Server started on port " + config.getHttpPort()))
      .onFailure(t -> System.err.println("HTTP Server failed: " + t.getMessage()));
  }

}
