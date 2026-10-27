package com.slotmind;

import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.SuggestionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class PatientSessionFilter extends OncePerRequestFilter {
    private final PatientAuthService authService;
    private final AppointmentRepository appointments;
    private final SuggestionRepository suggestions;

    public PatientSessionFilter(PatientAuthService authService, AppointmentRepository appointments,
                                SuggestionRepository suggestions) {
        this.authService = authService;
        this.appointments = appointments;
        this.suggestions = suggestions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && (header.startsWith("Bearer pt_") || header.startsWith("Bearer demo_"))) {
            String token = header.substring("Bearer ".length());
            AppPrincipal principal = authService.authenticate(token);
            if (principal != null) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, token, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (principal.role().equals("PATIENT") && !ownsRequestedResource(request, principal)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"You can only access your own patient data.\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private boolean ownsRequestedResource(HttpServletRequest request, AppPrincipal principal) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        if ((method.equals("GET") || method.equals("POST")) && path.matches("/portal/appointments/[^/]+(?:/reschedule)?")) {
            String[] parts = path.split("/");
            String appointmentId = parts[3];
            return appointments.findDetailed(appointmentId)
                    .map(appointment -> appointment.getPatientName().equalsIgnoreCase(principal.patientName()))
                    .orElse(false);
        }
        if (method.equals("PATCH") && path.matches("/suggestions/[^/]+")) {
            String suggestionId = path.substring("/suggestions/".length());
            return suggestions.findDetailed(suggestionId)
                    .map(suggestion -> suggestion.getAppointment().getPatientName().equalsIgnoreCase(principal.patientName()))
                    .orElse(false);
        }
        if ((method.equals("GET") || method.equals("PUT")) && path.matches("/preferences/[^/]+")) {
            String patientName = path.substring("/preferences/".length());
            return patientName.equalsIgnoreCase(principal.patientName());
        }
        return true;
    }
}
