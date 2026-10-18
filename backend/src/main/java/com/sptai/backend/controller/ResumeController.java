package com.sptai.backend.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sptai.backend.entity.Resume;
import com.sptai.backend.entity.User;
import com.sptai.backend.repository.ApplicationRepository;
import com.sptai.backend.service.ResumeAnalysisService;
import com.sptai.backend.service.ResumeService;
import com.sptai.backend.service.UserService;

@RestController
@RequestMapping("/api/resume")

@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://haranig07-cmd.github.io"
    },
    allowCredentials = "true"
)

public class ResumeController {

    private final ResumeService resumeService;
    private final UserService userService;
    private final ResumeAnalysisService resumeAnalysisService;
        private final ApplicationRepository applicationRepository;

    private final String uploadDirectory = "uploads/resumes/";

    public ResumeController(
            ResumeService resumeService,
            UserService userService,
                        ResumeAnalysisService resumeAnalysisService,
                        ApplicationRepository applicationRepository) {

        this.resumeService = resumeService;
        this.userService = userService;
        this.resumeAnalysisService = resumeAnalysisService;
                this.applicationRepository = applicationRepository;
    }

    // ==============================
    // GET EXISTING RESUME
    // ==============================

    @GetMapping("/{userId}")
    public ResponseEntity<?> getResume(
                        @PathVariable Long userId,
                        HttpServletRequest request) {

                Optional<ResponseEntity<Map<String, String>>> authorizationFailure =
                                authorizeResumeView(userId, request);

                if (authorizationFailure.isPresent()) {
                        return authorizationFailure.get();
                }

        Optional<Resume> resume =
                resumeService.findByUserId(userId);

        if (resume.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Resume savedResume = resume.get();

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "id",
                savedResume.getId()
        );

        response.put(
                "fileName",
                savedResume.getFileName()
        );

        response.put(
                "uploadedAt",
                savedResume.getUploadedAt()
        );

        response.put(
                "atsScore",
                savedResume.getAtsScore()
        );

        // Re-analyze the saved PDF
        if (savedResume.getFilePath() != null) {

            Map<String, Object> analysis =
                    resumeAnalysisService.analyzeResume(
                            savedResume.getFilePath()
                    );

            response.put(
                    "analysis",
                    analysis
            );
        }

        return ResponseEntity.ok(response);
    }

    // ==============================
    // SERVE ACTUAL PDF FILE
    // ==============================

