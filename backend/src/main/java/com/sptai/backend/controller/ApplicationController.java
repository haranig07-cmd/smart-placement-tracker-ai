package com.sptai.backend.controller;
import com.sptai.backend.service.NotificationService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.sptai.backend.repository.ResumeRepository;
import com.sptai.backend.service.ApplicationService;
import com.sptai.backend.service.JobService;
import com.sptai.backend.service.UserService;
import com.sptai.backend.repository.StudentProfileRepository;

@RestController
@RequestMapping("/api/applications")

@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://haranig07-cmd.github.io"
    },
    allowCredentials = "true"
)

public class ApplicationController {

        private static final Set<String> APPLICATION_STATUSES = Set.of(
                        "Applied",
                        "Shortlisted",
                        "Rejected",
                        "Selected");

private final ApplicationService applicationService;
private final UserService userService;
private final JobService jobService;
private final ResumeRepository resumeRepository;
private final StudentProfileRepository studentProfileRepository;
private final NotificationService notificationService;


public ApplicationController(
    ApplicationService applicationService,
    UserService userService,
    JobService jobService,
    ResumeRepository resumeRepository,
    StudentProfileRepository studentProfileRepository,
    NotificationService notificationService) {

    this.applicationService = applicationService;
    this.userService = userService;
    this.jobService = jobService;
    this.resumeRepository = resumeRepository;
    this.studentProfileRepository = studentProfileRepository;
    this.notificationService = notificationService;
}

    // Student applies for a job
    @PostMapping
    public ResponseEntity<?> applyForJob(
                        @RequestBody Map<String, Long> request,
                        HttpSession session) {

        Optional<User> authenticatedUser = getAuthenticatedUser(session);

        if (authenticatedUser.isEmpty()) {
                return authenticationRequired();
        }

        User student = authenticatedUser.get();

        if (!hasRole(student, "STUDENT")) {
                return studentAccessRequired();
        }

        Long requestedStudentId = request.get("studentId");
        Long jobId = request.get("jobId");

        if (requestedStudentId != null &&
                !Objects.equals(student.getId(), requestedStudentId)) {
                return applicationAccessDenied();
                }

        Long studentId = student.getId();

if (studentId == null || jobId == null) {

    return ResponseEntity
            .badRequest()
            .body("studentId and jobId are required");
}


// =====================================================
// CHECK STUDENT PROFILE AND RESUME BEFORE APPLYING
// =====================================================

boolean profileComplete = studentProfileRepository
        .findByUserId(studentId)
        .map(profile ->
                profile.getPhone() != null && !profile.getPhone().isBlank()
                && profile.getCollege() != null && !profile.getCollege().isBlank()
                && profile.getDepartment() != null && !profile.getDepartment().isBlank()
                && profile.getGraduationYear() != null
                && profile.getCgpa() != null && !profile.getCgpa().isBlank()
                && profile.getSkills() != null && !profile.getSkills().isBlank()
        )
        .orElse(false);

Optional<com.sptai.backend.entity.Resume> studentResume =
        resumeRepository.findByUserId(studentId);

boolean resumeUploaded = studentResume.isPresent();

if (!profileComplete && !resumeUploaded) {
    return ResponseEntity
            .badRequest()
            .body("Please complete your profile and upload your resume before applying for jobs.");
}

if (!profileComplete) {
    return ResponseEntity
            .badRequest()
            .body("Please complete your student profile before applying for jobs.");
}

if (!resumeUploaded) {
    return ResponseEntity
            .badRequest()
            .body("Please upload your resume before applying for jobs.");
}

// ATS score requirement
Integer atsScore = studentResume.get().getAtsScore();

if (atsScore == null || atsScore < 65) {
    return ResponseEntity
            .badRequest()
            .body("Your ATS score must be at least 65% to apply for jobs.");
}
        Optional<Job> job = jobService.getJobById(jobId);

if (job.isEmpty()) {
    return ResponseEntity
            .badRequest()
            .body("Job not found");
}

// Check whether the application deadline has passed
if (job.get().getDeadline() != null
        && job.get().getDeadline().isBefore(java.time.LocalDate.now())) {

    return ResponseEntity
            .badRequest()
            .body("Applications for this job are closed because the deadline has passed.");
}

        if (applicationService.alreadyApplied(studentId, jobId)) {
            return ResponseEntity
                    .badRequest()
                    .body("You have already applied for this job");
        }

        Application application = new Application();

application.setStudent(student);
application.setJob(job.get());


// Get student's resume ATS score
resumeRepository.findByUserId(studentId)
        .ifPresent(resume -> {

            if (resume.getAtsScore() != null) {

                application.setAtsScore(
                        resume.getAtsScore()
                );

            }

        });


Application savedApplication =
        applicationService.saveApplication(application);

        User companyUser = userService.findByCompanyName(job.get().getCompanyName())
    .orElse(null);

if (companyUser != null) {
    String studentName = student.getName() != null
        ? student.getName()
        : "A student";

    String jobTitle = job.get().getTitle() != null
        ? job.get().getTitle()
        : "your job";

    notificationService.createNotification(
        companyUser,
        studentName + " applied for your job: " + jobTitle,
        "APPLICATION",
        "applications.html"
    );
}

        Map<String, Object> response = new HashMap<>();

        response.put("id", savedApplication.getId());
        response.put("studentId", studentId);
        response.put("jobId", jobId);
        response.put("status", savedApplication.getStatus());
        response.put("appliedAt", savedApplication.getAppliedAt());

        return ResponseEntity.ok(response);
    }

