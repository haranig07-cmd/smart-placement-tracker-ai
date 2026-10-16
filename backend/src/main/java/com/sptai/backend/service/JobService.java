package com.sptai.backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.sptai.backend.entity.Job;
import com.sptai.backend.repository.JobRepository;

@Service
public class JobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public Job saveJob(Job job) {

        if (job.getPostedAt() == null) {
            job.setPostedAt(
                    java.time.LocalDateTime.now()
            );
        }

        return jobRepository.save(job);
    }

    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }

    public Optional<Job> getJobById(Long id) {
        return jobRepository.findById(id);
    }

    public void deleteJob(Long id) {
        jobRepository.deleteById(id);
    }
}