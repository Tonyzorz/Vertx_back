package io.vertx.blog.first;

import io.vertx.core.json.JsonObject;

public class Whisky {

  private final int id;

  private String name;

  private String origin;

  public Whisky(String name, String origin) {
    this.name = name;
    this.origin = origin;
    this.id = -1;
  }

  public Whisky(JsonObject json) {
    //System.out.println("JSON : " + json.encodePrettily());
    this.id = json.getInteger("id");
    this.name = json.getString("name");
    this.origin = json.getString("origin");
  }

  public Whisky() {
    this.id = -1;
  }

  public Whisky(int id, String name, String origin) {
    this.id = id;
    this.name = name;
    this.origin = origin;
  }

  public String getName() {
    return name;
  }

  public String getOrigin() {
    return origin;
  }

  public int getId() {
    return id;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setOrigin(String origin) {
    this.origin = origin;
  }
}