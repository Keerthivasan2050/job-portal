import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MatchingEngine - Two-Way Resume-to-Career Intelligence & Matching Engine
 * =========================================================================
 * Core Capabilities:
 *  1. Seeker-to-Job Matching with Explainable Scoring ("Why Am I a Match?")
 *  2. Similar Job Finder & Career Clusters
 *  3. Skill Gap Analysis & Projected Match Improvement Calculator
 *  4. Multi-Stage Career Path Progression Planner
 *  5. Recruiter Reverse Candidate Matching (Two-Way Matching)
 *  6. Smart Alert Evaluation (>80% Match)
 */
public class MatchingEngine {

    // ============================================================
    // DATA STRUCTURES
    // ============================================================

    public static class JobMatchResult {
        public JobDataStore.JobRecord job;
        public int matchPercentage;
        public List<String> matchedSkills = new ArrayList<>();
        public List<String> missingSkills = new ArrayList<>();
        public List<String> whyReasons = new ArrayList<>();
        public int projectedPercentage;
        public List<String> recommendedSkillsToLearn = new ArrayList<>();

        public JobMatchResult(JobDataStore.JobRecord job, int matchPercentage) {
            this.job = job;
            this.matchPercentage = matchPercentage;
        }
    }

    public static class CandidateMatchResult {
        public int candidateId;
        public String candidateName;
        public String candidateEmail;
        public String candidatePhone;
        public int matchPercentage;
        public List<String> matchedSkills = new ArrayList<>();
        public List<String> missingSkills = new ArrayList<>();
        public String experience = "";
        public String qualification = "";
        public String resumeFileName = "";
        public List<String> whyReasons = new ArrayList<>();
    }

    public static class SimilarRoleOption {
        public String title;
        public int matchPercentage;
        public int jobCount;
        public List<String> keySkills = new ArrayList<>();

        public SimilarRoleOption(String title, int matchPercentage, int jobCount, List<String> keySkills) {
            this.title = title;
            this.matchPercentage = matchPercentage;
            this.jobCount = jobCount;
            this.keySkills = keySkills;
        }
    }

    public static class CareerLevel {
        public int level;
        public String title;
        public String experienceRange;
        public String status; // "COMPLETED", "CURRENT", "NEXT", "TARGET"
        public List<String> requiredSkills = new ArrayList<>();
        public List<String> skillsToLearn = new ArrayList<>();
        public int matchingJobsAvailable;

        public CareerLevel(int level, String title, String experienceRange, String status) {
            this.level = level;
            this.title = title;
            this.experienceRange = experienceRange;
            this.status = status;
        }
    }

    public static class CareerPath {
        public String currentProfile;
        public String primaryTrack;
        public List<CareerLevel> levels = new ArrayList<>();
    }

    public static class SkillGapReport {
        public int currentMatchPercentage;
        public int projectedMatchPercentage;
        public List<String> skillsYouHave = new ArrayList<>();
        public List<String> missingSkills = new ArrayList<>();
        public List<LearningStep> learningSteps = new ArrayList<>();
        public String disclaimer = "Projected percentage is calculated based on SmartHire's scoring formula and does not constitute an employment guarantee.";
    }

    public static class LearningStep {
        public int stepNumber;
        public String skillName;
        public int impactPoints;
        public String recommendation;

        public LearningStep(int stepNumber, String skillName, int impactPoints, String recommendation) {
            this.stepNumber = stepNumber;
            this.skillName = skillName;
            this.impactPoints = impactPoints;
            this.recommendation = recommendation;
        }
    }

    // ============================================================
    // CANDIDATE PROFILE HOLDER
    // ============================================================

    public static class CandidateRecord {
        public int userId;
        public String fullName;
        public String email;
        public String phone;
        public String qualification;
        public String experience;
        public List<String> skills = new ArrayList<>();
        public String resumeFileName;

