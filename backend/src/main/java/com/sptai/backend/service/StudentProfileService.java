package com.sptai.backend.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.sptai.backend.entity.StudentProfile;
import com.sptai.backend.repository.StudentProfileRepository;

@Service
public class StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;

    public StudentProfileService(
            StudentProfileRepository studentProfileRepository) {

        this.studentProfileRepository = studentProfileRepository;
    }

    // Find profile using logged-in user's ID
    public Optional<StudentProfile> findByUserId(Long userId) {

        return studentProfileRepository.findByUserId(userId);
    }

    // Create or save student profile
    public StudentProfile saveProfile(StudentProfile profile) {

        return studentProfileRepository.save(profile);
    }
}