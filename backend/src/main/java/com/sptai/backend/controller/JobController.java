package com.sptai.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sptai.backend.entity.Application;
import com.sptai.backend.entity.Job;
import com.sptai.backend.entity.User;
import com.sptai.backend.repository.ApplicationRepository;
import com.sptai.backend.service.JobService;
import com.sptai.backend.service.UserService;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*", "null"}, allowCredentials = "true")
public class JobController {

    private final JobService jobService;
    private final ApplicationRepository applicationRepository;
    private final UserService userService;

    public JobController(
            JobService jobService,
            ApplicationRepository applicationRepository,
            UserService userService) {

        this.jobService = jobService;
        this.applicationRepository = applicationRepository;
        this.userService = userService;
    }

    // Get all jobs
    @GetMapping
    public ResponseEntity<List<Job>> getAllJobs() {

        return ResponseEntity.ok(
                jobService.getAllJobs()
        );
    }

    // Get one job
    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(
            @PathVariable Long id) {

        return jobService
                .getJobById(id)
                .map(ResponseEntity::ok)
                .orElse(
                        ResponseEntity.notFound().build()
                );
    }

    // Post a job
    @PostMapping
    public ResponseEntity<?> createJob(
            @RequestBody Job job,
            HttpSession session) {

        Optional<User> authenticatedUser =
                getAuthenticatedUser(session);

        if (authenticatedUser.isEmpty()) {
            return authenticationRequired();
        }

        User company = authenticatedUser.get();

        if (!hasCompanyAccess(company)) {
            return companyAccessRequired();
        }

        job.setId(null);
        job.setCompanyName(company.getCompanyName());

        Job savedJob =
                jobService.saveJob(job);

        return ResponseEntity.ok(savedJob);
    }

    // Update a job
    @PutMapping("/{id}")
    public ResponseEntity<?> updateJob(
            @PathVariable Long id,
            @RequestBody Job updatedJob,
            HttpSession session) {

        Optional<User> authenticatedUser =
                getAuthenticatedUser(session);

        if (authenticatedUser.isEmpty()) {
            return authenticationRequired();
        }

        User company = authenticatedUser.get();

        if (!hasCompanyAccess(company)) {
            return companyAccessRequired();
        }

        Optional<Job> optionalJob = jobService.getJobById(id);
        if (optionalJob.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Job existingJob = optionalJob.get();

        if (!ownsJob(existingJob, company)) {
            return jobAccessDenied();
        }

        // Update editable fields only, preserving original ID, companyName, and postedAt
        existingJob.setTitle(updatedJob.getTitle());
        existingJob.setLocation(updatedJob.getLocation());
        existingJob.setJobType(updatedJob.getJobType());
        existingJob.setExperience(updatedJob.getExperience());
        existingJob.setSalary(updatedJob.getSalary());
        existingJob.setDeadline(updatedJob.getDeadline());
        existingJob.setDescription(updatedJob.getDescription());
        existingJob.setSkills(updatedJob.getSkills());

        Job savedJob = jobService.saveJob(existingJob);

        return ResponseEntity.ok(savedJob);
    }

    // Delete a job
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteJob(
            @PathVariable Long id,
            HttpSession session) {

        Optional<User> authenticatedUser =
                getAuthenticatedUser(session);

        if (authenticatedUser.isEmpty()) {
            return authenticationRequired();
        }

        User company = authenticatedUser.get();

        if (!hasCompanyAccess(company)) {
            return companyAccessRequired();
        }

        Optional<Job> optionalJob = jobService.getJobById(id);
        if (optionalJob.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Job job = optionalJob.get();

        if (!ownsJob(job, company)) {
            return jobAccessDenied();
        }

        // Safely remove associated applications before deleting the job to prevent FK constraint failures
        List<Application> applications = applicationRepository.findByJobId(id);
        if (applications != null && !applications.isEmpty()) {
            applicationRepository.deleteAll(applications);
        }

        jobService.deleteJob(id);

        return ResponseEntity.ok(
                "Job deleted successfully"
        );
    }

    private Optional<User> getAuthenticatedUser(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }

        Object sessionUserId = session.getAttribute("userId");
        Object sessionRole = session.getAttribute("userRole");

        if (!(sessionUserId instanceof Number userId) ||
                !(sessionRole instanceof String role)) {
            return Optional.empty();
        }

        Optional<User> user =
                userService.findById(userId.longValue());

        if (user.isEmpty() || user.get().getRole() == null ||
                !role.equalsIgnoreCase(user.get().getRole())) {
            return Optional.empty();
        }

        return user;
    }

    private boolean hasCompanyAccess(User user) {
    return "COMPANY".equalsIgnoreCase(user.getRole()) &&
            "ACTIVE".equalsIgnoreCase(user.getAccountStatus()) &&
            user.getCompanyName() != null &&
            !user.getCompanyName().isBlank();
}

    private boolean ownsJob(Job job, User company) {
        return job.getCompanyName() != null &&
            job.getCompanyName().trim().equalsIgnoreCase(
                company.getCompanyName().trim());
    }

    private ResponseEntity<Map<String, String>> authenticationRequired() {
        return jobError("Authentication required");
    }

    private ResponseEntity<Map<String, String>> companyAccessRequired() {
        return jobError("Company access required");
    }

    private ResponseEntity<Map<String, String>> jobAccessDenied() {
        return jobError("Job access denied");
    }

    private ResponseEntity<Map<String, String>> jobError(String message) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", message));
    }
}