    // Get all applications submitted by one student
    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getStudentApplications(
                        @PathVariable Long studentId,
                        HttpSession session) {

                Optional<User> authenticatedUser = getAuthenticatedUser(session);

                if (authenticatedUser.isEmpty()) {
                        return authenticationRequired();
                }

                User student = authenticatedUser.get();

                if (!hasRole(student, "STUDENT")) {
                        return studentAccessRequired();
                }

                if (!Objects.equals(student.getId(), studentId)) {
                        return applicationAccessDenied();
                }

        return ResponseEntity.ok(
                applicationService.getApplicationsByStudent(studentId)
        );
    }

    // Get all applications for one job
    @GetMapping("/job/{jobId}")
    public ResponseEntity<?> getJobApplications(
            @PathVariable Long jobId,
            HttpSession session) {

        Optional<User> authenticatedUser = getAuthenticatedUser(session);

        if (authenticatedUser.isEmpty()) {
                return authenticationRequired();
        }

        User company = authenticatedUser.get();

        if (!hasRole(company, "COMPANY")) {
                return companyAccessRequired();
        }

        Optional<Job> job = jobService.getJobById(jobId);

        if (job.isEmpty()) {
                return ResponseEntity.notFound().build();
        }

        if (!ownsJob(job.get(), company)) {
                return applicationAccessDenied();
        }

        return ResponseEntity.ok(
                applicationService.getApplicationsByJob(jobId)
        );
    }

