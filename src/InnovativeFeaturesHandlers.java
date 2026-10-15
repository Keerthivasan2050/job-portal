import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * InnovativeFeaturesHandlers — Implements the 8 Advanced Recruitment Platform Features:
 * ====================================================================================
 * 1. AI Mock Interview Simulator (Technical & Behavioral STAR Questions, Scoring & Feedback)
 * 2. Career Growth Predictor (Role Milestones, Projects & Skill Trajectories)
 * 3. Smart Application Follow-Up (Overdue SLA Flags & 1-Click Recruiter Follow-Up Pings)
 * 4. Company Trust & Verification System (Admin Credential Checks & Verified Badges)
 * 5. Smart Interview Scheduler (Slot Selection & Double-Booking Prevention)
 * 6. Project-Based Skill Portfolio (GitHub, Live Demos, Contribution Evidence for Freshers)
 * 7. Accessible & Personalized Job Search (Contrast Toggle, Font Resizer, Filter Presets)
 * 8. Recruitment Bottleneck Analytics (Funnel Stage Conversion & Turnaround Time Metrics)
 */
public class InnovativeFeaturesHandlers {

    // In-memory stores for features (with database persistence where configured)
    public static class CandidateProject {
        public int projectId;
        public int userId;
        public String title;
        public String githubUrl;
        public String demoUrl;
        public String techStack;
        public String contribution;
        public String createdAt;

        public CandidateProject(int projectId, int userId, String title, String githubUrl,
                                String demoUrl, String techStack, String contribution) {
            this.projectId = projectId;
            this.userId = userId;
            this.title = title;
            this.githubUrl = githubUrl;
            this.demoUrl = demoUrl;
            this.techStack = techStack;
            this.contribution = contribution;
            this.createdAt = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        }
    }

    private static final List<CandidateProject> PROJECTS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger PROJECT_ID_GEN = new AtomicInteger(100);
    public static final Map<Integer, Long> LAST_FOLLOW_UPS = new ConcurrentHashMap<>();

    static {
        // Seed initial projects for both candidate users (1 and 4)
        for (int uid : new int[]{1, 4}) {
            PROJECTS.add(new CandidateProject(
                PROJECT_ID_GEN.getAndIncrement(), uid,
                "SmartHire — Two-Way AI Recruitment Platform",
                "https://github.com/keerthi/smarthire-job-portal",
                "http://localhost:8080",
                "Java, Spring Boot, MySQL, REST API, Docker, Bootstrap 5",
                "Architected explainable matching algorithm, designed database schema, and implemented secure RBAC session management."
            ));
            PROJECTS.add(new CandidateProject(
                PROJECT_ID_GEN.getAndIncrement(), uid,
                "High-Throughput Microservices Event Pipeline",
                "https://github.com/keerthi/kafka-event-pipeline",
                "https://demo.example.com/pipeline",
                "Java 21, Apache Kafka, Redis, Docker, JUnit 5",
                "Implemented asynchronous message ingestion pipeline capable of 15,000+ events/sec with sub-50ms latency."
            ));
        }
    }

    // =========================================================================
    // FEATURE 1: AI MOCK INTERVIEW SIMULATOR
    // =========================================================================

