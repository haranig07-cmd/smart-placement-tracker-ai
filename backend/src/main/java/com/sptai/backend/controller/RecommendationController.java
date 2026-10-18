package com.sptai.backend.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sptai.backend.entity.Job;
import com.sptai.backend.entity.Resume;
import com.sptai.backend.entity.StudentProfile;
import com.sptai.backend.repository.JobRepository;
import com.sptai.backend.repository.ResumeRepository;
import com.sptai.backend.repository.StudentProfileRepository;
import com.sptai.backend.service.JobRecommendationService;
import com.sptai.backend.service.ResumeAnalysisService;

@RestController
@RequestMapping("/api/recommendations")

@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://haranig07-cmd.github.io"
    },
    allowCredentials = "true"
)

public class RecommendationController {

    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final JobRecommendationService recommendationService;
    private final ResumeAnalysisService resumeAnalysisService;

    public RecommendationController(
            JobRepository jobRepository,
            ResumeRepository resumeRepository,
            StudentProfileRepository studentProfileRepository,
            JobRecommendationService recommendationService,
            ResumeAnalysisService resumeAnalysisService) {

        this.jobRepository = jobRepository;
        this.resumeRepository = resumeRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.recommendationService = recommendationService;
        this.resumeAnalysisService = resumeAnalysisService;
    }

    // ----------------------------------------------------
    // GET JOB RECOMMENDATIONS FOR A STUDENT
    // ----------------------------------------------------

    @GetMapping("/{studentId}")
    public ResponseEntity<?> getRecommendations(
            @PathVariable Long studentId) {

        List<String> studentSkills = new ArrayList<>();
        String source = "none";

        // 1. Check if student has an uploaded resume
        Resume resume =
                resumeRepository.findByUserId(studentId)
                        .orElse(null);

        if (resume != null && resume.getFilePath() != null) {
            Map<String, Object> analysis =
                    resumeAnalysisService.analyzeResume(
                            resume.getFilePath()
                    );

            Object detectedSkillsObject = analysis.get("detectedSkills");
            if (detectedSkillsObject != null) {
                List<?> detectedSkills = (List<?>) detectedSkillsObject;
                for (Object skill : detectedSkills) {
                    if (skill != null && !skill.toString().trim().isEmpty()) {
                        studentSkills.add(skill.toString().trim());
                    }
                }
            }

            if (!studentSkills.isEmpty()) {
                source = "resume";
            }
        }

        // 2. Fallback to student profile skills if no resume or no skills detected from resume
        if (studentSkills.isEmpty()) {
            StudentProfile profile =
                    studentProfileRepository.findByUserId(studentId)
                            .orElse(null);

            if (profile != null && profile.getSkills() != null && !profile.getSkills().trim().isEmpty()) {
                String[] profileSkills = profile.getSkills().split("[,;]");
                for (String s : profileSkills) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty()) {
                        studentSkills.add(trimmed);
                    }
                }

                if (!studentSkills.isEmpty()) {
                    source = "profile";
                }
            }
        }

        // Get all jobs
        List<Job> jobs =
                jobRepository.findAll();

        List<Map<String, Object>> recommendations =
                new ArrayList<>();

        // Compare student with every job
        for (Job job : jobs) {

            Map<String, Object> match =
                    recommendationService.calculateMatch(
                            studentSkills,
                            job
                    );

            Map<String, Object> recommendation =
                    new LinkedHashMap<>();

            recommendation.put(
                    "jobId",
                    job.getId()
            );

            recommendation.put(
                    "title",
                    job.getTitle()
            );

            recommendation.put(
                    "companyName",
                    job.getCompanyName()
            );

            recommendation.put(
                    "location",
                    job.getLocation()
            );

            recommendation.put(
                    "jobType",
                    job.getJobType()
            );

            recommendation.put(
                    "experience",
                    job.getExperience()
            );

            recommendation.put(
                    "salary",
                    job.getSalary()
            );

            recommendation.put(
                    "deadline",
                    job.getDeadline()
            );

            recommendation.put(
                    "description",
                    job.getDescription()
            );

            recommendation.put(
                    "requiredSkills",
                    job.getSkills()
            );

            recommendation.put(
                    "matchPercentage",
                    match.get("matchPercentage")
            );

            recommendation.put(
                    "matchedSkills",
                    match.get("matchedSkills")
            );

            recommendation.put(
                    "missingSkills",
                    match.get("missingSkills")
            );

            recommendations.add(
                    recommendation
            );
        }

        // Highest matching jobs first
        recommendations.sort(
                Comparator.comparing(
                        recommendation ->
                                ((Number) recommendation
                                        .get("matchPercentage"))
                                        .intValue(),
                        Comparator.reverseOrder()
                )
        );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "studentId",
                studentId
        );

        response.put(
                "studentSkills",
                studentSkills
        );

        response.put(
                "source",
                source
        );

        if ("none".equals(source)) {
            response.put(
                    "message",
                    "Add skills to your profile or upload a resume for personalized matches."
            );
        }

        response.put(
                "recommendations",
                recommendations
        );

        return ResponseEntity.ok(response);
    }
}