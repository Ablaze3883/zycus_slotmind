package com.slotmind.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointment_note")
public class AppointmentNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(nullable = false, length = 200)
    private String author;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AppointmentNote() {}

    public AppointmentNote(Appointment appointment, String content, String author) {
        this.appointment = appointment;
        this.content = content;
        this.author = author;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public String getContent() { return content; }
    public String getAuthor() { return author; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
