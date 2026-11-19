package com.mediaflow.security;

public class AccessContext {
  public Long userId;
  public boolean admin;
  public boolean editor;
  public boolean reviewer;

  public static AccessContext of(CurrentUser actor) {
    var c = new AccessContext();
    c.userId = actor.user().id();
    c.admin = actor.user().roles().contains("ADMIN");
    c.editor = actor.user().roles().contains("EDITOR");
    c.reviewer = actor.user().roles().contains("REVIEWER");
    return c;
  }
}
