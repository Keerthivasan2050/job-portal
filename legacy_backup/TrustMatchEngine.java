import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TrustMatchEngine — AI-Powered Job Matching & Trust Intelligence
 * ================================================================
 * Implements OWASP-aligned, explainable, rule-based matching.
 *
 * Features:
 *  1. TrustMatch Score with per-criterion breakdown and explanation
 *  2. Employer Trust Verification status management
 *  3. Scam Signal detection across job postings
 *  4. Career Roadmap generation with skill gap analysis
 *  5. AI audit trail logging
 *
 * Design principles:
 *  - Deterministic, rule-based scoring (no black-box ML)
 *  - All scoring criteria are job-related and auditable
 *  - No protected characteristics used in ranking
 *  - Explanations shown to candidates so they can correct inaccuracies
 */
public class TrustMatchEngine {

    // ============================================================
    // DATA MODELS
    // ============================================================

    public static class TrustMatchResult {
        public int score;
        public int skillScore;
        public int experienceScore;
        public int qualificationScore;
        public int salaryScore;
        public int locationScore;
        public int trustBonus;
        public List<String> matchedSkills = new ArrayList<>();
        public List<String> missingSkills  = new ArrayList<>();
        public List<String> explanations   = new ArrayList<>();
        public String recommendation = "";
        public String riskLevel = "LOW";
        public String trustBadge = "";
    }

    public static class CompanyTrustRecord {
        public int companyId;
        public String companyName;
        public String status;       // UNVERIFIED, VERIFIED, UNDER_REVIEW, SUSPENDED
        public String verifiedBy;
        public String evidenceRef;
        public String notes;
        public String updatedAt;

        public CompanyTrustRecord(int companyId, String companyName) {
            this.companyId   = companyId;
            this.companyName = companyName;
            this.status      = "UNVERIFIED";
        }
    }

    public static class ScamSignals {
        public String riskLevel;   // LOW, MEDIUM, HIGH, CRITICAL
        public List<String> flags = new ArrayList<>();
        public String warningMessage;
        public int flagCount;
    }

    public static class CareerRoadmap {
        public String targetRole;
        public List<String> currentSkills  = new ArrayList<>();
        public List<String> requiredSkills = new ArrayList<>();
        public List<String> gapSkills      = new ArrayList<>();
        public List<String[]> resources    = new ArrayList<>(); // [title, url, platform]
        public int progressPct;
        public String summary;
    }

    // ============================================================
    // ROLE SKILL REQUIREMENTS (explainable, auditable rule base)
    // ============================================================

    private static final Map<String, List<String>> ROLE_SKILLS = new LinkedHashMap<>();
    static {
        ROLE_SKILLS.put("Java Developer",       Arrays.asList("Java","Spring Boot","MySQL","REST API","Hibernate","Git","Maven","JUnit"));
        ROLE_SKILLS.put("Frontend Developer",   Arrays.asList("HTML","CSS","JavaScript","React","TypeScript","Git","Webpack","Jest"));
        ROLE_SKILLS.put("Full Stack Developer", Arrays.asList("Java","Spring Boot","React","JavaScript","MySQL","REST API","Git","Docker"));
        ROLE_SKILLS.put("Data Scientist",       Arrays.asList("Python","Machine Learning","Pandas","NumPy","SQL","TensorFlow","Statistics","Jupyter"));
        ROLE_SKILLS.put("DevOps Engineer",      Arrays.asList("Docker","Kubernetes","CI/CD","Linux","AWS","Jenkins","Terraform","Ansible"));
        ROLE_SKILLS.put("Machine Learning Engineer", Arrays.asList("Python","TensorFlow","PyTorch","Scikit-learn","Statistics","SQL","MLOps","NumPy"));
        ROLE_SKILLS.put("Data Analyst",         Arrays.asList("SQL","Python","Excel","Tableau","PowerBI","Statistics","Data Visualization"));
        ROLE_SKILLS.put("Cloud Engineer",       Arrays.asList("AWS","Azure","GCP","Terraform","Docker","Linux","Networking","Kubernetes"));
        ROLE_SKILLS.put("Software Engineer",    Arrays.asList("Java","Python","Data Structures","Algorithms","Git","SQL","REST API","OOP"));
        ROLE_SKILLS.put("Mobile Developer",     Arrays.asList("Android","Kotlin","Java","Flutter","Dart","REST API","Git","Firebase"));
    }

