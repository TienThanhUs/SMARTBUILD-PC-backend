package com.smartbuildpc.backend.modules.auth.controller;

import com.smartbuildpc.backend.modules.auth.dto.RegisterRequest;
import com.smartbuildpc.backend.modules.auth.dto.RegisterResponse;
import com.smartbuildpc.backend.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Các thành viên tiếp theo có thể thêm phương thức login tại đây mà không ảnh hưởng luồng register:
    // @PostMapping("/login")
    // public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) { ... }
}
