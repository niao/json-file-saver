package com.example.handler.storage;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class SetMd5Handler implements Handler<RoutingContext> {
  private final Path packPath;
  private final Consumer<String> onMd5Update; // Принимает новое значение MD5
  private String currentMd5;

  public SetMd5Handler(String storageDir, String packFilePath, String initialMd5, Consumer<String> onMd5Update) {
    this.packPath = Paths.get(storageDir).resolve(packFilePath);
    this.currentMd5 = initialMd5;
    this.onMd5Update = onMd5Update;
  }

  @Override
  public void handle(RoutingContext ctx) {
    String md5Param = ctx.request().getParam("md5");

    if (md5Param == null || md5Param.isEmpty()) {
      // Режим: вычислить MD5 файла
      if (!Files.exists(packPath)) {
        sendError(ctx, 404, "File not found: " + packPath);
        return;
      }
      try {
        String calculated = com.example.util.Md5Util.calculate(packPath);
        // ✅ Обновляем текущее значение
        this.currentMd5 = calculated;
        // ✅ Уведомляем внешний компонент
        onMd5Update.accept(calculated);

        ctx.json(new JsonObject().put("file_md5", calculated));
      } catch (Exception e) {
        sendError(ctx, 500, "MD5 calc failed: " + e.getMessage());
      }
    } else {
      // Режим: установить вручную
      if (!md5Param.matches("[a-fA-F0-9]{32}")) {
        sendError(ctx, 400, "Invalid MD5 format: must be 32 hex chars");
        return;
      }
      // ✅ Сохраняем и передаём новое значение
      this.currentMd5 = md5Param.toLowerCase();
      onMd5Update.accept(this.currentMd5);

      ctx.json(new JsonObject()
        .put("success", true)
        .put("message", "MD5 set manually")
        .put("pack_file_md5", this.currentMd5));
    }
  }

  private void sendError(RoutingContext ctx, int code, String msg) {
    ctx.response().setStatusCode(code).end(new JsonObject().put("error", msg).encode());
  }

  public String getCurrentMd5() {
    return currentMd5;
  }
}
