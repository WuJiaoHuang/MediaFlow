package com.mediaflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.mediaflow.entity.*;
import com.mediaflow.mapper.*;
import com.mediaflow.security.JwtService;
import com.mediaflow.storage.FileStorage;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
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
class MediaIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired PasswordEncoder encoder;
  @Autowired FileStorage storage;
  @Autowired JwtService jwt;
  @MockitoSpyBean MediaAssetMapper assets;
  @MockitoSpyBean MediaFileMapper files;
  @MockitoSpyBean RoleMapper roles;
  static String hash;
  String admin, editor, reviewer;

  @BeforeEach
  void seed() throws Exception {
    cleanup();
    db.update("DELETE FROM sys_user_role");
    db.update("DELETE FROM sys_user");
    if (hash == null) hash = encoder.encode("LocalTest@123456");
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
    admin = jwt.issue(1L);
    editor = jwt.issue(2L);
    reviewer = jwt.issue(3L);
  }

  @AfterEach
  void cleanup() throws Exception {
    for (String key : db.queryForList("SELECT storage_key FROM media_file", String.class))
      storage.delete(key);
    db.update("DELETE FROM media_change_log");
    db.update("DELETE FROM media_asset");
    db.update("DELETE FROM media_file");
    db.update("UPDATE media_category SET parent_id=NULL");
    db.update("DELETE FROM media_category");
  }

  String body(Object v) throws Exception {
    return json.writeValueAsString(v);
  }

  JsonNode data(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString()).path("data");
  }

  byte[] png() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  MockMultipartFile image() throws Exception {
    return new MockMultipartFile("file", "sample.png", "image/png", png());
  }

  JsonNode upload(String token, String title, Long category) throws Exception {
    var r =
        multipart("/api/media/assets")
            .file(image())
            .param("title", title)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", "Bearer " + token);
    if (category != null) r.param("categoryId", category.toString());
    return data(mvc.perform(r).andExpect(status().isCreated()).andReturn());
  }

  long category(String name, Long parent) throws Exception {
    var value = new LinkedHashMap<String, Object>();
    value.put("categoryName", name);
    value.put("sortOrder", 1);
    value.put("status", 1);
    value.put("parentId", parent);
    return data(mvc.perform(
                post("/api/media/categories")
                    .header("Authorization", "Bearer " + admin)
                    .contentType("application/json")
                    .content(body(value)))
            .andExpect(status().isCreated())
            .andReturn())
        .path("id")
        .asLong();
  }

  @Test
  void uploadsImageAudioAndRealVideo() throws Exception {
    upload(admin, "Image", null);
    byte[] wav = new byte[48];
    System.arraycopy("RIFF".getBytes(), 0, wav, 0, 4);
    System.arraycopy("WAVEfmt ".getBytes(), 0, wav, 8, 8);
    wav[4] = 40;
    wav[16] = 16;
    wav[20] = 1;
    wav[22] = 1;
    wav[24] = 64;
    wav[25] = 31;
    wav[28] = (byte) 128;
    wav[29] = 62;
    wav[32] = 2;
    wav[34] = 16;
    System.arraycopy("data".getBytes(), 0, wav, 36, 4);
    wav[40] = 4;
    for (var file :
        List.of(
            new MockMultipartFile("file", "sample.wav", "audio/wav", wav),
            new MockMultipartFile(
                "file",
                "sample.webm",
                "video/webm",
                Base64.getDecoder()
                    .decode(
                        "GkXfo59ChoEBQveBAULygQRC84EIQoKEd2VibUKHgQRChYECGFOAZwEAAAAAAAGZEU2bdLlNu4tTq4QVSalmU6yBbk27i1OrhBZUrmtTrIGTTbuLU6uEH0O2dVOsgdRNu4xTq4QcU7trU6yCAYfsrgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAVSalmoCrXsYMPQkBEiYQ/gAAATYCGQ2hyb21lV0GGQ2hyb21lFlSua7yuuteBAXPFhxlJmJkpoRuDgQFV7oEBhoVWX1ZQOOCdsIGguoFaU8CBAVWwkFWxgQFVuYECVbqBDVW7gQEfQ7Z1AQAAAAAAAKfngQCgQKGh3IEAAABQBgCdASqgAFoAAAcIhYWImYSIASQQDsA/gD+gIG/7epimTJeT506dOnTp06dOnTp06dOnTp06dOnTfAD++jPL//+2Z/BcLM/+2aP//xzJuYYn/GxifykYdaHApr7ugQGluVAFAJ0BKqAAWgAARwiFhYiZhIgCAgAGKA8IdVSa7iHVUmu4h1VJruIdVSa7iHVUmu4hvAD+/7qDABxTu2uNu4uzgQC3hveBAfGB1A=="))))
      mvc.perform(
              multipart("/api/media/assets")
                  .file(file)
                  .param("title", file.getOriginalFilename())
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .header("Authorization", "Bearer " + admin))
          .andExpect(status().isCreated());
    mvc.perform(get("/api/media/stats").header("Authorization", "Bearer " + admin))
        .andExpect(jsonPath("$.data.total").value(3))
        .andExpect(jsonPath("$.data.images").value(1))
        .andExpect(jsonPath("$.data.audios").value(1))
        .andExpect(jsonPath("$.data.videos").value(1));
  }

  @Test
  void rejectsBadMimeExtensionAndTraversal() throws Exception {
    for (var file :
        List.of(
            new MockMultipartFile(
                "file", "fake.png", "image/png", "<script>bad</script>".getBytes()),
            new MockMultipartFile("file", "fake.mp4", "video/mp4", png()),
            new MockMultipartFile("file", "wrong.txt", "image/png", png())))
      mvc.perform(
              multipart("/api/media/assets")
                  .file(file)
                  .param("title", "Bad")
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .header("Authorization", "Bearer " + admin))
          .andExpect(status().isUnsupportedMediaType());
    mvc.perform(
            multipart("/api/media/assets")
                .file(new MockMultipartFile("file", "../escape.png", "image/png", png()))
                .param("title", "Bad")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isBadRequest());
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM media_file", Integer.class));
  }

  @Test
  void oversizedFileRejectedWithoutOrphan() throws Exception {
    byte[] big = new byte[1048577];
    System.arraycopy(png(), 0, big, 0, png().length);
    mvc.perform(
            multipart("/api/media/assets")
                .file(new MockMultipartFile("file", "big.png", "image/png", big))
                .param("title", "Big")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void binaryRangeDownloadAndAnonymousBlock() throws Exception {
    long id = upload(admin, "Preview", null).path("id").asLong();
    String path = "/api/media/assets/" + id + "/content";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(
            get(path).header("Authorization", "Bearer " + reviewer).header("Range", "bytes=0-9"))
        .andExpect(status().isPartialContent())
        .andExpect(header().string("Content-Range", "bytes 0-9/" + png().length))
        .andExpect(content().bytes(Arrays.copyOf(png(), 10)));
    mvc.perform(
            get("/api/media/assets/" + id + "/download")
                .header("Authorization", "Bearer " + reviewer))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
        .andExpect(content().bytes(png()));
  }

  @Test
  void scopedPreviewCookieCannotAccessOtherApiOrAsset() throws Exception {
    long id = upload(admin, "Preview", null).path("id").asLong();
    var r =
        mvc.perform(
                post("/api/media/assets/" + id + "/access")
                    .header("Authorization", "Bearer " + reviewer))
            .andExpect(status().isOk())
            .andReturn();
    String cookie = r.getResponse().getHeader("Set-Cookie");
    assertTrue(cookie.contains("HttpOnly"));
    String ticket = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    String path = "/api/media/assets/" + id + "/content";
    mvc.perform(
            get(path)
                .servletPath(path)
                .cookie(new jakarta.servlet.http.Cookie("mf_preview_" + id, ticket)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + ticket))
        .andExpect(status().isUnauthorized());
    String other = "/api/media/assets/" + (id + 1) + "/content";
    mvc.perform(
            get(other)
                .servletPath(other)
                .cookie(new jakarta.servlet.http.Cookie("mf_preview_" + (id + 1), ticket)))
        .andExpect(status().isUnauthorized());
    db.update("UPDATE sys_user SET status=0 WHERE id=3");
    mvc.perform(
            get(path)
                .servletPath(path)
                .cookie(new jakarta.servlet.http.Cookie("mf_preview_" + id, ticket)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidNumericPreviewPathRemainsProtected() throws Exception {
    String path = "/api/media/assets/999999999999999999999999999999/content";
    mvc.perform(get(path).servletPath(path))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(40100));
  }

  @Test
  void editorOwnerAndReviewerPermissions() throws Exception {
    long id = upload(admin, "Owned by admin", null).path("id").asLong();
    String b = body(Map.of("title", "Try", "description", "", "version", 0));
    for (String t : List.of(editor, reviewer))
      mvc.perform(
              put("/api/media/assets/" + id)
                  .header("Authorization", "Bearer " + t)
                  .contentType("application/json")
                  .content(b))
          .andExpect(status().isForbidden());
    long own = upload(editor, "Own", null).path("id").asLong();
    mvc.perform(
            put("/api/media/assets/" + own)
                .header("Authorization", "Bearer " + editor)
                .contentType("application/json")
                .content(b))
        .andExpect(status().isOk());
    mvc.perform(
            multipart("/api/media/assets")
                .file(image())
                .param("title", "Not allowed")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + reviewer))
        .andExpect(status().isForbidden());
    for (String t : List.of(editor, reviewer))
      mvc.perform(
              post("/api/media/categories")
                  .header("Authorization", "Bearer " + t)
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isForbidden());
  }

  @Test
  void categoriesDepthCycleDisableAndReferencedDeletion() throws Exception {
    long parent = category("Root", null), child = category("Child", parent);
    mvc.perform(
            put("/api/media/categories/" + parent)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "categoryName",
                            "Root",
                            "parentId",
                            child,
                            "sortOrder",
                            0,
                            "status",
                            1))))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/media/categories")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "categoryName",
                            "Grandchild",
                            "parentId",
                            child,
                            "sortOrder",
                            0,
                            "status",
                            1))))
        .andExpect(status().isConflict());
    upload(admin, "Categorized", child);
    mvc.perform(delete("/api/media/categories/" + child).header("Authorization", "Bearer " + admin))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/media/categories/" + parent)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{\"categoryName\":\"Renamed\",\"sortOrder\":2,\"status\":0}"))
        .andExpect(status().isOk());
    mvc.perform(
            multipart("/api/media/assets")
                .file(image())
                .param("title", "Disabled")
                .param("categoryId", String.valueOf(child))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isConflict());
  }

  @Test
  void filtersPaginationSortingAndStatsAgree() throws Exception {
    long cat = category("Assets", null);
    upload(admin, "Alpha", cat);
    upload(editor, "Beta", cat);
    upload(admin, "Other", null);
    String qs =
        "?keyword=a&categoryId="
            + cat
            + "&mediaType=IMAGE&uploadedBy=2&from=2020-01-01T00:00:00&to=2099-01-01T00:00:00&sort=title&direction=asc&size=1";
    mvc.perform(get("/api/media/assets" + qs).header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].title").value("Beta"));
    mvc.perform(get("/api/media/stats" + qs).header("Authorization", "Bearer " + admin))
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(
            get("/api/media/assets?sort=title&direction=asc&size=1&page=2")
                .header("Authorization", "Bearer " + admin))
        .andExpect(jsonPath("$.data.total").value(3))
        .andExpect(jsonPath("$.data.records[0].title").value("Beta"));
    mvc.perform(get("/api/media/assets?sort=DROP").header("Authorization", "Bearer " + admin))
        .andExpect(status().isBadRequest());
  }

  @Test
  void actualFieldChangesAndNoopAndUnclassification() throws Exception {
    long cat = category("Original category", null), next = category("New category", null);
    long id = upload(admin, "Old title", cat).path("id").asLong();
    long initial = db.queryForObject("SELECT COUNT(*) FROM media_change_log", Long.class);
    mvc.perform(
            put("/api/media/assets/" + id)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "title",
                            "Old title",
                            "description",
                            "",
                            "categoryId",
                            cat,
                            "version",
                            0))))
        .andExpect(status().isOk());
    assertEquals(initial, db.queryForObject("SELECT COUNT(*) FROM media_change_log", Long.class));
    mvc.perform(
            put("/api/media/assets/" + id)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "title",
                            "New title",
                            "description",
                            "changed",
                            "categoryId",
                            next,
                            "version",
                            0,
                            "operatorId",
                            3))))
        .andExpect(status().isOk());
    assertEquals(
        initial + 3, db.queryForObject("SELECT COUNT(*) FROM media_change_log", Long.class));
    assertEquals(
        "Original category",
        db.queryForObject(
            "SELECT old_label FROM media_change_log WHERE operation_type='UPDATE' AND"
                + " field_name='category_id'",
            String.class));
    assertEquals(
        1L,
        db.queryForObject(
            "SELECT operator_id FROM media_change_log WHERE operation_type='UPDATE' AND"
                + " field_name='title'",
            Long.class));
    mvc.perform(
            put("/api/media/assets/" + id)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(
                    "{\"title\":\"New"
                        + " title\",\"description\":\"changed\",\"categoryId\":null,\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.categoryId").isEmpty());
    mvc.perform(
            get("/api/media/changes?assetId=" + id + "&operatorId=1")
                .header("Authorization", "Bearer " + reviewer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(initial + 4));
  }

  @Test
  void businessUpdateFailureRollsBackAudit() throws Exception {
    long id = upload(admin, "Original", null).path("id").asLong();
    long initial = db.queryForObject("SELECT COUNT(*) FROM media_change_log", Long.class);
    try {
      doThrow(
              new org.springframework.dao.DataAccessResourceFailureException(
                  "simulated update failure"))
          .when(assets)
          .updateById(any(MediaAsset.class));
      mvc.perform(
              put("/api/media/assets/" + id)
                  .header("Authorization", "Bearer " + admin)
                  .contentType("application/json")
                  .content("{\"title\":\"Changed\",\"description\":\"\",\"version\":0}"))
          .andExpect(status().isInternalServerError());
    } finally {
      reset(assets);
    }
    assertEquals(initial, db.queryForObject("SELECT COUNT(*) FROM media_change_log", Long.class));
    assertEquals(
        "Original",
        db.queryForObject("SELECT title FROM media_asset WHERE id=?", String.class, id));
  }

  @Test
  void databaseUploadFailureCleansStoredObject() throws Exception {
    long before;
    try (var stream = Files.list(Path.of("target/test-uploads"))) {
      before = stream.count();
    }
    try {
      doThrow(
              new org.springframework.dao.DataAccessResourceFailureException(
                  "simulated insert failure"))
          .when(files)
          .insert(any(MediaFile.class));
      mvc.perform(
              multipart("/api/media/assets")
                  .file(image())
                  .param("title", "Rollback")
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .header("Authorization", "Bearer " + admin))
          .andExpect(status().isInternalServerError());
    } finally {
      reset(files);
    }
    try (var stream = Files.list(Path.of("target/test-uploads"))) {
      assertEquals(before, stream.count());
    }
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM media_file", Integer.class));
  }

  @Test
  void duplicateSubmissionDoesNotCreateExtraFile() throws Exception {
    String key = UUID.randomUUID().toString();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              multipart("/api/media/assets")
                  .file(image())
                  .param("title", "Duplicate")
                  .header("Idempotency-Key", key)
                  .header("Authorization", "Bearer " + admin))
          .andExpect(i == 0 ? status().isCreated() : status().isConflict());
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM media_file", Integer.class));
  }

  @Test
  void archiveChangesVisibilityCountsAndHistory() throws Exception {
    long id = upload(admin, "To archive", null).path("id").asLong();
    mvc.perform(
            post("/api/media/assets/" + id + "/archive")
                .header("Authorization", "Bearer " + editor))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/media/assets/" + id + "/archive").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk());
    mvc.perform(get("/api/media/stats").header("Authorization", "Bearer " + admin))
        .andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(get("/api/media/assets?status=ARCHIVED").header("Authorization", "Bearer " + admin))
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(
            get("/api/media/assets/" + id + "/content")
                .header("Authorization", "Bearer " + reviewer))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/media/changes").header("Authorization", "Bearer " + reviewer))
        .andExpect(jsonPath("$.data.total").value(0));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM media_file", Integer.class));
  }

  @Test
  void batchUserRolesKeepsResponseWithoutNPlusOne() throws Exception {
    clearInvocations(roles);
    mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(3));
    verify(roles, times(1)).batchCodes(any());
    verify(roles, times(1)).codes(1L);
    verify(roles, never()).codes(2L);
    verify(roles, never()).codes(3L);
  }

  @Test
  void emptyUserPageSkipsRoleBatch() throws Exception {
    clearInvocations(roles);
    mvc.perform(get("/api/admin/users?keyword=NoMatch").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
    verify(roles, never()).batchCodes(any());
  }
}