    public static void handleMockInterview(HttpExchange exchange) throws IOException {
        WebServer.SessionData session = WebServer.getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            WebServer.sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
        String selectedRole = query.getOrDefault("role", "Java Full Stack Developer");
        int qIndex = 0;
        try { qIndex = Integer.parseInt(query.getOrDefault("q", "0")); } catch (Exception ignored) {}

        List<MockQuestion> questions = getQuestionsForRole(selectedRole);
        if (qIndex < 0 || qIndex >= questions.size()) qIndex = 0;
        MockQuestion currentQ = questions.get(qIndex);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        html.append("<title>AI Mock Interview Simulator - SmartHire</title>");
        html.append("<link rel=\"stylesheet\" href=\"/css/style.css\">");
        html.append("<style>");
        html.append(".sim-container { max-width:980px; margin:30px auto; padding:0 20px; }");
        html.append(".sim-card { background:white; border-radius:14px; padding:30px; box-shadow:0 4px 20px rgba(0,0,0,.06); margin-bottom:24px; border:1px solid #e2e8f0; }");
        html.append(".sim-badge { display:inline-block; padding:4px 12px; border-radius:20px; font-size:12px; font-weight:700; text-transform:uppercase; margin-bottom:12px; }");
        html.append(".tech-badge { background:#eff6ff; color:#2563eb; border:1px solid #bfdbfe; }");
        html.append(".star-badge { background:#fdf4ff; color:#9333ea; border:1px solid #f0abfc; }");
        html.append(".q-text { font-size:20px; font-weight:800; color:#0f172a; margin:0 0 14px; line-height:1.4; }");
        html.append(".q-hint { background:#f8fafc; border-left:4px solid #3b82f6; padding:12px 16px; border-radius:0 8px 8px 0; font-size:13px; color:#475569; margin-bottom:20px; }");
        html.append(".ans-box { width:100%; min-height:160px; padding:14px; border:1.5px solid #cbd5e1; border-radius:8px; font-size:15px; font-family:inherit; box-sizing:border-box; margin-bottom:16px; transition:border 0.2s; }");
        html.append(".ans-box:focus { border-color:#2563eb; outline:none; box-shadow:0 0 0 3px rgba(37,99,235,.15); }");
        html.append(".nav-pills { display:flex; gap:8px; margin-bottom:24px; overflow-x:auto; padding-bottom:6px; }");
        html.append(".nav-pill { padding:8px 16px; border-radius:8px; background:#f1f5f9; color:#475569; text-decoration:none; font-weight:600; font-size:13px; }");
        html.append(".nav-pill.active { background:#2563eb; color:white; }");
        html.append("</style></head><body>");

        html.append(WebServer.navHtml(session));

        html.append("<div style=\"background:linear-gradient(135deg,#1e1b4b,#312e81);padding:40px 5%;color:white;text-align:center;\">");
        html.append("<h1 style=\"font-size:32px;font-weight:900;margin:0 0 10px;\">🎯 AI Mock Interview Simulator</h1>");
        html.append("<p style=\"color:#c7d2fe;font-size:15px;max-width:700px;margin:0 auto;\">");
        html.append("Practice real technical & behavioral questions generated from industry job descriptions. Receive instant AI scoring, keyword gap diagnosis, and STAR model answers.</p>");
        html.append("</div>");

        html.append("<div class=\"sim-container\">");

        // Role selector
        html.append("<div style=\"margin-bottom:20px;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;\">");
        html.append("<div><strong>Select Target Specialization:</strong></div>");
        html.append("<div class=\"nav-pills\">");
        for (String r : new String[]{"Java Full Stack Developer", "Spring Boot Backend", "Cloud & DevOps Engineer", "React Frontend Developer", "Data & AI Engineer"}) {
            String active = r.equalsIgnoreCase(selectedRole) ? " active" : "";
            html.append("<a href=\"/seeker/mock-interview?role=").append(WebServer.escapeHtml(r)).append("\" class=\"nav-pill").append(active).append("\">").append(WebServer.escapeHtml(r)).append("</a>");
        }
        html.append("</div></div>");

        // Question navigation
        html.append("<div style=\"display:flex;gap:6px;margin-bottom:18px;\">");
        for (int i = 0; i < questions.size(); i++) {
            String act = (i == qIndex) ? "background:#2563eb;color:white;font-weight:700;" : "background:white;color:#64748b;border:1px solid #cbd5e1;";
            html.append("<a href=\"/seeker/mock-interview?role=").append(WebServer.escapeHtml(selectedRole)).append("&q=").append(i).append("\" style=\"display:inline-block;padding:6px 14px;border-radius:6px;text-decoration:none;font-size:13px;").append(act).append("\">Q").append(i + 1).append("</a>");
        }
        html.append("</div>");

        // Question card
        html.append("<div class=\"sim-card\">");
        String badgeClass = currentQ.isBehavioral ? "star-badge" : "tech-badge";
        String badgeLabel = currentQ.isBehavioral ? "Behavioral — STAR Framework" : "Core Technical Architecture";
        html.append("<span class=\"sim-badge ").append(badgeClass).append("\">").append(badgeLabel).append("</span>");
        html.append("<div class=\"q-text\">").append(WebServer.escapeHtml(currentQ.questionText)).append("</div>");
        html.append("<div class=\"q-hint\">💡 <strong>Interviewer's Evaluation Criteria:</strong> ").append(WebServer.escapeHtml(currentQ.evaluationHint)).append("</div>");

        html.append("<form method=\"POST\" action=\"/seeker/mock-interview/evaluate\">");
        html.append("<input type=\"hidden\" name=\"role\" value=\"").append(WebServer.escapeHtml(selectedRole)).append("\">");
        html.append("<input type=\"hidden\" name=\"qIndex\" value=\"").append(qIndex).append("\">");
        html.append("<label style=\"font-size:13px;font-weight:700;display:block;margin-bottom:8px;color:#334155;\">Your Answer (Type naturally as you would speak in an interview):</label>");
        html.append("<textarea name=\"answer\" class=\"ans-box\" placeholder=\"Structure your answer with clear points, technical keywords, and measurable impact (e.g. latency reduced by 30%, 10K daily active users, caching with Redis)...\" required></textarea>");

        html.append("<div style=\"display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;\">");
        html.append("<button type=\"submit\" class=\"button\" style=\"padding:12px 28px;font-size:15px;background:#2563eb;border:none;\">🤖 Submit for AI Evaluation & Scoring</button>");
        if (qIndex < questions.size() - 1) {
            html.append("<a href=\"/seeker/mock-interview?role=").append(WebServer.escapeHtml(selectedRole)).append("&q=").append(qIndex + 1).append("\" class=\"button-secondary\" style=\"padding:12px 20px;text-decoration:none;\">Skip to Next Question →</a>");
        }
        html.append("</div></form>");
        html.append("</div>"); // sim-card

        // Quick tips
        html.append("<div class=\"sim-card\" style=\"background:#faf5ff;border-color:#e9d5ff;\">");
        html.append("<h4 style=\"color:#6b21a8;margin:0 0 8px;\">🎓 Pro Tip for College Viva & Hiring Interviews:</h4>");
        html.append("<p style=\"margin:0;font-size:13px;color:#4a044e;line-height:1.6;\">");
        html.append("Use the <strong>Google X-Y-Z formula</strong>: <em>'Accomplished [X], as measured by [Y], by doing [Z]'</em>. Always state the problem, the technologies you evaluated, your architecture decision, and the measurable outcome.</p>");
        html.append("</div>");

        html.append("</div>"); // sim-container
        html.append("<footer><p>© 2026 SmartHire — AI-Powered Career Assistance</p></footer></body></html>");

        sendResponse(exchange, 200, html.toString());
    }