    private static final Map<String, List<String[]>> ROLE_RESOURCES = new LinkedHashMap<>();
    static {
        ROLE_RESOURCES.put("Java Developer", Arrays.asList(
            new String[]{"Java Masterclass", "https://www.udemy.com/course/java-the-complete-java-developer-course/", "Udemy"},
            new String[]{"Spring Boot Tutorial", "https://spring.io/guides", "Spring Official"},
            new String[]{"GeeksforGeeks Java", "https://www.geeksforgeeks.org/java/", "GeeksforGeeks"},
            new String[]{"Baeldung Spring", "https://www.baeldung.com/", "Baeldung"}
        ));
        ROLE_RESOURCES.put("Frontend Developer", Arrays.asList(
            new String[]{"freeCodeCamp Responsive Web Design", "https://www.freecodecamp.org/", "freeCodeCamp"},
            new String[]{"React Official Tutorial", "https://react.dev/learn", "React Docs"},
            new String[]{"The Odin Project", "https://www.theodinproject.com/", "The Odin Project"},
            new String[]{"JavaScript.info", "https://javascript.info/", "JavaScript.info"}
        ));
        ROLE_RESOURCES.put("Data Scientist", Arrays.asList(
            new String[]{"Python for Data Science - IBM", "https://www.coursera.org/professional-certificates/ibm-data-science", "Coursera"},
            new String[]{"Kaggle Learn", "https://www.kaggle.com/learn", "Kaggle"},
            new String[]{"fast.ai Deep Learning", "https://www.fast.ai/", "fast.ai"},
            new String[]{"StatQuest YouTube", "https://www.youtube.com/@statquest", "YouTube"}
        ));
        ROLE_RESOURCES.put("DevOps Engineer", Arrays.asList(
            new String[]{"Docker Official Getting Started", "https://docs.docker.com/get-started/", "Docker Docs"},
            new String[]{"Kubernetes Tutorial", "https://kubernetes.io/docs/tutorials/", "Kubernetes Docs"},
            new String[]{"AWS Free Tier Labs", "https://aws.amazon.com/free/", "AWS"},
            new String[]{"DevOps Roadmap", "https://roadmap.sh/devops", "roadmap.sh"}
        ));
        // Default resources for all other roles
    }

    private static List<String[]> getResourcesForRole(String role) {
        return ROLE_RESOURCES.getOrDefault(role, Arrays.asList(
            new String[]{"roadmap.sh Career Paths", "https://roadmap.sh/", "roadmap.sh"},
            new String[]{"Coursera Professional Certificates", "https://www.coursera.org/", "Coursera"},
            new String[]{"Udemy Top Courses", "https://www.udemy.com/", "Udemy"},
            new String[]{"YouTube Tutorials", "https://www.youtube.com/", "YouTube"}
        ));
    }

    // ============================================================
    // SCAM DETECTION KEYWORDS
    // ============================================================

    private static final List<String> SCAM_KEYWORDS_CRITICAL = Arrays.asList(
        "pay deposit", "send money", "western union", "wire transfer",
        "registration fee", "training fee", "pay to work", "advance payment"
    );

    private static final List<String> SCAM_KEYWORDS_HIGH = Arrays.asList(
        "guaranteed income", "earn lakhs", "earn crores", "no experience needed",
        "work from anywhere earn", "100% placement guaranteed", "no interview required",
        "direct joining", "immediate joining no documents"
    );

    private static final List<String> SCAM_KEYWORDS_MEDIUM = Arrays.asList(
        "whatsapp only", "call immediately", "limited seats", "act fast",
        "earn daily", "passive income", "multi-level", "mlm", "pyramid",
        "investment required", "franchise required"
    );

    // ============================================================
    // 1. TRUSTMATCH SCORE COMPUTATION
    // ============================================================

