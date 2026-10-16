package com.sptai.backend.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.sptai.backend.entity.Resume;
import com.sptai.backend.repository.ResumeRepository;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;

    public ResumeService(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    public Optional<Resume> findByUserId(Long userId) {
        return resumeRepository.findByUserId(userId);
    }

    public Resume saveResume(Resume resume) {
        return resumeRepository.save(resume);
    }

    public void deleteResume(Long userId) {

        Optional<Resume> resume =
                resumeRepository.findByUserId(userId);

        if (resume.isPresent()) {
            resumeRepository.delete(resume.get());
        }
    }
}