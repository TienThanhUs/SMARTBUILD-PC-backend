package com.smartbuildpc.backend.modules.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbuildpc.backend.common.AbstractIntegrationTest;
import com.smartbuildpc.backend.modules.auth.entity.Role;
import com.smartbuildpc.backend.modules.auth.entity.User;
import com.smartbuildpc.backend.modules.auth.repository.UserRepository;
import com.smartbuildpc.backend.modules.cart.entity.Cart;
import com.smartbuildpc.backend.modules.cart.repository.CartRepository;
import com.smartbuildpc.backend.modules.cart.service.CartInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegisterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    @SpyBean
    private CartInitializer cartInitializer;

    @BeforeEach
    void cleanUp() {
        cartRepository.deleteAll();
        userRepository.deleteAll();
    }

    // =========================================================================
    // 1. HAPPY PATH
    // =========================================================================
    @Test
    @DisplayName("Happy: Đăng ký thành công trả về 201, đúng 2 khóa message & userId, băm BCrypt, tạo cart rỗng và không lộ password")
    void given_validRequest_whenRegister_thenReturns201AndPersistsDataProperly() throws Exception {
        Map<String, Object> request = Map.of(
                "fullName", "Nguyễn Văn A",
                "email", "user.happy@example.com",
                "password", "SecureP@ss123",
                "phone", "0912345678"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("Đăng ký tài khoản thành công")))
                .andExpect(jsonPath("$.userId", notNullValue()))
                .andReturn();

        // Kiểm tra response có đúng 2 trường
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> fieldNames = new ArrayList<>();
        root.fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames).containsExactlyInAnyOrder("message", "userId");

        Long userId = root.get("userId").asLong();

        // Kiểm tra dữ liệu trong DB
        Optional<User> userOpt = userRepository.findById(userId);
        assertThat(userOpt).isPresent();
        User user = userOpt.get();
        assertThat(user.getEmail()).isEqualTo("user.happy@example.com");
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.getIsActive()).isTrue();
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(user.getPasswordHash()).isNotEqualTo("SecureP@ss123");

        // Kiểm tra có đúng 1 giỏ hàng rỗng được tạo
        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        assertThat(cartOpt).isPresent();
        assertThat(cartOpt.get().getUser().getId()).isEqualTo(userId);

        // Đảm bảo không chứa trường password nào trong response
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SecureP@ss123");
    }

    // =========================================================================
    // 2. BIÊN HỢP LỆ
    // =========================================================================
    @Test
    @DisplayName("Biên hợp lệ: fullName 2 ký tự và 100 ký tự đều được chấp nhận")
    void given_fullNameBoundary_whenRegister_thenSuccess() throws Exception {
        // Đúng 2 ký tự
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "An",
                                "email", "an@example.com",
                                "password", "password123",
                                "phone", "0912345678"
                        ))))
                .andExpect(status().isCreated());

        // Đúng 100 ký tự
        String name100 = "A".repeat(100);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", name100,
                                "email", "a100@example.com",
                                "password", "password123",
                                "phone", "0912345678"
                        ))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Biên hợp lệ: password đúng 8 ký tự, phone 0 và +84, tên tiếng Việt có dấu, email in hoa và khoảng trắng")
    void given_validEdgeValues_whenRegister_thenSuccessAndNormalizeEmail() throws Exception {
        // Phone +84, tiếng Việt có dấu, email hoa có khoảng trắng
        Map<String, Object> request = Map.of(
                "fullName", "Nguyễn Thị Ánh Tuyết",
                "email", "   NgUyeN.Tuyet@Example.COM   ",
                "password", "8charPas", // đúng 8 ký tự
                "phone", "+84912345678" // đúng regex +84
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Long userId = root.get("userId").asLong();

        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getEmail()).isEqualTo("nguyen.tuyet@example.com");
        assertThat(user.getFullName()).isEqualTo("Nguyễn Thị Ánh Tuyết");
        assertThat(user.getPhone()).isEqualTo("+84912345678");
    }

    // =========================================================================
    // 3. BIÊN KHÔNG HỢP LỆ (400 VALIDATION_ERROR)
    // =========================================================================
    @ParameterizedTest
    @ValueSource(strings = {
            "091234567",     // thiếu 1 số (9 số)
            "09123456789",   // thừa 1 số (11 số)
            "091234567a",    // có chữ
            "1912345678",    // đầu số 1 sai (không phải 0 hay +84)
            "+1234567890",   // không phải mã quốc gia +84
            "84912345678"    // thiếu dấu +
    })
    @DisplayName("Biên không hợp lệ: Số điện thoại sai định dạng bị trả 400 VALIDATION_ERROR")
    void given_invalidPhone_whenRegister_then400(String invalidPhone) throws Exception {
        Map<String, Object> request = Map.of(
                "fullName", "Nguyễn Văn Test",
                "email", "phone.test@example.com",
                "password", "password123",
                "phone", invalidPhone
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details", notNullValue()));
    }

    @Test
    @DisplayName("Biên không hợp lệ: fullName 1 và 101 ký tự bị trả 400")
    void given_invalidNameLength_whenRegister_then400() throws Exception {
        // 1 ký tự
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "A",
                                "email", "shortname@example.com",
                                "password", "password123",
                                "phone", "0912345678"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        // 101 ký tự
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "A".repeat(101),
                                "email", "longname@example.com",
                                "password", "password123",
                                "phone", "0912345678"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Biên không hợp lệ: password 7 ký tự và password > 72 byte UTF-8 bị trả 400")
    void given_invalidPasswordLength_whenRegister_then400() throws Exception {
        // 7 ký tự
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Nguyễn Văn Test",
                                "email", "pass7@example.com",
                                "password", "1234567",
                                "phone", "0912345678"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        // 73 bytes ASCII
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Nguyễn Văn Test",
                                "email", "pass73@example.com",
                                "password", "a".repeat(73),
                                "phone", "0912345678"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Biên không hợp lệ: email thiếu @ hoặc rỗng, thiếu từng trường một bị trả 400")
    void given_invalidEmailOrMissingFields_whenRegister_then400() throws Exception {
        // Email thiếu @
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Nguyễn Văn Test",
                                "email", "invalidemail.com",
                                "password", "password123",
                                "phone", "0912345678"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        // Thiếu fullName
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "nofullname@example.com",
                                "password", "password123",
                                "phone", "0912345678"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        // Body rỗng
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        // JSON hỏng
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"fullName\": \"Nguyễn Văn A\", \"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
    }

    // =========================================================================
    // 4. LỖI NGHIỆP VỤ (409 CONFLICT_STATE)
    // =========================================================================
    @Test
    @DisplayName("Lỗi nghiệp vụ: Đăng ký trùng email (cả cùng hoa thường hoặc khác hoa thường) trả về 409 CONFLICT_STATE")
    void given_duplicateEmail_whenRegister_thenReturns409() throws Exception {
        Map<String, Object> firstRequest = Map.of(
                "fullName", "Người Dùng Thứ Nhất",
                "email", "unique.email@example.com",
                "password", "password123",
                "phone", "0912345678"
        );

        // Lần đầu thành công
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        // Lần hai trùng email chính xác -> 409
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.errorCode", is("CONFLICT_STATE")))
                .andExpect(jsonPath("$.message", is("Email đã tồn tại trong hệ thống")));

        // Lần ba trùng email nhưng viết hoa và khoảng trắng -> vẫn 409
        Map<String, Object> thirdRequest = Map.of(
                "fullName", "Người Dùng Thứ Ba",
                "email", "  UNIQUE.EMAIL@example.com  ",
                "password", "password123",
                "phone", "0987654321"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(thirdRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("CONFLICT_STATE")));
    }

    // =========================================================================
    // 5. BẢO MẬT
    // =========================================================================
    @Test
    @DisplayName("Bảo mật: Gửi kèm 'role':'ADMIN' thì role vẫn là CUSTOMER, SQL Injection trong fullName không gây lỗi")
    void given_maliciousPayloads_whenRegister_thenSecuredAndHandledSafely() throws Exception {
        // Gửi role = ADMIN
        Map<String, Object> payloadWithRole = Map.of(
                "fullName", "Hacker A",
                "email", "hacker@example.com",
                "password", "password123",
                "phone", "0912345678",
                "role", "ADMIN" // Trường lạ
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payloadWithRole)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Long userId = root.get("userId").asLong();
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);

        // SQL Injection payload trong fullName
        String sqlInjectionName = "'; DROP TABLE users;--";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", sqlInjectionName,
                                "email", "sqli@example.com",
                                "password", "password123",
                                "phone", "0912345678"
                        ))))
                .andExpect(status().isCreated());

        // Bảng users vẫn tồn tại và lưu được user
        assertThat(userRepository.findByEmail("sqli@example.com")).isPresent();
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Bảo mật: Response lỗi không chứa StackTrace, tên bảng DB hoặc tên class Java")
    void given_invalidRequest_whenErrorResponseReturned_thenNoSensitiveInfoLeaked() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"invalid\" }"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("org.springframework");
        assertThat(responseBody).doesNotContain("Exception");
        assertThat(responseBody).doesNotContain("users");
        assertThat(responseBody).doesNotContain("at com.smartbuildpc");
    }

    // =========================================================================
    // 6. ĐỒNG THỜI (CONCURRENCY)
    // =========================================================================
    @Test
    @DisplayName("Đồng thời: 2 request đăng ký cùng email song song thì đúng 1 request 201 và 1 request 409, DB chỉ có 1 user")
    void given_concurrentRequestsWithSameEmail_whenExecuted_thenExactlyOneSucceeds() throws Exception {
        String sharedEmail = "concurrent.register@example.com";
        Map<String, Object> requestBody = Map.of(
                "fullName", "Đồng Thời User",
                "email", sharedEmail,
                "password", "securePassword123",
                "phone", "0912345678"
        );
        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Đợi cả 2 thread sẵn sàng
                    MvcResult mvcResult = mockMvc.perform(post("/api/v1/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonPayload))
                            .andReturn();
                    statusCodes.add(mvcResult.getResponse().getStatus());
                } catch (Exception e) {
                    statusCodes.add(500);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Kích hoạt 2 request đồng thời
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(statusCodes).hasSize(2);
        assertThat(statusCodes).containsExactlyInAnyOrder(201, 409);
        assertThat(statusCodes).doesNotContain(500);

        // Chỉ có đúng 1 user trong DB
        List<User> usersInDb = userRepository.findAll().stream()
                .filter(u -> sharedEmail.equalsIgnoreCase(u.getEmail()))
                .toList();
        assertThat(usersInDb).hasSize(1);
    }

    // =========================================================================
    // 7. ROLLBACK GIAO DỊCH
    // =========================================================================
    @Test
    @DisplayName("Rollback: Nếu bước khởi tạo Cart gặp lỗi thì User cũng không được lưu vào DB (không có user mồ côi)")
    void given_cartInitFailure_whenRegister_thenRollbackEntireTransaction() {
        // Giả lập CartInitializer ném exception
        doThrow(new RuntimeException("Simulated cart service failure"))
                .when(cartInitializer).initializeCartForUser(any());

        Map<String, Object> request = Map.of(
                "fullName", "User Rollback",
                "email", "rollback@example.com",
                "password", "password123",
                "phone", "0912345678"
        );

        try {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError());
        } catch (Exception ignored) {
        }

        // Kiểm tra user không bị lưu mồ côi trong DB
        Optional<User> userOpt = userRepository.findByEmail("rollback@example.com");
        assertThat(userOpt).isEmpty();
    }

    // =========================================================================
    // 8. LOGGING
    // =========================================================================
    @Test
    @DisplayName("Log: Kiểm tra log của request đăng ký không chứa plain text password")
    void given_registerRequest_whenLogged_thenDoesNotContainRawPassword() throws Exception {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        String secretPassword = "SuperSecretPlainPassword999";
        Map<String, Object> request = Map.of(
                "fullName", "Log Test User",
                "email", "logtest@example.com",
                "password", secretPassword,
                "phone", "0912345678"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        for (ILoggingEvent event : listAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(secretPassword);
        }

        rootLogger.detachAppender(listAppender);
    }
}