    /**
     * Compute a TrustMatch score for a job posting against a seeker's profile.
     *
     * Criteria (job-related only, no protected characteristics):
     *  - Skills overlap:    40 points (most important signal)
     *  - Experience match:  20 points
     *  - Qualification:     15 points
     *  - Salary range:      10 points
     *  - Location:          10 points
     *  - Employer trust:     5 points bonus
     *
     * @param job           The job posting to evaluate
     * @param seekerSkills  Comma-separated skills from seeker's profile/resume
     * @param seekerExp     Seeker's experience string (e.g., "3 years")
     * @param seekerQual    Seeker's qualification string
     * @param seekerLoc     Seeker's preferred location (optional)
     * @param companyTrust  Company verification status
     */
    public static TrustMatchResult computeScore(
            JobDataStore.JobRecord job,
            String seekerSkills,
            String seekerExp,
            String seekerQual,
            String seekerLoc,
            String companyTrust) {

        TrustMatchResult result = new TrustMatchResult();

        // --- Skills Match (40 pts) ---
        List<String> jobSkillList    = parseSkills(job.skillsRequired);
        List<String> seekerSkillList = parseSkills(seekerSkills);

        for (String jobSkill : jobSkillList) {
            boolean matched = seekerSkillList.stream()
                .anyMatch(s -> skillMatch(s, jobSkill));
            if (matched) result.matchedSkills.add(jobSkill);
            else         result.missingSkills.add(jobSkill);
        }

        int totalJobSkills = jobSkillList.isEmpty() ? 1 : jobSkillList.size();
        double skillRatio  = (double) result.matchedSkills.size() / totalJobSkills;
        result.skillScore  = (int) Math.round(skillRatio * 40);

        if (skillRatio >= 0.8)
            result.explanations.add("Strong skills match — you have most required skills.");
        else if (skillRatio >= 0.5)
            result.explanations.add("Good skills match — you have " + result.matchedSkills.size()
                + " of " + totalJobSkills + " required skills.");
        else
            result.explanations.add("Partial skills match — consider upskilling in: "
                + String.join(", ", result.missingSkills.subList(0, Math.min(3, result.missingSkills.size()))));

        // --- Experience Match (20 pts) ---
        result.experienceScore = computeExperienceScore(job.experienceRequired, seekerExp);
        if (result.experienceScore >= 16)
            result.explanations.add("Your experience level meets the job requirements.");
        else if (result.experienceScore >= 10)
            result.explanations.add("Your experience is slightly below the preferred range.");
        else
            result.explanations.add("This role requires more experience than you currently have.");

        // --- Qualification Match (15 pts) ---
        result.qualificationScore = computeQualificationScore(job.qualification, seekerQual);
        if (result.qualificationScore >= 12)
            result.explanations.add("Your qualification matches the requirement.");

        // --- Salary Match (10 pts) ---
        result.salaryScore = 7; // neutral default — salary expectations not collected yet
        result.explanations.add("Salary range: " + (job.salary != null ? job.salary : "Negotiable"));

        // --- Location Match (10 pts) ---
        result.locationScore = computeLocationScore(job.location, seekerLoc);
        if (result.locationScore >= 8)
            result.explanations.add("Job location matches your preference.");

        // --- Employer Trust Bonus (5 pts) ---
        result.trustBonus = 0;
        if ("VERIFIED".equals(companyTrust)) {
            result.trustBonus = 5;
            result.trustBadge = buildTrustBadge("VERIFIED");
            result.explanations.add("Employer is verified by SmartHire.");
        } else if ("UNDER_REVIEW".equals(companyTrust)) {
            result.trustBadge = buildTrustBadge("UNDER_REVIEW");
        } else if ("SUSPENDED".equals(companyTrust)) {
            result.trustBadge = buildTrustBadge("SUSPENDED");
            result.riskLevel = "HIGH";
        } else {
            result.trustBadge = buildTrustBadge("UNVERIFIED");
        }

        // --- Total Score ---
        result.score = result.skillScore + result.experienceScore
            + result.qualificationScore + result.salaryScore
            + result.locationScore + result.trustBonus;
        result.score = Math.min(100, Math.max(0, result.score));

        // --- Recommendation ---
        if (!result.missingSkills.isEmpty()) {
            String topGap = result.missingSkills.get(0);
            result.recommendation = "To improve this match, focus on learning "
                + topGap + ". Consider adding a practical project to your portfolio.";
        } else {
            result.recommendation = "Great match! Make sure your resume highlights "
                + "your experience with " + String.join(", ", result.matchedSkills.subList(0, Math.min(3, result.matchedSkills.size())));
        }

        return result;
    }

    // --- Score computation helpers ---

    private static int computeExperienceScore(String required, String seeker) {
        if (required == null || required.isEmpty()) return 15;
        int reqYears = extractYears(required);
        int seekYears = extractYears(seeker);
        if (seekYears >= reqYears) return 20;
        if (seekYears >= reqYears - 1) return 15;
        if (seekYears >= reqYears - 2) return 10;
        return 5;
    }

