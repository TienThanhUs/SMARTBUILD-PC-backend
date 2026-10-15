package com.smartbuildpc.backend.modules.auth.service;

import com.smartbuildpc.backend.common.exception.BusinessException;
import com.smartbuildpc.backend.common.exception.ErrorCode;
import com.smartbuildpc.backend.modules.auth.dto.RegisterRequest;
import com.smartbuildpc.backend.modules.auth.dto.RegisterResponse;
import com.smartbuildpc.backend.modules.auth.entity.Role;
import com.smartbuildpc.backend.modules.auth.entity.User;
import com.smartbuildpc.backend.modules.auth.repository.UserRepository;
import com.smartbuildpc.backend.modules.cart.service.CartInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CartInitializer cartInitializer;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Happy: Đăng ký thành công, hash mật khẩu bắt đầu bằng $2, role CUSTOMER, isActive true, tạo cart đúng 1 lần và trả đúng userId")
    void given_validRegisterRequest_whenRegister_thenSuccessAndCartCreated() {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Nguyễn Văn A")
                .email("user.a@example.com")
                .password("plainPassword123")
                .phone("0912345678")
                .build();

        String fakeHash = "$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm";
        given(userRepository.existsByEmail("user.a@example.com")).willReturn(false);
        given(passwordEncoder.encode("plainPassword123")).willReturn(fakeHash);

        User savedUser = User.builder()
                .id(100L)
                .fullName("Nguyễn Văn A")
                .email("user.a@example.com")
                .passwordHash(fakeHash)
                .phone("0912345678")
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        // When
        RegisterResponse response = authService.register(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(100L);
        assertThat(response.getMessage()).contains("thành công");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertThat(capturedUser.getPasswordHash()).startsWith("$2");
        assertThat(capturedUser.getPasswordHash()).isNotEqualTo("plainPassword123");
        assertThat(capturedUser.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(capturedUser.getIsActive()).isTrue();

        verify(cartInitializer, times(1)).initializeCartForUser(savedUser);
    }

    @Test
    @DisplayName("Happy: Đăng ký với phone null vẫn thành công và lưu phone null")
    void given_requestWithNullPhone_whenRegister_thenSuccessWithNullPhone() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Lê Thị C")
                .email("user.c@example.com")
                .password("plainPassword123")
                .phone(null)
                .build();

        given(userRepository.existsByEmail("user.c@example.com")).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("$2a$10$hashed");

        User savedUser = User.builder()
                .id(102L)
                .fullName("Lê Thị C")
                .email("user.c@example.com")
                .passwordHash("$2a$10$hashed")
                .phone(null)
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        RegisterResponse response = authService.register(request);

        assertThat(response.getUserId()).isEqualTo(102L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isNull();
    }

    @Test
    @DisplayName("Biên: Email có chữ hoa và khoảng trắng đầu/cuối được chuẩn hóa trước khi kiểm tra và lưu")
    void given_emailWithUppercaseAndWhitespaces_whenRegister_thenEmailNormalized() {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .fullName("  Trần Văn B  ")
                .email("  User.Test@Example.COM  ")
                .password("plainPassword123")
                .phone("0987654321")
                .build();

        String expectedNormalizedEmail = "user.test@example.com";
        given(userRepository.existsByEmail(expectedNormalizedEmail)).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("$2a$10$hashedPasswordMock");

        User savedUser = User.builder()
                .id(101L)
                .fullName("Trần Văn B")
                .email(expectedNormalizedEmail)
                .passwordHash("$2a$10$hashedPasswordMock")
                .phone("0987654321")
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        // When
        RegisterResponse response = authService.register(request);

        // Then
        assertThat(response.getUserId()).isEqualTo(101L);
        verify(userRepository).existsByEmail(expectedNormalizedEmail);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(expectedNormalizedEmail);
        assertThat(userCaptor.getValue().getFullName()).isEqualTo("Trần Văn B");
    }

    @Test
    @DisplayName("Lỗi: Email đã tồn tại thì ném BusinessException (409 CONFLICT_STATE), không gọi save và không tạo cart")
    void given_existingEmail_whenRegister_thenThrowsConflictException() {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Lê Văn C")
                .email("duplicate@example.com")
                .password("password123")
                .phone("0912345678")
                .build();

        given(userRepository.existsByEmail("duplicate@example.com")).willReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email đã tồn tại")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT_STATE));

        verify(userRepository, never()).save(any(User.class));
        verify(cartInitializer, never()).initializeCartForUser(any());
    }

    @Test
    @DisplayName("Lỗi: Khi save ném DataIntegrityViolationException do tranh chấp đồng thời thì quy về BusinessException (409)")
    void given_concurrentDuplicateEmailOnSave_whenRegister_thenThrowsConflictException() {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Đặng Văn D")
                .email("race@example.com")
                .password("password123")
                .phone("0912345678")
                .build();

        given(userRepository.existsByEmail("race@example.com")).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("$2a$10$hashed");
        given(userRepository.save(any(User.class))).willThrow(new DataIntegrityViolationException("Duplicate key email"));

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email đã tồn tại")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT_STATE));

        verify(cartInitializer, never()).initializeCartForUser(any());
    }
}