    // Get one application
    @GetMapping("/{id}")
    public ResponseEntity<?> getApplication(
                        @PathVariable Long id,
                        HttpSession session) {

                Optional<User> authenticatedUser = getAuthenticatedUser(session);

                if (authenticatedUser.isEmpty()) {
                                return authenticationRequired();
                }

        Optional<Application> application =
                applicationService.getApplicationById(id);

        if (application.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = authenticatedUser.get();
        Application savedApplication = application.get();

        boolean isStudentOwner = hasRole(user, "STUDENT") &&
                savedApplication.getStudent() != null &&
                Objects.equals(savedApplication.getStudent().getId(), user.getId());
        boolean isCompanyOwner = hasRole(user, "COMPANY") &&
                ownsJob(savedApplication.getJob(), user);

        if (!isStudentOwner && !isCompanyOwner) {
                return applicationAccessDenied();
        }

        return ResponseEntity.ok(savedApplication);
    }

    // Update application status
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            HttpSession session) {

                Optional<User> authenticatedUser = getAuthenticatedUser(session);

                if (authenticatedUser.isEmpty()) {
                        return authenticationRequired();
                }

                User company = authenticatedUser.get();

                if (!hasRole(company, "COMPANY")) {
                        return companyAccessRequired();
        }

        String requestedStatus = request.get("status");

        if (requestedStatus == null || requestedStatus.isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body("Status is required");
        }

        String status = APPLICATION_STATUSES.stream()
                .filter(allowedStatus -> allowedStatus.equalsIgnoreCase(
                        requestedStatus.trim()))
                .findFirst()
                .orElse(null);

        if (status == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Status must be Applied, Shortlisted, Rejected, or Selected");
        }

        Optional<Application> existingApplication =
                applicationService.getApplicationById(id);

        if (existingApplication.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Application application = existingApplication.get();

        if (!ownsJob(application.getJob(), company)) {
            return applicationAccessDenied();
        }

        application.setStatus(status);

        Application updatedApplication =
        applicationService.saveApplication(application);

// Notify the student about the application status change
User studentUser = application.getStudent();

if (studentUser != null) {

    String jobTitle = application.getJob() != null
            && application.getJob().getTitle() != null
            ? application.getJob().getTitle()
            : "the job";

    String message;

    if ("Shortlisted".equalsIgnoreCase(status)) {

        message = "Your application for " + jobTitle
                + " has been shortlisted.";

    } else if ("Selected".equalsIgnoreCase(status)) {

        message = "Congratulations! You have been selected for "
                + jobTitle + ".";

    } else if ("Rejected".equalsIgnoreCase(status)) {

        message = "Your application for " + jobTitle
                + " has been rejected.";

    } else {

        message = "Your application for " + jobTitle
                + " has been updated to " + status + ".";
    }

    notificationService.createNotification(
            studentUser,
            message,
            "APPLICATION_STATUS",
            "student-applications.html"
    );
}

Map<String, Object> response = new HashMap<>();

        response.put("id", updatedApplication.getId());
        response.put("status", updatedApplication.getStatus());
        response.put("appliedAt", updatedApplication.getAppliedAt());

        return ResponseEntity.ok(response);
    }

        private boolean hasRole(User user, String role) {
                return user.getRole() != null &&
                                role.equalsIgnoreCase(user.getRole());
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

                Optional<User> user = userService.findById(userId.longValue());

                if (user.isEmpty() || user.get().getRole() == null ||
                                !role.equalsIgnoreCase(user.get().getRole())) {
                        return Optional.empty();
                }

                return user;
        }

        private boolean ownsJob(Job job, User company) {
                return job != null &&
                                job.getCompanyName() != null &&
                                company.getCompanyName() != null &&
                                job.getCompanyName().trim().equalsIgnoreCase(
                                                company.getCompanyName().trim());
        }

        private ResponseEntity<Map<String, String>> authenticationRequired() {
                return applicationError("Authentication required");
        }

        private ResponseEntity<Map<String, String>> studentAccessRequired() {
                return applicationError("Student access required");
        }

        private ResponseEntity<Map<String, String>> companyAccessRequired() {
                return applicationError("Company access required");
        }

        private ResponseEntity<Map<String, String>> applicationAccessDenied() {
                return applicationError("Application access denied");
        }

        private ResponseEntity<Map<String, String>> applicationError(String message) {
                return ResponseEntity.status(403).body(Map.of("error", message));
        }
    // Update application ATS score from student's resume
@PutMapping("/{id}/ats-score")
public ResponseEntity<?> updateAtsScore(
        @PathVariable Long id,
        HttpSession session) {

    Optional<User> authenticatedUser = getAuthenticatedUser(session);

    if (authenticatedUser.isEmpty()) {
        return authenticationRequired();
    }

    User student = authenticatedUser.get();

    if (!hasRole(student, "STUDENT")) {
        return studentAccessRequired();
    }

    Optional<Application> existingApplication =
            applicationService.getApplicationById(id);

    if (existingApplication.isEmpty()) {
        return ResponseEntity.notFound().build();
    }

    Application application = existingApplication.get();

        if (application.getStudent() == null ||
                        !Objects.equals(application.getStudent().getId(), student.getId())) {
                return applicationAccessDenied();
        }

    Long studentId = application.getStudent().getId();

    Optional<com.sptai.backend.entity.Resume> resume =
            resumeRepository.findByUserId(studentId);

    if (resume.isEmpty() || resume.get().getAtsScore() == null) {
        return ResponseEntity
                .badRequest()
                .body("Student does not have an ATS score");
    }

    application.setAtsScore(
            resume.get().getAtsScore()
    );

    Application updatedApplication =
            applicationService.saveApplication(application);

    Map<String, Object> response = new HashMap<>();

    response.put("id", updatedApplication.getId());
    response.put("atsScore", updatedApplication.getAtsScore());

    return ResponseEntity.ok(response);
}
@DeleteMapping("/{applicationId}")
public ResponseEntity<?> withdrawApplication(
        @PathVariable Long applicationId,
        HttpServletRequest request) {

    HttpSession session = request.getSession(false);

    if (session == null || session.getAttribute("userId") == null) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Authentication required"));
    }

    String role = (String) session.getAttribute("userRole");

    if (!"STUDENT".equals(role)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Student access required"));
    }

    Long studentId = (Long) session.getAttribute("userId");

    Optional<Application> applicationOpt =
            applicationService.getApplicationById(applicationId);

    if (applicationOpt.isEmpty()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Application not found"));
    }

    Application application = applicationOpt.get();

    if (application.getStudent() == null
            || !application.getStudent().getId().equals(studentId)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "You can withdraw only your own application"));
    }

    String status = application.getStatus();

    if ("Selected".equalsIgnoreCase(status)
            || "Rejected".equalsIgnoreCase(status)) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "This application cannot be withdrawn"));
    }

    applicationService.deleteApplication(applicationId);

    return ResponseEntity.ok(
            Map.of("message", "Application withdrawn successfully"));
}
}