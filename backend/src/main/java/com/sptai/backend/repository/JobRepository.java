package com.sptai.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sptai.backend.entity.Job;

public interface JobRepository
        extends JpaRepository<Job, Long> {

    List<Job> findByCompanyNameIgnoreCase(
            String companyName
    );

}