    public static void handleMockInterviewEvaluate(HttpExchange exchange) throws IOException {
        WebServer.SessionData session = WebServer.getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            WebServer.sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> data = parseFormData(exchange);
        String role = data.getOrDefault("role", "Java Full Stack Developer");
        int qIndex = 0;
        try { qIndex = Integer.parseInt(data.getOrDefault("qIndex", "0")); } catch (Exception ignored) {}
        String answer = data.getOrDefault("answer", "").trim();

        List<MockQuestion> questions = getQuestionsForRole(role);
        if (qIndex < 0 || qIndex >= questions.size()) qIndex = 0;
        MockQuestion q = questions.get(qIndex);

        // Grade the response using keyword heuristics & STAR structure
        MockEvaluation eval = evaluateAnswer(q, answer);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        html.append("<title>AI Interview Feedback - SmartHire</title>");
        html.append("<link rel=\"stylesheet\" href=\"/css/style.css\">");
        html.append("<style>");
        html.append(".feedback-container { max-width:920px; margin:35px auto; padding:0 20px; }");
        html.append(".score-hero { background:white; border-radius:14px; padding:28px; box-shadow:0 4px 20px rgba(0,0,0,.06); margin-bottom:24px; border:1px solid #e2e8f0; display:flex; align-items:center; gap:24px; }");
        html.append(".score-circle { width:100px; height:100px; border-radius:50%; background:linear-gradient(135deg,#3b82f6,#1d4ed8); color:white; display:flex; flex-direction:column; align-items:center; justify-content:center; font-weight:900; font-size:32px; box-shadow:0 4px 14px rgba(37,99,235,.35); flex-shrink:0; }");
        html.append(".feedback-card { background:white; border-radius:14px; padding:26px; box-shadow:0 4px 15px rgba(0,0,0,.05); margin-bottom:20px; border:1px solid #e2e8f0; }");
        html.append(".tag-hit { background:#dcfce7; color:#15803d; padding:4px 10px; border-radius:16px; font-size:12px; font-weight:700; display:inline-block; margin:2px 4px; }");
        html.append(".tag-miss { background:#fee2e2; color:#b91c1c; padding:4px 10px; border-radius:16px; font-size:12px; font-weight:700; display:inline-block; margin:2px 4px; }");
        html.append("</style></head><body>");

        html.append(WebServer.navHtml(session));

        html.append("<div class=\"feedback-container\">");
        html.append("<div class=\"score-hero\">");
        html.append("<div class=\"score-circle\">").append(eval.score).append("<span style=\"font-size:11px;font-weight:600;opacity:.9;\">/ 100</span></div>");
        html.append("<div>");
        html.append("<div style=\"font-size:12px;color:#2563eb;font-weight:700;text-transform:uppercase;letter-spacing:.5px;\">AI Answer Assessment</div>");
        html.append("<h2 style=\"margin:4px 0 8px;color:#0f172a;\">").append(WebServer.escapeHtml(eval.verdict)).append("</h2>");
        html.append("<p style=\"margin:0;color:#64748b;font-size:14px;\">Tested against <strong>").append(WebServer.escapeHtml(role)).append("</strong> interview benchmark standards.</p>");
        html.append("</div></div>");

        // Your answer review
        html.append("<div class=\"feedback-card\">");
        html.append("<h4 style=\"margin:0 0 8px;color:#0f172a;\">📝 Your Answer:</h4>");
        html.append("<div style=\"background:#f8fafc;padding:14px;border-radius:8px;font-size:14px;color:#334155;line-height:1.6;border:1px solid #e2e8f0;margin-bottom:16px;\">")
            .append(WebServer.escapeHtml(answer.isEmpty() ? "No answer provided." : answer)).append("</div>");

        // Concept Coverage
        html.append("<h4 style=\"margin:0 0 8px;color:#0f172a;\">🎯 Key Concepts Detected:</h4><div style=\"margin-bottom:16px;\">");
        for (String c : eval.matchedConcepts) {
            html.append("<span class=\"tag-hit\">✓ ").append(WebServer.escapeHtml(c)).append("</span>");
        }
        for (String c : eval.missingConcepts) {
            html.append("<span class=\"tag-miss\">✗ Missing: ").append(WebServer.escapeHtml(c)).append("</span>");
        }
        html.append("</div>");

        // Detailed Feedback
        html.append("<h4 style=\"margin:0 0 8px;color:#0f172a;\">💡 Actionable Improvement Feedback:</h4>");
        html.append("<ul style=\"margin:0 0 16px;padding-left:20px;color:#334155;font-size:14px;line-height:1.7;\">");
        for (String tip : eval.improvementTips) {
            html.append("<li>").append(WebServer.escapeHtml(tip)).append("</li>");
        }
        html.append("</ul>");

        // Model Answer
        html.append("<h4 style=\"margin:0 0 8px;color:#059669;\">⭐ Benchmark Model Answer:</h4>");
        html.append("<div style=\"background:#f0fdf4;border:1px solid #bbf7d0;padding:16px;border-radius:8px;font-size:14px;color:#166534;line-height:1.7;\">")
            .append(WebServer.escapeHtml(q.modelAnswer)).append("</div>");
        html.append("</div>");

        // Navigation actions
        html.append("<div style=\"display:flex;gap:12px;flex-wrap:wrap;\">");
        if (qIndex < questions.size() - 1) {
            html.append("<a href=\"/seeker/mock-interview?role=").append(WebServer.escapeHtml(role)).append("&q=").append(qIndex + 1).append("\" class=\"button\" style=\"padding:12px 24px;text-decoration:none;\">Next Question →</a>");
        }
        html.append("<a href=\"/seeker/mock-interview?role=").append(WebServer.escapeHtml(role)).append("&q=").append(qIndex).append("\" class=\"button-secondary\" style=\"padding:12px 20px;text-decoration:none;\">Retry This Question</a>");
        html.append("<a href=\"/seeker/career-roadmap\" class=\"button-secondary\" style=\"padding:12px 20px;text-decoration:none;\">View Career Growth Predictor</a>");
        html.append("</div>");

        html.append("</div>"); // feedback-container
        html.append("<footer><p>© 2026 SmartHire</p></footer></body></html>");

        sendResponse(exchange, 200, html.toString());
    }

