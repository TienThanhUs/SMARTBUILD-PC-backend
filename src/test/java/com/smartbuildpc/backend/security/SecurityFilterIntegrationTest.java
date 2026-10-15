package com.smartbuildpc.backend.security;

import com.smartbuildpc.backend.common.AbstractIntegrationTest;
import com.smartbuildpc.backend.modules.auth.entity.Role;
import com.smartbuildpc.backend.modules.auth.entity.User;
import com.smartbuildpc.backend.modules.auth.repository.UserRepository;
import com.smartbuildpc.backend.modules.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    private User customerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        userRepository.deleteAll();

        customerUser = userRepository.save(User.builder()
                .fullName("Customer Tester")
                .email("cust.security@example.com")
                .passwordHash("$2a$10$dummyhashfordemotestingpurposes1234567890123456")
                .phone("0912345678")
                .role(Role.CUSTOMER)
                .isActive(true)
                .build());

        adminUser = userRepository.save(User.builder()
                .fullName("Admin Tester")
                .email("admin.security@example.com")
                .passwordHash("$2a$10$dummyhashfordemotestingpurposes1234567890123456")
                .phone("0987654321")
                .role(Role.ADMIN)
                .isActive(true)
                .build());
    }

    // =========================================================================
    // 1. CHƯA ĐĂNG NHẬP HOẶC TOKEN KHÔNG HỢP LỆ (401 UNAUTHORIZED)
    // =========================================================================
    @Test
    @DisplayName("Gọi /api/v1/users/profile không có token trả về 401 JSON ApiErrorResponse chuẩn (chứ không phải 404)")
    void given_noToken_whenAccessProtectedEndpoint_thenReturns401Json() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.message", is("Chưa xác thực hoặc token không hợp lệ")))
                .andExpect(jsonPath("$.path", is("/api/v1/users/profile")));
    }

    @Test
    @DisplayName("Token sai chữ ký thì bị chặn 401")
    void given_tokenWithWrongSignature_whenAccessProtectedEndpoint_thenReturns401() throws Exception {
        JwtTokenProvider foreignProvider = new JwtTokenProvider(
                "foreign_secret_key_at_least_256_bits_length_for_signature_testing",
                86400000L
        );
        String invalidSignatureToken = foreignProvider.generateToken(customerUser.getId(), customerUser.getEmail(), "CUSTOMER");

        mockMvc.perform(get("/api/v1/test/customer-only")
                        .header("Authorization", "Bearer " + invalidSignatureToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Token hết hạn thì bị chặn 401")
    void given_expiredToken_whenAccessProtectedEndpoint_thenReturns401() throws Exception {
        // Tạo token với thời hạn âm (đã hết hạn)
        JwtTokenProvider expiredProvider = new JwtTokenProvider(
                "smartbuildpc_jwt_secret_key_minimum_256_bits_length_for_security_compliance",
                -10000L,
                Clock.systemUTC()
        );
        String expiredToken = expiredProvider.generateToken(customerUser.getId(), customerUser.getEmail(), "CUSTOMER");

        mockMvc.perform(get("/api/v1/test/customer-only")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Header 'Authorization: Bearer ' rỗng hoặc thiếu chữ Bearer thì bị chặn 401")
    void given_emptyBearerOrMissingBearerPrefix_whenAccessProtectedEndpoint_thenReturns401() throws Exception {
        // Header "Authorization: Bearer " rỗng
        mockMvc.perform(get("/api/v1/test/customer-only")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));

        // Thiếu chữ "Bearer" (chỉ truyền token thô)
        String validToken = jwtTokenProvider.generateToken(customerUser.getId(), customerUser.getEmail(), "CUSTOMER");
        mockMvc.perform(get("/api/v1/test/customer-only")
                        .header("Authorization", validToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }

    // =========================================================================
    // 2. PHÂN QUYỀN RBAC (403 FORBIDDEN & 200 OK)
    // =========================================================================
    @Test
    @DisplayName("Token CUSTOMER gọi endpoint yêu cầu ADMIN bị chặn 403 JSON ApiErrorResponse chuẩn")
    void given_customerToken_whenAccessAdminEndpoint_thenReturns403Json() throws Exception {
        String customerToken = jwtTokenProvider.generateToken(customerUser.getId(), customerUser.getEmail(), "CUSTOMER");

        mockMvc.perform(get("/api/v1/test/admin-only")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")))
                .andExpect(jsonPath("$.message", is("Không có quyền thực hiện thao tác này")))
                .andExpect(jsonPath("$.path", is("/api/v1/test/admin-only")));
    }

    @Test
    @DisplayName("Token ADMIN gọi endpoint yêu cầu ADMIN thì qua thành công (200 OK)")
    void given_adminToken_whenAccessAdminEndpoint_thenSuccess() throws Exception {
        String adminToken = jwtTokenProvider.generateToken(adminUser.getId(), adminUser.getEmail(), "ADMIN");

        mockMvc.perform(get("/api/v1/test/admin-only")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Xin chào Admin")));
    }

    @Test
    @DisplayName("Token hợp lệ trích xuất đúng userId và role trong SecurityContext")
    void given_validToken_whenGetMe_thenExtractsCorrectUserPrincipal() throws Exception {
        String customerToken = jwtTokenProvider.generateToken(customerUser.getId(), customerUser.getEmail(), "CUSTOMER");

        mockMvc.perform(get("/api/v1/test/me")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(customerUser.getId().intValue())))
                .andExpect(jsonPath("$.email", is(customerUser.getEmail())))
                .andExpect(jsonPath("$.role", is("CUSTOMER")));
    }

    // =========================================================================
    // 3. PUBLIC ENDPOINT
    // =========================================================================
    @Test
    @DisplayName("/api/v1/auth/register là Public endpoint, không cần Authorization header")
    void given_noToken_whenCallRegister_thenPermittedWithout401() throws Exception {
        // Gửi body rỗng tới register -> trả 400 Validation error chứ KHÔNG trả 401 Unauthorized
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
    }
}
