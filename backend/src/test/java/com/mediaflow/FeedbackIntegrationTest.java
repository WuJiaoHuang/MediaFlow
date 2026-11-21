package com.mediaflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.mediaflow.entity.*;
import com.mediaflow.mapper.*;
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
class FeedbackIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired JwtService jwt;
  @Autowired FileStorage storage;
  @Autowired PasswordEncoder passwords;
  @MockitoSpyBean EditingTaskLogMapper logMapper;
  @MockitoSpyBean TaskFeedbackLogMapper feedbackLogs;
  @MockitoSpyBean TaskFeedbackAttachmentMapper attachmentMapper;
  @MockitoSpyBean FileStorage fileStorage;
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
    for (String key :
        db.queryForList("SELECT storage_key FROM task_feedback_attachment", String.class))
      storage.delete(key);
    db.update("DELETE FROM task_feedback_log");
    db.update("DELETE FROM task_feedback_reply");
    db.update("DELETE FROM task_feedback_attachment");
    db.update("DELETE FROM task_feedback");
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

  long task() throws Exception {
    long id = create(2, 3).path("id").asLong();
    act(id, "start", a, 0, null, 200);
    return id;
  }

  String path(long t) {
    return "/api/tasks/" + t + "/feedback";
  }

  JsonNode text(long t, String token, String content, int expected) throws Exception {
    return data(
        mvc.perform(
                post(path(t))
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType("application/json")
                    .content(body(Map.of("type", "TEXT", "content", content, "creatorId", 1))))
            .andExpect(status().is(expected))
            .andReturn());
  }

  JsonNode mutate(long t, long f, String op, String token, int version, String reason, int expected)
      throws Exception {
    var req = new HashMap<String, Object>();
    req.put("version", version);
    req.put("reason", reason);
    return data(
        mvc.perform(
                post(path(t) + "/" + f + "/" + op)
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(body(req)))
            .andExpect(status().is(expected))
            .andReturn());
  }

  MockMultipartFile wav() {
    byte[] bytes = new byte[48];
    byte[] h =
        java.util.HexFormat.of()
            .parseHex(
                "524946462800000057415645666d74201000000001000100401f0000803e000002001000646174610400000000000000");
    return new MockMultipartFile("file", "voice.wav", "audio/wav", h);
  }

  JsonNode attachment(long t, String token, MockMultipartFile file, String kind, int expected)
      throws Exception {
    return data(
        mvc.perform(
                multipart(path(t))
                    .file(file)
                    .param("type", kind)
                    .param("content", "audio explanation")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().is(expected))
            .andReturn());
  }

  Cookie attachmentCookie(long t, long f, long id, String token) throws Exception {
    var res =
        mvc.perform(
                post(path(t) + "/" + f + "/attachments/" + id + "/access")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    String c = res.getResponse().getHeader("Set-Cookie").split(";", 2)[0];
    return new Cookie(c.substring(0, c.indexOf('=')), c.substring(c.indexOf('=') + 1));
  }

  @Test
  void textReplyAndIdentityLogs() throws Exception {
    long t = task();
    long f = text(t, r, "<img src=x onerror=alert(1)>", 201).path("feedback").path("id").asLong();
    mvc.perform(
            post(path(t) + "/" + f + "/replies")
                .header("Authorization", "Bearer " + a)
                .contentType("application/json")
                .content("{\"version\":0,\"content\":\"Fixed\",\"creatorId\":1}"))
        .andExpect(status().isOk());
    mvc.perform(get(path(t) + "/" + f + "/replies").header("Authorization", "Bearer " + r))
        .andExpect(jsonPath("$.data.records[0].creatorId").value(2));
    mvc.perform(get(path(t) + "/" + f + "/logs").header("Authorization", "Bearer " + r))
        .andExpect(jsonPath("$.data.total").value(2))
        .andExpect(jsonPath("$.data.records[0].operatorId").value(3));
  }

  @Test
  void allLegalStatesAndRequiredReason() throws Exception {
    long t = task(), f = text(t, r, "fix", 201).path("feedback").path("id").asLong();
    mutate(t, f, "resolve", a, 0, null, 200);
    mutate(t, f, "close", r, 1, null, 200);
    mutate(t, f, "reopen", r, 2, " ", 400);
    mutate(t, f, "reopen", r, 2, "still wrong", 200);
    mutate(t, f, "resolve", a, 3, null, 200);
    mutate(t, f, "reopen", r, 4, "again", 200);
    mutate(t, f, "resolve", a, 5, null, 200);
    mutate(t, f, "close", r, 6, null, 200);
  }

  @Test
  void illegalActionsAndWriteRoles() throws Exception {
    long t = task(), f = text(t, r, "fix", 201).path("feedback").path("id").asLong();
    mutate(t, f, "close", r, 0, null, 409);
    mutate(t, f, "resolve", r, 0, null, 403);
    mutate(t, f, "resolve", a, 0, null, 200);
    mutate(t, f, "close", a, 1, null, 403);
    mutate(t, f, "resolve", a, 1, null, 409);
  }

  @Test
  void authorCanConfirmOnlyWithCurrentTaskAccess() throws Exception {
    long t = task(), f = text(t, a, "self", 201).path("feedback").path("id").asLong();
    mutate(t, f, "resolve", a, 0, null, 200);
    mutate(t, f, "close", a, 1, null, 200);
    db.update("UPDATE editing_task SET assignee_id=4 WHERE id=?", t);
    mutate(t, f, "reopen", a, 2, "why", 404);
  }

  @Test
  void pendingAndCompletedAlwaysReadOnly() throws Exception {
    long t = create(2, 3).path("id").asLong();
    text(t, admin, "pending", 409);
    act(t, "start", a, 0, null, 200);
    long f = text(t, r, "fix", 201).path("feedback").path("id").asLong();
    mutate(t, f, "resolve", a, 0, null, 200);
    mutate(t, f, "close", r, 1, null, 200);
    act(t, "submit", a, 1, null, 200);
    act(t, "approve", r, 2, null, 200);
    text(t, admin, "complete", 409);
    mutate(t, f, "reopen", admin, 2, "no", 409);
    mutate(t, f, "replies", admin, 2, "no", 400);
    mvc.perform(get(path(t) + "/" + f).header("Authorization", "Bearer " + a))
        .andExpect(status().isOk());
  }

  @Test
  void reviewingOnlyReviewerMayCreate() throws Exception {
    long t = task();
    act(t, "submit", a, 1, null, 200);
    text(t, a, "not review", 403);
    text(t, r, "review", 201);
    text(t, admin, "admin", 201);
  }

  @Test
  void submitAndApprovalGatesAreReachable() throws Exception {
    long t = task(), f = text(t, r, "fix", 201).path("feedback").path("id").asLong();
    act(t, "submit", a, 1, null, 409);
    act(t, "submit", admin, 1, null, 400);
    mutate(t, f, "resolve", a, 0, null, 200);
    act(t, "submit", a, 1, null, 200);
    act(t, "approve", r, 2, null, 409);
    mutate(t, f, "close", r, 1, null, 200);
    act(t, "approve", r, 2, null, 200);
  }

  @Test
  void adminOverrideIsExplicitAndAuditedAndRejectKeepsStates() throws Exception {
    long t = task(), f = text(t, r, "fix", 201).path("feedback").path("id").asLong();
    act(t, "submit", admin, 1, "urgent acceptance", 200);
    assertEquals(
        "SUBMIT_OVERRIDE",
        db.queryForObject(
            "SELECT operation_type FROM editing_task_log WHERE task_id=? AND"
                + " operation_type='SUBMIT_OVERRIDE'",
            String.class,
            t));
    assertTrue(
        db.queryForObject(
                "SELECT reason FROM editing_task_log WHERE task_id=? AND"
                    + " operation_type='SUBMIT_OVERRIDE'",
                String.class,
                t)
            .contains("1"));
    act(t, "approve", admin, 2, null, 409);
    act(t, "reject", r, 2, "fix it", 200);
    assertEquals(
        "OPEN", db.queryForObject("SELECT status FROM task_feedback WHERE id=?", String.class, f));
  }

  @Test
  void unrelatedUsersAndCrossParentIdsHidden() throws Exception {
    long t = task(),
        f = text(t, r, "fix", 201).path("feedback").path("id").asLong(),
        other = create(4, 5).path("id").asLong();
    for (String token : List.of(b, s))
      for (String suffix :
          List.of("", "/summary", "/" + f, "/" + f + "/replies", "/" + f + "/logs"))
        mvc.perform(get(path(t) + suffix).header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    mutate(t, f, "resolve", b, 0, null, 404);
    mvc.perform(get(path(other) + "/" + f).header("Authorization", "Bearer " + admin))
        .andExpect(status().isNotFound());
    mvc.perform(get(path(t) + "/999999").header("Authorization", "Bearer " + a))
        .andExpect(status().isNotFound());
    text(999999, r, "missing", 404);
  }

  @Test
  void validatesContentTimecodeAndAssetMembership() throws Exception {
    long t = task();
    text(t, r, " ", 400);
    text(t, r, "x".repeat(4001), 400);
    for (String fields :
        List.of(
            "\"assetId\":999999",
            "\"assetId\":" + asset + ",\"timecodeMs\":1",
            "\"timecodeMs\":-1")) {
      mvc.perform(
              post(path(t))
                  .header("Authorization", "Bearer " + r)
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .contentType("application/json")
                  .content("{\"type\":\"TEXT\",\"content\":\"fix\"," + fields + "}"))
          .andExpect(status().is(fields.contains("999999") ? 404 : 400));
    }
  }

  @Test
  void sqlPaginationFiltersAndIdempotency() throws Exception {
    long t = task();
    String key = UUID.randomUUID().toString();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post(path(t))
                  .header("Authorization", "Bearer " + r)
                  .header("Idempotency-Key", key)
                  .contentType("application/json")
                  .content("{\"type\":\"TEXT\",\"content\":\"one\"}"))
          .andExpect(status().isCreated());
    text(t, a, "two", 201);
    mvc.perform(
            get(path(t))
                .param("size", "1")
                .param("direction", "ASC")
                .param("status", "OPEN")
                .header("Authorization", "Bearer " + a))
        .andExpect(jsonPath("$.data.total").value(2))
        .andExpect(jsonPath("$.data.records.length()").value(1))
        .andExpect(jsonPath("$.data.records[0].content").value("one"));
  }

  @Test
  void attachmentsRangeAndRevokedCookie() throws Exception {
    long t = task();
    var d = attachment(t, r, wav(), "AUDIO", 201);
    long f = d.path("feedback").path("id").asLong(), att = d.path("attachment").path("id").asLong();
    assertFalse(d.path("attachment").has("storageKey"));
    var c = attachmentCookie(t, f, att, a);
    String url = path(t) + "/" + f + "/attachments/" + att;
    mvc.perform(
            get(url + "/content")
                .servletPath(url + "/content")
                .cookie(c)
                .header("Range", "bytes=0-3"))
        .andExpect(status().isPartialContent());
    mvc.perform(get(url + "/download").servletPath(url + "/download").cookie(c))
        .andExpect(status().isOk());
    mvc.perform(get(url + "/content").header("Authorization", "Bearer " + b))
        .andExpect(status().isNotFound());
    mvc.perform(
            get(path(t) + "/" + f + "/attachments/999999/content")
                .header("Authorization", "Bearer " + a))
        .andExpect(status().isNotFound());
    db.update("UPDATE editing_task SET assignee_id=4 WHERE id=?", t);
    mvc.perform(get(url + "/content").servletPath(url + "/content").cookie(c))
        .andExpect(status().isNotFound());
    mvc.perform(post(url + "/access").header("Authorization", "Bearer " + a))
        .andExpect(status().isNotFound());
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM media_asset", Integer.class));
  }

  @Test
  void disabledAndRoleRevokedCookiesUseFreshIdentity() throws Exception {
    long t = task();
    var d = attachment(t, r, wav(), "AUDIO", 201);
    long f = d.path("feedback").path("id").asLong(), att = d.path("attachment").path("id").asLong();
    var c = attachmentCookie(t, f, att, a);
    String url = path(t) + "/" + f + "/attachments/" + att + "/content";
    db.update("DELETE FROM sys_user_role WHERE user_id=2");
    mvc.perform(get(url).servletPath(url).cookie(c)).andExpect(status().isNotFound());
    db.update("UPDATE sys_user SET status=0 WHERE id=2");
    mvc.perform(get(url).servletPath(url).cookie(c)).andExpect(status().isUnauthorized());
  }

  @Test
  void mimeExtensionAndSizeValidation() throws Exception {
    long t = task();
    attachment(
        t,
        r,
        new MockMultipartFile("file", "bad.wav", "audio/wav", "not audio".getBytes()),
        "AUDIO",
        415);
    attachment(
        t,
        r,
        new MockMultipartFile("file", "bad.mp3", "audio/wav", wav().getBytes()),
        "AUDIO",
        415);
    attachment(t, r, wav(), "VIDEO", 415);
    attachment(
        t,
        r,
        new MockMultipartFile("file", "huge.wav", "audio/wav", new byte[10485761]),
        "AUDIO",
        413);
    attachment(
        t,
        r,
        new MockMultipartFile("file", "../voice.wav", "audio/wav", wav().getBytes()),
        "AUDIO",
        400);
  }

  @Test
  void staleVersionAndConcurrentActionsOnlyOneWins() throws Exception {
    long t = task(), f = text(t, r, "fix", 201).path("feedback").path("id").asLong();
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> call =
          () ->
              mvc.perform(
                      post(path(t) + "/" + f + "/resolve")
                          .header("Authorization", "Bearer " + a)
                          .contentType("application/json")
                          .content("{\"version\":0}"))
                  .andReturn()
                  .getResponse()
                  .getStatus();
      var results = pool.invokeAll(List.of(call, call));
      var codes = new ArrayList<Integer>();
      for (var v : results) codes.add(v.get());
      Collections.sort(codes);
      assertEquals(List.of(200, 409), codes);
    } finally {
      pool.shutdownNow();
    }
    mutate(t, f, "close", r, 0, null, 409);
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM task_feedback_log WHERE feedback_id=?", Integer.class, f));
  }

  @Test
  void statusLogFailureRollsBackStateAndVersion() throws Exception {
    long t = task(), f = text(t, r, "fix", 201).path("feedback").path("id").asLong();
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("test failure"))
        .when(feedbackLogs)
        .insert(any(TaskFeedbackLog.class));
    mutate(t, f, "resolve", a, 0, null, 500);
    assertEquals(
        "OPEN", db.queryForObject("SELECT status FROM task_feedback WHERE id=?", String.class, f));
    assertEquals(
        0, db.queryForObject("SELECT version FROM task_feedback WHERE id=?", Integer.class, f));
  }

  @Test
  void attachmentDatabaseFailureCleansOnlyNewFile() throws Exception {
    long t = task();
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("test failure"))
        .when(attachmentMapper)
        .insert(any(TaskFeedbackAttachment.class));
    attachment(t, r, wav(), "AUDIO", 500);
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM task_feedback", Integer.class));
    verify(fileStorage).delete(anyString());
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM media_asset", Integer.class));
  }

  @Test
  void storageWriteFailureRollsBackFeedback() throws Exception {
    long t = task();
    doThrow(new IOException("injected write failure"))
        .when(fileStorage)
        .store(any(InputStream.class), anyLong(), anyString());
    attachment(t, r, wav(), "AUDIO", 500);
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM task_feedback", Integer.class));
    assertEquals(
        0, db.queryForObject("SELECT COUNT(*) FROM task_feedback_attachment", Integer.class));
  }

  @Test
  void referencedAssetCannotBeRemovedButOtherEditsSucceed() throws Exception {
    long t = task();
    mvc.perform(
            post(path(t))
                .header("Authorization", "Bearer " + r)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(body(Map.of("type", "TEXT", "content", "fix", "assetId", asset))))
        .andExpect(status().isCreated());
    long replacement = upload(false);
    mvc.perform(
            put("/api/tasks/" + t)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(body(request(2, 3, List.of(replacement), 1))))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/tasks/" + t)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(body(request(4, 3, List.of(asset, replacement), 1))))
        .andExpect(status().isOk());
    mvc.perform(get(path(t)).header("Authorization", "Bearer " + a))
        .andExpect(status().isNotFound());
  }

  MockMultipartFile recorded(String kind) throws Exception {
    try (var in = getClass().getResourceAsStream("/feedback-fixtures/" + kind + ".webm")) {
      return new MockMultipartFile("file", kind + ".webm", kind + "/webm", in.readAllBytes());
    }
  }

  @Test
  void genuineAudioAndVideoContainersAndCrossAttachment() throws Exception {
    long t = task();
    var audio = attachment(t, r, recorded("audio"), "AUDIO", 201);
    var video = attachment(t, r, recorded("video"), "VIDEO", 201);
    long f = video.path("feedback").path("id").asLong(),
        att = video.path("attachment").path("id").asLong();
    var cookie = attachmentCookie(t, f, att, a);
    String url = path(t) + "/" + f + "/attachments/" + att + "/content";
    mvc.perform(get(url).servletPath(url).cookie(cookie).header("Range", "bytes=0-15"))
        .andExpect(status().isPartialContent())
        .andExpect(header().string("Content-Type", "video/webm"));
    String swapped =
        path(t)
            + "/"
            + audio.path("feedback").path("id").asLong()
            + "/attachments/"
            + att
            + "/content";
    mvc.perform(get(swapped).servletPath(swapped).cookie(cookie))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(url).header("Authorization", "Bearer " + cookie.getValue()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    var wrong =
        new MockMultipartFile("file", "pretend.webm", "audio/webm", recorded("video").getBytes());
    attachment(t, r, wrong, "AUDIO", 415);
    wrong =
        new MockMultipartFile("file", "pretend.webm", "video/webm", recorded("audio").getBytes());
    attachment(t, r, wrong, "VIDEO", 415);
  }

  @Test
  void completedReplyAndClosedReplyAreRejectedWithoutLogs() throws Exception {
    long t = task(), f = text(t, r, "fix", 201).path("feedback").path("id").asLong();
    mutate(t, f, "resolve", a, 0, null, 200);
    mutate(t, f, "close", r, 1, null, 200);
    for (int state = 0; state < 2; state++) {
      if (state == 1) {
        act(t, "submit", a, 1, null, 200);
        act(t, "approve", r, 2, null, 200);
      }
      mvc.perform(
              post(path(t) + "/" + f + "/replies")
                  .header("Authorization", "Bearer " + a)
                  .contentType("application/json")
                  .content("{\"version\":2,\"content\":\"new reply\"}"))
          .andExpect(status().isConflict());
    }
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM task_feedback_reply", Integer.class));
  }

  @Test
  void dualRolesUseUnionForReadButSpecificIdentityForActions() throws Exception {
    long t = create(6, 3).path("id").asLong();
    act(t, "start", dual, 0, null, 200);
    long f = text(t, r, "fix", 201).path("feedback").path("id").asLong();
    mutate(t, f, "resolve", dual, 0, null, 200);
    mutate(t, f, "close", dual, 1, null, 403);
  }

  @Test
  void timecodeUsesRealTaskVideoAndArchivedHistoryIsRetained() throws Exception {
    var result =
        mvc.perform(
                multipart("/api/media/assets")
                    .file(recorded("video"))
                    .param("title", "video fixture")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .header("Authorization", "Bearer " + admin))
            .andExpect(status().isCreated())
            .andReturn();
    long video = data(result).path("id").asLong();
    long t = create(2, 3).path("id").asLong();
    mvc.perform(
            put("/api/tasks/" + t)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(body(request(2, 3, List.of(asset, video), 0))))
        .andExpect(status().isOk());
    act(t, "start", a, 1, null, 200);
    var d =
        data(
            mvc.perform(
                    post(path(t))
                        .header("Authorization", "Bearer " + r)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(
                            body(
                                Map.of(
                                    "type",
                                    "TEXT",
                                    "content",
                                    "at this position",
                                    "assetId",
                                    video,
                                    "timecodeMs",
                                    1000))))
                .andExpect(status().isCreated())
                .andReturn());
    long f = d.path("feedback").path("id").asLong();
    assertEquals(1000, d.path("feedback").path("timecodeMs").asLong());
    mvc.perform(
            post("/api/media/assets/" + video + "/archive")
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk());
    mvc.perform(get(path(t) + "/" + f).header("Authorization", "Bearer " + r))
        .andExpect(status().isOk());
    mvc.perform(
            post(path(t))
                .header("Authorization", "Bearer " + admin)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(body(Map.of("type", "TEXT", "content", "new", "assetId", video))))
        .andExpect(status().isBadRequest());
  }
}
