package com.yegkim.task_reloader_api.auth.jwt;

import com.yegkim.task_reloader_api.auth.entity.UserRole;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider 단위테스트")
class JwtTokenProviderTest {

    private static final String RAW_SECRET = "test-secret-key-for-jwt-signing-at-least-32-bytes";

    @Test
    @DisplayName("access token 생성/파싱 성공")
    void generateAndParseAccessToken_success() {
        JwtTokenProvider provider = new JwtTokenProvider(RAW_SECRET, 900L);

        String token = provider.generateAccessToken(100L, UserRole.USER);
        AccessTokenPayload payload = provider.parseAccessToken(token);

        assertThat(payload.userId()).isEqualTo(100L);
        assertThat(payload.role()).isEqualTo(UserRole.USER);
        assertThat(provider.isValidAccessToken(token)).isTrue();
    }

    @Test
    @DisplayName("잘못된 토큰 포맷은 유효하지 않음")
    void invalidTokenFormat_returnsFalse() {
        JwtTokenProvider provider = new JwtTokenProvider(RAW_SECRET, 900L);

        assertThat(provider.isValidAccessToken("not-a-jwt-token")).isFalse();
    }

    @Test
    @DisplayName("type이 ACCESS가 아닌 토큰은 파싱 실패")
    void parseAccessToken_rejectsNonAccessType() {
        JwtTokenProvider provider = new JwtTokenProvider(RAW_SECRET, 900L);

        String refreshLikeToken = Jwts.builder()
                .subject("1")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .claim("role", UserRole.USER.name())
                .claim("type", "REFRESH")
                .signWith(Keys.hmacShaKeyFor(RAW_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> provider.parseAccessToken(refreshLikeToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Base64 인코딩 secret도 정상 동작")
    void base64Secret_supported() {
        String base64Secret = Base64.getEncoder().encodeToString(RAW_SECRET.getBytes(StandardCharsets.UTF_8));
        JwtTokenProvider provider = new JwtTokenProvider(base64Secret, 900L);

        String token = provider.generateAccessToken(55L, UserRole.ADMIN);
        AccessTokenPayload payload = provider.parseAccessToken(token);

        assertThat(payload.userId()).isEqualTo(55L);
        assertThat(payload.role()).isEqualTo(UserRole.ADMIN);
    }
}
