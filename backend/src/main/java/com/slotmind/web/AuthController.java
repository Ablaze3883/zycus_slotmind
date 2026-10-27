package com.slotmind.web;

import com.slotmind.AppPrincipal;
import com.slotmind.PatientAuthService;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final PatientAuthService authService;

    public AuthController(PatientAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/auth/config")
    public Map<String, Object> config() {
        return authService.authConfig();
    }

    @PostMapping("/auth/patient/login")
    public Map<String, Object> patientLogin(@RequestBody Map<String, Object> body) {
        return authService.loginPatient(string(body, "userId"), string(body, "password"));
    }

    @PostMapping("/auth/demo/login")
    public Map<String, Object> demoLogin(@RequestBody Map<String, Object> body) {
        return authService.loginDemoRole(string(body, "userId"), string(body, "password"));
    }

    @PostMapping("/auth/patient/logout")
    public Map<String, String> logout(Authentication authentication) {
        if (authentication != null && authentication.getCredentials() instanceof String token) {
            authService.logout(token);
        }
        return Map.of("status", "SIGNED_OUT");
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AppPrincipal principal) return authService.userView(principal);
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst().orElse("UNKNOWN");
        return Map.of("userId", authentication.getName(), "role", role, "patientName", "");
    }

    private static String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value instanceof String text ? text : null;
    }
}
