import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * TrustMatchHandlers — HTTP handlers for all TrustMatch AI features.
 */
public class TrustMatchHandlers {

    static void send(HttpExchange ex, String html) throws IOException {
        WebServer.SessionData session = WebServer.getSession(ex);
        String augmentedHtml = ResumeAIChatbot.injectWidget(html, session);
        SecurityManager.setSecurityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] b = augmentedHtml.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(b); }
    }

    static void redirect(HttpExchange ex, String loc) throws IOException {
        SecurityManager.setSecurityHeaders(ex);
        ex.getResponseHeaders().set("Location", loc);
        ex.sendResponseHeaders(302, -1);
    }

    static String e(String s) { return SecurityManager.escapeHtml(s); }

    static Map<String, String> parseForm(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = new HashMap<>();
        if (body == null || body.isEmpty()) return data;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                data.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                         URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return data;
    }

    static Map<String, String> parseQuery(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String query = ex.getRequestURI().getQuery();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static String nav(String userType, String fullName, String companyName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<nav>");
        sb.append("<div class=\"logo\">");
        sb.append("<a href=\"/\" style=\"display:flex;align-items:center;gap:10px;text-decoration:none;color:inherit;\">");
        sb.append("<span style=\"font-size:24px;\">🚀</span>");
        sb.append("<div style=\"line-height:1.2;\">");
        sb.append("<div style=\"font-weight:800;font-size:20px;color:#1e293b;\">SmartHire</div>");
        sb.append("<div style=\"font-size:9px;font-weight:700;color:#0284c7;\">TRUSTMATCH AI</div>");
        sb.append("</div></a></div>");
        sb.append("<div class=\"nav-links\">");
        sb.append("<a href=\"/\">Home</a><a href=\"/jobs\">Find Jobs</a>");
        sb.append("<a href=\"javascript:void(0)\" onclick=\"openAiChatbot()\" style=\"color:#4f46e5;font-weight:700;display:inline-flex;align-items:center;gap:4px;background:#eef2ff;padding:5px 10px;border-radius:6px;border:1px solid #c7d2fe;\">🤖 AI Resume Coach <span style=\"font-size:10px;background:#4f46e5;color:white;padding:1px 5px;border-radius:8px;\">AI</span></a>");

        if ("ADMIN".equals(userType)) {
            sb.append("<a href=\"/admin/dashboard\" class=\"nav-dashboard-btn\">⚙️ Admin Panel</a>");
            sb.append("<a href=\"/admin/reports\" style=\"color:#dc2626;font-weight:600;\">🛡️ Reports</a>");
            sb.append("<a href=\"/trust/verify\" style=\"color:#10b981;font-weight:700;\">✅ Trust Mgmt</a>");
            sb.append("<a href=\"/admin/audit-log\" style=\"color:#7c3aed;font-weight:600;\">🔒 Audit Log</a>");
            sb.append("<a href=\"/logout\" class=\"btn-logout\">Logout</a>");
        } else if ("RECRUITER".equals(userType)) {
            sb.append("<a href=\"/recruiter/dashboard\" class=\"nav-dashboard-btn\">📋 Dashboard</a>");
            sb.append("<a href=\"/recruiter/pipeline\" style=\"color:#7c3aed;font-weight:700;\">📊 Pipeline AI</a>");
            sb.append("<a href=\"/recruiter/candidates\" style=\"color:#0284c7;font-weight:700;\">🔍 Candidates</a>");
            sb.append("<a href=\"/logout\" class=\"btn-logout\">Logout</a>");
        } else {
            sb.append("<a href=\"/trustmatch\" style=\"color:#059669;font-weight:700;background:#ecfdf5;padding:6px 12px;border-radius:6px;border:1px solid #a7f3d0;\">🛡️ TrustMatch AI</a>");
            sb.append("<a href=\"/seeker/matches-v2\" style=\"color:#0284c7;font-weight:700;\">🎯 Smart Matches</a>");
            sb.append("<a href=\"/seeker/career-roadmap\" style=\"color:#7c3aed;font-weight:700;\">🗺️ Career Roadmap</a>");
            sb.append("<a href=\"/seeker/dashboard\" class=\"nav-dashboard-btn\">👤 Dashboard</a>");
            sb.append("<a href=\"/logout\" class=\"btn-logout\">Logout</a>");
        }
        sb.append("</div></nav>");
        return sb.toString();
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private static int getCompanyIdForJob(int jobId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT company_id FROM jobs WHERE job_id=?")) {
            ps.setInt(1, jobId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("company_id");
        } catch (Exception ignored) {}
        return 1;
    }

    private static String getCandidateSkills(String userIdStr) {
        int uid = parseIntSafe(userIdStr);
        if (uid <= 0) return "";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT skills FROM users WHERE user_id=?")) {
            ps.setInt(1, uid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String s = rs.getString("skills");
                return s != null ? s : "";
            }
        } catch (Exception ignored) {}
        return "";
    }

    // ============================================================
    // 1. TRUSTMATCH AI DASHBOARD  /trustmatch
    // ============================================================

    public static void trustMatchDashboard(HttpExchange ex,
            int userId, String email, String userType, String fullName, String companyName)
            throws IOException {

        if ("RECRUITER".equals(userType)) { redirect(ex, "/recruiter/pipeline"); return; }
        if ("ADMIN".equals(userType))     { redirect(ex, "/trust/verify"); return; }

        String seekerSkills = "", seekerExp = "", seekerQual = "", seekerLoc = "";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT skills, experience, qualification FROM users WHERE user_id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                seekerSkills = rs.getString("skills") != null ? rs.getString("skills") : "";
                seekerExp    = rs.getString("experience") != null ? rs.getString("experience") : "";
                seekerQual   = rs.getString("qualification") != null ? rs.getString("qualification") : "";
            }
        } catch (Exception ignored) {}

        List<JobDataStore.JobRecord> jobs = JobDataStore.getJobs(null, null);
        List<Map.Entry<JobDataStore.JobRecord, TrustMatchEngine.TrustMatchResult>> scored = new ArrayList<>();
        for (JobDataStore.JobRecord job : jobs) {
            if (!"OPEN".equals(job.status)) continue;
            int companyId = getCompanyIdForJob(job.jobId);
            TrustMatchEngine.CompanyTrustRecord trust = TrustMatchEngine.getTrustRecord(companyId, job.companyName);
            TrustMatchEngine.TrustMatchResult result = TrustMatchEngine.computeScore(
                job, seekerSkills, seekerExp, seekerQual, seekerLoc, trust.status);
            scored.add(new AbstractMap.SimpleEntry<>(job, result));
        }
        scored.sort((a, b) -> b.getValue().score - a.getValue().score);

        StringBuilder cards = new StringBuilder();
        int shown = 0;
        for (Map.Entry<JobDataStore.JobRecord, TrustMatchEngine.TrustMatchResult> entry : scored) {
            if (shown >= 12) break;
            JobDataStore.JobRecord job = entry.getKey();
            TrustMatchEngine.TrustMatchResult res = entry.getValue();

            TrustMatchEngine.ScamSignals scam = TrustMatchEngine.detectScamSignals(job);
            String scamWarning = "";
            if (!"LOW".equals(scam.riskLevel)) {
                scamWarning = "<div style=\"background:#fff5f5;border:1px solid #fca5a5;border-radius:8px;padding:10px 14px;margin:10px 0;font-size:13px;color:#dc2626;\">"
                    + "<strong>" + e(scam.warningMessage) + "</strong></div>";
            }

            String scoreColor = res.score >= 75 ? "#10b981" : res.score >= 50 ? "#f59e0b" : "#dc2626";
            String scoreLabel = res.score >= 75 ? "Great Match" : res.score >= 50 ? "Good Match" : "Low Match";

            StringBuilder skillPills = new StringBuilder();
            for (String s : res.matchedSkills) {
                skillPills.append("<span style=\"background:#dcfce7;color:#15803d;padding:3px 8px;border-radius:6px;font-size:11px;font-weight:700;margin:2px;display:inline-block;\">✓ ").append(e(s)).append("</span>");
            }
            int missLimit = Math.min(3, res.missingSkills.size());
            for (int i = 0; i < missLimit; i++) {
                skillPills.append("<span style=\"background:#fff5f5;color:#dc2626;padding:3px 8px;border-radius:6px;font-size:11px;font-weight:700;margin:2px;display:inline-block;\">⚠ ").append(e(res.missingSkills.get(i))).append("</span>");
            }

            StringBuilder expls = new StringBuilder();
            for (String expl : res.explanations) {
                expls.append("<li style=\"margin-bottom:4px;\">").append(e(expl)).append("</li>");
            }

            cards.append("<div style=\"background:white;border-radius:14px;padding:22px;border:1px solid #e2e8f0;box-shadow:0 3px 12px rgba(0,0,0,.05);margin-bottom:20px;border-left:4px solid ")
                 .append(scoreColor).append(";\">");
            cards.append("<div style=\"display:flex;gap:16px;align-items:flex-start;flex-wrap:wrap;\">");
            cards.append("<div style=\"text-align:center;flex-shrink:0;\">");
            cards.append("<div style=\"width:80px;height:80px;border-radius:50%;background:conic-gradient(").append(scoreColor).append(" ").append(res.score).append("%, #e2e8f0 0);display:flex;align-items:center;justify-content:center;\">");
            cards.append("<div style=\"width:64px;height:64px;border-radius:50%;background:white;display:flex;flex-direction:column;align-items:center;justify-content:center;\">");
            cards.append("<div style=\"font-size:22px;font-weight:900;color:").append(scoreColor).append(";\">").append(res.score).append("</div>");
            cards.append("<div style=\"font-size:9px;color:#64748b;\">%</div>");
            cards.append("</div></div>");
            cards.append("<div style=\"font-size:11px;font-weight:700;color:").append(scoreColor).append(";margin-top:4px;\">").append(scoreLabel).append("</div>");
            cards.append("</div>");

            cards.append("<div style=\"flex:1;min-width:200px;\">");
            cards.append("<h3 style=\"margin:0 0 4px;color:#0f172a;font-size:18px;\">").append(e(job.jobTitle)).append("</h3>");
            cards.append("<div style=\"color:#475569;font-size:14px;margin-bottom:6px;\">🏢 ").append(e(job.companyName)).append(" &nbsp; ").append(res.trustBadge).append("</div>");
            cards.append("<div style=\"font-size:13px;color:#64748b;margin-bottom:8px;\">📍 ").append(e(job.location)).append(" &nbsp;|&nbsp; 💼 ").append(e(job.jobType)).append(" &nbsp;|&nbsp; 💰 ").append(e(job.salary)).append("</div>");
            cards.append("<div style=\"margin:8px 0;\">").append(skillPills).append("</div>");
            cards.append(scamWarning);
            cards.append("</div>");

            cards.append("<div style=\"flex-shrink:0;display:flex;flex-direction:column;gap:8px;\">");
            cards.append("<a href=\"/apply?job_id=").append(job.jobId).append("\" class=\"button\" style=\"text-decoration:none;padding:8px 16px;font-size:13px;\">Apply Now</a>");
            cards.append("<a href=\"/seeker/matches-v2?job_id=").append(job.jobId).append("\" class=\"button-secondary\" style=\"text-decoration:none;padding:8px 16px;font-size:13px;text-align:center;\">Why This Match?</a>");
            cards.append("</div></div>");

            cards.append("<details style=\"margin-top:12px;\">");
            cards.append("<summary style=\"cursor:pointer;font-size:13px;font-weight:700;color:#7c3aed;\">💡 AI Match Explanation (click to expand)</summary>");
            cards.append("<ul style=\"margin:10px 0;padding-left:20px;font-size:13px;color:#475569;\">").append(expls).append("</ul>");
            cards.append("<div style=\"background:#f8fafc;border-radius:8px;padding:10px 14px;font-size:13px;color:#334155;margin-top:8px;\"><strong>📌 Recommendation:</strong> ").append(e(res.recommendation)).append("</div>");
            cards.append("</details></div>");
            shown++;
        }

        TrustMatchEngine.logAiEvent("TRUSTMATCH_DASHBOARD", userId,
            "Skills: " + (seekerSkills.length() > 100 ? seekerSkills.substring(0, 100) : seekerSkills),
            "Showed " + shown + " matches");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>TrustMatch AI - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(nav(userType, fullName, companyName));
        html.append("<div style=\"background:linear-gradient(135deg,#0f172a 0%,#1e40af 100%);padding:50px 5%;text-align:center;color:white;\">");
        html.append("<div style=\"font-size:48px;margin-bottom:12px;\">🛡️</div>");
        html.append("<h1 style=\"font-size:36px;font-weight:900;margin:0 0 12px;\">TrustMatch AI</h1>");
        html.append("<p style=\"font-size:16px;color:#93c5fd;max-width:600px;margin:0 auto;\">Personalized job recommendations with explainable AI matching, verified employers, and scam protection.</p>");
        html.append("<div style=\"display:flex;justify-content:center;gap:30px;margin-top:30px;flex-wrap:wrap;\">");
        html.append("<div style=\"background:rgba(255,255,255,.1);border-radius:12px;padding:16px 24px;text-align:center;\"><div style=\"font-size:28px;font-weight:900;\">").append(shown).append("</div><div style=\"font-size:12px;color:#93c5fd;\">Jobs Analyzed</div></div>");
        html.append("<div style=\"background:rgba(255,255,255,.1);border-radius:12px;padding:16px 24px;text-align:center;\"><div style=\"font-size:28px;font-weight:900;\">🧠</div><div style=\"font-size:12px;color:#93c5fd;\">Explainable AI</div></div>");
        html.append("<div style=\"background:rgba(255,255,255,.1);border-radius:12px;padding:16px 24px;text-align:center;\"><div style=\"font-size:28px;font-weight:900;\">✅</div><div style=\"font-size:12px;color:#93c5fd;\">Trust Verified</div></div>");
        html.append("</div></div>");

        html.append("<div style=\"max-width:900px;margin:30px auto;padding:0 20px;\">");
        html.append("<div style=\"display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;flex-wrap:wrap;gap:12px;\">");
        html.append("<div><h2 style=\"margin:0;color:#0f172a;\">Your Personalized Matches</h2>");
        html.append("<p style=\"color:#64748b;font-size:14px;margin:4px 0 0;\">Ranked by skills, experience, qualification, salary range, location & employer trust.</p></div>");
        html.append("<div style=\"display:flex;gap:10px;\">");
        html.append("<a href=\"/seeker/career-roadmap\" class=\"button-secondary\" style=\"text-decoration:none;padding:8px 16px;font-size:13px;\">🗺️ Career Roadmap</a>");
        html.append("<a href=\"/seeker/skill-gap\" class=\"button-secondary\" style=\"text-decoration:none;padding:8px 16px;font-size:13px;\">📊 Skill Gap</a>");
        html.append("</div></div>");

        html.append("<div style=\"background:#f0fdf4;border:1px solid #bbf7d0;border-radius:10px;padding:12px 16px;margin-bottom:20px;font-size:13px;color:#15803d;\">");
        html.append("<strong>ℹ️ About TrustMatch AI:</strong> Scores are based on job-related criteria only. No protected characteristics are used in ranking. <a href=\"/seeker/profile\" style=\"color:#0284c7;\">Update your profile</a> to improve match accuracy.</div>");

        if (cards.length() > 0) {
            html.append(cards);
        } else {
            html.append("<div style=\"text-align:center;padding:60px;color:#64748b;\"><div style=\"font-size:48px;\">🔍</div><h3>No matches yet</h3><p>Upload your resume or update your profile skills to get personalized matches.</p><a href=\"/seeker/resume/upload\" class=\"button\" style=\"text-decoration:none;\">Upload Resume</a></div>");
        }
        html.append("</div>");
        html.append("<footer><p>© 2026 SmartHire — TrustMatch AI uses rule-based, explainable scoring. Employer verification does not guarantee job safety.</p></footer>");
        html.append("</body></html>");
        send(ex, html.toString());
    }

    // ============================================================
    // 2. EMPLOYER TRUST VERIFICATION  /trust/verify  (admin)
    // ============================================================

    public static void trustVerifyCompany(HttpExchange ex,
            int userId, String email, String userType, String fullName, String companyName)
            throws IOException {

        if (!"ADMIN".equals(userType)) { redirect(ex, "/login"); return; }

        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> data = parseForm(ex);
            int compId       = parseIntSafe(data.get("company_id"));
            String action    = SecurityManager.sanitizeInput(data.getOrDefault("action", ""), 20);
            String notes     = SecurityManager.sanitizeInput(data.getOrDefault("notes", ""), 500);
            String newStatus = switch (action) {
                case "verify"   -> "VERIFIED";
                case "suspend"  -> "SUSPENDED";
                case "review"   -> "UNDER_REVIEW";
                case "unverify" -> "UNVERIFIED";
                default         -> null;
            };
            if (newStatus != null) {
                TrustMatchEngine.updateTrustStatus(compId, newStatus, fullName, notes);
                SecurityManager.logSecurityEvent("COMPANY_TRUST_UPDATED", email,
                    "Company " + compId + " status -> " + newStatus + ". Notes: " + notes, ex);
            }
            redirect(ex, "/trust/verify");
            return;
        }

        List<TrustMatchEngine.CompanyTrustRecord> companies = TrustMatchEngine.getAllTrustRecords();
        StringBuilder rows = new StringBuilder();
        for (TrustMatchEngine.CompanyTrustRecord rec : companies) {
            String badge = TrustMatchEngine.buildTrustBadge(rec.status != null ? rec.status : "UNVERIFIED");
            String rowBg = "SUSPENDED".equals(rec.status) ? "background:#fff5f5;" :
                           "VERIFIED".equals(rec.status) ? "background:#f0fdf4;" : "";
            rows.append("<tr style=\"").append(rowBg).append("\">");
            rows.append("<td>").append(rec.companyId).append("</td>");
            rows.append("<td><strong>").append(e(rec.companyName)).append("</strong></td>");
            rows.append("<td>").append(badge).append("</td>");
            rows.append("<td>").append(e(rec.verifiedBy != null ? rec.verifiedBy : "—")).append("</td>");
            rows.append("<td style=\"font-size:12px;color:#64748b;\">").append(e(rec.updatedAt != null ? rec.updatedAt : "—")).append("</td>");
            rows.append("<td>");
            rows.append("<form method=\"POST\" action=\"/trust/verify\" style=\"display:flex;flex-direction:column;gap:4px;min-width:200px;\">");
            rows.append("<input type=\"hidden\" name=\"company_id\" value=\"").append(rec.companyId).append("\">");
            rows.append("<input type=\"text\" name=\"notes\" placeholder=\"Notes (optional)\" style=\"padding:4px 8px;border:1px solid #cbd5e1;border-radius:4px;font-size:12px;\">");
            rows.append("<div style=\"display:flex;gap:4px;flex-wrap:wrap;\">");
            rows.append("<button name=\"action\" value=\"verify\" class=\"action-btn action-success\" style=\"font-size:11px;\">✅ Verify</button>");
            rows.append("<button name=\"action\" value=\"review\" class=\"action-btn\" style=\"font-size:11px;background:#fef3c7;color:#92400e;\">🔍 Review</button>");
            rows.append("<button name=\"action\" value=\"suspend\" class=\"action-btn action-danger\" style=\"font-size:11px;\" onclick=\"return confirm('Suspend this company?')\">🚫 Suspend</button>");
            rows.append("<button name=\"action\" value=\"unverify\" class=\"action-btn\" style=\"font-size:11px;\">↩ Unverify</button>");
            rows.append("</div></form></td></tr>");
        }

        long verified   = companies.stream().filter(c -> "VERIFIED".equals(c.status)).count();
        long suspended  = companies.stream().filter(c -> "SUSPENDED".equals(c.status)).count();
        long underReview= companies.stream().filter(c -> "UNDER_REVIEW".equals(c.status)).count();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Employer Trust Verification - Admin</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(nav(userType, fullName, companyName));
        html.append("<div class=\"dashboard-container\">");
        html.append("<div class=\"dashboard-sidebar\"><div class=\"sidebar-user\">⚙️ Admin Panel</div><div class=\"sidebar-role\">Administrator</div>");
        html.append("<nav class=\"sidebar-nav\">");
        html.append("<a href=\"/admin/dashboard\">🏠 Dashboard</a><a href=\"/admin/users\">👥 Users</a><a href=\"/admin/jobs\">💼 Jobs</a><a href=\"/admin/reports\">🛡️ Reports</a>");
        html.append("<a href=\"/trust/verify\" class=\"active\">✅ Trust Verify</a><a href=\"/trust/scam-check\">🚨 Scam Check</a><a href=\"/admin/audit-log\">🔒 Audit Log</a><a href=\"/logout\">🚪 Logout</a>");
        html.append("</nav></div>");
        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">✅ Employer Trust Verification</h1>");
        html.append("<p class=\"dashboard-subtitle\">Manage company verification status. VERIFIED badge is shown to candidates on job cards and match results.</p>");
        html.append("<div class=\"stats-grid\" style=\"margin-bottom:24px;\">");
        html.append("<div class=\"stat-card stat-success\"><div class=\"stat-num\">").append(verified).append("</div><div class=\"stat-label\">Verified</div></div>");
        html.append("<div class=\"stat-card\"><div class=\"stat-num\">").append(underReview).append("</div><div class=\"stat-label\">Under Review</div></div>");
        html.append("<div class=\"stat-card stat-danger\"><div class=\"stat-num\">").append(suspended).append("</div><div class=\"stat-label\">Suspended</div></div>");
        html.append("<div class=\"stat-card\"><div class=\"stat-num\">").append(companies.size()).append("</div><div class=\"stat-label\">Total Companies</div></div>");
        html.append("</div>");
        html.append("<div style=\"background:#fef9c3;border:1px solid #fde68a;border-radius:10px;padding:12px 16px;margin-bottom:20px;font-size:13px;color:#92400e;\">");
        html.append("⚠️ <strong>Important:</strong> VERIFIED status confirms company identity and registration only. It does NOT guarantee the safety of individual job posts or recruiter communications.</div>");
        html.append("<div style=\"overflow-x:auto;\"><table class=\"data-table\">");
        html.append("<thead><tr><th>#</th><th>Company</th><th>Status</th><th>Verified By</th><th>Last Updated</th><th>Actions</th></tr></thead><tbody>");
        if (rows.length() > 0) html.append(rows);
        else html.append("<tr><td colspan=\"6\" style=\"text-align:center;color:#64748b;\">No companies found.</td></tr>");
        html.append("</tbody></table></div></div></div>");
        html.append("<footer><p>© 2026 SmartHire</p></footer></body></html>");
        send(ex, html.toString());
    }

    // ============================================================
    // 3. SCAM SIGNAL MODERATION  /trust/scam-check  (admin)
    // ============================================================

    public static void trustScamCheck(HttpExchange ex,
            int userId, String email, String userType, String fullName, String companyName)
            throws IOException {

        if (!"ADMIN".equals(userType)) { redirect(ex, "/login"); return; }

        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> data = parseForm(ex);
            int signalId  = parseIntSafe(data.get("signal_id"));
            String action = SecurityManager.sanitizeInput(data.getOrDefault("action", ""), 20);
            if (signalId > 0 && !action.isEmpty()) {
                TrustMatchEngine.updateScamReviewStatus(signalId, action.toUpperCase());
                SecurityManager.logSecurityEvent("SCAM_REVIEW_ACTION", email, "Signal " + signalId + " -> " + action, ex);
                if ("CONFIRMED".equals(action.toUpperCase())) {
                    int jobId = parseIntSafe(data.get("job_id"));
                    if (jobId > 0) JobDataStore.setJobStatusAdmin(jobId, "CLOSED");
                }
            }
            redirect(ex, "/trust/scam-check");
            return;
        }

        List<JobDataStore.JobRecord> allJobs = JobDataStore.getJobs(null, null);
        for (JobDataStore.JobRecord job : allJobs) {
            if ("OPEN".equals(job.status)) TrustMatchEngine.detectScamSignals(job);
        }

        List<Map<String, String>> signals = TrustMatchEngine.getAllScamSignals();
        StringBuilder rows = new StringBuilder();
        for (Map<String, String> sig : signals) {
            String risk = sig.get("risk_level");
            String riskColor = "CRITICAL".equals(risk) ? "#7c3aed" : "HIGH".equals(risk) ? "#dc2626" : "MEDIUM".equals(risk) ? "#f59e0b" : "#10b981";
            String riskBg = "CRITICAL".equals(risk) ? "#f3e8ff" : "HIGH".equals(risk) ? "#fee2e2" : "MEDIUM".equals(risk) ? "#fffbeb" : "#f0fdf4";
            rows.append("<tr style=\"background:").append(riskBg).append("\">");
            rows.append("<td>").append(sig.get("signal_id")).append("</td>");
            rows.append("<td><strong>").append(e(sig.get("job_title"))).append("</strong><br><span style=\"font-size:12px;color:#64748b;\">🏢 ").append(e(sig.get("company_name"))).append("</span></td>");
            rows.append("<td><span style=\"background:").append(riskBg).append(";color:").append(riskColor).append(";padding:3px 10px;border-radius:12px;font-size:11px;font-weight:800;\">").append(risk).append("</span></td>");
            rows.append("<td style=\"font-size:12px;max-width:280px;word-break:break-word;\">").append(e(sig.get("flags"))).append("</td>");
            rows.append("<td><span class=\"status-badge ").append("PENDING".equals(sig.get("review_status")) ? "status-danger" : "").append("\">").append(e(sig.get("review_status"))).append("</span></td>");
            rows.append("<td>").append(e(sig.get("created_at"))).append("</td>");
            rows.append("<td><form method=\"POST\" action=\"/trust/scam-check\" style=\"display:flex;flex-direction:column;gap:4px;\">");
            rows.append("<input type=\"hidden\" name=\"signal_id\" value=\"").append(sig.get("signal_id")).append("\">");
            rows.append("<input type=\"hidden\" name=\"job_id\" value=\"").append(sig.get("job_id")).append("\">");
            rows.append("<button name=\"action\" value=\"DISMISSED\" class=\"action-btn action-success\" style=\"font-size:11px;\">✕ Dismiss</button>");
            rows.append("<button name=\"action\" value=\"CONFIRMED\" class=\"action-btn action-danger\" style=\"font-size:11px;\" onclick=\"return confirm('Confirm scam and close job?')\">🚫 Confirm & Close</button>");
            rows.append("<button name=\"action\" value=\"RESOLVED\" class=\"action-btn\" style=\"font-size:11px;\">✅ Resolved</button>");
            rows.append("</form></td></tr>");
        }

        long critical = signals.stream().filter(s -> "CRITICAL".equals(s.get("risk_level"))).count();
        long high     = signals.stream().filter(s -> "HIGH".equals(s.get("risk_level"))).count();
        long pending  = signals.stream().filter(s -> "PENDING".equals(s.get("review_status"))).count();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Scam Shield - Admin</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(nav(userType, fullName, companyName));
        html.append("<div class=\"dashboard-container\"><div class=\"dashboard-sidebar\"><div class=\"sidebar-user\">⚙️ Admin Panel</div><div class=\"sidebar-role\">Administrator</div>");
        html.append("<nav class=\"sidebar-nav\"><a href=\"/admin/dashboard\">🏠 Dashboard</a><a href=\"/trust/verify\">✅ Trust Verify</a><a href=\"/trust/scam-check\" class=\"active\">🚨 Scam Check</a><a href=\"/admin/reports\">🛡️ Reports</a><a href=\"/admin/audit-log\">🔒 Audit Log</a><a href=\"/logout\">🚪 Logout</a></nav></div>");
        html.append("<div class=\"dashboard-main\"><h1 class=\"dashboard-title\">🚨 Scam Shield — Early Warning System</h1>");
        html.append("<p class=\"dashboard-subtitle\">Automated scam signal detection across all job postings. Review and take action on flagged listings.</p>");
        html.append("<div class=\"stats-grid\" style=\"margin-bottom:24px;\">");
        html.append("<div class=\"stat-card stat-danger\"><div class=\"stat-num\">").append(critical).append("</div><div class=\"stat-label\">Critical</div></div>");
        html.append("<div class=\"stat-card\" style=\"border-left:4px solid #f59e0b;\"><div class=\"stat-num\">").append(high).append("</div><div class=\"stat-label\">High Risk</div></div>");
        html.append("<div class=\"stat-card\"><div class=\"stat-num\">").append(pending).append("</div><div class=\"stat-label\">Pending Review</div></div>");
        html.append("<div class=\"stat-card\"><div class=\"stat-num\">").append(signals.size()).append("</div><div class=\"stat-label\">Total Signals</div></div>");
        html.append("</div><div style=\"overflow-x:auto;\"><table class=\"data-table\">");
        html.append("<thead><tr><th>#</th><th>Job Posting</th><th>Risk Level</th><th>Detection Flags</th><th>Review Status</th><th>Detected</th><th>Actions</th></tr></thead><tbody>");
        if (rows.length() > 0) html.append(rows);
        else html.append("<tr><td colspan=\"7\" style=\"text-align:center;color:#10b981;padding:40px;\">✅ No scam signals detected across all job postings.</td></tr>");
        html.append("</tbody></table></div></div></div><footer><p>© 2026 SmartHire — Human review required for moderation.</p></footer></body></html>");
        send(ex, html.toString());
    }

    // ============================================================
    // 4. CAREER ROADMAP  /seeker/career-roadmap  (seeker)
    // ============================================================

    public static void seekerCareerRoadmap(HttpExchange ex,
            int userId, String email, String userType, String fullName, String companyName)
            throws IOException {

        if (!"JOB_SEEKER".equals(userType)) { redirect(ex, "/login"); return; }

        String seekerSkills = "";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT skills FROM users WHERE user_id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) seekerSkills = rs.getString("skills") != null ? rs.getString("skills") : "";
        } catch (Exception ignored) {}

        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> data = parseForm(ex);
            String targetRole = SecurityManager.sanitizeInput(data.getOrDefault("targetRole", ""), 100);
            if (!targetRole.isEmpty()) {
                TrustMatchEngine.generateRoadmap(userId, targetRole, seekerSkills);
                TrustMatchEngine.logAiEvent("CAREER_ROADMAP", userId, "Target: " + targetRole, "Generated roadmap");
                SecurityManager.logSecurityEvent("CAREER_ROADMAP_SET", email, "Target role: " + targetRole, ex);
            }
            redirect(ex, "/seeker/career-roadmap");
            return;
        }

        TrustMatchEngine.CareerRoadmap roadmap = TrustMatchEngine.getSavedRoadmap(userId, seekerSkills);
        List<String> roles = TrustMatchEngine.getAvailableRoles();
        StringBuilder roleOptions = new StringBuilder();
        for (String role : roles) {
            String sel = roadmap != null && role.equals(roadmap.targetRole) ? " selected" : "";
            roleOptions.append("<option value=\"").append(e(role)).append("\"").append(sel).append(">").append(e(role)).append("</option>");
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Career Roadmap - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(nav(userType, fullName, companyName));
        html.append("<div style=\"background:linear-gradient(135deg,#4c1d95,#1e40af);padding:40px 5%;text-align:center;color:white;\">");
        html.append("<h1 style=\"font-size:32px;font-weight:900;margin:0 0 10px;\">🗺️ Career Roadmap</h1>");
        html.append("<p style=\"color:#c4b5fd;font-size:15px;\">Choose your target role and get a personalized learning path with skill gaps and resources.</p></div>");

        html.append("<div style=\"max-width:900px;margin:30px auto;padding:0 20px;\">");
        html.append("<div style=\"background:white;border-radius:14px;padding:24px;border:1px solid #e2e8f0;box-shadow:0 3px 12px rgba(0,0,0,.05);margin-bottom:24px;\">");
        html.append("<h3 style=\"margin:0 0 16px;color:#0f172a;\">🎯 Select Your Target Role</h3>");
        html.append("<form method=\"POST\" action=\"/seeker/career-roadmap\" style=\"display:flex;gap:12px;flex-wrap:wrap;align-items:flex-end;\">");
        html.append("<div style=\"flex:1;min-width:200px;\">");
        html.append("<label style=\"font-size:13px;font-weight:600;display:block;margin-bottom:6px;\">Target Role</label>");
        html.append("<select name=\"targetRole\" style=\"width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;\">").append(roleOptions).append("</select>");
        html.append("</div>");
        html.append("<button type=\"submit\" class=\"button\" style=\"padding:10px 24px;\">Generate Roadmap</button>");
        html.append("</form></div>");

        if (roadmap != null) {
            String progColor = roadmap.progressPct >= 75 ? "#10b981" : roadmap.progressPct >= 50 ? "#f59e0b" : "#dc2626";
            html.append("<div style=\"background:white;border-radius:14px;padding:24px;border:1px solid #e2e8f0;box-shadow:0 4px 16px rgba(0,0,0,.05);margin-bottom:24px;\">");
            html.append("<div style=\"display:flex;gap:20px;align-items:center;flex-wrap:wrap;margin-bottom:20px;\">");
            html.append("<div style=\"text-align:center;\"><div style=\"width:100px;height:100px;border-radius:50%;background:conic-gradient(").append(progColor).append(" ").append(roadmap.progressPct).append("%, #e2e8f0 0);display:flex;align-items:center;justify-content:center;\">");
            html.append("<div style=\"width:82px;height:82px;border-radius:50%;background:white;display:flex;flex-direction:column;align-items:center;justify-content:center;\">");
            html.append("<div style=\"font-size:28px;font-weight:900;color:").append(progColor).append(";\">").append(roadmap.progressPct).append("</div>");
            html.append("<div style=\"font-size:10px;color:#64748b;\">% ready</div></div></div></div>");
            html.append("<div style=\"flex:1;min-width:200px;\">");
            html.append("<h2 style=\"margin:0 0 8px;color:#0f172a;\">").append(e(roadmap.targetRole)).append("</h2>");
            html.append("<div style=\"background:#f8fafc;border-radius:8px;padding:12px;font-size:14px;color:#334155;\">").append(e(roadmap.summary)).append("</div>");
            html.append("<div style=\"margin-top:12px;font-size:13px;color:#64748b;\">✅ <strong>").append(roadmap.requiredSkills.size() - roadmap.gapSkills.size()).append("</strong> skills ready &nbsp;|&nbsp; 📚 <strong>").append(roadmap.gapSkills.size()).append("</strong> skills to learn</div>");
            html.append("</div></div></div>");

            html.append("<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:20px;flex-wrap:wrap;\">");
            html.append("<div><h3 style=\"color:#0f172a;margin-bottom:16px;\">📍 Learning Path</h3>");
            int stepNum = 1;
            for (String req : roadmap.requiredSkills) {
                boolean have = roadmap.currentSkills.stream().anyMatch(s -> s.equalsIgnoreCase(req) || s.toLowerCase().contains(req.toLowerCase()));
                String stepColor = have ? "#10b981" : "#dc2626";
                String stepIcon  = have ? "✅" : "📚";
                String stepLabel = have ? "You have this skill" : "Skill Gap — needs learning";
                html.append("<div style=\"display:flex;align-items:center;gap:14px;margin-bottom:12px;\">");
                html.append("<div style=\"width:36px;height:36px;border-radius:50%;background:").append(stepColor).append(";color:white;display:flex;align-items:center;justify-content:center;font-weight:800;flex-shrink:0;\">").append(stepNum++).append("</div>");
                html.append("<div style=\"flex:1;background:white;border-radius:10px;padding:12px 16px;border:1px solid #e2e8f0;border-left:4px solid ").append(stepColor).append(";\">");
                html.append("<div style=\"font-weight:700;color:#0f172a;\">").append(stepIcon).append(" ").append(e(req)).append("</div>");
                html.append("<div style=\"font-size:12px;color:").append(stepColor).append(";margin-top:2px;\">").append(stepLabel).append("</div>");
                html.append("</div></div>");
            }
            html.append("</div>");

            html.append("<div><h3 style=\"color:#0f172a;margin-bottom:16px;\">📚 Recommended Learning Resources</h3>");
            for (String[] res : roadmap.resources) {
                html.append("<a href=\"").append(e(res[1])).append("\" target=\"_blank\" rel=\"noopener noreferrer\" style=\"display:block;background:white;border-radius:10px;padding:14px 16px;margin-bottom:10px;border:1px solid #e2e8f0;text-decoration:none;color:#0f172a;\">");
                html.append("<div style=\"font-weight:700;color:#0284c7;\">📖 ").append(e(res[0])).append("</div>");
                html.append("<div style=\"font-size:12px;color:#64748b;margin-top:2px;\">🏫 ").append(e(res[2])).append("</div></a>");
            }
            html.append("</div></div>");
        } else {
            html.append("<div style=\"text-align:center;padding:60px;color:#64748b;\"><div style=\"font-size:48px;\">🗺️</div><h3>Select a target role above to generate your roadmap.</h3></div>");
        }
        html.append("</div><footer><p>© 2026 SmartHire</p></footer></body></html>");
        send(ex, html.toString());
    }

    // ============================================================
    // 5. EXPLAINABLE MATCHES  /seeker/matches-v2  (seeker)
    // ============================================================

    public static void seekerMatchesV2(HttpExchange ex,
            int userId, String email, String userType, String fullName, String companyName)
            throws IOException {

        if (!"JOB_SEEKER".equals(userType)) { redirect(ex, "/seeker/matching-jobs"); return; }

        Map<String, String> q = parseQuery(ex);
        int focusJobId = parseIntSafe(q.get("job_id"));

        String seekerSkills = "", seekerExp = "", seekerQual = "";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT skills, experience, qualification FROM users WHERE user_id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                seekerSkills = rs.getString("skills") != null ? rs.getString("skills") : "";
                seekerExp    = rs.getString("experience") != null ? rs.getString("experience") : "";
                seekerQual   = rs.getString("qualification") != null ? rs.getString("qualification") : "";
            }
        } catch (Exception ignored) {}

        List<JobDataStore.JobRecord> jobs = new ArrayList<>();
        if (focusJobId > 0) {
            JobDataStore.JobRecord j = JobDataStore.getJobById(focusJobId);
            if (j != null) jobs.add(j);
        } else {
            for (JobDataStore.JobRecord j : JobDataStore.getJobs(null, null)) {
                if ("OPEN".equals(j.status)) {
                    jobs.add(j);
                    if (jobs.size() >= 15) break;
                }
            }
        }

        StringBuilder content = new StringBuilder();
        for (JobDataStore.JobRecord job : jobs) {
            int companyId = getCompanyIdForJob(job.jobId);
            TrustMatchEngine.CompanyTrustRecord trust = TrustMatchEngine.getTrustRecord(companyId, job.companyName);
            TrustMatchEngine.TrustMatchResult res = TrustMatchEngine.computeScore(
                job, seekerSkills, seekerExp, seekerQual, "", trust.status);
            TrustMatchEngine.ScamSignals scam = TrustMatchEngine.detectScamSignals(job);

            String scoreColor = res.score >= 75 ? "#10b981" : res.score >= 50 ? "#f59e0b" : "#dc2626";

            content.append("<div style=\"background:white;border-radius:14px;padding:26px;border:1px solid #e2e8f0;box-shadow:0 4px 16px rgba(0,0,0,.05);margin-bottom:28px;\">");
            content.append("<div style=\"display:flex;gap:20px;align-items:flex-start;flex-wrap:wrap;margin-bottom:20px;padding-bottom:20px;border-bottom:1px solid #f1f5f9;\">");
            content.append("<div style=\"text-align:center;flex-shrink:0;\">");
            content.append("<div style=\"width:100px;height:100px;border-radius:50%;background:conic-gradient(").append(scoreColor).append(" ").append(res.score).append("%, #e2e8f0 0);display:flex;align-items:center;justify-content:center;\">");
            content.append("<div style=\"width:82px;height:82px;border-radius:50%;background:white;display:flex;flex-direction:column;align-items:center;justify-content:center;\">");
            content.append("<div style=\"font-size:28px;font-weight:900;color:").append(scoreColor).append(";\">").append(res.score).append("</div>");
            content.append("<div style=\"font-size:10px;color:#64748b;\">% TrustMatch</div></div></div></div>");

            content.append("<div style=\"flex:1;min-width:200px;\">");
            content.append("<h2 style=\"margin:0 0 6px;color:#0f172a;font-size:22px;\">").append(e(job.jobTitle)).append("</h2>");
            content.append("<div style=\"color:#475569;margin-bottom:6px;\">🏢 ").append(e(job.companyName)).append(" &nbsp; ").append(res.trustBadge).append("</div>");
            content.append("<div style=\"font-size:13px;color:#64748b;\">📍 ").append(e(job.location)).append(" &nbsp;|&nbsp; 💼 ").append(e(job.jobType)).append(" &nbsp;|&nbsp; 💰 ").append(e(job.salary)).append("</div></div>");
            content.append("<div><a href=\"/apply?job_id=").append(job.jobId).append("\" class=\"button\" style=\"text-decoration:none;\">Apply Now</a></div></div>");

            content.append("<h3 style=\"color:#0f172a;margin:0 0 14px;font-size:16px;\">📊 Score Breakdown (Explainable AI)</h3>");
            content.append("<div style=\"display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:12px;margin-bottom:20px;\">");
            content.append(scoreBreakdownItem("Skills", res.skillScore, 40, "#0284c7"));
            content.append(scoreBreakdownItem("Experience", res.experienceScore, 20, "#7c3aed"));
            content.append(scoreBreakdownItem("Qualification", res.qualificationScore, 15, "#10b981"));
            content.append(scoreBreakdownItem("Salary Range", res.salaryScore, 10, "#f59e0b"));
            content.append(scoreBreakdownItem("Location", res.locationScore, 10, "#ec4899"));
            content.append(scoreBreakdownItem("Employer Trust", res.trustBonus, 5, "#14b8a6"));
            content.append("</div>");

            content.append("<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:20px;\">");
            content.append("<div style=\"background:#f0fdf4;border-radius:10px;padding:14px;\"><div style=\"font-weight:700;color:#15803d;margin-bottom:8px;\">✅ Skills Matched (").append(res.matchedSkills.size()).append(")</div><div style=\"display:flex;flex-wrap:wrap;gap:6px;\">");
            for (String s : res.matchedSkills) {
                content.append("<span style=\"background:#dcfce7;color:#15803d;padding:4px 10px;border-radius:8px;font-size:12px;font-weight:700;\">✓ ").append(e(s)).append("</span>");
            }
            content.append("</div></div>");

            content.append("<div style=\"background:#fff5f5;border-radius:10px;padding:14px;\"><div style=\"font-weight:700;color:#dc2626;margin-bottom:8px;\">📚 Skills to Develop (").append(res.missingSkills.size()).append(")</div><div style=\"display:flex;flex-wrap:wrap;gap:6px;\">");
            for (String s : res.missingSkills) {
                content.append("<span style=\"background:#fee2e2;color:#dc2626;padding:4px 10px;border-radius:8px;font-size:12px;font-weight:700;\">⚠ ").append(e(s)).append("</span>");
            }
            content.append("</div></div></div>");

            if (!"LOW".equals(scam.riskLevel)) {
                content.append("<div style=\"background:#fee2e2;border:1px solid #fca5a5;border-radius:10px;padding:14px 18px;margin-bottom:16px;\">");
                content.append("<div style=\"font-weight:800;color:#dc2626;margin-bottom:6px;\">🚨 Scam Shield Alert (").append(scam.riskLevel).append(")</div>");
                content.append("<div style=\"font-size:13px;color:#334155;\">").append(e(scam.warningMessage)).append("</div></div>");
            }

            content.append("<div style=\"background:#f0f9ff;border:1px solid #bae6fd;border-radius:10px;padding:14px 18px;\">");
            content.append("<div style=\"font-weight:700;color:#0369a1;margin-bottom:6px;\">📌 AI Recommendation</div>");
            content.append("<div style=\"font-size:14px;color:#334155;\">").append(e(res.recommendation)).append("</div></div>");
            content.append("</div>");
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Explainable Matches - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(nav(userType, fullName, companyName));
        html.append("<div style=\"background:linear-gradient(135deg,#0f172a,#1e40af);padding:36px 5%;text-align:center;color:white;\">");
        html.append("<h1 style=\"font-size:28px;font-weight:900;margin:0 0 8px;\">🧠 Explainable Job Matches</h1>");
        html.append("<p style=\"color:#93c5fd;font-size:14px;\">See exactly why each job matches your profile — with full transparency.</p></div>");
        html.append("<div style=\"max-width:900px;margin:30px auto;padding:0 20px;\">").append(content).append("</div>");
        html.append("<footer><p>© 2026 SmartHire</p></footer></body></html>");
        send(ex, html.toString());
    }

    private static String scoreBreakdownItem(String label, int val, int max, String color) {
        int pct = max > 0 ? (int) Math.round((double) val / max * 100) : 0;
        return "<div style=\"background:#f8fafc;border-radius:10px;padding:12px;text-align:center;border:1px solid #e2e8f0;\">"
            + "<div style=\"font-size:20px;font-weight:900;color:" + color + ";\">" + val + "/" + max + "</div>"
            + "<div style=\"font-size:11px;font-weight:700;color:#0f172a;margin:2px 0;\">" + label + "</div>"
            + "<div style=\"height:4px;background:#e2e8f0;border-radius:2px;margin-top:6px;\">"
            + "<div style=\"height:100%;width:" + pct + "%;background:" + color + ";border-radius:2px;\"></div></div></div>";
    }

    // ============================================================
    // 6. RECRUITER PIPELINE INTELLIGENCE  /recruiter/pipeline
    // ============================================================

    public static void recruiterPipelineIntelligence(HttpExchange ex,
            int userId, String email, String userType, String fullName, String companyName)
            throws IOException {

        if (!"RECRUITER".equals(userType)) { redirect(ex, "/login"); return; }

        List<Map<String, String>> applications = JobDataStore.getApplicationsForRecruiter(userId, 0);
        Map<String, List<Map<String, String>>> byStage = new LinkedHashMap<>();
        byStage.put("APPLIED", new ArrayList<>());
        byStage.put("SHORTLISTED", new ArrayList<>());
        byStage.put("INTERVIEW", new ArrayList<>());
        byStage.put("REJECTED", new ArrayList<>());

        for (Map<String, String> app : applications) {
            String status = String.valueOf(app.getOrDefault("status", "APPLIED")).toUpperCase();
            if (byStage.containsKey(status)) byStage.get(status).add(app);
            else byStage.get("APPLIED").add(app);
        }

        StringBuilder pipeline = new StringBuilder();
        String[][] stages = {
            {"APPLIED", "📥 Applied", "#3b82f6", "#eff6ff"},
            {"SHORTLISTED", "⭐ Shortlisted", "#8b5cf6", "#f5f3ff"},
            {"INTERVIEW", "📅 Interview", "#0284c7", "#f0f9ff"},
            {"REJECTED", "❌ Rejected", "#dc2626", "#fff5f5"}
        };

        for (String[] st : stages) {
            String stageKey = st[0];
            String label    = st[1];
            String color    = st[2];
            String bg       = st[3];
            List<Map<String, String>> stageApps = byStage.get(stageKey);

            pipeline.append("<div style=\"background:").append(bg).append(";border-radius:12px;padding:16px;min-width:240px;flex:1;\">");
            pipeline.append("<div style=\"font-weight:800;color:").append(color).append(";margin-bottom:12px;font-size:14px;\">");
            pipeline.append(label).append(" <span style=\"background:").append(color).append(";color:white;border-radius:10px;padding:2px 8px;font-size:11px;margin-left:6px;\">").append(stageApps.size()).append("</span></div>");

            if (stageApps.isEmpty()) {
                pipeline.append("<div style=\"font-size:13px;color:#94a3b8;text-align:center;padding:20px;\">No candidates</div>");
            } else {
                for (Map<String, String> app : stageApps) {
                    String appName  = e(String.valueOf(app.getOrDefault("candidateName", "Candidate")));
                    String appEmail = e(String.valueOf(app.getOrDefault("email", "")));
                    String jobTitle = e(String.valueOf(app.getOrDefault("job_title", "—")));
                    String appId    = String.valueOf(app.getOrDefault("application_id", ""));

                    String candSkills = getCandidateSkills(String.valueOf(app.getOrDefault("user_id", "")));
                    int jId = parseIntSafe(String.valueOf(app.getOrDefault("job_id", "0")));
                    JobDataStore.JobRecord j = jId > 0 ? JobDataStore.getJobById(jId) : null;
                    String scoreHtml = "";
                    if (j != null && !candSkills.isEmpty()) {
                        TrustMatchEngine.TrustMatchResult res = TrustMatchEngine.computeScore(j, candSkills, "", "", "", "UNVERIFIED");
                        String sc = res.score >= 75 ? "#10b981" : res.score >= 50 ? "#f59e0b" : "#dc2626";
                        scoreHtml = "<div style=\"font-size:18px;font-weight:900;color:" + sc + ";\">" + res.score + "%</div><div style=\"font-size:10px;color:#64748b;\">match</div>";
                    }

                    pipeline.append("<div style=\"background:white;border-radius:10px;padding:14px;border:1px solid #e2e8f0;margin-bottom:10px;\">");
                    pipeline.append("<div style=\"display:flex;justify-content:space-between;align-items:flex-start;gap:10px;\">");
                    pipeline.append("<div><div style=\"font-weight:700;color:#0f172a;font-size:14px;\">").append(appName).append("</div>");
                    pipeline.append("<div style=\"font-size:12px;color:#64748b;\">").append(appEmail).append("</div>");
                    pipeline.append("<div style=\"font-size:12px;color:#0284c7;margin-top:2px;\">📋 ").append(jobTitle).append("</div></div>");
                    pipeline.append("<div style=\"text-align:center;flex-shrink:0;\">").append(scoreHtml).append("</div></div>");
                    pipeline.append("<div style=\"margin-top:10px;display:flex;gap:6px;flex-wrap:wrap;\">");
                    pipeline.append("<a href=\"/recruiter/applications?app_id=").append(appId).append("\" class=\"action-btn\" style=\"font-size:11px;\">View App</a>");
                    pipeline.append("<a href=\"/seeker/resume/view?user_id=").append(String.valueOf(app.getOrDefault("user_id", ""))).append("\" class=\"action-btn\" style=\"font-size:11px;\" target=\"_blank\">View Resume</a>");
                    pipeline.append("</div></div>");
                }
            }
            pipeline.append("</div>");
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Pipeline Intelligence - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(nav(userType, fullName, companyName));
        html.append("<div style=\"background:linear-gradient(135deg,#4c1d95,#1e40af);padding:36px 5%;text-align:center;color:white;\">");
        html.append("<h1 style=\"font-size:28px;font-weight:900;margin:0 0 8px;\">📊 Candidate Pipeline Intelligence</h1>");
        html.append("<p style=\"color:#c4b5fd;font-size:14px;\">AI-assisted candidate tracking with skill analysis and automated duplicate detection.</p></div>");

        html.append("<div style=\"max-width:1200px;margin:30px auto;padding:0 20px;\">");
        html.append("<div style=\"background:#fef9c3;border:1px solid #fde68a;border-radius:10px;padding:12px 16px;margin-bottom:20px;font-size:13px;color:#92400e;\">");
        html.append("⚠️ AI outputs are labeled as <strong>suggestions</strong>. Human recruiters are responsible for all hiring decisions.</div>");
        html.append("<div style=\"display:flex;gap:16px;overflow-x:auto;padding-bottom:20px;align-items:flex-start;\">").append(pipeline).append("</div>");
        html.append("</div><footer><p>© 2026 SmartHire</p></footer></body></html>");
        send(ex, html.toString());
    }

    // ============================================================
    // 7. FORGOT PASSWORD  /forgot-password
    // ============================================================

    public static void forgotPasswordPage(HttpExchange ex) throws IOException {
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> data = parseForm(ex);
            String em = SecurityManager.sanitizeInput(data.getOrDefault("email", ""), 150);
            String resetToken = null;

            if (SecurityManager.isValidEmail(em)) {
                try (Connection conn = DatabaseConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM users WHERE email=? AND is_active=TRUE")) {
                    ps.setString(1, em);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        int uid = rs.getInt("user_id");
                        resetToken = SecurityManager.generatePasswordResetToken(uid);
                        if (resetToken != null) {
                            String resetLink = "http://localhost:8080/reset-password?token=" + resetToken;
                            System.out.println("[PASSWORD RESET] Link for " + em + ": " + resetLink);
                            SecurityManager.logSecurityEvent("RESET_TOKEN_GENERATED", em, "Reset link created", ex);
                        }
                    }
                } catch (Exception ignored) {}
            }

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><title>Reset Email Sent</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
            html.append("<div class=\"success-page\"><div class=\"success-box\"><div class=\"success-icon\">📧</div>");
            html.append("<h1>Check Your Email</h1><p>If an account exists for <strong>").append(e(em)).append("</strong>, a password reset link has been created.</p>");
            if (resetToken != null) {
                html.append("<div style=\"background:#f0f9ff;border:1px solid #bae6fd;padding:12px;border-radius:8px;margin:16px 0;font-size:13px;word-break:break-all;\">");
                html.append("<strong>Demo Reset Link:</strong><br><a href=\"/reset-password?token=").append(resetToken).append("\" style=\"color:#0284c7;\">/reset-password?token=").append(resetToken).append("</a></div>");
            }
            html.append("<a class=\"button\" href=\"/login\">Back to Login</a></div></div></body></html>");
            send(ex, html.toString());
            return;
        }

        String html = "<!DOCTYPE html><html><head><title>Forgot Password - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>"
            + "<nav><div class=\"logo\"><span style=\"font-size:24px;\">🚀</span><span style=\"font-weight:800;font-size:20px;color:#1e293b;margin-left:10px;\">SmartHire</span></div>"
            + "<div class=\"nav-links\"><a href=\"/\">Home</a><a href=\"/login\">Login</a></div></nav>"
            + "<div class=\"form-container\"><form method=\"POST\" action=\"/forgot-password\" class=\"form-card\">"
            + "<h1>🔑 Forgot Password</h1>"
            + "<p style=\"color:#64748b;font-size:14px;margin-bottom:20px;\">Enter your registered email address. We will generate a secure reset link valid for 30 minutes.</p>"
            + "<label>Email Address *</label><input type=\"email\" name=\"email\" required placeholder=\"your@email.com\" maxlength=\"150\">"
            + "<button type=\"submit\" style=\"margin-top:15px;\">Send Reset Link</button>"
            + "<p style=\"text-align:center;margin-top:15px;\">Remember your password? <a href=\"/login\">Login here</a></p>"
            + "</form></div></body></html>";
        send(ex, html);
    }

    // ============================================================
    // 8. RESET PASSWORD  /reset-password
    // ============================================================

    public static void resetPasswordPage(HttpExchange ex) throws IOException {
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> data = parseForm(ex);
            String token    = SecurityManager.sanitizeInput(data.getOrDefault("token", ""), 128);
            String password = data.getOrDefault("newPassword", "");
            String confirm  = data.getOrDefault("confirmPassword", "");

            int uid = SecurityManager.validatePasswordResetToken(token);
            if (uid <= 0) {
                send(ex, "<!DOCTYPE html><html><head><title>Invalid Link</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>"
                    + "<div class=\"success-page\"><div class=\"success-box\"><div class=\"success-icon\">❌</div>"
                    + "<h1 style=\"color:#dc2626;\">Invalid or Expired Link</h1>"
                    + "<p>This reset link is either invalid, expired (30-minute limit), or already used.</p>"
                    + "<a class=\"button\" href=\"/forgot-password\">Request New Link</a></div></div></body></html>");
                return;
            }

            String pwErr = SecurityManager.validatePassword(password);
            if (pwErr != null) {
                send(ex, "<!DOCTYPE html><html><head><title>Reset Failed</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>"
                    + "<div class=\"success-page\"><div class=\"success-box\"><h1 style=\"color:#dc2626;\">Password Invalid</h1>"
                    + "<p>" + e(pwErr) + "</p><a class=\"button\" href=\"/forgot-password\">Try Again</a></div></div></body></html>");
                return;
            }
            if (!password.equals(confirm)) {
                send(ex, "<!DOCTYPE html><html><head><title>Reset Failed</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>"
                    + "<div class=\"success-page\"><div class=\"success-box\"><h1 style=\"color:#dc2626;\">Passwords Do Not Match</h1>"
                    + "<a class=\"button\" href=\"/forgot-password\">Try Again</a></div></div></body></html>");
                return;
            }

            String newHash = SecurityManager.hashPassword(password);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE users SET password=? WHERE user_id=?")) {
                ps.setString(1, newHash);
                ps.setInt(2, uid);
                ps.executeUpdate();
                SecurityManager.logSecurityEvent("PASSWORD_RESET_SUCCESS", String.valueOf(uid), "Password reset via token", ex);
            } catch (Exception err) {
                send(ex, "<!DOCTYPE html><html><head><title>Error</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>"
                    + "<div class=\"success-page\"><div class=\"success-box\"><h1 style=\"color:#dc2626;\">Database Error</h1>"
                    + "<a class=\"button\" href=\"/login\">Back to Login</a></div></div></body></html>");
                return;
            }

            send(ex, "<!DOCTYPE html><html><head><title>Password Changed</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>"
                + "<div class=\"success-page\"><div class=\"success-box\"><div class=\"success-icon\">✅</div>"
                + "<h1>Password Reset Successful!</h1><p>Your password has been securely updated. You can now log in.</p>"
                + "<a class=\"button\" href=\"/login\">Login Now</a></div></div></body></html>");
            return;
        }

        Map<String, String> q = parseQuery(ex);
        String token = SecurityManager.sanitizeInput(q.getOrDefault("token", ""), 128);
        if (token.isEmpty()) { redirect(ex, "/forgot-password"); return; }

        String html = "<!DOCTYPE html><html><head><title>Reset Password - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>"
            + "<nav><div class=\"logo\"><span style=\"font-size:24px;\">🚀</span><span style=\"font-weight:800;font-size:20px;color:#1e293b;margin-left:10px;\">SmartHire</span></div>"
            + "<div class=\"nav-links\"><a href=\"/login\">Login</a></div></nav>"
            + "<div class=\"form-container\"><form method=\"POST\" action=\"/reset-password\" class=\"form-card\">"
            + "<h1>🔒 Set New Password</h1>"
            + "<input type=\"hidden\" name=\"token\" value=\"" + e(token) + "\">"
            + "<label>New Password *</label><input type=\"password\" name=\"newPassword\" required minlength=\"8\" maxlength=\"128\" placeholder=\"Min 8 characters\">"
            + "<label>Confirm Password *</label><input type=\"password\" name=\"confirmPassword\" required minlength=\"8\" maxlength=\"128\" placeholder=\"Re-enter password\">"
            + "<div style=\"background:#f0f9ff;border:1px solid #bae6fd;padding:10px;border-radius:8px;margin:10px 0;font-size:12px;color:#0369a1;\">"
            + "🔐 Minimum 8 characters. Avoid common passwords.</div>"
            + "<button type=\"submit\" style=\"margin-top:10px;\">Update Password</button>"
            + "</form></div></body></html>";
        send(ex, html);
    }

    // ============================================================
    // 9. ADMIN AUDIT LOG  /admin/audit-log
    // ============================================================

    public static void adminAuditLog(HttpExchange ex,
            int userId, String email, String userType, String fullName, String companyName)
            throws IOException {

        if (!"ADMIN".equals(userType)) { redirect(ex, "/login"); return; }
        SecurityManager.logSecurityEvent("AUDIT_LOG_VIEWED", email, "Admin viewed audit log", ex);

        StringBuilder rows = new StringBuilder();
        int total = 0;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT log_id, event_type, actor, ip_address, details, created_at FROM security_audit_log ORDER BY created_at DESC LIMIT 300")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                total++;
                String evType = rs.getString("event_type");
                String rowStyle = (evType.contains("FAIL") || evType.contains("DENIED") || evType.contains("LOCKED"))
                    ? "background:#fff5f5;" : evType.contains("SUCCESS") ? "background:#f0fdf4;" : "";
                rows.append("<tr style=\"").append(rowStyle).append("\">");
                rows.append("<td>").append(rs.getInt("log_id")).append("</td>");
                rows.append("<td><span style=\"background:#e0f2fe;color:#0369a1;padding:2px 8px;border-radius:6px;font-size:11px;font-weight:700;\">").append(e(evType)).append("</span></td>");
                rows.append("<td>").append(e(rs.getString("actor"))).append("</td>");
                rows.append("<td>").append(e(rs.getString("ip_address"))).append("</td>");
                rows.append("<td style=\"max-width:280px;word-break:break-word;font-size:12px;\">").append(e(rs.getString("details"))).append("</td>");
                rows.append("<td style=\"font-size:12px;color:#64748b;\">").append(e(rs.getString("created_at"))).append("</td></tr>");
            }
        } catch (Exception err) {
            rows.append("<tr><td colspan=\"6\" style=\"color:#dc2626;\">Audit log table empty or initializing.</td></tr>");
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Security Audit Log - Admin</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(nav(userType, fullName, companyName));
        html.append("<div class=\"dashboard-container\"><div class=\"dashboard-sidebar\"><div class=\"sidebar-user\">⚙️ Admin Panel</div><div class=\"sidebar-role\">Administrator</div>");
        html.append("<nav class=\"sidebar-nav\"><a href=\"/admin/dashboard\">🏠 Dashboard</a><a href=\"/admin/users\">👥 Users</a><a href=\"/admin/jobs\">💼 Jobs</a><a href=\"/admin/reports\">🛡️ Reports</a><a href=\"/trust/verify\">✅ Trust Verify</a><a href=\"/trust/scam-check\">🚨 Scam Check</a><a href=\"/admin/audit-log\" class=\"active\">🔒 Security Audit</a><a href=\"/logout\">🚪 Logout</a></nav></div>");
        html.append("<div class=\"dashboard-main\"><h1 class=\"dashboard-title\">🔒 Security Audit Log</h1>");
        html.append("<p class=\"dashboard-subtitle\">Centralized audit trail: logins, password resets, role changes, lockout events.</p>");
        html.append("<div class=\"stats-grid\" style=\"margin-bottom:20px;\"><div class=\"stat-card\"><div class=\"stat-num\">").append(total).append("</div><div class=\"stat-label\">Events Recorded</div></div></div>");
        html.append("<div style=\"overflow-x:auto;\"><table class=\"data-table\"><thead><tr><th>#</th><th>Event</th><th>Actor</th><th>IP Address</th><th>Details</th><th>Timestamp</th></tr></thead><tbody>");
        if (rows.length() > 0) html.append(rows);
        else html.append("<tr><td colspan=\"6\" style=\"text-align:center;color:#64748b;padding:40px;\">No security events recorded yet.</td></tr>");
        html.append("</tbody></table></div></div></div><footer><p>© 2026 SmartHire</p></footer></body></html>");
        send(ex, html.toString());
    }
}