        public CandidateRecord(int userId, String fullName, String email, String phone,
                               String qualification, String experience, List<String> skills, String resumeFileName) {
            this.userId = userId;
            this.fullName = fullName;
            this.email = email;
            this.phone = phone;
            this.qualification = qualification != null ? qualification : "";
            this.experience = experience != null ? experience : "";
            this.skills = skills != null ? skills : new ArrayList<>();
            this.resumeFileName = resumeFileName != null ? resumeFileName : "resume.pdf";
        }
    }

    // ============================================================
    // 1. MATCHING ENGINE: SEEKER TO SINGLE JOB
    // ============================================================

    public static JobMatchResult calculateJobMatch(JobDataStore.JobRecord job,
                                                  List<String> seekerSkills,
                                                  String seekerEducation,
                                                  String seekerExperience,
                                                  String seekerLocation) {
        if (seekerSkills == null) seekerSkills = Collections.emptyList();
        Set<String> userSkillSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String s : seekerSkills) {
            if (s != null && !s.trim().isEmpty()) userSkillSet.add(s.trim());
        }

        // Parse required skills from job
        List<String> requiredSkills = parseSkillsList(job.skillsRequired);
        if (requiredSkills.isEmpty()) {
            requiredSkills = parseSkillsList(job.description);
        }

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> whyReasons = new ArrayList<>();

        int matchedSkillCount = 0;
        for (String req : requiredSkills) {
            if (hasSkill(userSkillSet, req)) {
                matched.add(req);
                matchedSkillCount++;
                whyReasons.add("✓ " + req + " Strong (Core Skill Match)");
            } else {
                missing.add(req);
                whyReasons.add("⚠ " + req + " Missing");
            }
        }

        // Education Match
        boolean eduMatch = isEducationMatch(seekerEducation, job.qualification);
        if (eduMatch && seekerEducation != null && !seekerEducation.isEmpty()) {
            whyReasons.add("✓ " + seekerEducation + " (Education Fit)");
        }

        // Experience Match
        boolean expMatch = isExperienceMatch(seekerExperience, job.experienceRequired);
        if (expMatch && seekerExperience != null && !seekerExperience.isEmpty()) {
            whyReasons.add("✓ Experience (" + seekerExperience + ") Matches Requirement");
        }

        // Location Match
        boolean locMatch = isLocationMatch(seekerLocation, job.location);
        if (locMatch && job.location != null) {
            whyReasons.add("✓ " + job.location + " Location Match");
        }

        // Score Calculation
        // Skills: 65% weight
        double skillScore = 0.0;
        if (!requiredSkills.isEmpty()) {
            skillScore = ((double) matchedSkillCount / requiredSkills.size()) * 65.0;
        } else {
            skillScore = 40.0;
        }

        // Title / Domain Semantic Similarity: 15% weight
        double titleScore = calculateTitleSimilarity(job.jobTitle, userSkillSet);

        // Experience: 10% weight
        double expScore = expMatch ? 10.0 : 4.0;

        // Education: 10% weight
        double eduScore = eduMatch ? 10.0 : 5.0;

        int totalScore = (int) Math.round(skillScore + titleScore + expScore + eduScore);
        if (totalScore > 98) totalScore = 98; // reserve 99-100% for 100% exact skill+exp
        if (matchedSkillCount == requiredSkills.size() && !requiredSkills.isEmpty()) {
            totalScore = Math.max(totalScore, 94);
        }
        if (totalScore < 15 && matchedSkillCount == 0) totalScore = 15;

        JobMatchResult res = new JobMatchResult(job, totalScore);
        res.matchedSkills = matched;
        res.missingSkills = missing;
        res.whyReasons = whyReasons;

        // Skill Gap calculation
        int potentialGain = Math.min(missing.size() * 7, 100 - totalScore);
        res.projectedPercentage = Math.min(totalScore + potentialGain, 96);
        res.recommendedSkillsToLearn = missing;

