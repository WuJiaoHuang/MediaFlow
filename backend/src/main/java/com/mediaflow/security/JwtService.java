package com.mediaflow.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final long ttl;

  public JwtService(
      @Value("${mediaflow.jwt-secret}") String secret,
      @Value("${mediaflow.token-ttl-seconds}") long ttl) {
    byte[] key = secret.getBytes(StandardCharsets.UTF_8);
    if (key.length < 32 || ttl < 1 || ttl > 3600)
      throw new IllegalArgumentException(
          "JWT secret requires 32+ bytes; TTL must be 1..3600 seconds");
    var secretKey = new SecretKeySpec(key, "HmacSHA256");
    encoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(secretKey));
    var d = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    var time = new JwtTimestampValidator(java.time.Duration.ZERO);
    d.setJwtValidator(
        new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            time, new JwtIssuerValidator("mediaflow")));
    decoder = d;
    this.ttl = ttl;
  }

  public String issue(Long id) {
    var now = Instant.now();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                    .issuer("mediaflow")
                    .subject(id.toString())
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(ttl))
                    .build()))
        .getTokenValue();
  }

  public Long verify(String token) {
    var jwt = decoder.decode(token);
    if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null)
      throw new IllegalArgumentException("Missing timestamps");
    if (jwt.hasClaim("purpose")) throw new IllegalArgumentException("Not an access token");
    return Long.valueOf(jwt.getSubject());
  }

  public String issuePreview(Long userId, Long assetId) {
    var now = Instant.now();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                    .issuer("mediaflow")
                    .subject(userId.toString())
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(120))
                    .claim("purpose", "preview")
                    .claim("asset", assetId)
                    .build()))
        .getTokenValue();
  }

  public Long verifyPreview(String token, Long assetId) {
    var jwt = decoder.decode(token);
    if (jwt.getExpiresAt() == null
        || !"preview".equals(jwt.getClaimAsString("purpose"))
        || !assetId.toString().equals(String.valueOf((Object) jwt.getClaim("asset"))))
      throw new IllegalArgumentException("Invalid preview scope");
    return Long.valueOf(jwt.getSubject());
  }

  public long ttl() {
    return ttl;
  }
}
