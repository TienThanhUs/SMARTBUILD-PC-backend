package com.smartbuildpc.backend.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
public class TestSecurityController {

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> adminOnly() {
        return ResponseEntity.ok(Map.of("message", "Xin chào Admin"));
    }

    @GetMapping("/customer-only")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, String>> customerOnly() {
        return ResponseEntity.ok(Map.of("message", "Xin chào Customer"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUserInfo(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of(
                "userId", principal.getId(),
                "email", principal.getEmail(),
                "role", principal.getRole().name()
        ));
    }
}
