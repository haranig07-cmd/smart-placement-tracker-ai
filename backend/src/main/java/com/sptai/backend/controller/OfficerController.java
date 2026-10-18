package com.sptai.backend.controller;

import java.util.Map;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sptai.backend.service.OfficerService;

@RestController
@RequestMapping("/api/officer")

@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://haranig07-cmd.github.io"
    },
    allowCredentials = "true"
)

public class OfficerController {

    private final OfficerService officerService;

    public OfficerController(OfficerService officerService) {
        this.officerService = officerService;
    }

    // -----------------------------------------
    // OFFICER DASHBOARD
    // -----------------------------------------

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(HttpServletRequest request) {

        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(
                officerService.getDashboardStats()
        );
    }

    // -----------------------------------------
    // STUDENTS
    // -----------------------------------------

    @GetMapping("/students")
    public ResponseEntity<?> getStudents(HttpServletRequest request) {

        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(
                officerService.getStudents()
        );
    }

    // -----------------------------------------
    // COMPANIES
    // -----------------------------------------

    @GetMapping("/companies")
    public ResponseEntity<?> getCompanies(HttpServletRequest request) {

        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(
                officerService.getCompanies()
        );
    }
    @GetMapping("/companies/pending")
public ResponseEntity<?> getPendingCompanies(HttpServletRequest request) {

    if (!hasOfficerAccess(request)) {
        return officerAccessRequired();
    }

    return ResponseEntity.ok(officerService.getPendingCompanies());
}
@PutMapping("/companies/{id}/approve")
public ResponseEntity<?> approveCompany(
        @PathVariable Long id,
        HttpServletRequest request) {

    if (!hasOfficerAccess(request)) {
        return officerAccessRequired();
    }

    return officerService.approveCompany(id);
}

    // -----------------------------------------
    // RECRUITMENT DRIVES
    // -----------------------------------------

    @GetMapping("/drives")
    public ResponseEntity<?> getDrives(HttpServletRequest request) {

        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(
                officerService.getDrives()
        );
    }

    // -----------------------------------------
    // DRIVE ELIGIBLE STUDENTS
    // -----------------------------------------

    @GetMapping("/drives/{id}/eligible-students")
    public ResponseEntity<?> getEligibleStudentsForDrive(
            @PathVariable Long id,
            HttpServletRequest request) {

        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(
                officerService.getEligibleStudentsForDrive(id)
        );
    }

    // -----------------------------------------
    // REPORTS
    // -----------------------------------------

    @GetMapping("/reports")
    public ResponseEntity<?> getReports(HttpServletRequest request) {

        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(
                officerService.getReports()
        );
    }

    private boolean hasOfficerAccess(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        return session != null &&
                Objects.equals("OFFICER", session.getAttribute("userRole"));
    }

    private ResponseEntity<Map<String, String>> officerAccessRequired() {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Officer access required"));
    }
}