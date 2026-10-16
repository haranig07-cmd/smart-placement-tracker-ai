package com.sptai.backend.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class ResumeAnalysisService {

    // Common technical skills
    private final List<String> technicalSkills = Arrays.asList(
            "java",
            "python",
            "c",
            "c++",
            "javascript",
            "html",
            "css",
            "react",
            "react.js",
            "node.js",
            "nodejs",
            "spring boot",
            "spring",
            "mysql",
            "mongodb",
            "sql",
            "git",
            "github",
            "docker",
            "aws",
            "azure",
            "machine learning",
            "deep learning",
            "artificial intelligence",
            "data structures",
            "algorithms",
            "tensorflow",
            "pytorch",
            "opencv"
    );

    // Target benchmark for 100% technical skill pillar score (30 points).
    // In technical resumes, 7 verified technical skills represent a comprehensive skill set.
    private static final int TARGET_SKILL_BENCHMARK = 7;

    // Important resume sections
    private final List<String> resumeSections = Arrays.asList(
            "education",
            "experience",
            "skills",
            "projects",
            "certifications",
            "summary",
            "objective",
            "achievements"
    );

    public Map<String, Object> analyzeResume(String filePath) {

        Map<String, Object> result = new LinkedHashMap<>();

        try {

            File file = new File(filePath);

            if (!file.exists()) {
                result.put("error", "Resume file not found");
                return result;
            }

            // --------------------------------
            // READ PDF
            // --------------------------------

            String text;

            try (PDDocument document = Loader.loadPDF(file)) {

                PDFTextStripper stripper = new PDFTextStripper();

                text = stripper.getText(document);
            }

            text = text.toLowerCase();

            // --------------------------------
            // SKILL DETECTION
            // --------------------------------

            List<String> detectedSkills = new ArrayList<>();

            for (String skill : technicalSkills) {

                if (text.contains(skill.toLowerCase())) {
                    detectedSkills.add(skill);
                }
            }

            // --------------------------------
            // SECTION DETECTION
            // --------------------------------

            List<String> detectedSections = new ArrayList<>();

            for (String section : resumeSections) {

                if (text.contains(section)) {
                    detectedSections.add(section);
                }
            }

            // --------------------------------
            // KEYWORD CHECK
            // --------------------------------

            List<String> importantKeywords = Arrays.asList(
                    "project",
                    "internship",
                    "team",
                    "problem solving",
                    "communication",
                    "leadership",
                    "development",
                    "software",
                    "technology",
                    "database"
            );

            List<String> detectedKeywords = new ArrayList<>();

            for (String keyword : importantKeywords) {

                if (text.contains(keyword)) {
                    detectedKeywords.add(keyword);
                }
            }

            // --------------------------------
            // ATS SCORE CALCULATION
            // --------------------------------

            /*
             * DETERMINISTIC 4-PILLAR ATS SCORING (TOTAL = 100):
             *
             * 1. Skills (30 pts):
             *    Evaluated against TARGET_SKILL_BENCHMARK (7 skills = 100% of skill points).
             *    Prevents penalizing candidates for not having all 29 global catalog skills on one resume.
             *    Formula: Math.min(30, Math.round((detectedSkills.size() / 7.0) * 30))
             *    Example: 5 relevant skills = 21/30 (70%), 7+ skills = 30/30 (100%).
             *
             * 2. Sections (30 pts):
             *    Percentage of 8 standard resume sections present (Education, Experience, Projects, etc.).
             *    Formula: Math.round((detectedSections.size() / 8.0) * 30)
             *
             * 3. Keywords (20 pts):
             *    Percentage of 10 professional keywords present (Leadership, Development, etc.).
             *    Formula: Math.round((detectedKeywords.size() / 10.0) * 20)
             *
             * 4. Content / Length (20 pts):
             *    Tiered character count: >=1500 chars (20 pts), >=800 chars (15 pts),
             *    >=400 chars (10 pts), <400 chars (5 pts).
             */

            // Skills: deterministic score capped at 30 points
            int skillScore = (int) Math.min(
                    30,
                    Math.round(((double) detectedSkills.size() / TARGET_SKILL_BENCHMARK) * 30)
            );

            // Sections: percentage of all available sections
            int sectionScore = (int) Math.round(
                    ((double) detectedSections.size()
                            / resumeSections.size()) * 30
            );

            // Keywords: percentage of all available keywords
            int keywordScore = (int) Math.round(
                    ((double) detectedKeywords.size()
                            / importantKeywords.size()) * 20
            );

            // Content / Resume length
            int lengthScore;

            if (text.length() >= 1500) {
                lengthScore = 20;
            }
            else if (text.length() >= 800) {
                lengthScore = 15;
            }
            else if (text.length() >= 400) {
                lengthScore = 10;
            }
            else {
                lengthScore = 5;
            }

            // Final ATS score
            int atsScore =
                    skillScore
                    + sectionScore
                    + keywordScore
                    + lengthScore;

            atsScore = Math.min(atsScore, 100);

            // --------------------------------
            // DETAILED PERCENTAGES
            // --------------------------------

            int skillsPercentage = (int) Math.round(
                    ((double) skillScore / 30) * 100
            );

            int sectionsPercentage = (int) Math.round(
                    ((double) sectionScore / 30) * 100
            );

            int keywordsPercentage = (int) Math.round(
                    ((double) keywordScore / 20) * 100
            );

            int contentPercentage = (int) Math.round(
                    ((double) lengthScore / 20) * 100
            );

            // --------------------------------
            // STRENGTHS
            // --------------------------------

            List<String> strengths = new ArrayList<>();

            if (skillsPercentage >= 70) {
                strengths.add("Good technical skill coverage");
            }

            if (sectionsPercentage >= 70) {
                strengths.add("Important resume sections are present");
            }

            if (keywordsPercentage >= 70) {
                strengths.add("Good use of professional keywords");
            }

            if (contentPercentage >= 75) {
                strengths.add("Resume contains sufficient content");
            }

            // --------------------------------
            // IMPROVEMENTS
            // --------------------------------

            List<String> improvements = new ArrayList<>();

            if (skillsPercentage < 70) {
                improvements.add(
                        "Add more relevant technical skills"
                );
            }

            if (!detectedSections.contains("projects")) {
                improvements.add(
                        "Add a Projects section"
                );
            }

            if (!detectedSections.contains("experience")) {
                improvements.add(
                        "Add internship or experience details"
                );
            }

            if (!detectedSections.contains("certifications")) {
                improvements.add(
                        "Add relevant certifications"
                );
            }

            if (keywordsPercentage < 70) {
                improvements.add(
                        "Use more job-relevant keywords"
                );
            }

            if (contentPercentage < 75) {
                improvements.add(
                        "Add more relevant resume content"
                );
            }

            // --------------------------------
            // FINAL RESPONSE
            // --------------------------------

            result.put("atsScore", atsScore);

            result.put("skillScore", skillScore);
            result.put("sectionScore", sectionScore);
            result.put("keywordScore", keywordScore);
            result.put("lengthScore", lengthScore);

            result.put("skillsPercentage", skillsPercentage);
            result.put("sectionsPercentage", sectionsPercentage);
            result.put("keywordsPercentage", keywordsPercentage);
            result.put("contentPercentage", contentPercentage);

            result.put("detectedSkills", detectedSkills);
            result.put("detectedSections", detectedSections);
            result.put("detectedKeywords", detectedKeywords);

            result.put("strengths", strengths);
            result.put("improvements", improvements);

            result.put("textLength", text.length());

            return result;

        }
        catch (IOException e) {

            result.put(
                    "error",
                    "Unable to analyze resume: "
                    + e.getMessage()
            );

            return result;
        }
    }
}