package com.mediaflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.mediaflow.entity.EditingTaskLog;
import com.mediaflow.mapper.EditingTaskLogMapper;
import com.mediaflow.security.JwtService;
import com.mediaflow.storage.FileStorage;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EditingTaskIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired JwtService jwt;
  @Autowired FileStorage storage;
  @Autowired PasswordEncoder passwords;
  @MockitoSpyBean EditingTaskLogMapper logMapper;
  String admin, a, b, r, s, dual;
  static String hash;
  long asset;

  @BeforeEach
  void seed() throws Exception {
    cleanup();
    db.update("DELETE FROM sys_user_role");
    db.update("DELETE FROM sys_user");
    if (hash == null) hash = passwords.encode(UUID.randomUUID().toString());
    for (int id = 1; id <= 7; id++) {
      db.update(
          "INSERT INTO"
              + " sys_user(id,username,password_hash,display_name,status,created_at,updated_at)"
              + " VALUES(?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          id,
          "task_user_" + id,
          hash,
          "Task user " + id,
          id == 7 ? 0 : 1);
      int role = id == 1 ? 1 : (id == 3 || id == 5) ? 3 : 2;
      db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", id, role);
    }
    db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(6,3)");
    admin = jwt.issue(1L);
    a = jwt.issue(2L);
    r = jwt.issue(3L);
    b = jwt.issue(4L);
    s = jwt.issue(5L);
    dual = jwt.issue(6L);
    asset = upload(true);
  }

  @AfterEach
  void cleanup() throws Exception {
    db.update("DELETE FROM editing_task_log");
    db.update("DELETE FROM editing_task_asset");
    db.update("DELETE FROM editing_task");
    for (String key : db.queryForList("SELECT storage_key FROM media_file", String.class))
      storage.delete(key);
    db.update("DELETE FROM media_change_log");
    db.update("DELETE FROM media_asset");
    db.update("DELETE FROM media_file");
    db.update("UPDATE media_category SET parent_id=NULL");
    db.update("DELETE FROM media_category");
  }

  String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }

  JsonNode data(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString()).path("data");
  }

  long upload(boolean restricted) throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    var result =
        mvc.perform(
                multipart("/api/media/assets")
                    .file(new MockMultipartFile("file", "task.png", "image/png", out.toByteArray()))
                    .param("title", "Task fixture")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .header("Authorization", "Bearer " + admin))
            .andExpect(status().isCreated())
            .andReturn();
    long id = data(result).path("id").asLong();
    if (restricted)
      mvc.perform(
              put("/api/media/assets/" + id + "/scope")
                  .header("Authorization", "Bearer " + admin)
                  .contentType("application/json")
                  .content("{\"accessScope\":\"RESTRICTED\",\"version\":0}"))
          .andExpect(status().isOk());
    return id;
  }

  Map<String, Object> request(long executor, long reviewer, List<Long> ids, Integer version) {
    var req = new LinkedHashMap<String, Object>();
    req.put("title", "Editing task");
    req.put("description", "Cut a short highlight");
    req.put("assigneeId", executor);
    req.put("reviewerId", reviewer);
    req.put("priority", "NORMAL");
    req.put("dueAt", null);
    req.put("assetIds", ids);
    req.put("version", version);
    return req;
  }

  JsonNode create(long executor, long reviewer) throws Exception {
    return data(
        mvc.perform(
                post("/api/tasks")
                    .header("Authorization", "Bearer " + admin)
                    .contentType("application/json")
                    .content(body(request(executor, reviewer, List.of(asset), null))))
            .andExpect(status().isCreated())
            .andReturn());
  }

  JsonNode act(long id, String action, String token, int version, String reason, int status)
      throws Exception {
    var req = new LinkedHashMap<String, Object>();
    req.put("version", version);
    req.put("reason", reason);
    req.put("operatorId", 1);
    return data(
        mvc.perform(
                post("/api/tasks/" + id + "/" + action)
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(body(req)))
            .andExpect(status().is(status))
            .andReturn());
  }

  Cookie cookie(long id, Long task, String token) throws Exception {
    var req =
        post("/api/media/assets/" + id + "/access").header("Authorization", "Bearer " + token);
    if (task != null) req.param("taskId", task.toString());
    var result = mvc.perform(req).andExpect(status().isOk()).andReturn();
    String value = result.getResponse().getHeader("Set-Cookie").split(";", 2)[0];
    return new Cookie(
        value.substring(0, value.indexOf('=')), value.substring(value.indexOf('=') + 1));
  }

  @Test
  void validCreationAndAllDetailsRequireCurrentGrant() throws Exception {
    long id = create(2, 3).path("id").asLong();
    create(4, 5);
    mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + a))
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(get("/api/tasks/" + id).header("Authorization", "Bearer " + a))
        .andExpect(status().isOk());
    for (String token : List.of(b, s)) {
      for (String suffix : List.of("", "/assets", "/logs"))
        mvc.perform(get("/api/tasks/" + id + suffix).header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
      act(id, "start", token, 0, null, 404);
    }
    mvc.perform(get("/api/tasks/" + id + "/assets").header("Authorization", "Bearer " + r))
        .andExpect(jsonPath("$.data[0].id").value(asset));
    mvc.perform(get("/api/tasks/" + id + "/logs").header("Authorization", "Bearer " + a))
        .andExpect(jsonPath("$.data.records[0].operationType").value("CREATE"));
  }

  @Test
  void validatesPersonnelDuplicateArchivedAndRequestParameters() throws Exception {
    for (var req :
        List.of(
            request(1, 3, List.of(asset), null),
            request(7, 3, List.of(asset), null),
            request(99999, 3, List.of(asset), null),
            request(2, 2, List.of(asset), null),
            request(2, 3, List.of(asset, asset), null),
            request(2, 3, List.of(), null),
            request(2, 3, List.of(99999L), null))) {
      mvc.perform(
              post("/api/tasks")
                  .header("Authorization", "Bearer " + admin)
                  .contentType("application/json")
                  .content(body(req)))
          .andExpect(status().isBadRequest());
    }
    var req = request(2, 3, List.of(asset), null);
    req.put("priority", "BROKEN");
    mvc.perform(
            post("/api/tasks")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(body(req)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/media/assets/" + asset + "/archive")
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/tasks")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(body(request(2, 3, List.of(asset), null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void cannotCreateOrSelfAssignAsOrdinaryUser() throws Exception {
    for (String token : List.of(a, r)) {
      mvc.perform(
              post("/api/tasks")
                  .header("Authorization", "Bearer " + token)
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/tasks/people").header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    }
    long id = create(2, 3).path("id").asLong();
    mvc.perform(
            put("/api/tasks/" + id)
                .header("Authorization", "Bearer " + a)
                .contentType("application/json")
                .content(body(request(4, 5, List.of(asset), 0))))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminOnlyUpdateDoesNotRevealTaskExistence() throws Exception {
    long id = create(4, 5).path("id").asLong();
    for (long target : List.of(id, 99999999L))
      mvc.perform(
              put("/api/tasks/" + target)
                  .header("Authorization", "Bearer " + a)
                  .contentType("application/json")
                  .content(body(request(2, 3, List.of(asset), 0))))
          .andExpect(status().isForbidden());
  }

  @Test
  void completeStateMachineRejectReasonAndCompletedImmutability() throws Exception {
    long id = create(2, 3).path("id").asLong();
    act(id, "approve", admin, 0, null, 409);
    act(id, "submit", a, 0, null, 409);
    act(id, "start", r, 0, null, 403);
    assertEquals("PROCESSING", act(id, "start", a, 0, null, 200).path("status").asText());
    act(id, "submit", a, 0, null, 409);
    act(id, "submit", a, 1, null, 200);
    act(id, "approve", a, 2, null, 403);
    act(id, "reject", r, 2, " ", 400);
    act(id, "reject", r, 2, "Please shorten the opening", 200);
    assertEquals(
        "PROCESSING",
        db.queryForObject("SELECT status FROM editing_task WHERE id=?", String.class, id));
    act(id, "submit", a, 3, null, 200);
    assertEquals("COMPLETED", act(id, "approve", r, 4, null, 200).path("status").asText());
    act(id, "start", admin, 5, null, 409);
    act(id, "reject", admin, 5, "Reopen", 409);
    mvc.perform(
            put("/api/tasks/" + id)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(body(request(2, 3, List.of(asset), 5))))
        .andExpect(status().isConflict());
    assertEquals(
        2L,
        db.queryForObject(
            "SELECT operator_id FROM editing_task_log WHERE task_id=? AND operation_type='START'",
            Long.class,
            id));
    assertEquals(
        "Please shorten the opening",
        db.queryForObject(
            "SELECT reason FROM editing_task_log WHERE task_id=? AND operation_type='REJECT'",
            String.class,
            id));
    assertEquals(
        6,
        db.queryForObject(
            "SELECT COUNT(*) FROM editing_task_log WHERE task_id=?", Integer.class, id));
  }

  @Test
  void reassignRevokesAllRestrictedEntrypointsAndExistingCookies() throws Exception {
    long id = create(2, 3).path("id").asLong();
    Cookie global = cookie(asset, null, a), context = cookie(asset, id, a);
    mvc.perform(
            put("/api/tasks/" + id)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(body(request(4, 5, List.of(asset), 0))))
        .andExpect(status().isOk());
    for (String suffix : List.of("", "/assets", "/logs"))
      mvc.perform(get("/api/tasks/" + id + suffix).header("Authorization", "Bearer " + a))
          .andExpect(status().isNotFound());
    mvc.perform(get("/api/media/assets/" + asset).header("Authorization", "Bearer " + a))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/media/assets").header("Authorization", "Bearer " + a))
        .andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(get("/api/media/stats").header("Authorization", "Bearer " + a))
        .andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(get("/api/media/changes").header("Authorization", "Bearer " + a))
        .andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(get("/api/media/changes?assetId=" + asset).header("Authorization", "Bearer " + a))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/media/assets/" + asset + "/access").header("Authorization", "Bearer " + a))
        .andExpect(status().isNotFound());
    for (String ending : List.of("content", "download")) {
      String path = "/api/media/assets/" + asset + "/" + ending;
      mvc.perform(get(path).servletPath(path).cookie(global)).andExpect(status().isNotFound());
      mvc.perform(get(path).servletPath(path).param("taskId", String.valueOf(id)).cookie(context))
          .andExpect(status().isNotFound());
    }
    mvc.perform(get("/api/tasks/" + id).header("Authorization", "Bearer " + b))
        .andExpect(status().isOk());
    mvc.perform(get("/api/tasks/" + id).header("Authorization", "Bearer " + r))
        .andExpect(status().isNotFound());
  }

  @Test
  void taskCookieCannotSwitchTaskOrDropContextAndAssetsMustBeLinked() throws Exception {
    long id = create(2, 3).path("id").asLong();
    long unrelated = upload(true);
    Cookie c = cookie(asset, id, a);
    String path = "/api/media/assets/" + asset + "/content";
    mvc.perform(get(path).servletPath(path).cookie(new Cookie("mf_preview_" + asset, c.getValue())))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get(path)
                .servletPath(path)
                .param("taskId", String.valueOf(id + 1))
                .cookie(new Cookie("mf_preview_" + asset + "_task_" + (id + 1), c.getValue())))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/media/assets/" + unrelated)
                .param("taskId", String.valueOf(id))
                .header("Authorization", "Bearer " + a))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/media/assets/" + unrelated + "/access")
                .param("taskId", String.valueOf(id))
                .header("Authorization", "Bearer " + a))
        .andExpect(status().isNotFound());
  }

  @Test
  void sharedLibraryAccessIsIndependentButCannotBypassTaskContext() throws Exception {
    long shared = upload(false);
    long id =
        data(mvc.perform(
                    post("/api/tasks")
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content(body(request(2, 3, List.of(shared), null))))
                .andExpect(status().isCreated())
                .andReturn())
            .path("id")
            .asLong();
    Cookie context = cookie(shared, id, a);
    mvc.perform(
            put("/api/tasks/" + id)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(body(request(4, 3, List.of(shared), 0))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/media/assets/" + shared).header("Authorization", "Bearer " + a))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/media/assets/" + shared)
                .param("taskId", String.valueOf(id))
                .header("Authorization", "Bearer " + a))
        .andExpect(status().isNotFound());
    String path = "/api/media/assets/" + shared + "/content";
    mvc.perform(get(path).servletPath(path).param("taskId", String.valueOf(id)).cookie(context))
        .andExpect(status().isNotFound());
  }

  @Test
  void multipleRolesUnionWithoutDuplicatePaginationAndActionSeparation() throws Exception {
    create(6, 3);
    long reviewTask = create(2, 6).path("id").asLong();
    create(6, 6);
    mvc.perform(get("/api/tasks?size=2").header("Authorization", "Bearer " + dual))
        .andExpect(jsonPath("$.data.total").value(3))
        .andExpect(jsonPath("$.data.records.length()").value(2));
    act(reviewTask, "start", dual, 0, null, 403);
    act(reviewTask, "start", a, 0, null, 200);
    act(reviewTask, "submit", a, 1, null, 200);
    act(reviewTask, "approve", dual, 2, null, 200);
  }

  @Test
  void roleChangeRevokesGrantWithoutWaitingForAccessTokenExpiry() throws Exception {
    long id = create(4, 3).path("id").asLong();
    Cookie c = cookie(asset, id, b);
    db.update("DELETE FROM sys_user_role WHERE user_id=4");
    db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(4,3)");
    mvc.perform(get("/api/tasks/" + id).header("Authorization", "Bearer " + b))
        .andExpect(status().isNotFound());
    String path = "/api/media/assets/" + asset + "/content";
    mvc.perform(get(path).servletPath(path).param("taskId", String.valueOf(id)).cookie(c))
        .andExpect(status().isNotFound());
  }

  @Test
  void updatesRecordEveryChangedFieldAndNoopDoesNotInventLogs() throws Exception {
    long id = create(2, 3).path("id").asLong();
    mvc.perform(
            put("/api/tasks/" + id)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(body(request(2, 3, List.of(asset), 0))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.version").value(0));
    long next = upload(true);
    var req = request(4, 5, List.of(next), 0);
    req.put("title", "Updated title");
    req.put("description", "New requirements");
    req.put("priority", "URGENT");
    req.put("dueAt", "2027-01-01T12:00:00");
    mvc.perform(
            put("/api/tasks/" + id)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(body(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.version").value(1));
    assertEquals(
        8,
        db.queryForObject(
            "SELECT COUNT(*) FROM editing_task_log WHERE task_id=?", Integer.class, id));
    assertEquals(
        "2",
        db.queryForObject(
            "SELECT old_value FROM editing_task_log WHERE task_id=? AND"
                + " operation_type='ASSIGNEE_CHANGED'",
            String.class,
            id));
    assertEquals(
        "4",
        db.queryForObject(
            "SELECT new_value FROM editing_task_log WHERE task_id=? AND"
                + " operation_type='ASSIGNEE_CHANGED'",
            String.class,
            id));
  }

  @Test
  void auditFailureRollsBackStatusAndCreation() throws Exception {
    long id = create(2, 3).path("id").asLong();
    try {
      doThrow(
              new org.springframework.dao.DataAccessResourceFailureException(
                  "simulated audit failure"))
          .when(logMapper)
          .insert(any(EditingTaskLog.class));
      act(id, "start", a, 0, null, 500);
      mvc.perform(
              post("/api/tasks")
                  .header("Authorization", "Bearer " + admin)
                  .contentType("application/json")
                  .content(body(request(2, 3, List.of(asset), null))))
          .andExpect(status().isInternalServerError());
    } finally {
      reset(logMapper);
    }
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM editing_task", Integer.class));
    assertEquals(
        "PENDING",
        db.queryForObject("SELECT status FROM editing_task WHERE id=?", String.class, id));
    assertEquals(
        0, db.queryForObject("SELECT version FROM editing_task WHERE id=?", Integer.class, id));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM editing_task_log", Integer.class));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM editing_task_asset", Integer.class));
  }

  @Test
  void concurrentActionsWithSameVersionCommitOnce() throws Exception {
    long id = create(2, 3).path("id").asLong();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      var gate = new CountDownLatch(1);
      Callable<Integer> action =
          () -> {
            gate.await();
            return mvc.perform(
                    post("/api/tasks/" + id + "/start")
                        .header("Authorization", "Bearer " + a)
                        .contentType("application/json")
                        .content("{\"version\":0}"))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var first = pool.submit(action);
      var second = pool.submit(action);
      gate.countDown();
      var outcomes =
          new ArrayList<>(
              List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
      Collections.sort(outcomes);
      assertEquals(List.of(200, 409), outcomes);
    } finally {
      pool.shutdownNow();
    }
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM editing_task_log WHERE operation_type='START'", Integer.class));
  }

  @Test
  void filtersSortPaginationStillRespectSqlPermissions() throws Exception {
    long first = create(2, 3).path("id").asLong();
    create(4, 5);
    act(first, "start", a, 0, null, 200);
    mvc.perform(
            get("/api/tasks?status=PROCESSING&priority=NORMAL&assigneeId=2&reviewerId=3&sort=priority&direction=asc&size=1")
                .header("Authorization", "Bearer " + admin))
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(get("/api/tasks?assigneeId=4").header("Authorization", "Bearer " + a))
        .andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(get("/api/tasks?sort=DROP").header("Authorization", "Bearer " + admin))
        .andExpect(status().isBadRequest());
  }
}