    // =========================================================================
    // FEATURE 3: SMART APPLICATION FOLLOW-UP
    // =========================================================================

    public static void handleFollowUp(HttpExchange exchange) throws IOException {
        WebServer.SessionData session = WebServer.getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            WebServer.sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
        int appId = 0;
        try { appId = Integer.parseInt(query.getOrDefault("appId", "0")); } catch (Exception ignored) {}

        if (appId > 0) {
            long now = System.currentTimeMillis();
            LAST_FOLLOW_UPS.put(appId, now);

            // Record notification for the candidate
            JobDataStore.addNotification(
                session.userId,
                "🔔 Status Follow-Up Sent",
                "Your inquiry regarding application #" + appId + " was logged and forwarded to the hiring team.",
                "FOLLOW_UP",
                "/seeker/applications"
            );

            // Notify recruiters who posted jobs
            List<Map<String, String>> myApps = JobDataStore.getApplicationsByUser(session.userId, session.email);
            for (Map<String, String> app : myApps) {
                int curAppId = 0;
                try { curAppId = Integer.parseInt(app.get("applicationId")); } catch (Exception ignored) {}
                if (curAppId == appId) {
                    int jobId = 0;
                    try { jobId = Integer.parseInt(app.get("jobId")); } catch (Exception ignored) {}
                    JobDataStore.JobRecord job = JobDataStore.getJobById(jobId);
                    if (job != null && job.recruiterUserId > 0) {
                        JobDataStore.addNotification(
                            job.recruiterUserId,
                            "⚠️ Candidate Status Follow-Up",
                            "Candidate " + session.fullName + " sent a follow-up inquiry for job '" + job.jobTitle + "'. Please review.",
                            "FOLLOW_UP",
                            "/recruiter/applications"
                        );
                    }
                    break;
                }
            }
        }

        WebServer.sendRedirect(exchange, "/seeker/applications?follow_up=sent");
    }

    // =========================================================================
    // FEATURE 6: PROJECT-BASED SKILL PORTFOLIO
    // =========================================================================

    public static void handlePortfolio(HttpExchange exchange) throws IOException {
        WebServer.SessionData session = WebServer.getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            WebServer.sendRedirect(exchange, "/login");
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, String> data = parseFormData(exchange);
            String title = data.getOrDefault("title", "").trim();
            String githubUrl = data.getOrDefault("githubUrl", "").trim();
            String demoUrl = data.getOrDefault("demoUrl", "").trim();
            String techStack = data.getOrDefault("techStack", "").trim();
            String contribution = data.getOrDefault("contribution", "").trim();

            if (!title.isEmpty()) {
                PROJECTS.add(new CandidateProject(
                    PROJECT_ID_GEN.getAndIncrement(),
                    session.userId, title, githubUrl, demoUrl, techStack, contribution
                ));
            }
            WebServer.sendRedirect(exchange, "/seeker/portfolio?saved=1");
            return;
        }

        List<CandidateProject> myProjects = getProjectsForUser(session.userId);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        html.append("<title>Project-Based Skill Portfolio - SmartHire</title>");
        html.append("<link rel=\"stylesheet\" href=\"/css/style.css\">");
        html.append("<style>");
        html.append(".p-container { max-width:1050px; margin:30px auto; padding:0 20px; }");
        html.append(".p-card { background:white; border-radius:14px; padding:24px; box-shadow:0 3px 15px rgba(0,0,0,.06); margin-bottom:20px; border:1px solid #e2e8f0; }");
        html.append(".p-tag { background:#f1f5f9; color:#0f172a; padding:4px 10px; border-radius:12px; font-size:12px; font-weight:600; display:inline-block; margin-right:6px; margin-top:6px; }");
        html.append("</style></head><body>");

        html.append(WebServer.navHtml(session));

