package com.mediaflow;

import static org.junit.jupiter.api.Assertions.*;

import com.mediaflow.security.JwtService;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  @Test
  void roundTrip() {
    var jwt = new JwtService("test-only-secret-at-least-32-bytes-long", 60);
    assertEquals(42L, jwt.verify(jwt.issue(42L)));
  }

  @Test
  void rejectsWeakConfiguration() {
    assertThrows(IllegalArgumentException.class, () -> new JwtService("short", 60));
    assertThrows(
        IllegalArgumentException.class,
        () -> new JwtService("test-only-secret-at-least-32-bytes-long", 7200));
  }

  @Test
  void tamperingRejected() {
    var jwt = new JwtService("test-only-secret-at-least-32-bytes-long", 60);
    var t = jwt.issue(1L).split("\\.");
    assertThrows(Exception.class, () -> jwt.verify(t[0] + ".eyJzdWIiOiI5OTkifQ." + t[2]));
  }
}
