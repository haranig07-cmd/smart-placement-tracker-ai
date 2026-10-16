package com.sptai.backend.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.sptai.backend.service.JobRecommendationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sptai.backend.entity.Application;
import com.sptai.backend.entity.Job;
import com.sptai.backend.entity.StudentProfile;
import com.sptai.backend.repository.ApplicationRepository;
import com.sptai.backend.repository.JobRepository;
import com.sptai.backend.repository.StudentProfileRepository;
import com.sptai.backend.service.UserService;

@RestController
@RequestMapping("/api/company/dashboard")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*", "null"}, allowCredentials = "true")
public class CompanyDashboardController {

    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final StudentProfileRepository studentProfileRepository;
        private final UserService userService;
        private final JobRecommendationService jobRecommendationService;

    public CompanyDashboardController(
        JobRepository jobRepository,
        ApplicationRepository applicationRepository,
        StudentProfileRepository studentProfileRepository,
        UserService userService,
        JobRecommendationService jobRecommendationService) {

    this.jobRepository = jobRepository;
    this.applicationRepository = applicationRepository;
    this.studentProfileRepository = studentProfileRepository;
    this.userService = userService;
    this.jobRecommendationService = jobRecommendationService;
}

    // ==============================
    // COMPANY DASHBOARD
    // ==============================

    @GetMapping("/{companyName}")
    public ResponseEntity<?> getDashboard(
            @PathVariable String companyName) {

        List<Job> jobs =
                jobRepository.findByCompanyNameIgnoreCase(companyName);

        List<Application> applications = new ArrayList<>();

        for (Job job : jobs) {

            applications.addAll(
                    applicationRepository.findByJobId(job.getId())
            );
        }

        int totalJobs = jobs.size();

        int totalApplications = applications.size();

        int shortlisted =
                (int) applications.stream()
                        .filter(application ->
                                "Shortlisted".equalsIgnoreCase(
                                        application.getStatus()
                                )
                        )
                        .count();

        int newApplications =
                (int) applications.stream()
                        .filter(application ->
                                "Applied".equalsIgnoreCase(
                                        application.getStatus()
                                )
                        )
                        .count();

        int selected =
                (int) applications.stream()
                        .filter(application ->
                                "Selected".equalsIgnoreCase(
                                        application.getStatus()
                                )
                        )
                        .count();

        double selectionRate = 0;

        if (totalApplications > 0) {
            selectionRate =
                    ((double) selected / totalApplications) * 100;
        }

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("companyName", companyName);
        response.put("totalJobs", totalJobs);
        response.put("totalApplications", totalApplications);
        response.put("shortlisted", shortlisted);
        response.put("newApplications", newApplications);
        response.put("selected", selected);
        response.put("selectionRate", Math.round(selectionRate));
        response.put("jobs", jobs);

        return ResponseEntity.ok(response);
    }


    // ==============================
    // ALL COMPANY APPLICATIONS
    // ==============================

    @GetMapping("/{companyName}/applications")
    public ResponseEntity<?> getCompanyApplications(
                        @PathVariable String companyName,
                        HttpSession session) {

                Optional<ResponseEntity<Map<String, String>>> authorizationFailure =
                                authorizeCompanyApplications(companyName, session);

                if (authorizationFailure.isPresent()) {
                        return authorizationFailure.get();
                }

        List<Job> jobs =
                jobRepository.findByCompanyNameIgnoreCase(companyName);

        List<Application> applications = new ArrayList<>();

        for (Job job : jobs) {

            applications.addAll(
                    applicationRepository.findByJobId(job.getId())
            );
        }

        return ResponseEntity.ok(applications);
    }
    // ==============================
    // ENRICHED APPLICATIONS (with student profile)
    // ==============================

