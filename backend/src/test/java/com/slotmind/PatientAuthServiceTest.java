package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PatientAuthServiceTest {
    @Test
    void issuesPatientSessionForASeededAccount() {
        PatientAuthService service = service(true);

        Map<String, Object> response = service.loginPatient("john.martinez", "patient-password");
        String token = (String) response.get("accessToken");
        AppPrincipal principal = service.authenticate(token);

        assertEquals("pt_", token.substring(0, 3));
        assertEquals("PATIENT", principal.role());
        assertEquals("John Martinez", principal.patientName());
        assertEquals("APT-001", ((Map<?, ?>) response.get("user")).get("appointmentId"));
        service.logout(token);
        assertNull(service.authenticate(token));
    }

    @Test
    void demoCredentialsIssueDistinctStaffAndAdminRoles() {
        PatientAuthService service = service(true);

        String staffToken = (String) service.loginDemoRole("staff.demo", "staff-password").get("accessToken");
        String adminToken = (String) service.loginDemoRole("admin.demo", "admin-password").get("accessToken");

        assertEquals("STAFF", service.authenticate(staffToken).role());
        assertEquals("ADMIN", service.authenticate(adminToken).role());
        assertEquals(true, service.authConfig().get("demoAuthEnabled"));
        assertThrows(AuthFailureException.class,
                () -> service.loginDemoRole("staff.demo", "admin-password"));
    }

    @Test
    void demoRolesAreUnavailableWhenDisabled() {
        PatientAuthService service = service(false);

        assertEquals(false, service.authConfig().get("demoAuthEnabled"));
        assertThrows(AuthFailureException.class,
                () -> service.loginDemoRole("admin.demo", "admin-password"));
        assertNotNull(service.loginPatient("john.martinez", "patient-password"));
    }

    private static PatientAuthService service(boolean demoRolesEnabled) {
        return new PatientAuthService("patient-password", Duration.ofMinutes(30), "",
                demoRolesEnabled, "staff-password", "admin-password");
    }
}
