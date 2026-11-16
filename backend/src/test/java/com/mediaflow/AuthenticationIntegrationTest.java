package com.mediaflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.mediaflow.security.JwtService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder encoder;
  @Autowired ObjectMapper json;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.mediaflow.service.UserService userService;

  private static String hash;

  @BeforeEach
  void seed() {
    if (hash == null) hash = encoder.encode("LocalTest@123456");
    db.update("DELETE FROM sys_user_role");
    db.update("DELETE FROM sys_user");
    for (int i = 1; i <= 3; i++) {
      db.update(
          "INSERT INTO"
              + " sys_user(id,username,password_hash,display_name,status,created_at,updated_at)"
              + " VALUES(?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          i,
          List.of("admin", "editor01", "reviewer01").get(i - 1),
          hash,
          "Test " + i);
      db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", i, i);
    }
  }

  private String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }

  private String token(String username) throws Exception {
    String result =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content(body(Map.of("username", username, "password", "LocalTest@123456"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(result).path("data").path("accessToken").asText();
  }

  @Test
  void validLoginAndProfileExcludePassword() throws Exception {
    String t = token("admin");
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("admin"))
        .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
  }

  @Test
  void wrongPasswordAndUnknownUser() throws Exception {
    for (String user : List.of("admin", "unknown"))
      mvc.perform(
              post("/api/auth/login")
                  .contentType("application/json")
                  .content(body(Map.of("username", user, "password", "WrongPassword"))))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(40100));
  }

  @Test
  void anonymousBlocked() throws Exception {
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void editorCannotAccessAdmin() throws Exception {
    String t = token("editor01");
    mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + t))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/admin/users")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/admin/users/1/roles")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content("{\"roles\":[\"EDITOR\"]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void reviewerCannotAccessAdmin() throws Exception {
    mvc.perform(get("/api/admin/roles").header("Authorization", "Bearer " + token("reviewer01")))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidAndExpiredJwt() throws Exception {
    var expired =
        new JwtService("test-only-random-looking-secret-not-for-deployment-0123456789", 1);
    String t = expired.issue(1L);
    Thread.sleep(1200);
    for (String bad :
        List.of(
            t,
            "invalid.token.value",
            "",
            new JwtService("different-signing-key-for-test-only-0123456789", 60).issue(1L)))
      mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + bad))
          .andExpect(status().isUnauthorized());
  }

  @Test
  void disableInvalidatesExistingTokenAndLogin() throws Exception {
    String a = token("admin"), e = token("editor01");
    mvc.perform(
            put("/api/admin/users/2")
                .header("Authorization", "Bearer " + a)
                .contentType("application/json")
                .content("{\"displayName\":\"Disabled\",\"status\":0}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + e))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(body(Map.of("username", "editor01", "password", "LocalTest@123456"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void roleChangesApplyToOldToken() throws Exception {
    String a = token("admin"), e = token("editor01");
    mvc.perform(
            put("/api/admin/users/2/roles")
                .header("Authorization", "Bearer " + a)
                .contentType("application/json")
                .content("{\"roles\":[\"ADMIN\",\"EDITOR\"]}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + e))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/admin/users/2/roles")
                .header("Authorization", "Bearer " + a)
                .contentType("application/json")
                .content("{\"roles\":[\"EDITOR\"]}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + e))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCreatesUserAndDuplicateConflicts() throws Exception {
    String t = token("admin");
    String b =
        body(
            Map.of(
                "username",
                "newuser",
                "displayName",
                "New User",
                "password",
                "NewUser@123456",
                "roles",
                List.of("EDITOR", "REVIEWER")));
    mvc.perform(
            post("/api/admin/users")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(b))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.roles.length()").value(2));
    mvc.perform(
            post("/api/admin/users")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(b))
        .andExpect(status().isConflict());
  }

  @Test
  void malformedInputsAndPaginationRejected() throws Exception {
    mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/auth/login").contentType("application/json").content("{"))
        .andExpect(status().isBadRequest());
    String t = token("admin");
    mvc.perform(get("/api/admin/users?page=0&size=1000").header("Authorization", "Bearer " + t))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/admin/users?size=not-a-number").header("Authorization", "Bearer " + t))
        .andExpect(status().isBadRequest());
  }

  @Test
  void missingResourcesAndSelfLockout() throws Exception {
    String t = token("admin");
    mvc.perform(get("/api/admin/users/999999").header("Authorization", "Bearer " + t))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/admin/users/1")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content("{\"displayName\":\"Admin\",\"status\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/admin/users/1/roles")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content("{\"roles\":[\"EDITOR\"]}"))
        .andExpect(status().isConflict());
  }

  @Test
  void unknownRoleIsRejectedAtomically() throws Exception {
    String t = token("admin");
    mvc.perform(
            put("/api/admin/users/2/roles")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content("{\"roles\":[\"ADMIN\",\"UNKNOWN\"]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/admin/users/2").header("Authorization", "Bearer " + t))
        .andExpect(jsonPath("$.data.roles[0]").value("EDITOR"));
  }

  @Test
  void openApiIsAvailable() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/auth/login']").exists());
  }

  @Test
  void serverErrorsReturnFailureEnvelope() throws Exception {
    String t = token("admin");
    try {
      org.mockito.Mockito.doThrow(new IllegalStateException("simulated")).when(userService).roles();
      mvc.perform(get("/api/admin/roles").header("Authorization", "Bearer " + t))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value(50000));
    } finally {
      org.mockito.Mockito.reset(userService);
    }
  }

  @Test
  void authenticationDatabaseFailureReturns500() throws Exception {
    String t = token("admin");
    try {
      org.mockito.Mockito.doThrow(
              new org.springframework.dao.DataAccessResourceFailureException("simulated"))
          .when(userService)
          .view(1L);
      mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + t))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value(50000));
    } finally {
      org.mockito.Mockito.reset(userService);
    }
  }
}
