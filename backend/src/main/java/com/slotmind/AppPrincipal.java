package com.slotmind;

public record AppPrincipal(String userId, String role, String patientName, String appointmentId) {
    public static AppPrincipal fromAuth0(String subject, String role) {
        return new AppPrincipal(subject, role, null, null);
    }

    public static AppPrincipal patient(String userId, String patientName, String appointmentId) {
        return new AppPrincipal(userId, "PATIENT", patientName, appointmentId);
    }
}
