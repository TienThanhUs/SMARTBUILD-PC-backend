package com.smartbuildpc.backend.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String VALID_SECRET = "smartbuildpc_jwt_secret_key_minimum_256_bits_length_for_security_compliance";
    private static final String ANOTHER_SECRET = "another_completely_different_secret_key_at_least_256_bits_for_testing";
    private static final long EXPIRATION_MS = 86400000L; // 24 hours

    private Instant initialTime;
    private Clock clock;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        initialTime = Instant.parse("2026-10-15T12:00:00Z");
        clock = Clock.fixed(initialTime, ZoneOffset.UTC);
        jwtTokenProvider = new JwtTokenProvider(VALID_SECRET, EXPIRATION_MS, clock);
    }

    @Test
    @DisplayName("Happy: Sinh token rồi xác thực thành công, claims đúng (sub, email, role, exp = iat + 24h)")
    void given_validUserInfo_whenGenerateToken_thenValidateSuccessAndClaimsMatch() {
        Long userId = 123L;
        String email = "customer@example.com";
        String role = "CUSTOMER";

        String token = jwtTokenProvider.generateToken(userId, email, role);

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();

        Claims claims = jwtTokenProvider.getClaimsFromToken(token);
        assertThat(claims.getSubject()).isEqualTo("123");
        assertThat(claims.get("email", String.class)).isEqualTo("customer@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");

        Instant issuedAt = claims.getIssuedAt().toInstant();
        Instant expiration = claims.getExpiration().toInstant();
        assertThat(issuedAt).isEqualTo(initialTime);
        assertThat(expiration).isEqualTo(initialTime.plusMillis(EXPIRATION_MS));
        assertThat(Duration.between(issuedAt, expiration)).isEqualTo(Duration.ofHours(24));

        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(123L);
        assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo("customer@example.com");
        assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("Biên: Token vừa hết hạn thì invalid (dùng Clock dịch chuyển thời gian, không dùng Thread.sleep)")
    void given_expiredToken_whenValidate_thenReturnsFalse() {
        Long userId = 456L;
        String email = "expired@example.com";
        String role = "CUSTOMER";

        String token = jwtTokenProvider.generateToken(userId, email, role);
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();

        // Dịch chuyển clock sang thời điểm vượt quá 24h (24h + 1 giây)
        Clock fastForwardClock = Clock.fixed(initialTime.plusMillis(EXPIRATION_MS).plusSeconds(1), ZoneOffset.UTC);
        jwtTokenProvider.setClock(fastForwardClock);

        // Token hết hạn phải trả về false và không ném Exception
        assertThat(jwtTokenProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("Lỗi: Token bị sửa 1 ký tự thì validate trả về false")
    void given_tamperedToken_whenValidate_thenReturnsFalse() {
        String token = jwtTokenProvider.generateToken(1L, "user@example.com", "CUSTOMER");

        // Thay đổi 1 ký tự ở giữa chuỗi token
        char lastChar = token.charAt(token.length() - 2);
        char replacedChar = (lastChar == 'a') ? 'b' : 'a';
        String tamperedToken = token.substring(0, token.length() - 2) + replacedChar + token.substring(token.length() - 1);

        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("Lỗi: Token được ký bằng secret khác thì validate trả về false")
    void given_tokenSignedWithAnotherSecret_whenValidate_thenReturnsFalse() {
        JwtTokenProvider anotherProvider = new JwtTokenProvider(ANOTHER_SECRET, EXPIRATION_MS, clock);
        String tokenFromAnotherSecret = anotherProvider.generateToken(1L, "user@example.com", "CUSTOMER");

        assertThat(jwtTokenProvider.validateToken(tokenFromAnotherSecret)).isFalse();
    }

    @Test
    @DisplayName("Lỗi: Chuỗi rỗng, khoảng trắng, null, 'abc.def.ghi' hoặc thuật toán none thì validate trả về false và không ném Exception")
    void given_invalidTokens_whenValidate_thenReturnsFalseWithoutException() {
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
        assertThat(jwtTokenProvider.validateToken("   ")).isFalse();
        assertThat(jwtTokenProvider.validateToken("abc.def.ghi")).isFalse();

        // Giả lập token với thuật toán "none" (header: {"alg":"none"}, payload: {"sub":"123"}, signature: "")
        // base64url({"alg":"none"}) = "eyJhbGciOiJub25lIn0"
        // base64url({"sub":"123"}) = "eyJzdWIiOiIxMjMifQ"
        String unsignedToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxMjMifQ.";
        assertThat(jwtTokenProvider.validateToken(unsignedToken)).isFalse();
    }

    @Test
    @DisplayName("Lỗi: Secret < 32 bytes (256-bit) hoặc null/rỗng thì khởi tạo thất bại với thông báo rõ ràng")
    void given_shortOrEmptySecret_whenConstruct_thenThrowsIllegalArgumentException() {
        String shortSecret = "short_secret_only_24_bytes!";
        assertThat(shortSecret.getBytes().length).isLessThan(32);

        assertThatThrownBy(() -> new JwtTokenProvider(shortSecret, EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tối thiểu 256-bit (32 bytes UTF-8)");

        assertThatThrownBy(() -> new JwtTokenProvider(null, EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được để trống");

        assertThatThrownBy(() -> new JwtTokenProvider("   ", EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được để trống");
    }

    @Test
    @DisplayName("Clock null trong constructor hoặc setClock thì tự động fallback về system UTC clock")
    void given_nullClock_whenConstructOrSetClock_thenFallbackToSystemUtc() {
        JwtTokenProvider provider = new JwtTokenProvider(VALID_SECRET, EXPIRATION_MS, null);
        provider.setClock(null);
        String token = provider.generateToken(999L, "fallback@example.com", "CUSTOMER");
        assertThat(provider.validateToken(token)).isTrue();
    }
}