    @GetMapping("/{userId}/file")
    public ResponseEntity<?> getResumeFile(
                        @PathVariable Long userId,
                        HttpServletRequest request) {

                Optional<ResponseEntity<Map<String, String>>> authorizationFailure =
                                authorizeResumeView(userId, request);

                if (authorizationFailure.isPresent()) {
                        return authorizationFailure.get();
                }

        Optional<Resume> resumeOpt =
                resumeService.findByUserId(userId);

        if (resumeOpt.isEmpty()) {
            return ResponseEntity
                    .status(404)
                    .body("No resume uploaded for this student.");
        }

        Resume resume = resumeOpt.get();

        if (resume.getFilePath() == null) {
            return ResponseEntity
                    .status(404)
                    .body("Resume file path not found.");
        }

        try {

            Path filePath =
                    Paths.get(resume.getFilePath());

            if (!Files.exists(filePath)) {
                return ResponseEntity
                        .status(404)
                        .body("Resume file no longer exists on the server.");
            }

            byte[] fileBytes =
                    Files.readAllBytes(filePath);

            String fileName =
                    resume.getFileName() != null
                            ? resume.getFileName()
                            : "resume.pdf";

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_PDF
            );

            headers.setContentDispositionFormData(
                    "inline",
                    fileName
            );

            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .body(fileBytes);

        } catch (IOException e) {

            return ResponseEntity
                    .internalServerError()
                                        .body(Map.of("error", "Unable to read resume file"));
        }
    }

    // ==============================
    // UPLOAD + ANALYZE RESUME
    // ==============================

    @PostMapping("/{userId}/upload")
    public ResponseEntity<?> uploadResume(
            @PathVariable Long userId,
                        @RequestParam("file") MultipartFile file,
                        HttpServletRequest request) {

                Optional<ResponseEntity<Map<String, String>>> authorizationFailure =
                                authorizeResumeChange(userId, request);

                if (authorizationFailure.isPresent()) {
                        return authorizationFailure.get();
                }

        try {

            // 1. Check user
            Optional<User> user =
                    userService.findById(userId);

            if (user.isEmpty()) {

                return ResponseEntity
                        .badRequest()
                        .body("User not found");
            }

            // 2. Check file
            if (file.isEmpty()) {

                return ResponseEntity
                        .badRequest()
                        .body("Please select a resume file");
            }

            // 3. Check file name
            String originalFileName =
                    file.getOriginalFilename();

            if (originalFileName == null) {

                return ResponseEntity
                        .badRequest()
                        .body("Invalid file");
            }

            // 4. Check file type
            String lowerFileName =
                    originalFileName.toLowerCase();

            if (!lowerFileName.endsWith(".pdf")
                    && !lowerFileName.endsWith(".doc")
                    && !lowerFileName.endsWith(".docx")) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Only PDF, DOC and DOCX files are allowed"
                        );
            }

            // 5. Create upload folder
            Path uploadPath =
                    Paths.get(uploadDirectory);

            if (!Files.exists(uploadPath)) {

                Files.createDirectories(uploadPath);
            }

            // 6. Create unique file name
            String uniqueFileName =
                    UUID.randomUUID()
                            + "_"
                            + originalFileName;

            Path filePath =
                    uploadPath.resolve(uniqueFileName);

            // 7. Save actual file
            Files.copy(
                    file.getInputStream(),
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            // 8. Check existing resume
            Optional<Resume> existingResume =
                    resumeService.findByUserId(userId);

            Resume resume;

            if (existingResume.isPresent()) {

                resume = existingResume.get();

                // Delete old file
                if (resume.getFilePath() != null) {

                    try {

                        Files.deleteIfExists(
                                Paths.get(
                                        resume.getFilePath()
                                )
                        );

                    } catch (IOException e) {

                        System.out.println(
                                "Could not delete old resume file: "
                                        + e.getMessage()
                        );
                    }
                }

            } else {

                resume = new Resume();
            }

            // 9. Save resume information
            resume.setUser(user.get());

            resume.setFileName(
                    originalFileName
            );

            resume.setFilePath(
                    filePath.toString()
            );

            resume.setUploadedAt(
                    LocalDateTime.now()
            );

            // =================================
            // 10. REAL RESUME ANALYSIS
            // =================================

            Map<String, Object> analysis =
                    resumeAnalysisService.analyzeResume(
                            filePath.toString()
                    );

            // Get ATS score
            Integer atsScore =
                    (Integer) analysis.get("atsScore");

            resume.setAtsScore(
                    atsScore != null ? atsScore : 0
            );

            // 11. Save to database
            Resume savedResume =
                    resumeService.saveResume(resume);

            // =================================
            // 12. SEND RESULT TO FRONTEND
            // =================================

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "id",
                    savedResume.getId()
            );

            response.put(
                    "fileName",
                    savedResume.getFileName()
            );

            response.put(
                    "uploadedAt",
                    savedResume.getUploadedAt()
            );

            response.put(
                    "atsScore",
                    savedResume.getAtsScore()
            );

            response.put(
                    "analysis",
                    analysis
            );

            return ResponseEntity.ok(response);

        } catch (IOException e) {

            return ResponseEntity
                    .internalServerError()
                    .body(Map.of("error", "Unable to upload resume"));
        }
    }

    // ==============================
    // DELETE RESUME
    // ==============================

    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteResume(
                        @PathVariable Long userId,
                        HttpServletRequest request) {

                Optional<ResponseEntity<Map<String, String>>> authorizationFailure =
                                authorizeResumeChange(userId, request);

                if (authorizationFailure.isPresent()) {
                        return authorizationFailure.get();
                }

        Optional<Resume> resume =
                resumeService.findByUserId(userId);

        if (resume.isEmpty()) {

            return ResponseEntity
                    .notFound()
                    .build();
        }

        try {

            if (resume.get().getFilePath() != null) {

                Files.deleteIfExists(
                        Paths.get(
                                resume.get().getFilePath()
                        )
                );
            }

        } catch (IOException e) {

            System.out.println(
                    "Could not delete resume file: "
                            + e.getMessage()
            );
        }

        resumeService.deleteResume(userId);

        return ResponseEntity.ok(
                "Resume deleted successfully"
        );
    }

    private Optional<ResponseEntity<Map<String, String>>> authorizeResumeView(
            Long userId,
            HttpServletRequest request) {

        Optional<User> authenticatedUser =
                getAuthenticatedUser(request);

        if (authenticatedUser.isEmpty()) {
            return Optional.of(resumeError(
                    "Authentication required"));
        }

        Optional<User> student =
                userService.findById(userId);

        if (student.isEmpty() ||
                !"STUDENT".equalsIgnoreCase(student.get().getRole())) {
            return Optional.of(resumeError(
                    "Resume access denied"));
        }

        User user = authenticatedUser.get();

        if ("STUDENT".equalsIgnoreCase(user.getRole()) &&
                Objects.equals(user.getId(), userId)) {
            return Optional.empty();
        }

        if ("OFFICER".equalsIgnoreCase(user.getRole())) {
            return Optional.empty();
        }

        if ("COMPANY".equalsIgnoreCase(user.getRole()) &&
                hasAppliedToCompany(userId, user.getCompanyName())) {
            return Optional.empty();
        }

        return Optional.of(resumeError(
                "Resume access denied"));
    }

    private Optional<ResponseEntity<Map<String, String>>> authorizeResumeChange(
            Long userId,
            HttpServletRequest request) {

        Optional<User> authenticatedUser =
                getAuthenticatedUser(request);

        if (authenticatedUser.isEmpty()) {
            return Optional.of(resumeError(
                    "Authentication required"));
        }

        User user = authenticatedUser.get();

        if ("STUDENT".equalsIgnoreCase(user.getRole()) &&
                Objects.equals(user.getId(), userId)) {
            return Optional.empty();
        }

        return Optional.of(resumeError(
                "Resume access denied"));
    }

    private Optional<User> getAuthenticatedUser(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

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

    private boolean hasAppliedToCompany(
            Long studentId,
            String companyName) {

        if (companyName == null || companyName.isBlank()) {
            return false;
        }

        return applicationRepository.findByStudentId(studentId)
                .stream()
                .map(application -> application.getJob())
                .filter(Objects::nonNull)
                .anyMatch(job ->
                        job.getCompanyName() != null &&
                                companyName.trim().equalsIgnoreCase(
                                        job.getCompanyName().trim()));
    }

    private ResponseEntity<Map<String, String>> resumeError(String message) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", message));
    }
}