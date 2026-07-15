package com.example.config;

import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ServiceConfig {
  private final String uploadDir;
  private final String storageDir;
  private final int httpPort;
  private final int grpcPort;
  private final List<String> httpEndpoints;
  private final String prefix;
  private final String packFilePath;
  private volatile String packFileMd5; // mutable

  public ServiceConfig(JsonObject config) {
    this.uploadDir = sanitizePath(config.getString("UPLOAD_DIR", "./uploads/"));
    this.storageDir = sanitizePath(config.getString("STORAGE_DIR", "./storage/"));
    this.httpPort = config.getInteger("HTTP_PORT", 8888);
    this.grpcPort = config.getInteger("GRPC_PORT", 50051);
    this.httpEndpoints = parseEndpoints(config.getString("HTTP_ENDPOINT", "/save"));
    this.prefix = config.getString("PREFIX", "/");
    this.packFilePath = config.getString("PACK_FILE_PATH", "pack.zip");
    this.packFileMd5 = config.getString("PACK_FILE_MD5", "7bbce66cdc6de3bd07f0798e27dfa262");
  }

  private String sanitizePath(String path) {
    return path.endsWith("/") ? path : path + "/";
  }

  private List<String> parseEndpoints(String endpointsStr) {
    return Arrays.stream(endpointsStr.split(","))
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.toList());
  }

  // Getters
  public String getUploadDir() { return uploadDir; }
  public String getStorageDir() { return storageDir; }
  public int getHttpPort() { return httpPort; }
  public int getGrpcPort() { return grpcPort; }
  public List<String> getHttpEndpoints() { return httpEndpoints; }
  public String getPrefix() { return prefix; }
  public String getPackFilePath() { return packFilePath; }
  public String getPackFileMd5() { return packFileMd5; }
  public void setPackFileMd5(String md5) { this.packFileMd5 = md5.toLowerCase(); }

  // Utility
  public String getAbsolutePackPath() {
    return getStorageDir() + getPackFilePath();
  }
}
