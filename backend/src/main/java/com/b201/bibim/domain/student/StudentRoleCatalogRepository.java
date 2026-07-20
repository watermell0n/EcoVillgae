package com.b201.bibim.domain.student;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRoleCatalogRepository extends JpaRepository<StudentRoleCatalogEntity, Integer> {

    List<StudentRoleCatalogEntity> findAllByOrderByParticipantNumber();
}
