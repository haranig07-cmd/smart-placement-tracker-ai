package com.sptai.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.sptai.backend.entity.Application;
import com.sptai.backend.repository.ApplicationRepository;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;

    public ApplicationService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    public Application saveApplication(Application application) {

        if (application.getAppliedAt() == null) {
            application.setAppliedAt(LocalDateTime.now());
        }

        if (application.getStatus() == null ||
                application.getStatus().isBlank()) {
            application.setStatus("Applied");
        }

        return applicationRepository.save(application);
    }

    public List<Application> getApplicationsByStudent(Long studentId) {
        return applicationRepository.findByStudentId(studentId);
    }

    public List<Application> getApplicationsByJob(Long jobId) {
        return applicationRepository.findByJobId(jobId);
    }

    public Optional<Application> getApplicationById(Long id) {
        return applicationRepository.findById(id);
    }

    public Optional<Application> getApplicationByStudentAndJob(
            Long studentId,
            Long jobId) {

        return applicationRepository
                .findByStudentIdAndJobId(studentId, jobId);
    }

    public boolean alreadyApplied(Long studentId, Long jobId) {
        return applicationRepository
                .existsByStudentIdAndJobId(studentId, jobId);
    }
    public void deleteApplication(Long id) {
    applicationRepository.deleteById(id);
}
}