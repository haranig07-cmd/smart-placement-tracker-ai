package com.sptai.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sptai.backend.entity.Application;

public interface ApplicationRepository
        extends JpaRepository<Application, Long> {

    List<Application> findByStudentId(Long studentId);

    List<Application> findByJobId(Long jobId);

    Optional<Application> findByStudentIdAndJobId(
            Long studentId,
            Long jobId
    );

    boolean existsByStudentIdAndJobId(
            Long studentId,
            Long jobId
    );
}