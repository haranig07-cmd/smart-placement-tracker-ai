package com.sptai.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sptai.backend.entity.Resume;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByUserId(Long userId);
}