        return res;
    }

    private static boolean hasSkill(Set<String> userSkills, String req) {
        if (userSkills.contains(req)) return true;
        for (String u : userSkills) {
            if (u.equalsIgnoreCase(req)) return true;
            if (req.equalsIgnoreCase("Java") && u.equalsIgnoreCase("Core Java")) return true;
            if (req.equalsIgnoreCase("Spring Boot") && (u.equalsIgnoreCase("Spring") || u.equalsIgnoreCase("Spring Framework"))) return true;
            if (req.equalsIgnoreCase("React") && u.equalsIgnoreCase("React.js")) return true;
            if (req.equalsIgnoreCase("AWS") && u.toLowerCase().contains("amazon web services")) return true;
            if (req.equalsIgnoreCase("REST API") && u.toLowerCase().contains("rest")) return true;
        }
        return false;
    }

    private static boolean isEducationMatch(String userEdu, String jobQual) {
        if (jobQual == null || jobQual.trim().isEmpty()) return true;
        if (userEdu == null || userEdu.trim().isEmpty()) return false;
        String u = userEdu.toLowerCase();
        String j = jobQual.toLowerCase();
        if (j.contains("b.e") || j.contains("b.tech") || j.contains("computer science") || j.contains("engineering")) {
            return u.contains("b.e") || u.contains("b.tech") || u.contains("cs") || u.contains("computer") || u.contains("m.tech") || u.contains("mca");
        }
        return true;
    }

    private static boolean isExperienceMatch(String userExp, String jobExp) {
        if (jobExp == null || jobExp.trim().isEmpty()) return true;
        if (userExp == null || userExp.trim().isEmpty()) return true;
        int userYears = extractYearsNumber(userExp);
        int jobYears = extractYearsNumber(jobExp);
        return userYears >= Math.max(0, jobYears - 1);
    }

    private static int extractYearsNumber(String s) {
        if (s == null) return 0;
        Matcher m = Pattern.compile("(\\d+)").matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); }
            catch (Exception ignored) {}
        }
        return 0;
    }

    private static boolean isLocationMatch(String userLoc, String jobLoc) {
        if (jobLoc == null || jobLoc.trim().isEmpty()) return true;
        if (jobLoc.equalsIgnoreCase("Remote")) return true;
        if (userLoc == null || userLoc.trim().isEmpty()) return true;
        return userLoc.toLowerCase().contains(jobLoc.toLowerCase()) ||
               jobLoc.toLowerCase().contains(userLoc.toLowerCase());
    }

    private static double calculateTitleSimilarity(String jobTitle, Set<String> userSkills) {
        if (jobTitle == null) return 5.0;
        String lower = jobTitle.toLowerCase();
        double score = 5.0;
        if (lower.contains("java") && (userSkills.contains("Java") || userSkills.contains("Spring Boot"))) score += 7.0;
        if (lower.contains("python") && userSkills.contains("Python")) score += 7.0;
        if (lower.contains("react") && userSkills.contains("React")) score += 7.0;
        if (lower.contains("backend") && (userSkills.contains("Java") || userSkills.contains("Python") || userSkills.contains("Node.js"))) score += 6.0;
        if (lower.contains("full stack") && userSkills.contains("React") && userSkills.contains("Java")) score += 8.0;
        if (lower.contains("devops") && (userSkills.contains("Docker") || userSkills.contains("AWS"))) score += 7.0;
        return Math.min(score, 15.0);
    }

    public static List<String> parseSkillsList(String raw) {
        List<String> list = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return list;
        String[] parts = raw.split("[,;•|\\n]+");
        for (String p : parts) {
            String trimmed = p.trim().replaceAll("^[•\\-*\\d.]+\\s*", "");
            if (!trimmed.isEmpty() && trimmed.length() <= 35) {
                list.add(trimmed);
            }
        }
        return list;
    }

    // ============================================================
    // 2. FIND JOBS FOR MY RESUME (RANKED BY MATCH %)
    // ============================================================

    public static List<JobMatchResult> findMatchingJobsForSeeker(List<String> seekerSkills,
                                                                 String seekerEducation,
                                                                 String seekerExperience,
                                                                 String seekerLocation,
                                                                 List<JobDataStore.JobRecord> allJobs) {
        List<JobMatchResult> results = new ArrayList<>();
        for (JobDataStore.JobRecord job : allJobs) {
            if ("CLOSED".equalsIgnoreCase(job.status)) continue;
            JobMatchResult mr = calculateJobMatch(job, seekerSkills, seekerEducation, seekerExperience, seekerLocation);
            results.add(mr);
        }
        // Sort descending by match percentage
        results.sort((a, b) -> Integer.compare(b.matchPercentage, a.matchPercentage));
        return results;
    }

    // ============================================================
    // 3. SIMILAR JOB FINDER (CAREER CLUSTERS)
    // ============================================================

    public static List<SimilarRoleOption> findSimilarJobs(String primaryRole, int primaryMatch, List<JobDataStore.JobRecord> allJobs) {
        List<SimilarRoleOption> options = new ArrayList<>();

        if (primaryRole == null || primaryRole.isEmpty()) primaryRole = "Java Developer";
        String lowerRole = primaryRole.toLowerCase();

        if (lowerRole.contains("java")) {
            options.add(new SimilarRoleOption("Backend Developer", Math.max(primaryMatch - 5, 89),
                countJobsMatching(allJobs, "Backend"), List.of("Java", "REST API", "MySQL", "Microservices")));
            options.add(new SimilarRoleOption("Spring Boot Developer", Math.max(primaryMatch - 7, 87),
                countJobsMatching(allJobs, "Spring Boot"), List.of("Spring Boot", "Hibernate", "Java", "Docker")));
            options.add(new SimilarRoleOption("Software Engineer", Math.max(primaryMatch - 10, 84),
                countJobsMatching(allJobs, "Software Engineer"), List.of("Java", "Data Structures", "Algorithms", "Git")));
            options.add(new SimilarRoleOption("Full Stack Developer", Math.max(primaryMatch - 18, 76),
                countJobsMatching(allJobs, "Full Stack"), List.of("Java", "React", "Spring Boot", "HTML/CSS")));
            options.add(new SimilarRoleOption("Java Application Developer", Math.max(primaryMatch - 20, 74),
                countJobsMatching(allJobs, "Java Application"), List.of("Core Java", "J2EE", "SQL", "Unit Testing")));
        } else if (lowerRole.contains("frontend") || lowerRole.contains("react") || lowerRole.contains("web")) {
            options.add(new SimilarRoleOption("UI Developer", Math.max(primaryMatch - 3, 91),
                countJobsMatching(allJobs, "UI"), List.of("HTML5", "CSS3", "JavaScript", "Tailwind")));
            options.add(new SimilarRoleOption("React Developer", Math.max(primaryMatch - 6, 88),
                countJobsMatching(allJobs, "React"), List.of("React", "Redux", "TypeScript", "Next.js")));
            options.add(new SimilarRoleOption("Web Application Developer", Math.max(primaryMatch - 9, 85),
                countJobsMatching(allJobs, "Web"), List.of("JavaScript", "REST APIs", "CSS", "Git")));
            options.add(new SimilarRoleOption("Full Stack Engineer", Math.max(primaryMatch - 16, 78),
                countJobsMatching(allJobs, "Full Stack"), List.of("React", "Node.js", "MongoDB", "Express")));
        } else if (lowerRole.contains("python")) {
            options.add(new SimilarRoleOption("Python Backend Engineer", Math.max(primaryMatch - 4, 90),
                countJobsMatching(allJobs, "Python"), List.of("Python", "FastAPI", "PostgreSQL", "Docker")));
            options.add(new SimilarRoleOption("Data Engineer", Math.max(primaryMatch - 8, 86),
                countJobsMatching(allJobs, "Data"), List.of("Python", "SQL", "Pandas", "Kafka")));
            options.add(new SimilarRoleOption("Backend Developer", Math.max(primaryMatch - 10, 84),
                countJobsMatching(allJobs, "Backend"), List.of("Python", "Django", "Redis", "REST API")));
            options.add(new SimilarRoleOption("Machine Learning Engineer", Math.max(primaryMatch - 16, 78),
                countJobsMatching(allJobs, "Machine Learning"), List.of("Python", "Scikit-Learn", "PyTorch")));
        } else {
            options.add(new SimilarRoleOption("Backend Developer", 88, countJobsMatching(allJobs, "Backend"), List.of("Java", "MySQL", "REST API")));
            options.add(new SimilarRoleOption("Software Engineer", 85, countJobsMatching(allJobs, "Engineer"), List.of("OOP", "Git", "SQL")));
            options.add(new SimilarRoleOption("Full Stack Developer", 78, countJobsMatching(allJobs, "Full Stack"), List.of("Frontend", "Backend", "APIs")));
        }

        return options;
    }

    private static int countJobsMatching(List<JobDataStore.JobRecord> jobs, String keyword) {
        int count = 0;
        for (JobDataStore.JobRecord j : jobs) {
            if (j.jobTitle.toLowerCase().contains(keyword.toLowerCase()) ||
                j.skillsRequired.toLowerCase().contains(keyword.toLowerCase())) {
                count++;
            }
        }
        return Math.max(count, 1);
    }

    // ============================================================
    // 4. SKILL GAP ANALYSIS & PROJECTED IMPROVEMENT
    // ============================================================

    public static SkillGapReport analyzeSkillGap(JobDataStore.JobRecord job, List<String> userSkills) {
        SkillGapReport report = new SkillGapReport();
        Set<String> userSkillSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (userSkills != null) {
            for (String s : userSkills) if (s != null && !s.trim().isEmpty()) userSkillSet.add(s.trim());
        }

        List<String> required = parseSkillsList(job.skillsRequired);
        if (required.isEmpty()) required = parseSkillsList(job.description);

        for (String req : required) {
            if (hasSkill(userSkillSet, req)) {
                report.skillsYouHave.add(req);
            } else {
                report.missingSkills.add(req);
            }
        }

        int totalReq = required.size();
        if (totalReq > 0) {
            report.currentMatchPercentage = (int) Math.round(((double) report.skillsYouHave.size() / totalReq) * 80.0) + 12;
        } else {
            report.currentMatchPercentage = 75;
        }
        report.currentMatchPercentage = Math.min(Math.max(report.currentMatchPercentage, 20), 98);

        // Generate Learning Priority Steps
        int step = 1;
        int projected = report.currentMatchPercentage;
        for (String missing : report.missingSkills) {
            int impact = (step == 1) ? 10 : (step == 2) ? 8 : (step == 3) ? 6 : 4;
            projected += impact;
            report.learningSteps.add(new LearningStep(step, missing, impact,
                "Master " + missing + " concepts, build a mini-project, and update your resume. (+" + impact + "% match boost)"));
            step++;
            if (step > 5) break;
        }

        report.projectedMatchPercentage = Math.min(projected, 96);
        return report;
    }

    // ============================================================
    // 5. CAREER PATH PROGRESSION PLANNER
    // ============================================================

    public static CareerPath generateCareerPath(String primaryRole, String userExp, List<String> userSkills, List<JobDataStore.JobRecord> allJobs) {
        CareerPath cp = new CareerPath();
        if (primaryRole == null || primaryRole.isEmpty()) primaryRole = "Java Developer";
        cp.currentProfile = primaryRole;
        cp.primaryTrack = primaryRole.contains("Java") ? "Enterprise Java & Cloud Architecture"
                         : primaryRole.contains("Python") ? "Python Backend & Data Engineering"
                         : primaryRole.contains("React") ? "Modern Frontend & Full Stack Engineering"
                         : "Software Engineering & Architecture";

        int years = extractYearsNumber(userExp);

        // Level 1: Junior / Associate
        CareerLevel l1 = new CareerLevel(1, "Junior Java Developer", "0-2 Years",
            years >= 2 ? "COMPLETED" : "CURRENT");
        l1.requiredSkills = List.of("Java", "MySQL", "Git", "OOP Fundamentals", "JUnit");
        l1.skillsToLearn = List.of("REST APIs", "Spring Boot Basics");
        l1.matchingJobsAvailable = countJobsMatching(allJobs, "Junior");
        cp.levels.add(l1);

        // Level 2: Backend Developer
        CareerLevel l2 = new CareerLevel(2, "Java Backend Developer", "2-5 Years",
            years >= 5 ? "COMPLETED" : (years >= 2 ? "CURRENT" : "NEXT"));
        l2.requiredSkills = List.of("Spring Boot", "Hibernate", "REST APIs", "MySQL", "Docker");
        l2.skillsToLearn = List.of("Docker Containerization", "AWS Cloud Essentials", "Microservices");
        l2.matchingJobsAvailable = countJobsMatching(allJobs, "Backend");
        cp.levels.add(l2);

        // Level 3: Senior Backend Engineer / Tech Lead
        CareerLevel l3 = new CareerLevel(3, "Senior Backend Developer", "5-8 Years",
            years >= 8 ? "COMPLETED" : (years >= 5 ? "CURRENT" : "TARGET"));
        l3.requiredSkills = List.of("Microservices", "AWS / Cloud", "System Design", "Kafka", "Redis");
        l3.skillsToLearn = List.of("System Design Patterns", "Distributed Caching", "Kubernetes Orchestration");
        l3.matchingJobsAvailable = countJobsMatching(allJobs, "Senior");
        cp.levels.add(l3);

        // Level 4: Principal Systems Architect
        CareerLevel l4 = new CareerLevel(4, "Lead Solutions Architect", "8+ Years", "TARGET");
        l4.requiredSkills = List.of("Distributed Architecture", "Enterprise Security", "Cloud Strategy", "High Availability");
        l4.skillsToLearn = List.of("Enterprise Governance", "Cost Optimization", "Strategic Tech Leadership");
        l4.matchingJobsAvailable = Math.max(countJobsMatching(allJobs, "Architect"), 2);
        cp.levels.add(l4);

        return cp;
    }

    // ============================================================
    // 6. RECRUITER REVERSE MATCHING: JOB TO CANDIDATES
    // ============================================================

    public static List<CandidateMatchResult> findMatchingCandidatesForJob(JobDataStore.JobRecord job,
                                                                         List<CandidateRecord> candidates) {
        List<CandidateMatchResult> results = new ArrayList<>();
        List<String> requiredSkills = parseSkillsList(job.skillsRequired);
        if (requiredSkills.isEmpty()) requiredSkills = parseSkillsList(job.description);

        for (CandidateRecord cand : candidates) {
            Set<String> candSkillSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            candSkillSet.addAll(cand.skills);

            List<String> matched = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            List<String> whyReasons = new ArrayList<>();

            for (String req : requiredSkills) {
                if (hasSkill(candSkillSet, req)) {
                    matched.add(req);
                    whyReasons.add("✓ " + req + " Strong");
                } else {
                    missing.add(req);
                    whyReasons.add("⚠ " + req + " Missing");
                }
            }

            int matchCount = matched.size();
            double skillScore = requiredSkills.isEmpty() ? 50.0 : ((double) matchCount / requiredSkills.size()) * 70.0;
            double expScore = isExperienceMatch(cand.experience, job.experienceRequired) ? 15.0 : 6.0;
            double eduScore = isEducationMatch(cand.qualification, job.qualification) ? 15.0 : 8.0;

            int total = (int) Math.round(skillScore + expScore + eduScore);
            if (total > 96) total = 96;
            if (matched.size() == requiredSkills.size() && !requiredSkills.isEmpty()) total = 94;
            if (total < 20 && matchCount == 0) total = 20;

            CandidateMatchResult cm = new CandidateMatchResult();
            cm.candidateId = cand.userId;
            cm.candidateName = cand.fullName;
            cm.candidateEmail = cand.email;
            cm.candidatePhone = cand.phone;
            cm.matchPercentage = total;
            cm.matchedSkills = matched;
            cm.missingSkills = missing;
            cm.experience = cand.experience;
            cm.qualification = cand.qualification;
            cm.resumeFileName = cand.resumeFileName;
            cm.whyReasons = whyReasons;

            results.add(cm);
        }

        // Sort descending by match score
        results.sort((a, b) -> Integer.compare(b.matchPercentage, a.matchPercentage));
        return results;
    }
}
