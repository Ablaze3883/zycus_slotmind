package com.slotmind.service;

import com.slotmind.domain.AppointmentNote;
import com.slotmind.repository.AppointmentNoteRepository;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.view.Views.AppointmentNoteView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AppointmentNoteService {
    public record NoteRequest(String content) {}

    private final AppointmentRepository appointments;
    private final AppointmentNoteRepository notes;

    public AppointmentNoteService(AppointmentRepository appointments, AppointmentNoteRepository notes) {
        this.appointments = appointments;
        this.notes = notes;
    }

    public List<AppointmentNoteView> list(String appointmentId) {
        requireAppointment(appointmentId);
        return notes.findByAppointment_IdOrderByCreatedAtDesc(appointmentId).stream()
                .map(AppointmentNoteView::of).toList();
    }

    @Transactional
    public AppointmentNoteView add(String appointmentId, NoteRequest request, String author) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("Note content is required");
        }
        String content = request.content().trim();
        if (content.length() > 5000) throw new IllegalArgumentException("Note content must be 5000 characters or fewer");
        if (author == null || author.isBlank()) throw new IllegalArgumentException("Note author is required");
        var appointment = requireAppointment(appointmentId);
        return AppointmentNoteView.of(notes.save(new AppointmentNote(appointment, content, author)));
    }

    @Transactional
    public void delete(String appointmentId, Long noteId) {
        AppointmentNote note = notes.findByIdAndAppointment_Id(noteId, appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment note", noteId.toString()));
        notes.delete(note);
    }

    private com.slotmind.domain.Appointment requireAppointment(String appointmentId) {
        return appointments.findDetailed(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment", appointmentId));
    }
}
