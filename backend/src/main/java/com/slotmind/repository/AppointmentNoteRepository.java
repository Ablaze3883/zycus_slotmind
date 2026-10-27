package com.slotmind.repository;

import com.slotmind.domain.AppointmentNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppointmentNoteRepository extends JpaRepository<AppointmentNote, Long> {
    List<AppointmentNote> findByAppointment_IdOrderByCreatedAtDesc(String appointmentId);
    Optional<AppointmentNote> findByIdAndAppointment_Id(Long id, String appointmentId);
}