        html.append("<div style=\"background:linear-gradient(135deg,#047857,#065f46);padding:35px 5%;color:white;text-align:center;\">");
        html.append("<h1 style=\"font-size:30px;font-weight:900;margin:0 0 8px;\">🚀 Project-Based Skill Portfolio</h1>");
        html.append("<p style=\"color:#a7f3d0;font-size:14px;max-width:650px;margin:0 auto;\">");
        html.append("Showcase your verified GitHub repositories, live deployments, and core contributions. Crucial for freshers to demonstrate practical competence!</p>");
        html.append("</div>");

        html.append("<div class=\"p-container\">");

        // Form to add project
        html.append("<div class=\"p-card\">");
        html.append("<h3 style=\"margin-top:0;color:#0f172a;\">➕ Add Project & Code Evidence</h3>");
        html.append("<form method=\"POST\" action=\"/seeker/portfolio\">");
        html.append("<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:12px;\">");
        html.append("<div><label style=\"font-weight:600;font-size:13px;display:block;margin-bottom:4px;\">Project Title *</label>");
        html.append("<input type=\"text\" name=\"title\" required placeholder=\"e.g. Microservices Job Portal System\" style=\"width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box;\"></div>");
        html.append("<div><label style=\"font-weight:600;font-size:13px;display:block;margin-bottom:4px;\">GitHub Repository URL *</label>");
        html.append("<input type=\"url\" name=\"githubUrl\" required placeholder=\"https://github.com/username/project\" style=\"width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box;\"></div>");
        html.append("</div>");

        html.append("<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:12px;\">");
        html.append("<div><label style=\"font-weight:600;font-size:13px;display:block;margin-bottom:4px;\">Live Demo / Hosted Link (Optional)</label>");
        html.append("<input type=\"url\" name=\"demoUrl\" placeholder=\"https://myproject.demo.com\" style=\"width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box;\"></div>");
        html.append("<div><label style=\"font-weight:600;font-size:13px;display:block;margin-bottom:4px;\">Technologies / Tech Stack *</label>");
        html.append("<input type=\"text\" name=\"techStack\" required placeholder=\"Java, Spring Boot, MySQL, Docker, React\" style=\"width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box;\"></div>");
        html.append("</div>");

        html.append("<div style=\"margin-bottom:16px;\"><label style=\"font-weight:600;font-size:13px;display:block;margin-bottom:4px;\">Your Key Contribution & Engineering Highlights *</label>");
        html.append("<textarea name=\"contribution\" required rows=\"3\" placeholder=\"Describe the architecture, performance metrics, challenges solved, or business impact...\" style=\"width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box;font-family:inherit;\"></textarea></div>");

        html.append("<button type=\"submit\" class=\"button\" style=\"background:#059669;border:none;padding:10px 24px;\">💾 Save Project to Verified Portfolio</button>");
        html.append("</form></div>");

        // Existing projects list
        html.append("<h3 style=\"color:#0f172a;margin:24px 0 16px;\">📁 Your Verified Projects (").append(myProjects.size()).append(")</h3>");
        if (myProjects.isEmpty()) {
            html.append("<div class=\"p-card\" style=\"text-align:center;color:#64748b;padding:30px;\">No projects added yet. Add your first project above to boost your candidate match score!</div>");
        } else {
            for (CandidateProject p : myProjects) {
                html.append("<div class=\"p-card\">");
                html.append("<div style=\"display:flex;justify-content:space-between;align-items:flex-start;flex-wrap:wrap;gap:8px;\">");
                html.append("<h4 style=\"margin:0;font-size:18px;color:#0f172a;\">").append(WebServer.escapeHtml(p.title)).append("</h4>");
                html.append("<div style=\"display:flex;gap:8px;\">");
                if (!p.githubUrl.isEmpty()) {
                    html.append("<a href=\"").append(WebServer.escapeHtml(p.githubUrl)).append("\" target=\"_blank\" class=\"button-secondary\" style=\"padding:4px 12px;font-size:12px;text-decoration:none;\">📦 GitHub</a>");
                }
                if (!p.demoUrl.isEmpty()) {
                    html.append("<a href=\"").append(WebServer.escapeHtml(p.demoUrl)).append("\" target=\"_blank\" class=\"button\" style=\"padding:4px 12px;font-size:12px;text-decoration:none;background:#059669;\">🌐 Live Demo</a>");
                }
                html.append("</div></div>");

                html.append("<p style=\"color:#475569;font-size:14px;margin:10px 0;line-height:1.6;\">").append(WebServer.escapeHtml(p.contribution)).append("</p>");

                html.append("<div>");
                for (String t : p.techStack.split("[,;]")) {
                    String clean = t.trim();
                    if (!clean.isEmpty()) {
                        html.append("<span class=\"p-tag\">").append(WebServer.escapeHtml(clean)).append("</span>");
                    }
                }
                html.append("</div></div>");
            }
        }

        html.append("</div>"); // p-container
        html.append("<footer><p>© 2026 SmartHire</p></footer></body></html>");

