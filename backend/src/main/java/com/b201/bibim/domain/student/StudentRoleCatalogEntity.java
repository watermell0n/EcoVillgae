package com.b201.bibim.domain.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_role_catalog")
public class StudentRoleCatalogEntity {

    @Id
    @Column(name = "participant_number")
    private int participantNumber;

    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    protected StudentRoleCatalogEntity() {
    }

    public int getParticipantNumber() {
        return participantNumber;
    }

    public String getRoleName() {
        return roleName;
    }
}
