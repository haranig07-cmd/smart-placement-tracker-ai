package com.sptai.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.sptai.backend.entity.Job;

@Service
public class JobRecommendationService {

    // ----------------------------------------------------
    // CALCULATE MATCH BETWEEN STUDENT SKILLS AND JOB SKILLS
    // ----------------------------------------------------

    public Map<String, Object> calculateMatch(
            List<String> studentSkills,
            Job job) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        List<String> matchedSkills =
                new ArrayList<>();

        List<String> missingSkills =
                new ArrayList<>();

        // If job has no skills
        if (job.getSkills() == null ||
                job.getSkills().trim().isEmpty()) {

            result.put("matchPercentage", 0);
            result.put("matchedSkills", matchedSkills);
            result.put("missingSkills", missingSkills);

            return result;
        }

        // Convert job skills into individual skills
        String[] jobSkills =
                job.getSkills()
                        .toLowerCase()
                        .split("[,;]");

        // Compare every required job skill
        for (String jobSkill : jobSkills) {

            String requiredSkill =
                    jobSkill.trim();

            if (requiredSkill.isEmpty()) {
                continue;
            }

            boolean matched = false;

            // Compare with student's detected skills
            for (String studentSkill : studentSkills) {

                if (studentSkill == null) {
                    continue;
                }

                String student =
                        studentSkill
                                .trim()
                                .toLowerCase();

                if (isSkillMatch(student, requiredSkill)) {
                    matched = true;
                    break;
                }
            }

            if (matched) {

                matchedSkills.add(requiredSkill);

            } else {

                missingSkills.add(requiredSkill);

            }
        }

        // Calculate percentage
        int totalSkills =
                matchedSkills.size()
                + missingSkills.size();

        int matchPercentage = 0;

        if (totalSkills > 0) {

            matchPercentage =
                    (int) Math.round(
                            ((double) matchedSkills.size()
                                    / totalSkills) * 100
                    );
        }

        result.put(
                "matchPercentage",
                matchPercentage
        );

        result.put(
                "matchedSkills",
                matchedSkills
        );

        result.put(
                "missingSkills",
                missingSkills
        );

        return result;
    }

    private boolean isSkillMatch(String studentSkill, String requiredSkill) {
        if (studentSkill == null || requiredSkill == null) {
            return false;
        }

        String s = studentSkill.trim().toLowerCase();
        String r = requiredSkill.trim().toLowerCase();

        if (s.isEmpty() || r.isEmpty()) {
            return false;
        }

        // Exact match
        if (s.equals(r)) {
            return true;
        }

        // Normalized match (e.g., "react.js" and "react", "node.js" and "node", "c++" and "cpp")
        String normS = normalizeSkill(s);
        String normR = normalizeSkill(r);

        if (normS.equals(normR)) {
            return true;
        }

        // Check aliases (e.g., "sql" and "mysql")
        if (isAliasMatch(normS, normR)) {
            return true;
        }

        // Word-boundary matching (e.g., "spring" matching "spring boot", but NOT "c" matching "react" or "css")
        return containsWordBoundary(normS, normR) || containsWordBoundary(normR, normS);
    }

    private String normalizeSkill(String skill) {
        String n = skill.trim().toLowerCase();

        if (n.endsWith(".js")) {
            n = n.substring(0, n.length() - 3);
        } else if (n.endsWith("js") && n.length() > 3) {
            if (n.equals("nodejs")) {
                n = "node";
            } else if (n.equals("reactjs")) {
                n = "react";
            }
        }

        if (n.equals("cpp") || n.equals("c++")) {
            n = "c++";
        }

        return n.replace("-", " ").replace("_", " ").trim();
    }

    private boolean containsWordBoundary(String sub, String full) {
        if (sub.isEmpty() || full.isEmpty()) {
            return false;
        }

        // If skill is very short (<= 2 chars like "c", "r", "go"),
        // only match if it is an isolated token in the phrase
        String[] tokens = full.split("[\\s,/|]+");
        for (String token : tokens) {
            if (token.equals(sub)) {
                return true;
            }
        }

        // For longer multi-word phrases (e.g., "machine learning" in "machine learning / ai")
        if (sub.length() >= 3) {
            String regex = "(^|.*\\b)" + java.util.regex.Pattern.quote(sub) + "(\\b.*|$)";
            return full.matches(regex);
        }

        return false;
    }

    private boolean isAliasMatch(String a, String b) {
        if ((a.equals("sql") && b.equals("mysql")) || (a.equals("mysql") && b.equals("sql"))) {
            return true;
        }
        if ((a.equals("c") && b.equals("c programming")) || (a.equals("c programming") && b.equals("c"))) {
            return true;
        }
        return false;
    }
}