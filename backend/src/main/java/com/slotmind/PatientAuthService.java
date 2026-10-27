package com.slotmind;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PatientAuthService {
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, DemoPatient> patients = new LinkedHashMap<>();
    private final Map<String, DemoAccount> demoRoles = new LinkedHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Duration sessionTtl;
    private final boolean auth0Configured;
    private final boolean demoRolesEnabled;

    public PatientAuthService(
            @Value("${slotmind.auth.demo-patient-password:}") String patientPassword,
            @Value("${slotmind.auth.patient-token-ttl:PT30M}") Duration sessionTtl,
            @Value("${slotmind.auth0.issuer-uri:}") String issuerUri,
            @Value("${slotmind.auth.demo-roles-enabled:false}") boolean demoRolesEnabled,
            @Value("${slotmind.auth.demo-staff-password:}") String staffPassword,
            @Value("${slotmind.auth.demo-admin-password:}") String adminPassword) {
        this.sessionTtl = sessionTtl;
        this.auth0Configured = !issuerUri.isBlank();
        this.demoRolesEnabled = demoRolesEnabled;
        if (patientPassword != null && !patientPassword.isBlank()) {
            String hash = passwordEncoder.encode(patientPassword);
            addPatient("john.martinez", "John Martinez", "APT-001", hash);
            addPatient("emily.watson", "Emily Watson", "APT-002", hash);
            addPatient("aisha.patel", "Aisha Patel", "APT-003", hash);
            addPatient("oliver.brown", "Oliver Brown", "APT-004", hash);
        }
        if (demoRolesEnabled && staffPassword != null && !staffPassword.isBlank()
                && adminPassword != null && !adminPassword.isBlank()) {
            demoRoles.put("staff.demo", new DemoAccount("staff.demo", "STAFF", passwordEncoder.encode(staffPassword)));
            demoRoles.put("admin.demo", new DemoAccount("admin.demo", "ADMIN", passwordEncoder.encode(adminPassword)));
        }
    }

    public Map<String, Object> loginPatient(String userId, String password) {
        DemoPatient patient = patients.get(normalize(userId));
        if (patient == null || password == null || !passwordEncoder.matches(password, patient.passwordHash)) {
            throw new AuthFailureException("INVALID_CREDENTIALS", "User ID or password is incorrect.");
        }
        AppPrincipal principal = AppPrincipal.patient(patient.userId, patient.patientName, patient.appointmentId);
        return issue(principal, "pt_");
    }

    public Map<String, Object> loginDemoRole(String userId, String password) {
        if (!demoRolesEnabled || demoRoles.isEmpty()) {
            throw new AuthFailureException("DEMO_AUTH_DISABLED", "Demo staff sign-in is disabled.");
        }
        DemoAccount account = demoRoles.get(normalize(userId));
        if (account == null || password == null || !passwordEncoder.matches(password, account.passwordHash)) {
            throw new AuthFailureException("INVALID_CREDENTIALS", "User ID or password is incorrect.");
        }
        return issue(new AppPrincipal(account.userId, account.role, null, null), "demo_");
    }

    private Map<String, Object> issue(AppPrincipal principal, String prefix) {
        Instant expiresAt = Instant.now().plus(sessionTtl);
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(principal, expiresAt));
        Map<String, Object> user = new LinkedHashMap<>(userView(principal));
        if (principal.appointmentId() != null) user.put("appointmentId", principal.appointmentId());
        return Map.of("accessToken", token, "tokenType", "Bearer", "expiresAt", expiresAt.toString(), "user", user);
    }

    public AppPrincipal authenticate(String token) {
        Session session = sessions.get(token);
        if (session == null) return null;
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(token, session);
            return null;
        }
        return session.principal;
    }

    public void logout(String token) {
        sessions.remove(token);
    }

    public Map<String, Object> authConfig() {
        return Map.of(
                "auth0Configured", auth0Configured,
                "demoAuthEnabled", demoRolesEnabled && !demoRoles.isEmpty(),
                "patientAccounts", patients.keySet().stream().toList());
    }

    public Map<String, Object> userView(AppPrincipal principal) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", principal.userId());
        user.put("role", principal.role());
        user.put("patientName", principal.patientName() == null ? "" : principal.patientName());
        if (principal.appointmentId() != null) user.put("appointmentId", principal.appointmentId());
        return user;
    }

    private void addPatient(String userId, String name, String appointmentId, String passwordHash) {
        patients.put(userId, new DemoPatient(userId, name, appointmentId, passwordHash));
    }

    private static String normalize(String userId) {
        return userId == null ? "" : userId.trim().toLowerCase();
    }

    private record DemoPatient(String userId, String patientName, String appointmentId, String passwordHash) {}
    private record DemoAccount(String userId, String role, String passwordHash) {}
    private record Session(AppPrincipal principal, Instant expiresAt) {}
}
