package com.smartbuildpc.backend.modules.auth.service;

import com.smartbuildpc.backend.common.exception.BusinessException;
import com.smartbuildpc.backend.common.exception.ErrorCode;
import com.smartbuildpc.backend.modules.auth.dto.RegisterRequest;
import com.smartbuildpc.backend.modules.auth.dto.RegisterResponse;
import com.smartbuildpc.backend.modules.auth.entity.Role;
import com.smartbuildpc.backend.modules.auth.entity.User;
import com.smartbuildpc.backend.modules.auth.repository.UserRepository;
import com.smartbuildpc.backend.modules.cart.service.CartInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CartInitializer cartInitializer;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.CONFLICT_STATE, "Email đã tồn tại trong hệ thống");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();

        User savedUser;
        try {
            savedUser = userRepository.save(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Xung đột trùng lặp khi lưu user: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.CONFLICT_STATE, "Email đã tồn tại trong hệ thống");
        }

        cartInitializer.initializeCartForUser(savedUser);

        log.info("Đăng ký thành công tài khoản mới với userId: {}", savedUser.getId());

        return RegisterResponse.builder()
                .message("Đăng ký tài khoản thành công")
                .userId(savedUser.getId())
                .build();
    }
}