    @GetMapping("/{companyName}/applications/detail")
    public ResponseEntity<?> getCompanyApplicationsDetail(
            @PathVariable String companyName,
            HttpSession session) {

        Optional<ResponseEntity<Map<String, String>>> authorizationFailure =
                authorizeCompanyApplications(companyName, session);

        if (authorizationFailure.isPresent()) {
            return authorizationFailure.get();
        }

        List<Job> jobs =
                jobRepository.findByCompanyNameIgnoreCase(companyName);

        List<Map<String, Object>> result = new ArrayList<>();

        for (Job job : jobs) {

            List<Application> applications =
                    applicationRepository.findByJobId(job.getId());

            for (Application application : applications) {

                Map<String, Object> entry = new LinkedHashMap<>();

                entry.put("id", application.getId());
                entry.put("status", application.getStatus());
                entry.put("atsScore", application.getAtsScore());
                entry.put("appliedAt", application.getAppliedAt());
                // ==============================
// AI CANDIDATE-JOB MATCH
// ==============================

List<String> studentSkills = new ArrayList<>();

com.sptai.backend.entity.User student = application.getStudent();

if (student != null) {

    Optional<StudentProfile> profileOpt =
            studentProfileRepository.findByUserId(student.getId());

    if (profileOpt.isPresent()) {

        String skills = profileOpt.get().getSkills();

        if (skills != null && !skills.trim().isEmpty()) {

            for (String skill : skills.split("[,;]")) {

                String cleanedSkill = skill.trim();

                if (!cleanedSkill.isEmpty()) {
                    studentSkills.add(cleanedSkill);
                }
            }
        }
    }
}

Map<String, Object> aiMatch =
        jobRecommendationService.calculateMatch(studentSkills, job);

entry.put("aiMatchPercentage",
        aiMatch.get("matchPercentage"));

entry.put("matchedSkills",
        aiMatch.get("matchedSkills"));

entry.put("missingSkills",
        aiMatch.get("missingSkills"));

                // Student basic info (no password exposed)
                if (student != null) {
                    Map<String, Object> studentMap = new LinkedHashMap<>();
                    studentMap.put("id", student.getId());
                    studentMap.put("name", student.getName());
                    studentMap.put("email", student.getEmail());

                    // Enrich with student profile
                    Optional<StudentProfile> profileOpt =
                            studentProfileRepository.findByUserId(student.getId());

                    if (profileOpt.isPresent()) {
                        StudentProfile profile = profileOpt.get();
                        studentMap.put("phone", profile.getPhone());
                        studentMap.put("department", profile.getDepartment());
                        studentMap.put("cgpa", profile.getCgpa());
                        studentMap.put("skills", profile.getSkills());
                        studentMap.put("college", profile.getCollege());
                        studentMap.put("graduationYear", profile.getGraduationYear());
                    } else {
                        studentMap.put("phone", null);
                        studentMap.put("department", null);
                        studentMap.put("cgpa", null);
                        studentMap.put("skills", null);
                        studentMap.put("college", null);
                        studentMap.put("graduationYear", null);
                    }

                    entry.put("student", studentMap);
                }

                // Job info
                Map<String, Object> jobMap = new LinkedHashMap<>();
                jobMap.put("id", job.getId());
                jobMap.put("title", job.getTitle());
                jobMap.put("companyName", job.getCompanyName());
                jobMap.put("location", job.getLocation());
                entry.put("job", jobMap);

                result.add(entry);
            }
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{companyName}/shortlisted")
    public ResponseEntity<?> getShortlistedApplications(
            @PathVariable String companyName,
            HttpSession session) {

        Optional<ResponseEntity<Map<String, String>>> authorizationFailure =
                authorizeCompanyApplications(companyName, session);

        if (authorizationFailure.isPresent()) {
            return authorizationFailure.get();
        }

        List<Job> jobs =
                jobRepository.findByCompanyNameIgnoreCase(companyName);

        List<Application> shortlistedApplications =
                new ArrayList<>();

        for (Job job : jobs) {

            List<Application> applications =
                    applicationRepository.findByJobId(job.getId());

            for (Application application : applications) {

                if ("Shortlisted".equalsIgnoreCase(application.getStatus())
                        || "Selected".equalsIgnoreCase(application.getStatus())) {

                    shortlistedApplications.add(application);
                }
            }
        }

        return ResponseEntity.ok(shortlistedApplications);
    }

        private Optional<ResponseEntity<Map<String, String>>> authorizeCompanyApplications(
                        String companyName,
                        HttpSession session) {

                if (session == null) {
                        return Optional.of(applicationError("Authentication required"));
                }

                Object sessionUserId = session.getAttribute("userId");
                Object sessionRole = session.getAttribute("userRole");

                if (!(sessionUserId instanceof Number userId) ||
                                !(sessionRole instanceof String role)) {
                        return Optional.of(applicationError("Authentication required"));
                }

                Optional<com.sptai.backend.entity.User> user =
                                userService.findById(userId.longValue());

                if (user.isEmpty() || user.get().getRole() == null ||
                                !role.equalsIgnoreCase(user.get().getRole())) {
                        return Optional.of(applicationError("Authentication required"));
                }

                if (!"COMPANY".equalsIgnoreCase(user.get().getRole())) {
                        return Optional.of(applicationError("Company access required"));
                }

                if (user.get().getCompanyName() == null || companyName == null ||
                                !user.get().getCompanyName().trim()
                                                .equalsIgnoreCase(companyName.trim())) {
                        return Optional.of(applicationError("Application access denied"));
                }

                return Optional.empty();
        }

        private ResponseEntity<Map<String, String>> applicationError(String message) {
                return ResponseEntity.status(403).body(Map.of("error", message));
        }
}