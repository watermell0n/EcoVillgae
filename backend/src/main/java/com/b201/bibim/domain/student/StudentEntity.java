package com.b201.bibim.domain.student;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "students")
public class StudentEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "participant_number", nullable = false)
    private int participantNumber;

    @Column(name = "participation_code", nullable = false, length = 20)
    private String participationCode;

    @Column(length = 50)
    private String nickname;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "is_online", nullable = false)
    private boolean online;

    @Column(name = "login_version", nullable = false)
    private int loginVersion;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentEntity() {
    }

    public static StudentEntity invited(
            UUID roomId,
            int participantNumber,
            String participationCode
    ) {
        StudentEntity student = new StudentEntity();
        student.id = UUID.randomUUID();
        student.roomId = roomId;
        student.participantNumber = participantNumber;
        student.participationCode = participationCode;
        student.status = "INVITED";
        student.online = false;
        student.loginVersion = 0;
        student.updatedAt = Instant.now();
        return student;
    }

    public void completeSetup(String nickname, String passwordHash) {
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.status = "ACTIVE";
        recordLogin();
    }

    public void recordLogin() {
        this.loginVersion += 1;
        this.online = true;
        this.lastSeenAt = Instant.now();
        this.updatedAt = this.lastSeenAt;
    }

    public void reactivateFromAbsence() {
        if ("ABSENT".equals(this.status)) {
            this.status = "ACTIVE";
        }
        recordLogin();
    }

    public void forceLogout() {
        this.loginVersion += 1;
        this.online = false;
        this.updatedAt = Instant.now();
    }

    public void updateProfile(int participantNumber, String nickname) {
        this.participantNumber = participantNumber;
        this.nickname = nickname == null || nickname.isBlank() ? null : nickname.trim();
        this.updatedAt = Instant.now();
    }

    public void changeStatus(String status) {
        this.status = status;
        if (!"ACTIVE".equals(status)) {
            this.online = false;
        }
        this.updatedAt = Instant.now();
    }

    public void resetPassword() {
        this.passwordHash = null;
        this.status = "INVITED";
        forceLogout();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public int getParticipantNumber() {
        return participantNumber;
    }

    public String getParticipationCode() {
        return participationCode;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public int getLoginVersion() {
        return loginVersion;
    }

    public boolean isOnline() {
        return online;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
