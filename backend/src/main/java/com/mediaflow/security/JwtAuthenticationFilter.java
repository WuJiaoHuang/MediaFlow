package com.mediaflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediaflow.common.ApiResponse;
import com.mediaflow.service.UserService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  private final UserService users;
  private final ObjectMapper json;

  public JwtAuthenticationFilter(JwtService jwt, UserService users, ObjectMapper json) {
    this.jwt = jwt;
    this.users = users;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest r) {
    return r.getServletPath().equals("/api/auth/login");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String h = req.getHeader("Authorization");
    Long previewAsset = null;
    Long previewTask = null;
    String previewToken = null;
    Long feedbackTask = null, feedbackId = null, attachmentId = null;
    String feedbackToken = null;
    if (h == null && (req.getMethod().equals("GET") || req.getMethod().equals("HEAD"))) {
      var match =
          java.util.regex.Pattern.compile("^/api/media/assets/([0-9]+)/(content|download)$")
              .matcher(req.getServletPath());
      if (match.matches()) {
        try {
          previewAsset = Long.valueOf(match.group(1));
          if (req.getParameter("taskId") != null)
            previewTask = Long.valueOf(req.getParameter("taskId"));
        } catch (NumberFormatException ignored) {
          previewAsset = null;
          // An invalid path/context remains unauthenticated.
        }
        if (previewAsset != null && req.getCookies() != null)
          for (var cookie : req.getCookies())
            if (cookie
                .getName()
                .equals(
                    "mf_preview_"
                        + previewAsset
                        + (previewTask == null ? "" : "_task_" + previewTask)))
              previewToken = cookie.getValue();
      }
    }
    if (h == null && (req.getMethod().equals("GET") || req.getMethod().equals("HEAD"))) {
      var match =
          java.util.regex.Pattern.compile(
                  "^/api/tasks/([0-9]+)/feedback/([0-9]+)/attachments/([0-9]+)/(content|download)$")
              .matcher(req.getServletPath());
      if (match.matches())
        try {
          feedbackTask = Long.valueOf(match.group(1));
          feedbackId = Long.valueOf(match.group(2));
          attachmentId = Long.valueOf(match.group(3));
          if (req.getCookies() != null)
            for (var cookie : req.getCookies())
              if (cookie.getName().equals("mf_feedback_" + attachmentId))
                feedbackToken = cookie.getValue();
        } catch (NumberFormatException ignored) {
          feedbackToken = null;
        }
    }
    if (h != null || previewToken != null || feedbackToken != null) {
      try {
        if (h != null && !h.startsWith("Bearer ")) throw new IllegalArgumentException();
        Long id =
            h != null
                ? jwt.verify(h.substring(7))
                : feedbackToken != null
                    ? jwt.verifyFeedback(feedbackToken, feedbackTask, feedbackId, attachmentId)
                    : jwt.verifyPreview(previewToken, previewAsset, previewTask);
        var user = users.view(id);
        if (user.status() != 1) throw new IllegalArgumentException();
        var auth =
            new UsernamePasswordAuthenticationToken(
                new CurrentUser(user),
                null,
                user.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (org.springframework.security.oauth2.jwt.JwtException
          | IllegalArgumentException
          | com.mediaflow.common.BusinessException e) {
        SecurityContextHolder.clearContext();
        res.setStatus(401);
        res.setContentType("application/json;charset=UTF-8");
        json.writeValue(res.getOutputStream(), ApiResponse.error(40100, "登录已失效，请重新登录"));
        return;
      } catch (org.springframework.dao.DataAccessException e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .error("Authentication database unavailable: {}", e.getClass().getName());
        SecurityContextHolder.clearContext();
        res.setStatus(500);
        res.setContentType("application/json;charset=UTF-8");
        json.writeValue(res.getOutputStream(), ApiResponse.error(50000, "服务器内部错误"));
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