    private static int extractYears(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 0;
    }

    private static int computeQualificationScore(String required, String seeker) {
        if (required == null || seeker == null) return 10;
        String req  = required.toLowerCase();
        String seek = seeker.toLowerCase();
        if (seek.contains("phd") || seek.contains("m.tech") || seek.contains("m.e")) return 15;
        if ((req.contains("b.e") || req.contains("b.tech")) &&
            (seek.contains("b.e") || seek.contains("b.tech") || seek.contains("bachelor"))) return 15;
        if (req.contains("graduate") && (seek.contains("b") || seek.contains("degree"))) return 12;
        if (req.contains("diploma") && seek.contains("diploma")) return 12;
        return 8;
    }

    private static int computeLocationScore(String jobLoc, String seekerLoc) {
        if (seekerLoc == null || seekerLoc.isEmpty()) return 7; // neutral
        if (jobLoc == null || jobLoc.isEmpty()) return 7;
        String jl = jobLoc.toLowerCase();
        String sl = seekerLoc.toLowerCase();
        if (jl.contains(sl) || sl.contains(jl)) return 10;
        // Check remote
        if (jl.contains("remote") || jl.contains("anywhere")) return 9;
        return 5;
    }

    private static List<String> parseSkills(String skills) {
        List<String> result = new ArrayList<>();
        if (skills == null || skills.isEmpty()) return result;
        for (String s : skills.split("[,;/]")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private static boolean skillMatch(String seeker, String required) {
        String s = seeker.toLowerCase().trim();
        String r = required.toLowerCase().trim();
        return s.contains(r) || r.contains(s) ||
               s.replace(" ", "").contains(r.replace(" ", "")) ||
               r.replace(" ", "").contains(s.replace(" ", ""));
    }

    // ============================================================
    // 2. TRUST BADGE HTML
    // ============================================================

    public static String buildTrustBadge(String status) {
        return switch (status) {
            case "VERIFIED"     -> "<span style='background:#dcfce7;color:#15803d;padding:3px 10px;border-radius:12px;font-size:11px;font-weight:800;'>✅ VERIFIED EMPLOYER</span>";
            case "UNDER_REVIEW" -> "<span style='background:#fef9c3;color:#92400e;padding:3px 10px;border-radius:12px;font-size:11px;font-weight:800;'>🔍 UNDER REVIEW</span>";
            case "SUSPENDED"    -> "<span style='background:#fee2e2;color:#dc2626;padding:3px 10px;border-radius:12px;font-size:11px;font-weight:800;'>🚫 SUSPENDED</span>";
            default             -> "<span style='background:#f1f5f9;color:#64748b;padding:3px 10px;border-radius:12px;font-size:11px;font-weight:700;'>⚪ UNVERIFIED</span>";
        };
    }

    // ============================================================
    // 3. EMPLOYER TRUST RECORD LOOKUP
    // ============================================================

    public static CompanyTrustRecord getTrustRecord(int companyId, String companyName) {
        CompanyTrustRecord rec = new CompanyTrustRecord(companyId, companyName);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT status, verified_by, evidence_ref, notes, updated_at " +
                 "FROM company_verifications WHERE company_id=? LIMIT 1")) {
            ps.setInt(1, companyId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                rec.status      = rs.getString("status");
                rec.verifiedBy  = rs.getString("verified_by");
                rec.evidenceRef = rs.getString("evidence_ref");
                rec.notes       = rs.getString("notes");
                rec.updatedAt   = rs.getString("updated_at");
            }
        } catch (Exception ignored) {}
        return rec;
    }

