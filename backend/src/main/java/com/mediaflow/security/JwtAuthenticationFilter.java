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
    if (h != null) {
      try {
        if (!h.startsWith("Bearer ")) throw new IllegalArgumentException();
        Long id = jwt.verify(h.substring(7));
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