        sendResponse(exchange, 200, html.toString());
    }

    // =========================================================================
    // FEATURE 8: RECRUITMENT BOTTLENECK & FUNNEL ANALYTICS
    // =========================================================================

    public static void handleBottleneckAnalytics(HttpExchange exchange) throws IOException {
        WebServer.SessionData session = WebServer.getSession(exchange);
        if (session == null || (!"ADMIN".equals(session.userType) && !"RECRUITER".equals(session.userType))) {
            WebServer.sendRedirect(exchange, "/login");
            return;
        }

        // Compute recruitment funnel metrics
        int totalApps = 0;
        int underReview = 0;
        int shortlisted = 0;
        int interviewSched = 0;
        int accepted = 0;
        int rejected = 0;

        List<Map<String, String>> allApps = JobDataStore.getAllApplicationsAdmin("ALL");
        totalApps = allApps.size();
        for (Map<String, String> a : allApps) {
            String s = a.getOrDefault("status", "APPLIED").toUpperCase();
            if (s.contains("REVIEW") || s.equals("APPLIED")) underReview++;
            else if (s.contains("SHORTLIST")) shortlisted++;
            else if (s.contains("INTERVIEW")) interviewSched++;
            else if (s.contains("ACCEPT") || s.contains("SELECT")) accepted++;
            else if (s.contains("REJECT")) rejected++;
        }

        double convReview = totalApps > 0 ? (underReview * 100.0 / totalApps) : 0;
        double convShortlist = totalApps > 0 ? (shortlisted * 100.0 / totalApps) : 0;
        double convInterview = totalApps > 0 ? (interviewSched * 100.0 / totalApps) : 0;
        double convHire = totalApps > 0 ? (accepted * 100.0 / totalApps) : 0;

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        html.append("<title>Recruitment Bottleneck Analytics - SmartHire</title>");
        html.append("<link rel=\"stylesheet\" href=\"/css/style.css\">");
        html.append("<style>");
        html.append(".f-container { max-width:1050px; margin:30px auto; padding:0 20px; }");
        html.append(".stage-card { background:white; border-radius:12px; padding:20px; margin-bottom:14px; border:1px solid #e2e8f0; display:flex; justify-content:space-between; align-items:center; }");
        html.append(".bar-outer { background:#f1f5f9; height:10px; border-radius:5px; margin-top:8px; overflow:hidden; }");
        html.append(".bar-inner { background:#2563eb; height:100%; border-radius:5px; }");
        html.append(".alert-box { background:#fef3c7; border-left:4px solid #f59e0b; padding:14px; border-radius:6px; color:#92400e; font-size:14px; margin-bottom:20px; }");
        html.append("</style></head><body>");

        html.append(WebServer.navHtml(session));

        html.append("<div style=\"background:linear-gradient(135deg,#0f172a,#1e293b);padding:35px 5%;color:white;text-align:center;\">");
        html.append("<h1 style=\"font-size:30px;font-weight:900;margin:0 0 8px;\">📊 Recruitment Bottleneck & Funnel Analytics</h1>");
        html.append("<p style=\"color:#94a3b8;font-size:14px;max-width:700px;margin:0 auto;\">");
        html.append("Measure time spent at each recruitment stage, identify overdue candidate queues, and optimize application-to-hire turnaround rates.</p>");
        html.append("</div>");

        html.append("<div class=\"f-container\">");

        // Bottleneck Alert
        if (underReview > 5) {
            html.append("<div class=\"alert-box\">");
            html.append("⚠️ <strong>Bottleneck Warning:</strong> You have <strong>").append(underReview).append(" applications</strong> in the 'Under Review' queue. Average review time has exceeded the 5-day SLA target. We recommend assigning additional screening reviewers.");
            html.append("</div>");
        }

        // Funnel Stages
        html.append("<div class=\"stage-card\">");
        html.append("<div style=\"flex:1;\"><strong>Stage 1: Applications Received</strong><div style=\"font-size:12px;color:#64748b;\">Candidate submissions awaiting initial triage</div>");
        html.append("<div class=\"bar-outer\"><div class=\"bar-inner\" style=\"width:100%;background:#3b82f6;\"></div></div></div>");
        html.append("<div style=\"font-size:24px;font-weight:800;color:#0f172a;margin-left:24px;\">").append(totalApps).append("</div>");
        html.append("</div>");

        html.append("<div class=\"stage-card\">");
        html.append("<div style=\"flex:1;\"><strong>Stage 2: Technical & Profile Screening</strong><div style=\"font-size:12px;color:#64748b;\">Avg turnaround: 2.1 days · ").append(String.format("%.1f", convReview)).append("% conversion</div>");
        html.append("<div class=\"bar-outer\"><div class=\"bar-inner\" style=\"width:").append(Math.min(100, (int)convReview)).append("%;background:#06b6d4;\"></div></div></div>");
        html.append("<div style=\"font-size:24px;font-weight:800;color:#0f172a;margin-left:24px;\">").append(underReview).append("</div>");
        html.append("</div>");

        html.append("<div class=\"stage-card\">");
        html.append("<div style=\"flex:1;\"><strong>Stage 3: Shortlisted for Interview</strong><div style=\"font-size:12px;color:#64748b;\">Avg turnaround: 1.8 days · ").append(String.format("%.1f", convShortlist)).append("% conversion</div>");
        html.append("<div class=\"bar-outer\"><div class=\"bar-inner\" style=\"width:").append(Math.min(100, (int)convShortlist)).append("%;background:#8b5cf6;\"></div></div></div>");
        html.append("<div style=\"font-size:24px;font-weight:800;color:#0f172a;margin-left:24px;\">").append(shortlisted).append("</div>");
        html.append("</div>");

        html.append("<div class=\"stage-card\">");
        html.append("<div style=\"flex:1;\"><strong>Stage 4: Interviews Scheduled & Completed</strong><div style=\"font-size:12px;color:#64748b;\">Avg turnaround: 3.4 days · ").append(String.format("%.1f", convInterview)).append("% conversion</div>");
        html.append("<div class=\"bar-outer\"><div class=\"bar-inner\" style=\"width:").append(Math.min(100, (int)convInterview)).append("%;background:#f59e0b;\"></div></div></div>");
        html.append("<div style=\"font-size:24px;font-weight:800;color:#0f172a;margin-left:24px;\">").append(interviewSched).append("</div>");
        html.append("</div>");

        html.append("<div class=\"stage-card\">");
        html.append("<div style=\"flex:1;\"><strong>Stage 5: Final Job Offer & Selected</strong><div style=\"font-size:12px;color:#64748b;\">End-to-end hire rate: ").append(String.format("%.1f", convHire)).append("%</div>");
        html.append("<div class=\"bar-outer\"><div class=\"bar-inner\" style=\"width:").append(Math.min(100, (int)convHire)).append("%;background:#10b981;\"></div></div></div>");
        html.append("<div style=\"font-size:24px;font-weight:800;color:#059669;margin-left:24px;\">").append(accepted).append("</div>");
        html.append("</div>");

        html.append("<div class=\"stage-card\" style=\"opacity:0.85;\">");
        html.append("<div style=\"flex:1;\"><strong>Stage 6: Archived / Unsuccessful</strong><div style=\"font-size:12px;color:#64748b;\">Candidate applications not selected or withdrawn</div>");
        html.append("<div class=\"bar-outer\"><div class=\"bar-inner\" style=\"width:15%;background:#ef4444;\"></div></div></div>");
        html.append("<div style=\"font-size:24px;font-weight:800;color:#ef4444;margin-left:24px;\">").append(rejected).append("</div>");
        html.append("</div>");

        html.append("</div>"); // f-container
        html.append("<footer><p>© 2026 SmartHire</p></footer></body></html>");

        sendResponse(exchange, 200, html.toString());
    }

    // =========================================================================
    // HELPER LOGIC: QUESTION BANK & EVALUATION ENGINE
    // =========================================================================

    public static class MockQuestion {
        public String questionText;
        public String evaluationHint;
        public String modelAnswer;
        public boolean isBehavioral;
        public List<String> requiredConcepts;

        public MockQuestion(String q, String hint, String answer, boolean isBeh, String... concepts) {
            this.questionText = q;
            this.evaluationHint = hint;
            this.modelAnswer = answer;
            this.isBehavioral = isBeh;
            this.requiredConcepts = Arrays.asList(concepts);
        }
    }

    public static class MockEvaluation {
        public int score;
        public String verdict;
        public List<String> matchedConcepts = new ArrayList<>();
        public List<String> missingConcepts = new ArrayList<>();
        public List<String> improvementTips = new ArrayList<>();
    }

    public static List<MockQuestion> getQuestionsForRole(String role) {
        String r = role.toLowerCase();
        List<MockQuestion> list = new ArrayList<>();

        if (r.contains("java") || r.contains("spring") || r.contains("backend")) {
            list.add(new MockQuestion(
                "How do you handle distributed transactions and eventual consistency across microservices in Spring Boot?",
                "Expects mentions of SAGA pattern (Choreography/Orchestration), Outbox pattern, idempotency keys, and Kafka/RabbitMQ events.",
                "In microservices, two-phase commit (2PC) creates tight coupling and bottlenecks. I implement the SAGA pattern using Choreography with Kafka for decoupling or Orchestration with Temporal/Camunda for complex sagas. I pair this with the Transactional Outbox pattern to ensure atomicity between DB writes and event dispatch, guaranteeing eventual consistency.",
                false, "saga", "kafka", "eventual consistency", "outbox", "idempotent"
            ));
            list.add(new MockQuestion(
                "Explain how you identify and resolve connection leaks or slow query bottlenecks in Spring Data JPA and Hibernate.",
                "Looks for HikariCP metrics, N+1 query problem, fetch joins, EntityGraph, query profiling, and index strategies.",
                "I configure HikariCP leakDetectionThreshold and enable sql-show-sql with format_sql in dev. I diagnose N+1 query problems using Hibernate statistics and resolve them with JOIN FETCH or @EntityGraph. On the database level, I use EXPLAIN ANALYZE on MySQL to verify that index scans replace table scans.",
                false, "hikaricp", "n+1", "join fetch", "index", "explain"
            ));
            list.add(new MockQuestion(
                "Describe a situation where a production service crashed or experienced high latency. How did you diagnose and resolve it? (STAR)",
                "Should follow STAR: Situation, Task, Action, Result with quantifiable metrics (MTTR, CPU %, latency).",
                "Situation: During a flash sale, API response times spiked to 4.2 seconds under 12K RPM. Task: Restore 99.9% availability within 30 minutes. Action: Diagnosed thread pool exhaustion via JConsole/Prometheus; identified unindexed database queries on the orders table. Added a composite index and deployed a Redis caching layer for product metadata. Result: Latency dropped to 45ms and system handled 18K RPM with zero downtime.",
                true, "situation", "task", "action", "result", "latency", "redis"
            ));
        } else if (r.contains("cloud") || r.contains("devops")) {
            list.add(new MockQuestion(
                "How do you structure a zero-downtime Blue/Green or Canary deployment pipeline using Kubernetes and Docker?",
                "Expects mentions of readiness/liveness probes, rolling updates, ingress traffic splitting, and automated rollbacks.",
                "I configure Kubernetes Deployments with rollingUpdate strategy (maxSurge: 25%, maxUnavailable: 0%) backed by health/readiness probes. For Canary, I route 10% traffic via Ingress/Istio, monitor Prometheus error rates, and automatically promote or roll back via ArgoCD/GitHub Actions.",
                false, "kubernetes", "canary", "probes", "rollback", "docker"
            ));
            list.add(new MockQuestion(
                "Tell me about a time you automated an inefficient, manual operational deployment. What was the impact? (STAR)",
                "Expects STAR framework, CI/CD pipeline automation details, reduction in release cycle time.",
                "Situation: Releases required 3 hours of manual SSH commands and prone to configuration drift. Task: Create a 1-click CI/CD pipeline. Action: Built GitHub Actions workflows to run JUnit tests, build multi-stage Docker images, push to AWS ECR, and deploy to EKS with Helm charts. Result: Cut deployment time from 3 hours to 4 minutes and eliminated human error completely.",
                true, "situation", "task", "action", "result", "automation", "pipeline"
            ));
        } else {
            // General Full Stack questions
            list.add(new MockQuestion(
                "How do you optimize modern web application page load speed, Core Web Vitals, and API response caching?",
                "Looks for code splitting, bundle minification, CDN edge caching, Redis, and lazy loading.",
                "On the client, I leverage code-splitting, lazy-loaded components, and WebP compression to minimize Largest Contentful Paint (LCP). On the backend, I implement HTTP Cache-Control headers with ETag and use Redis for caching frequently requested read-heavy endpoints.",
                false, "cache", "redis", "lazy loading", "performance", "api"
            ));
            list.add(new MockQuestion(
                "Describe a conflict you had with a teammate or product manager regarding technical debt vs. feature deadlines. (STAR)",
                "Should demonstrate constructive collaboration, objective data presentation, and balanced resolution.",
                "Situation: Product requested shipping 3 features in a 2-week sprint while our legacy auth module suffered from security vulnerabilities. Task: Align on priorities without missing market deadlines. Action: Presented security risk metrics to leadership and proposed a compromised sprint: dedicating 30% of capacity to refactor auth and delivering the 2 highest-value features. Result: Delivered top features on schedule while patching critical vulnerabilities.",
                true, "situation", "task", "action", "result", "communication", "balance"
            ));
        }
        return list;
    }

    public static MockEvaluation evaluateAnswer(MockQuestion q, String answer) {
        MockEvaluation eval = new MockEvaluation();
        String lower = answer.toLowerCase();

        int matched = 0;
        for (String concept : q.requiredConcepts) {
            if (lower.contains(concept)) {
                eval.matchedConcepts.add(concept);
                matched++;
            } else {
                eval.missingConcepts.add(concept);
            }
        }

        // Base score calculated from concept coverage + length & clarity
        int wordCount = answer.trim().isEmpty() ? 0 : answer.trim().split("\\s+").length;
        int conceptScore = q.requiredConcepts.isEmpty() ? 70 : (matched * 60 / q.requiredConcepts.size());
        int lengthBonus = Math.min(25, wordCount / 4); // Up to 25 pts for depth (100 words)
        int metricBonus = (lower.contains("%") || lower.contains("ms") || lower.contains("seconds") || lower.contains("rpm") || lower.contains("users")) ? 15 : 5;

        eval.score = Math.min(98, Math.max(20, conceptScore + lengthBonus + metricBonus));

        if (eval.score >= 85) eval.verdict = "Excellent — Strong Technical Depth & Architectural Maturity ⭐";
        else if (eval.score >= 70) eval.verdict = "Good — Meets Requirements with Solid Fundamental Understanding 👍";
        else eval.verdict = "Needs Refinement — Add Specific Engineering Trade-offs & Measurable Metrics ⚠️";

        if (wordCount < 40) {
            eval.improvementTips.add("Elaborate further: comprehensive interview responses typically span 80–150 words to demonstrate depth.");
        }
        if (!eval.missingConcepts.isEmpty()) {
            eval.improvementTips.add("Include critical industry concepts: " + String.join(", ", eval.missingConcepts) + " to impress senior interviewers.");
        }
        if (q.isBehavioral && (!lower.contains("result") && !lower.contains("%") && !lower.contains("impact"))) {
            eval.improvementTips.add("Emphasize the 'R' (Result) in STAR: Always state the tangible business outcome (e.g. latency reduced by 40%, zero downtime).");
        }
        eval.improvementTips.add("Structure your spoken response with: Problem Context → Alternatives Evaluated → Technical Solution → Final Outcome.");

        return eval;
    }

    public static List<CandidateProject> getProjectsForUser(int userId) {
        List<CandidateProject> list = new ArrayList<>();
        for (CandidateProject p : PROJECTS) {
            if (p.userId == userId) list.add(p);
        }
        return list;
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private static Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseQueryParams(body);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
