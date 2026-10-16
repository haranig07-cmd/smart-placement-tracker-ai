package com.sptai.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sptai.backend.entity.Application;
import com.sptai.backend.entity.Job;
import com.sptai.backend.entity.Resume;
import com.sptai.backend.entity.StudentProfile;
import com.sptai.backend.entity.User;
import com.sptai.backend.repository.ApplicationRepository;
import com.sptai.backend.repository.JobRepository;
import com.sptai.backend.repository.ResumeRepository;
import com.sptai.backend.repository.StudentProfileRepository;
import com.sptai.backend.repository.UserRepository;

@Service
public class OfficerService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final JobRecommendationService jobRecommendationService;

    public OfficerService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            ResumeRepository resumeRepository,
            JobRecommendationService jobRecommendationService) {

        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.resumeRepository = resumeRepository;
        this.jobRecommendationService = jobRecommendationService;
    }

    // =========================================================
    // OFFICER DASHBOARD
    // =========================================================

    public Map<String, Object> getDashboardStats() {

        List<User> students =
                userRepository.findAll()
                        .stream()
                        .filter(user ->
                                "STUDENT".equalsIgnoreCase(
                                        user.getRole()))
                        .collect(Collectors.toList());

        List<Application> applications =
                applicationRepository.findAll();

        List<Job> jobs =
                jobRepository.findAll();

        long placedStudents =
                applications.stream()
                        .filter(this::isPlaced)
                        .filter(application ->
                                application.getStudent() != null)
                        .map(application ->
                                application.getStudent().getId())
                        .distinct()
                        .count();

        long shortlistedStudents =
                applications.stream()
                        .filter(this::isShortlisted)
                        .filter(application ->
                                application.getStudent() != null)
                        .map(application ->
                                application.getStudent().getId())
                        .distinct()
                        .count();

        long activeApplications =
                applications.stream()
                        .filter(application ->
                                !isPlaced(application))
                        .count();

        long totalCompanies =
        jobs.stream()
                .map(job -> job.getCompanyName())
                .filter(name -> name != null)
                .map(name -> name.trim())
                .filter(name -> !name.isEmpty())
                .map(name -> name.toLowerCase())
                .distinct()
                .count();

        double placementRate = 0;

        if (!students.isEmpty()) {

            placementRate =
                    ((double) placedStudents /
                    students.size()) * 100;
        }

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "totalStudents",
                students.size());

        result.put(
                "placedStudents",
                placedStudents);

        result.put(
                "unplacedStudents",
                students.size() - placedStudents);

        result.put(
                "totalApplications",
                applications.size());

        result.put(
                "activeApplications",
                activeApplications);

        result.put(
                "shortlistedStudents",
                shortlistedStudents);

        result.put(
                "totalJobs",
                jobs.size());

        result.put(
                "totalCompanies",
                totalCompanies);

        result.put(
                "placementRate",
                placementRate);

        return result;
    }

    // =========================================================
    // STUDENTS
    // =========================================================

    public List<Map<String, Object>> getStudents() {

        List<User> students =
                userRepository.findAll()
                        .stream()
                        .filter(user ->
                                "STUDENT".equalsIgnoreCase(
                                        user.getRole()))
                        .collect(Collectors.toList());

        List<Application> applications =
                applicationRepository.findAll();

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (User student : students) {

            StudentProfile profile =
                    studentProfileRepository
                            .findByUserId(student.getId())
                            .orElse(null);

            // Get student's resume
            Resume resume =
                    resumeRepository
                            .findByUserId(student.getId())
                            .orElse(null);

            long studentApplications =
                    applications.stream()
                            .filter(application ->
                                    application.getStudent() != null &&
                                    application.getStudent()
                                            .getId()
                                            .equals(student.getId()))
                            .count();

            boolean placed =
                    applications.stream()
                            .filter(application ->
                                    application.getStudent() != null &&
                                    application.getStudent()
                                            .getId()
                                            .equals(student.getId()))
                            .anyMatch(this::isPlaced);

            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "id",
                    student.getId());

            data.put(
                    "name",
                    safe(student.getName()));

            data.put(
                    "email",
                    safe(student.getEmail()));

            data.put(
                    "role",
                    safe(student.getRole()));

            data.put(
                    "phone",
                    profile != null
                            ? safe(profile.getPhone())
                            : "");

            data.put(
                    "department",
                    profile != null
                            ? safe(profile.getDepartment())
                            : "");

            data.put(
                    "college",
                    profile != null
                            ? safe(profile.getCollege())
                            : "");

            data.put(
                    "cgpa",
                    profile != null
                            ? safe(profile.getCgpa())
                            : "");

            // =================================================
            // ATS SCORE
            // =================================================

            data.put(
                    "atsScore",
                    resume != null
                            ? resume.getAtsScore()
                            : null);

            data.put(
                    "skills",
                    profile != null
                            ? safe(profile.getSkills())
                            : "");

            data.put(
                    "graduationYear",
                    profile != null
                            ? safe(profile.getGraduationYear())
                            : "");

            data.put(
                    "applications",
                    studentApplications);

            data.put(
                    "placed",
                    placed);

            result.add(data);
        }

        return result;
    }

    // =========================================================
    // COMPANIES
    // =========================================================

    public List<Map<String, Object>> getCompanies() {

        List<Job> jobs =
                jobRepository.findAll();

        List<Application> applications =
                applicationRepository.findAll();

        Map<String, List<Job>> groupedJobs =
                jobs.stream()
                        .filter(job ->
                                job.getCompanyName() != null &&
                                !job.getCompanyName()
                                        .trim()
                                        .isEmpty())
                        .collect(Collectors.groupingBy(
                                job ->
                                        job.getCompanyName()
                                                .trim(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (Map.Entry<String, List<Job>> entry :
                groupedJobs.entrySet()) {

            String companyName =
                    entry.getKey();

            List<Job> companyJobs =
                    entry.getValue();

            // =================================================
            // TOTAL APPLICATIONS
            // =================================================

            long totalApplications =
                    applications.stream()
                            .filter(application -> {

                                Job job =
                                        application.getJob();

                                return job != null &&
                                        companyName.equalsIgnoreCase(
                                                job.getCompanyName());
                            })
                            .count();

            // =================================================
            // ACTIVE JOBS
            // =================================================

            long activeJobs =
                    companyJobs.stream()
                            .filter(this::isJobActive)
                            .count();

            // =================================================
            // STUDENTS PLACED
            // =================================================

            long studentsPlaced =
                    applications.stream()
                            .filter(application -> {

                                if (!isPlaced(application)) {
                                    return false;
                                }

                                Job job =
                                        application.getJob();

                                return job != null &&
                                        companyName.equalsIgnoreCase(
                                                job.getCompanyName());
                            })
                            .filter(application ->
                                    application.getStudent() != null)
                            .map(application ->
                                    application.getStudent().getId())
                            .distinct()
                            .count();

            // =================================================
            // LOCATION
            // =================================================

            String location =
                    companyJobs.stream()
                            .map(job -> job.getLocation())
                            .filter(value ->
                                    value != null &&
                                    !value.trim().isEmpty())
                            .map(value -> value.trim())
                            .findFirst()
                            .orElse("—");

            // =================================================
            // COMPANY DATA
            // =================================================

            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "companyName",
                    companyName);

            data.put(
                    "totalJobs",
                    companyJobs.size());

            data.put(
                    "totalApplications",
                    totalApplications);

            data.put(
                    "activeJobs",
                    activeJobs);

            data.put(
                    "studentsPlaced",
                    studentsPlaced);

            data.put(
                    "location",
                    location);

            // Industry is not available
            // in the current Job/User structure.
            data.put(
                    "industry",
                    "—");

            result.add(data);
        }

        return result;
    }

    // =========================================================
    // DRIVES
    // =========================================================

    public List<Map<String, Object>> getDrives() {

        List<Job> jobs =
                jobRepository.findAll();

        List<Map<String, Object>> drives =
                new ArrayList<>();

        for (Job job : jobs) {

            List<Application> applications =
                    applicationRepository
                            .findByJobId(job.getId());

            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "id",
                    job.getId());

            data.put(
                    "companyName",
                    safe(job.getCompanyName()));

            data.put(
                    "jobTitle",
                    safe(job.getTitle()));

            data.put(
                    "location",
                    safe(job.getLocation()));

            data.put(
                    "jobType",
                    safe(job.getJobType()));

            data.put(
                    "deadline",
                    job.getDeadline());

            data.put(
                    "postedAt",
                    job.getPostedAt());

            data.put(
                    "applications",
                    applications.size());

            // Calculate eligible students
            int eligibleStudents =
                    calculateEligibleStudents(job);

            data.put(
                    "eligibleStudents",
                    eligibleStudents);

            data.put(
                    "status",
                    getDriveStatus(job));

            drives.add(data);
        }

        return drives;
    }

    // =========================================================
    // REPORTS
    // =========================================================

    public Map<String, Object> getReports() {

        List<User> students =
                userRepository.findAll()
                        .stream()
                        .filter(user ->
                                "STUDENT".equalsIgnoreCase(
                                        safe(user.getRole())))
                        .collect(Collectors.toList());

        List<Job> jobs =
                jobRepository.findAll();

        List<Application> applications =
                applicationRepository.findAll();

        // =====================================================
        // STUDENT STATISTICS
        // =====================================================

        int totalStudents =
                students.size();

        int placedStudents = 0;

        int shortlistedStudents = 0;

        for (User student : students) {

            List<Application> studentApplications =
                    applicationRepository
                            .findByStudentId(student.getId());

            boolean placed = false;

            boolean shortlisted = false;

            for (Application application :
                    studentApplications) {

                if (isPlaced(application)) {

                    placed = true;
                }

                if (isShortlisted(application)) {

                    shortlisted = true;
                }
            }

            if (placed) {

                placedStudents++;
            }

            if (shortlisted) {

                shortlistedStudents++;
            }
        }

        // =====================================================
        // COMPANY STATISTICS
        // =====================================================

        Set<String> companyNames =
                new HashSet<>();

        for (Job job : jobs) {

            String companyName =
                    safe(job.getCompanyName()).trim();

            if (!companyName.isEmpty()) {

                companyNames.add(
                        companyName.toLowerCase());
            }
        }

        int recruitingCompanies =
                companyNames.size();

        // =====================================================
        // ACTIVE RECRUITERS
        // =====================================================

        Set<String> activeCompanyNames =
                new HashSet<>();

        for (Job job : jobs) {

            if (isJobActive(job)) {

                String companyName =
                        safe(job.getCompanyName()).trim();

                if (!companyName.isEmpty()) {

                    activeCompanyNames.add(
                            companyName.toLowerCase());
                }
            }
        }

        int activeRecruiters =
                activeCompanyNames.size();

        // =====================================================
        // NEW RECRUITERS
        // Companies that posted a job during
        // the last 30 days.
        // =====================================================

        Set<String> newCompanyNames =
                new HashSet<>();

        LocalDate today =
                LocalDate.now();

        LocalDate thirtyDaysAgo =
                today.minusDays(30);

        for (Job job : jobs) {

            if (job.getPostedAt() == null) {
                continue;
            }

            LocalDate postedDate =
                    job.getPostedAt().toLocalDate();

            if (!postedDate.isBefore(thirtyDaysAgo)
                    && !postedDate.isAfter(today)) {

                String companyName =
                        safe(job.getCompanyName()).trim();

                if (!companyName.isEmpty()) {

                    newCompanyNames.add(
                            companyName.toLowerCase());
                }
            }
        }

        int newRecruiters =
                newCompanyNames.size();

        // =====================================================
        // DRIVES
        // Every Job is treated as one placement drive.
        // =====================================================

        int totalDrives =
                jobs.size();

        int completedDrives = 0;

        for (Job job : jobs) {

            String status =
                    getDriveStatus(job);

            if ("Completed".equalsIgnoreCase(status)) {

                completedDrives++;
            }
        }

        // =====================================================
        // ELIGIBLE STUDENTS
        // A student is currently considered eligible when
        // a StudentProfile exists for that student.
        // =====================================================

        int eligibleStudents =
                calculateTotalEligibleStudents();

        // =====================================================
        // PLACEMENT RATE
        // =====================================================

        double placementRate =
                totalStudents == 0
                        ? 0.0
                        : (placedStudents * 100.0)
                            / totalStudents;

        // =====================================================
        // TOP RECRUITERS
        // =====================================================

        List<Map<String, Object>> topRecruiters =
                buildTopRecruiters(
                        jobs,
                        applications);

        // =====================================================
        // DEPARTMENT-WISE REPORT
        // =====================================================

        List<StudentProfile> profiles =
                studentProfileRepository.findAll();

        List<Map<String, Object>> departmentReports =
                buildDepartmentReports(
                        students,
                        profiles,
                        applications);

        // =====================================================
        // FINAL REPORT
        // =====================================================

        Map<String, Object> report =
                new LinkedHashMap<>();

        report.put(
                "totalStudents",
                totalStudents);

        report.put(
                "eligibleStudents",
                eligibleStudents);

        report.put(
                "totalApplications",
                applications.size());

        report.put(
                "shortlistedStudents",
                shortlistedStudents);

        report.put(
                "placedStudents",
                placedStudents);

        report.put(
                "recruitingCompanies",
                recruitingCompanies);

        report.put(
                "activeRecruiters",
                activeRecruiters);

        report.put(
                "newRecruiters",
                newRecruiters);

        report.put(
                "totalDrives",
                totalDrives);

        report.put(
                "completedDrives",
                completedDrives);

        report.put(
                "placementRate",
                placementRate);

        report.put(
                "topRecruiters",
                topRecruiters);

        report.put(
                "departmentReports",
                departmentReports);

        return report;
    }

    // =========================================================
    // TOP RECRUITERS
    // =========================================================

    private List<Map<String, Object>> buildTopRecruiters(
            List<Job> jobs,
            List<Application> applications) {

        Map<String, List<Job>> companyJobs =
                jobs.stream()
                        .filter(job ->
                                job.getCompanyName() != null &&
                                !job.getCompanyName()
                                        .trim()
                                        .isEmpty())
                        .collect(Collectors.groupingBy(
                                job ->
                                        job.getCompanyName()
                                                .trim(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<Map<String, Object>> recruiters =
                new ArrayList<>();

        for (Map.Entry<String, List<Job>> entry :
                companyJobs.entrySet()) {

            String companyName =
                    entry.getKey();

            List<Job> companyJobList =
                    entry.getValue();

            long studentsHired =
                    applications.stream()
                            .filter(application -> {

                                if (!isPlaced(application)) {
                                    return false;
                                }

                                Job job =
                                        application.getJob();

                                return job != null &&
                                        companyName.equalsIgnoreCase(
                                                job.getCompanyName());
                            })
                            .filter(application ->
                                    application.getStudent() != null)
                            .map(application ->
                                    application.getStudent().getId())
                            .distinct()
                            .count();

            long companyApplications =
                    applications.stream()
                            .filter(application -> {

                                Job job =
                                        application.getJob();

                                return job != null &&
                                        companyName.equalsIgnoreCase(
                                                job.getCompanyName());
                            })
                            .count();

            String placementStatus =
                    getCompanyPlacementStatus(
                            companyJobList);

            String jobRole =
                    companyJobList.stream()
                            .map(job -> job.getTitle())
                            .filter(title ->
                                    title != null &&
                                    !title.trim().isEmpty())
                            .findFirst()
                            .orElse("—");

            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "company",
                    companyName);

            data.put(
                    "jobRole",
                    jobRole);

            data.put(
                    "studentsHired",
                    studentsHired);

            data.put(
                    "applications",
                    companyApplications);

            data.put(
                    "placementStatus",
                    placementStatus);

            recruiters.add(data);
        }

        recruiters.sort(
                Comparator.comparingLong(
                        (Map<String, Object> item) ->
                                ((Number) item
                                        .get("studentsHired"))
                                        .longValue())
                        .reversed()
        );

        if (recruiters.size() > 5) {

            return new ArrayList<>(
                    recruiters.subList(0, 5));
        }

        return recruiters;
    }

    // =========================================================
    // DEPARTMENT REPORTS
    // =========================================================

    private List<Map<String, Object>> buildDepartmentReports(
            List<User> students,
            List<StudentProfile> profiles,
            List<Application> applications) {

        Map<Long, StudentProfile> profileMap =
                profiles.stream()
                        .filter(profile ->
                                profile.getUser() != null)
                        .collect(Collectors.toMap(
                                profile ->
                                        profile.getUser().getId(),
                                profile ->
                                        profile,
                                (first, second) ->
                                        first
                        ));

        Map<String, List<User>> departments =
                new LinkedHashMap<>();

        for (User student : students) {

            StudentProfile profile =
                    profileMap.get(student.getId());

            String department =
                    profile != null
                            ? safe(profile.getDepartment())
                            : "";

            if (department.trim().isEmpty()) {

                department =
                        "Not Specified";
            }

            departments
                    .computeIfAbsent(
                            department,
                            key ->
                                    new ArrayList<>())
                    .add(student);
        }

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (Map.Entry<String, List<User>> entry :
                departments.entrySet()) {

            String department =
                    entry.getKey();

            List<User> departmentStudents =
                    entry.getValue();

            long placed =
                    departmentStudents.stream()
                            .filter(student ->
                                    applications.stream()
                                            .filter(application ->
                                                    application
                                                            .getStudent() != null &&
                                                    application
                                                            .getStudent()
                                                            .getId()
                                                            .equals(
                                                                    student.getId()))
                                            .anyMatch(
                                                    this::isPlaced))
                            .count();

            double rate = 0;

            if (!departmentStudents.isEmpty()) {

                rate =
                        ((double) placed /
                        departmentStudents.size()) * 100;
            }

            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "department",
                    department);

            data.put(
                    "totalStudents",
                    departmentStudents.size());

            data.put(
                    "placed",
                    placed);

            data.put(
                    "placementRate",
                    rate);

            result.add(data);
        }

        result.sort(
                Comparator.comparing(
                        item ->
                                String.valueOf(
                                        item.get("department")))
        );

        return result;
    }

    // =========================================================
    // CALCULATE ELIGIBLE STUDENTS FOR A DRIVE
    // =========================================================

    public List<Map<String, Object>> getEligibleStudentsForDrive(Long jobId) {

        Job job =
                jobRepository.findById(jobId).orElse(null);

        if (job == null) {
            return new ArrayList<>();
        }

        List<User> students =
                userRepository.findAll()
                        .stream()
                        .filter(user ->
                                "STUDENT".equalsIgnoreCase(
                                        safe(user.getRole())))
                        .collect(Collectors.toList());

        List<StudentProfile> profiles =
                studentProfileRepository.findAll();

        Map<Long, StudentProfile> profileMap =
                profiles.stream()
                        .filter(p -> p.getUser() != null)
                        .collect(Collectors.toMap(
                                p -> p.getUser().getId(),
                                p -> p,
                                (a, b) -> a));

        List<Map<String, Object>> eligibleList =
                new ArrayList<>();

        for (User student : students) {

            StudentProfile profile =
                    profileMap.get(student.getId());

            if (profile == null) {
                continue;
            }

            List<String> studentSkills =
                    getStudentSkills(profile);

            // If job specifies no required skills, any student with profile is eligible
            if (job.getSkills() == null ||
                    job.getSkills().trim().isEmpty()) {

                Map<String, Object> data =
                        new LinkedHashMap<>();

                data.put("id", student.getId());
                data.put("name", safe(student.getName()));
                data.put("email", safe(student.getEmail()));
                data.put("department", safe(profile.getDepartment()));
                data.put("cgpa", safe(profile.getCgpa()));
                data.put("graduationYear", safe(profile.getGraduationYear()));
                data.put("matchPercentage", 100);
                data.put("matchedSkills", studentSkills);
                data.put("missingSkills", new ArrayList<>());

                eligibleList.add(data);
                continue;
            }

            if (studentSkills.isEmpty()) {
                continue;
            }

            Map<String, Object> matchResult =
                    jobRecommendationService.calculateMatch(
                            studentSkills,
                            job);

            Object matchedSkillsObj =
                    matchResult.get("matchedSkills");

            List<?> matchedSkills =
                    (matchedSkillsObj instanceof List<?>)
                            ? (List<?>) matchedSkillsObj
                            : new ArrayList<>();

            // A student is eligible if they match at least 1 required skill
            if (!matchedSkills.isEmpty()) {

                Map<String, Object> data =
                        new LinkedHashMap<>();

                data.put("id", student.getId());
                data.put("name", safe(student.getName()));
                data.put("email", safe(student.getEmail()));
                data.put("department", safe(profile.getDepartment()));
                data.put("cgpa", safe(profile.getCgpa()));
                data.put("graduationYear", safe(profile.getGraduationYear()));
                data.put("matchPercentage", matchResult.get("matchPercentage"));
                data.put("matchedSkills", matchResult.get("matchedSkills"));
                data.put("missingSkills", matchResult.get("missingSkills"));

                eligibleList.add(data);
            }
        }

        // Sort by match percentage descending, then CGPA descending
        eligibleList.sort((a, b) -> {

            int scoreA =
                    a.get("matchPercentage") instanceof Number
                            ? ((Number) a.get("matchPercentage")).intValue()
                            : 0;

            int scoreB =
                    b.get("matchPercentage") instanceof Number
                            ? ((Number) b.get("matchPercentage")).intValue()
                            : 0;

            if (scoreB != scoreA) {
                return Integer.compare(scoreB, scoreA);
            }

            double cgpaA =
                    parseCgpa(a.get("cgpa"));

            double cgpaB =
                    parseCgpa(b.get("cgpa"));

            return Double.compare(cgpaB, cgpaA);
        });

        return eligibleList;
    }

    private int calculateEligibleStudents(Job job) {

        if (job == null) {
            return 0;
        }

        return getEligibleStudentsForDrive(job.getId()).size();
    }

    // =========================================================
    // CALCULATE TOTAL ELIGIBLE STUDENTS
    // =========================================================

    private int calculateTotalEligibleStudents() {

        List<User> students =
                userRepository.findAll()
                        .stream()
                        .filter(u ->
                                "STUDENT".equalsIgnoreCase(
                                        safe(u.getRole())))
                        .collect(Collectors.toList());

        List<StudentProfile> profiles =
                studentProfileRepository.findAll();

        Map<Long, StudentProfile> profileMap =
                profiles.stream()
                        .filter(p -> p.getUser() != null)
                        .collect(Collectors.toMap(
                                p -> p.getUser().getId(),
                                p -> p,
                                (a, b) -> a));

        List<Job> jobs =
                jobRepository.findAll();

        int count = 0;

        for (User student : students) {

            StudentProfile profile =
                    profileMap.get(student.getId());

            if (profile != null) {

                if (jobs.isEmpty()) {

                    count++;

                } else {

                    List<String> studentSkills =
                            getStudentSkills(profile);

                    boolean eligible =
                            jobs.stream().anyMatch(job -> {

                                if (job.getSkills() == null ||
                                        job.getSkills().trim().isEmpty()) {

                                    return true;
                                }

                                if (studentSkills.isEmpty()) {
                                    return false;
                                }

                                Map<String, Object> match =
                                        jobRecommendationService
                                                .calculateMatch(
                                                        studentSkills,
                                                        job);

                                Object matched =
                                        match.get("matchedSkills");

                                if (matched instanceof List<?>) {
    List<?> matchedList = (List<?>) matched;
    return !matchedList.isEmpty();
}

return false;
                            });

                    if (eligible) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    private List<String> getStudentSkills(StudentProfile profile) {

        List<String> skills = new ArrayList<>();

        if (profile != null &&
                profile.getSkills() != null &&
                !profile.getSkills().trim().isEmpty()) {

            String[] parts =
                    profile.getSkills().split("[,;]");

            for (String part : parts) {

                String trimmed = part.trim();

                if (!trimmed.isEmpty()) {
                    skills.add(trimmed);
                }
            }
        }

        return skills;
    }

    private double parseCgpa(Object cgpaObj) {

        if (cgpaObj == null) {
            return 0.0;
        }

        try {
            return Double.parseDouble(
                    cgpaObj.toString().trim()
            );
        } catch (Exception e) {
            return 0.0;
        }
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    private boolean isPlaced(
            Application application) {

        if (application == null ||
                application.getStatus() == null) {

            return false;
        }

        String status =
                application.getStatus()
                        .trim()
                        .toLowerCase();

        return status.equals("selected") ||
                status.equals("placed") ||
                status.equals("offer accepted");
    }

    private boolean isShortlisted(
            Application application) {

        if (application == null ||
                application.getStatus() == null) {

            return false;
        }

        String status =
                application.getStatus()
                        .trim()
                        .toLowerCase();

        return status.equals("shortlisted") ||
                status.equals("selected") ||
                status.equals("placed") ||
                status.equals("offer accepted");
    }

    private boolean isJobActive(
            Job job) {

        if (job == null) {

            return false;
        }

        if (job.getDeadline() == null) {

            return true;
        }

        return !job.getDeadline()
                .isBefore(LocalDate.now());
    }

    private String getDriveStatus(
            Job job) {

        if (job == null) {

            return "Unknown";
        }

        if (job.getDeadline() == null) {

            return "Ongoing";
        }

        if (job.getDeadline()
                .isBefore(LocalDate.now())) {

            return "Completed";
        }

        return "Upcoming";
    }

    private String getCompanyPlacementStatus(
            List<Job> companyJobs) {

        boolean ongoing =
                companyJobs.stream()
                        .anyMatch(job ->
                                "Ongoing".equals(
                                        getDriveStatus(job)));

        if (ongoing) {

            return "Ongoing";
        }

        boolean upcoming =
                companyJobs.stream()
                        .anyMatch(job ->
                                "Upcoming".equals(
                                        getDriveStatus(job)));

        if (upcoming) {

            return "Upcoming";
        }

        return "Completed";
    }

    private String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }
    // =========================================================
// PENDING COMPANY ACCOUNTS
// =========================================================

public List<Map<String, Object>> getPendingCompanies() {

    List<User> companies =
            userRepository.findAll()
                    .stream()
                    .filter(user ->
                            "COMPANY".equalsIgnoreCase(
                                    safe(user.getRole())))
                    .filter(user ->
                            "PENDING_VERIFICATION".equalsIgnoreCase(
                                    safe(user.getAccountStatus())))
                    .collect(Collectors.toList());

    List<Map<String, Object>> result =
            new ArrayList<>();

    for (User company : companies) {

        Map<String, Object> data =
                new LinkedHashMap<>();

        data.put("id", company.getId());
        data.put("name", safe(company.getName()));
        data.put("email", safe(company.getEmail()));
        data.put("companyName", safe(company.getCompanyName()));
        data.put("accountStatus", safe(company.getAccountStatus()));

        result.add(data);
    }

    return result;
}
// =========================================================
// APPROVE PENDING COMPANY
// =========================================================

public ResponseEntity<?> approveCompany(Long id) {

    Optional<User> userOptional = userRepository.findById(id);

    if (userOptional.isEmpty()) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Company account not found"));
    }

    User company = userOptional.get();

    if (!"COMPANY".equalsIgnoreCase(safe(company.getRole()))) {
        return ResponseEntity
                .badRequest()
                .body(Map.of("error", "The selected account is not a company account"));
    }

    if (!"PENDING_VERIFICATION".equalsIgnoreCase(
            safe(company.getAccountStatus()))) {

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error",
                        "This company account is not pending verification"));
    }

    company.setAccountStatus("ACTIVE");
    userRepository.save(company);

    return ResponseEntity.ok(
            Map.of(
                    "message",
                    "Company account approved successfully",
                    "id",
                    company.getId(),
                    "accountStatus",
                    company.getAccountStatus()
            )
    );
}
}