    public static List<CompanyTrustRecord> getAllTrustRecords() {
        List<CompanyTrustRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT c.company_id, c.company_name, " +
                 "COALESCE(cv.status,'UNVERIFIED') AS status, " +
                 "cv.verified_by, cv.notes, cv.updated_at " +
                 "FROM companies c LEFT JOIN company_verifications cv ON c.company_id=cv.company_id " +
                 "ORDER BY c.company_name")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                CompanyTrustRecord rec = new CompanyTrustRecord(
                    rs.getInt("company_id"), rs.getString("company_name"));
                rec.status     = rs.getString("status");
                rec.verifiedBy = rs.getString("verified_by");
                rec.notes      = rs.getString("notes");
                rec.updatedAt  = rs.getString("updated_at");
                list.add(rec);
            }
        } catch (Exception e) {
            // Return mock data if DB unavailable
        }
        return list;
    }

    public static void updateTrustStatus(int companyId, String status, String verifiedBy, String notes) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO company_verifications (company_id, status, verified_by, notes, updated_at) " +
                 "VALUES (?, ?, ?, ?, NOW()) " +
                 "ON DUPLICATE KEY UPDATE status=VALUES(status), verified_by=VALUES(verified_by), " +
                 "notes=VALUES(notes), updated_at=NOW()")) {
            ps.setInt(1, companyId);
            ps.setString(2, status);
            ps.setString(3, verifiedBy);
            ps.setString(4, notes);
            ps.executeUpdate();
        } catch (Exception e) {
            System.out.println("[TrustMatch] updateTrustStatus error: " + e.getMessage());
        }
    }

    // ============================================================
    // 4. SCAM SIGNAL DETECTION
    // ============================================================

    public static ScamSignals detectScamSignals(JobDataStore.JobRecord job) {
        ScamSignals signals = new ScamSignals();
        String combined = ((job.jobTitle != null ? job.jobTitle : "") + " "
            + (job.description != null ? job.description : "")).toLowerCase();

        // Critical signals
        for (String kw : SCAM_KEYWORDS_CRITICAL) {
            if (combined.contains(kw)) {
                signals.flags.add("CRITICAL: Contains payment request keyword: \"" + kw + "\"");
            }
        }

        // High-risk signals
        for (String kw : SCAM_KEYWORDS_HIGH) {
            if (combined.contains(kw)) {
                signals.flags.add("HIGH: Suspicious promise: \"" + kw + "\"");
            }
        }

        // Medium signals
        for (String kw : SCAM_KEYWORDS_MEDIUM) {
            if (combined.contains(kw)) {
                signals.flags.add("MEDIUM: Unusual pattern: \"" + kw + "\"");
            }
        }

        // Unrealistic salary check (fresher + extremely high salary)
        if (job.experienceRequired != null) {
            int expYears = extractYears(job.experienceRequired);
            if (expYears <= 1 && job.salary != null) {
                String salary = job.salary.toLowerCase();
                if (salary.contains("50") || salary.contains("80") || salary.contains("100")
                        || salary.contains("crore")) {
                    signals.flags.add("HIGH: Unrealistically high salary for fresher/junior role");
                }
            }
        }

        // Missing description
        if (job.description == null || job.description.trim().length() < 50) {
            signals.flags.add("MEDIUM: Very short or missing job description");
        }

        // Missing skills
        if (job.skillsRequired == null || job.skillsRequired.trim().isEmpty()) {
            signals.flags.add("LOW: No skills specified — incomplete posting");
        }

        // Determine risk level
        signals.flagCount = signals.flags.size();
        boolean hasCritical = signals.flags.stream().anyMatch(f -> f.startsWith("CRITICAL"));
        boolean hasHigh     = signals.flags.stream().anyMatch(f -> f.startsWith("HIGH"));
        long mediumCount    = signals.flags.stream().filter(f -> f.startsWith("MEDIUM")).count();

        if (hasCritical)          signals.riskLevel = "CRITICAL";
        else if (hasHigh)         signals.riskLevel = "HIGH";
        else if (mediumCount >= 2) signals.riskLevel = "MEDIUM";
        else if (signals.flagCount > 0) signals.riskLevel = "MEDIUM";
        else                      signals.riskLevel = "LOW";

        signals.warningMessage = switch (signals.riskLevel) {
            case "CRITICAL" -> "⛔ This job posting has been flagged for serious concerns including potential payment requests. Do NOT send money to employers. Report suspicious listings.";
            case "HIGH"     -> "🚨 This listing has been flagged with high-risk signals. Review carefully before applying and verify the company independently.";
            case "MEDIUM"   -> "⚠️ This listing has some unusual characteristics. Proceed with caution and verify company details before sharing personal information.";
            default         -> "✅ No major risk signals detected in this listing.";
        };

        // Save to DB
        saveScamSignals(job.jobId, signals);
        return signals;
    }

    private static void saveScamSignals(int jobId, ScamSignals signals) {
        if (signals.flagCount == 0) return;
        String flagsText = String.join("; ", signals.flags);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO scam_signals (job_id, risk_level, flags, review_status, created_at) " +
                 "VALUES (?, ?, ?, 'PENDING', NOW()) " +
                 "ON DUPLICATE KEY UPDATE risk_level=VALUES(risk_level), flags=VALUES(flags), review_status='PENDING'")) {
            ps.setInt(1, jobId);
            ps.setString(2, signals.riskLevel);
            ps.setString(3, flagsText.substring(0, Math.min(flagsText.length(), 1000)));
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    public static List<Map<String, String>> getAllScamSignals() {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT ss.signal_id, ss.job_id, ss.risk_level, ss.flags, ss.review_status, ss.created_at, " +
                 "j.job_title, c.company_name " +
                 "FROM scam_signals ss " +
                 "JOIN jobs j ON ss.job_id=j.job_id " +
                 "JOIN companies c ON j.company_id=c.company_id " +
                 "ORDER BY FIELD(ss.risk_level,'CRITICAL','HIGH','MEDIUM','LOW'), ss.created_at DESC " +
                 "LIMIT 100")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("signal_id",    rs.getString("signal_id"));
                row.put("job_id",       rs.getString("job_id"));
                row.put("risk_level",   rs.getString("risk_level"));
                row.put("flags",        rs.getString("flags"));
                row.put("review_status", rs.getString("review_status"));
                row.put("created_at",   rs.getString("created_at"));
                row.put("job_title",    rs.getString("job_title"));
                row.put("company_name", rs.getString("company_name"));
                list.add(row);
            }
        } catch (Exception e) {
            System.out.println("[TrustMatch] getAllScamSignals error: " + e.getMessage());
        }
        return list;
    }

    public static void updateScamReviewStatus(int signalId, String status) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE scam_signals SET review_status=? WHERE signal_id=?")) {
            ps.setString(1, status);
            ps.setInt(2, signalId);
            ps.executeUpdate();
        } catch (Exception e) {
            System.out.println("[TrustMatch] updateScamReviewStatus error: " + e.getMessage());
        }
    }

    // ============================================================
    // 5. CAREER ROADMAP
    // ============================================================

    public static CareerRoadmap generateRoadmap(int userId, String targetRole, String currentSkills) {
        CareerRoadmap roadmap = new CareerRoadmap();
        roadmap.targetRole     = targetRole;
        roadmap.requiredSkills = new ArrayList<>(ROLE_SKILLS.getOrDefault(targetRole,
            Arrays.asList("Communication", "Problem Solving", "Teamwork", "Git")));
        roadmap.currentSkills  = parseSkills(currentSkills);
        roadmap.resources      = getResourcesForRole(targetRole);

        // Compute gaps
        for (String req : roadmap.requiredSkills) {
            boolean have = roadmap.currentSkills.stream()
                .anyMatch(s -> skillMatch(s, req));
            if (!have) roadmap.gapSkills.add(req);
        }

        int have  = roadmap.requiredSkills.size() - roadmap.gapSkills.size();
        int total = roadmap.requiredSkills.isEmpty() ? 1 : roadmap.requiredSkills.size();
        roadmap.progressPct = (int) Math.round((double) have / total * 100);

        if (roadmap.gapSkills.isEmpty()) {
            roadmap.summary = "🎉 You have all the key skills for " + targetRole + "! Focus on building a strong portfolio.";
        } else {
            roadmap.summary = "You need " + roadmap.gapSkills.size() + " more skill"
                + (roadmap.gapSkills.size() > 1 ? "s" : "") + " to be job-ready for " + targetRole + ". Start with: "
                + roadmap.gapSkills.get(0) + ".";
        }

        // Persist roadmap
        saveRoadmap(userId, roadmap);
        return roadmap;
    }

    private static void saveRoadmap(int userId, CareerRoadmap roadmap) {
        String gapStr  = String.join(", ", roadmap.gapSkills);
        String curStr  = String.join(", ", roadmap.currentSkills);
        String reqStr  = String.join(", ", roadmap.requiredSkills);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT INTO career_roadmaps (user_id, target_role, current_skills, gap_skills, progress_pct, updated_at)
                 VALUES (?, ?, ?, ?, ?, NOW())
                 ON DUPLICATE KEY UPDATE
                     target_role=VALUES(target_role), current_skills=VALUES(current_skills),
                     gap_skills=VALUES(gap_skills), progress_pct=VALUES(progress_pct), updated_at=NOW()
             """)) {
            ps.setInt(1, userId);
            ps.setString(2, roadmap.targetRole);
            ps.setString(3, curStr.substring(0, Math.min(curStr.length(), 500)));
            ps.setString(4, gapStr.substring(0, Math.min(gapStr.length(), 500)));
            ps.setInt(5, roadmap.progressPct);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    public static CareerRoadmap getSavedRoadmap(int userId, String currentSkills) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT target_role, progress_pct FROM career_roadmaps WHERE user_id=? LIMIT 1")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return generateRoadmap(userId, rs.getString("target_role"), currentSkills);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static List<String> getAvailableRoles() {
        return new ArrayList<>(ROLE_SKILLS.keySet());
    }

    // ============================================================
    // 6. AI AUDIT TRAIL
    // ============================================================

    public static void logAiEvent(String feature, int actorUserId, String inputSummary, String outputSummary) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO ai_audit_trail (feature_name, actor_user_id, input_summary, output_summary, model_version, created_at) " +
                 "VALUES (?, ?, ?, ?, ?, NOW())")) {
            ps.setString(1, feature);
            ps.setInt(2, actorUserId);
            ps.setString(3, inputSummary != null ? inputSummary.substring(0, Math.min(inputSummary.length(), 500)) : "");
            ps.setString(4, outputSummary != null ? outputSummary.substring(0, Math.min(outputSummary.length(), 500)) : "");
            ps.setString(5, "RuleEngine-v1.0");
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    // ============================================================
    // 7. DATABASE SCHEMA INITIALIZATION
    // ============================================================

    public static void initializeTrustMatchSchema() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS company_verifications (
                    cv_id        INT AUTO_INCREMENT PRIMARY KEY,
                    company_id   INT NOT NULL UNIQUE,
                    status       ENUM('UNVERIFIED','VERIFIED','UNDER_REVIEW','SUSPENDED') DEFAULT 'UNVERIFIED',
                    verified_by  VARCHAR(150),
                    evidence_ref VARCHAR(500),
                    notes        TEXT,
                    verified_at  TIMESTAMP NULL,
                    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    FOREIGN KEY (company_id) REFERENCES companies(company_id) ON DELETE CASCADE,
                    INDEX idx_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS scam_signals (
                    signal_id     INT AUTO_INCREMENT PRIMARY KEY,
                    job_id        INT NOT NULL UNIQUE,
                    risk_level    ENUM('LOW','MEDIUM','HIGH','CRITICAL') DEFAULT 'LOW',
                    flags         TEXT,
                    reviewed_by   VARCHAR(150),
                    review_status ENUM('PENDING','DISMISSED','CONFIRMED','RESOLVED') DEFAULT 'PENDING',
                    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE,
                    INDEX idx_risk_level (risk_level),
                    INDEX idx_review_status (review_status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS career_roadmaps (
                    roadmap_id    INT AUTO_INCREMENT PRIMARY KEY,
                    user_id       INT NOT NULL UNIQUE,
                    target_role   VARCHAR(100) NOT NULL,
                    current_skills TEXT,
                    gap_skills    TEXT,
                    resources     TEXT,
                    progress_pct  INT DEFAULT 0,
                    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS trustmatch_explanations (
                    match_id      INT AUTO_INCREMENT PRIMARY KEY,
                    job_id        INT NOT NULL,
                    user_id       INT NOT NULL,
                    score         INT DEFAULT 0,
                    matched_skills TEXT,
                    missing_skills TEXT,
                    explanation   TEXT,
                    model_version VARCHAR(20) DEFAULT 'RuleEngine-v1.0',
                    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_job_user (job_id, user_id),
                    FOREIGN KEY (job_id)  REFERENCES jobs(job_id)  ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ai_audit_trail (
                    audit_id       INT AUTO_INCREMENT PRIMARY KEY,
                    feature_name   VARCHAR(100) NOT NULL,
                    actor_user_id  INT,
                    input_summary  TEXT,
                    output_summary TEXT,
                    model_version  VARCHAR(20) DEFAULT 'RuleEngine-v1.0',
                    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_feature (feature_name),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            System.out.println("✅ TrustMatch AI schema initialized (5 tables).");

        } catch (Exception e) {
            System.out.println("⚠️  TrustMatch schema init skipped: " + e.getMessage());
        }
    }
}
