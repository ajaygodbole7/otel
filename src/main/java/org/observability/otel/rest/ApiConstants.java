package org.observability.otel.rest;

public final class ApiConstants {
  public static final class ApiPath {
    public static final String BASE_V1_API_PATH = "/api/v1";
    public static final String CUSTOMERS = "/customers";
    public static final String ID_PATH_VAR = "/{id}";

    private ApiPath() {}
  }

  private ApiConstants() {}
}
