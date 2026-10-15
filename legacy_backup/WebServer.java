import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Job Portal System - Complete Web Server
 * ========================================
 * Full-featured job portal with:
 *  - Job Seeker: Register, Login, Search Jobs, Location Search, Apply, Dashboard, My Applications
 *  - Recruiter: Login, Dashboard, Post Job, Edit Job, Delete Job, View Applications, Shortlist/Reject
 *  - Admin: Login, Dashboard, Manage Users, Manage Jobs, Manage Recruiters, Statistics
 *
 * Technology: Java + JDBC + MySQL (with in-memory fallback)
 */
public class WebServer {

    private static final int PORT = 8080;
    private static final String ADMIN_ACCESS_PASSWORD = "admin";
    private static final String RECRUITER_ACCESS_PASSWORD = "recruiter";

    // ============================================================
    // SESSION MANAGEMENT (simple cookie-based in-memory sessions)
    // ============================================================

    private static final Map<String, SessionData> SESSIONS = new ConcurrentHashMap<>();

    static class SessionData {
        int userId;
        String fullName;
        String email;
        String userType;
        String companyName; // for recruiters
        long createdAt;

        SessionData(int userId, String fullName, String email, String userType, String companyName) {
            this.userId = userId;
            this.fullName = fullName;
            this.email = email;
            this.userType = userType;
            this.companyName = companyName;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private static String generateSessionId() {
        return SecurityManager.generateSecureToken(32); // 256-bit entropy (OWASP Session Management)
    }

    private static SessionData getSession(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String cookieHeader : cookies) {
            for (String cookie : cookieHeader.split(";")) {
                String[] kv = cookie.trim().split("=", 2);
                if (kv.length == 2 && "JPSESSION".equals(kv[0].trim())) {
                    return SESSIONS.get(kv[1].trim());
                }
            }
        }
        return null;
    }

    private static void setSession(HttpExchange exchange, String sessionId) {
        exchange.getResponseHeaders().add("Set-Cookie",
            "JPSESSION=" + sessionId + "; Path=/; HttpOnly; SameSite=Strict");
    }

    private static void clearSession(HttpExchange exchange) {
        String sid = getSessionId(exchange);
        if (sid != null) SESSIONS.remove(sid);
        exchange.getResponseHeaders().add("Set-Cookie",
            "JPSESSION=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
    }

    private static String getSessionId(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String cookieHeader : cookies) {
            for (String cookie : cookieHeader.split(";")) {
                String[] kv = cookie.trim().split("=", 2);
                if (kv.length == 2 && "JPSESSION".equals(kv[0].trim())) {
                    return kv[1].trim();
                }
            }
        }
        return null;
    }

    private static void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    // ============================================================
    // SERVER START
    // ============================================================

    public static void main(String[] args) throws Exception {
        JobDataStore.initializeDatabaseIfAvailable();

        SecurityManager.initializeSecuritySchema();
        TrustMatchEngine.initializeTrustMatchSchema(); // Create audit log + password reset tables

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Public pages
        server.createContext("/", WebServer::homePage);
        server.createContext("/jobs", WebServer::jobsPage);
        server.createContext("/apply", WebServer::applyPage);
        server.createContext("/register", WebServer::registerPage);
        server.createContext("/login", WebServer::loginPage);
        server.createContext("/logout", WebServer::logoutPage);
        server.createContext("/css/style.css", WebServer::cssPage);

        server.createContext("/forgot-password", WebServer::forgotPasswordPage);

        server.createContext("/reset-password", WebServer::resetPasswordPage);

        // Job Seeker Dashboard & Intelligence
        server.createContext("/seeker/dashboard", WebServer::seekerDashboard);
        server.createContext("/seeker/applications", WebServer::seekerApplications);
        server.createContext("/seeker/applications/withdraw", WebServer::seekerWithdrawApplication);
        server.createContext("/seeker/profile", WebServer::seekerProfile);
        server.createContext("/seeker/saved", WebServer::seekerSavedJobs);
        server.createContext("/seeker/saved/toggle", WebServer::seekerToggleSavedJob);
        server.createContext("/seeker/resume/upload", WebServer::seekerResumeUpload);
        server.createContext("/seeker/resume/view", WebServer::seekerResumeView);
        server.createContext("/seeker/matching-jobs", WebServer::seekerMatchingJobs);
        server.createContext("/seeker/similar-jobs", WebServer::seekerSimilarJobs);
        server.createContext("/seeker/skill-gap", WebServer::seekerSkillGap);
        server.createContext("/seeker/career-path", WebServer::seekerCareerPath);
        server.createContext("/seeker/notifications", WebServer::seekerNotifications);
        server.createContext("/seeker/notifications/read", WebServer::seekerMarkNotificationsRead);
        server.createContext("/seeker/interviews", WebServer::seekerInterviews);
        server.createContext("/report-job", WebServer::reportJob);

        // Recruiter Dashboard & Two-Way Matching
        server.createContext("/recruiter/dashboard", WebServer::recruiterDashboard);
        server.createContext("/recruiter/jobs", WebServer::recruiterJobs);
        server.createContext("/recruiter/jobs/add", WebServer::recruiterAddJob);
        server.createContext("/recruiter/jobs/edit", WebServer::recruiterEditJob);
        server.createContext("/recruiter/jobs/delete", WebServer::recruiterDeleteJob);
        server.createContext("/recruiter/jobs/status", WebServer::recruiterToggleJobStatus);
        server.createContext("/recruiter/applications", WebServer::recruiterApplications);
        server.createContext("/recruiter/applications/update", WebServer::recruiterUpdateApplication);
        server.createContext("/recruiter/profile", WebServer::recruiterProfile);
        server.createContext("/recruiter/candidates", WebServer::recruiterCandidates);
        server.createContext("/recruiter/invite", WebServer::recruiterInviteCandidate);
        server.createContext("/recruiter/interview/schedule", WebServer::recruiterScheduleInterview);

        // Admin Dashboard & Trust & Safety
        server.createContext("/admin/dashboard", WebServer::adminDashboard);
        server.createContext("/admin/users", WebServer::adminUsers);
        server.createContext("/admin/users/delete", WebServer::adminDeleteUser);
        server.createContext("/admin/users/toggle-active", WebServer::adminToggleUserActive);
        server.createContext("/admin/jobs", WebServer::adminJobs);
        server.createContext("/admin/jobs/delete", WebServer::adminDeleteJob);
        server.createContext("/admin/jobs/status", WebServer::adminUpdateJobStatus);
        server.createContext("/admin/recruiters", WebServer::adminRecruiters);
        server.createContext("/admin/applications", WebServer::adminApplications);
        server.createContext("/admin/applications/delete", WebServer::adminDeleteApplication);
        server.createContext("/admin/reports", WebServer::adminReports);
        server.createContext("/admin/reports/action", WebServer::adminReportAction);

        server.createContext("/admin/audit-log", WebServer::adminAuditLog);

        server.createContext("/trustmatch", WebServer::trustMatchDashboard);

        server.createContext("/trust/verify", WebServer::trustVerifyCompany);

        server.createContext("/trust/scam-check", WebServer::trustScamCheck);

        server.createContext("/seeker/career-roadmap", WebServer::seekerCareerRoadmap);

        server.createContext("/seeker/matches-v2", WebServer::seekerMatchesV2);

        server.createContext("/recruiter/pipeline", WebServer::recruiterPipelineIntelligence);

        server.setExecutor(null);

        System.out.println();
        System.out.println("============================================================");
        System.out.println("     SMARTHIRE - RESUME INTELLIGENCE & JOB MATCHING PORTAL");
        System.out.println("============================================================");
        System.out.println("Server started on: http://localhost:8080");
        System.out.println();
        System.out.println("Default Login Accounts:");
        System.out.println("  Job Seeker: keerthi@example.com  / keerthi123 (Keerthivasan)");
        System.out.println("  Job Seeker: seeker@example.com   / seeker123  (Rahul Sharma)");
        System.out.println("  Recruiter : recruit@abc.com      / recruit123 (TechCorp Solutions)");
        System.out.println("  Admin     : admin@jobportal.com  / admin123   (System Admin)");
        System.out.println("============================================================");
        server.start();

        try {
            if (java.awt.Desktop.isDesktopSupported() &&
                java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("http://localhost:8080"));
            } else {
                new ProcessBuilder("cmd", "/c", "start", "http://localhost:8080").start();
            }
        } catch (Exception ignored) {}
    }

    // ============================================================
    // SHARED NAV HTML
    // ============================================================

    private static String navHtml(SessionData session) {
        StringBuilder nav = new StringBuilder();
        int unreadNotifs = 0;
        if (session != null && "JOB_SEEKER".equals(session.userType)) {
            unreadNotifs = JobDataStore.getUnreadNotificationCount(session.userId);
        }

        nav.append("""
            <nav>
                <div class="logo">
                    <a href="/" style="display:flex;align-items:center;gap:10px;text-decoration:none;color:inherit;">
                        <span class="logo-icon" style="font-size:24px;">🚀</span>
                        <div style="line-height:1.2;">
                            <div style="font-weight:800;font-size:20px;letter-spacing:-0.5px;color:#1e293b;">SmartHire</div>
                            <div style="font-size:9px;font-weight:700;color:#0284c7;letter-spacing:0.5px;">RESUME-TO-CAREER MATCHING</div>
                        </div>
                    </a>
                </div>
                <div class="nav-links">
                    <a href="/">Home</a>
                    <a href="/jobs">Find Jobs</a>
            """);

        if (session == null) {
            nav.append("""
                        <a href="/login">Login</a>
                        <a href="/register">Register</a>
                        <a href="/register" class="btn-post-job">POST A JOB</a>
                    </div>
                </nav>
                """);
        } else if ("ADMIN".equals(session.userType)) {
            nav.append("""
                        <a href="/admin/dashboard" class="nav-dashboard-btn">⚙️ Admin Panel</a>
                        <a href="/admin/reports" style="color:#dc2626;font-weight:600;">🛡️ Job Reports</a>

                        <a href="/admin/audit-log" style="color:#7c3aed;font-weight:600;">🔒 Audit Log</a>
                        <a href="/logout" class="btn-logout">Logout</a>
                    </div>
                </nav>
                """);
        } else if ("RECRUITER".equals(session.userType)) {
            nav.append("""
                        <a href="/recruiter/dashboard" class="nav-dashboard-btn">📋 Dashboard</a>
                        <a href="/recruiter/candidates" style="color:#0284c7;font-weight:700;">🔍 Discover Candidates</a>
                        <a href="/recruiter/jobs/add" class="btn-post-job">POST A JOB</a>
                        <a href="/logout" class="btn-logout">Logout</a>
                    </div>
                </nav>
                """);
        } else {
            String badgeHtml = unreadNotifs > 0 ? " <span class=\"nav-notif-pill\">" + unreadNotifs + "</span>" : "";
            nav.append("""
                        <a href="/trustmatch" style="color:#059669;font-weight:700;background:#ecfdf5;padding:6px 12px;border-radius:6px;border:1px solid #a7f3d0;">🛡️ TrustMatch AI</a>

                        <a href="/seeker/matching-jobs" style="color:#059669;font-weight:700;background:#ecfdf5;padding:6px 12px;border-radius:6px;border:1px solid #a7f3d0;">🎯 Jobs For My Resume</a>
                        <a href="/seeker/career-path">🛣️ Career Path</a>
                        <a href="/seeker/dashboard" class="nav-dashboard-btn">👤 Dashboard</a>
                        <a href="/seeker/notifications" style="position:relative;">🔔 Alerts%s</a>
                        <a href="/logout" class="btn-logout">Logout</a>
                    </div>
                </nav>
                """.formatted(badgeHtml));
        }
        return nav.toString();
    }

    // ============================================================
    // HOME PAGE
    // ============================================================

    private static void homePage(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>JobPortal - Find the job that fits your life</title>
                <link rel="stylesheet" href="/css/style.css">
            </head>
            <body>
            """ + navHtml(session) + """

            <section class="hero-modern">
                <div class="hero-text-col">
                    <h1>Find the<br>job that fits your life</h1>
                    <p class="hero-subtext">
                        Discover thousands of opportunities and take the next step in your career.
                        Explore top hiring companies, trending roles, and direct application paths.
                    </p>
                    <form action="/jobs" method="GET" class="search-box">
                        <div class="search-field">
                            <span class="field-icon">🔍</span>
                            <input type="text" name="keyword" placeholder="Job Title, Company, or Skill">
                        </div>
                        <div class="search-field">
                            <span class="field-icon">📍</span>
                            <input type="text" name="location" placeholder="Location (City, State...)">
                        </div>
                        <button type="submit" class="btn-search-icon" title="Search Jobs">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                                <circle cx="11" cy="11" r="8"></circle>
                                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                            </svg>
                        </button>
                    </form>
                </div>
                <div class="hero-image-col">
                    <svg width="480" height="340" viewBox="0 0 500 360" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <rect x="20" y="40" width="460" height="300" rx="16" fill="#F8FAFC" />
                        <rect x="180" y="220" width="260" height="90" rx="10" fill="#E2E8F0" />
                        <rect x="195" y="200" width="120" height="25" rx="4" fill="#94A3B8" />
                        <rect x="220" y="140" width="70" height="50" rx="6" fill="#1E293B" />
                        <rect x="225" y="145" width="60" height="40" rx="3" fill="#38BDF8" />
                        <rect x="250" y="190" width="10" height="12" fill="#64748B" />
                        <circle cx="410" cy="100" r="22" fill="#FFFFFF" stroke="#CBD5E1" stroke-width="3" />
                        <polyline points="410,88 410,100 418,100" stroke="#475569" stroke-width="3" stroke-linecap="round" />
                        <rect x="290" y="70" width="90" height="65" rx="6" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2" />
                        <polyline points="305,115 325,95 345,105 365,85" stroke="#22C55E" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
                        <circle cx="435" cy="270" r="18" fill="#EF4444" />
                        <path d="M435,252 C420,225 410,210 420,195 C435,210 435,235 435,252 Z" fill="#22C55E" />
                        <path d="M435,252 C450,225 460,210 450,195 C435,210 435,235 435,252 Z" fill="#16A34A" />
                        <circle cx="105" cy="115" r="18" fill="#FCA5A5" />
                        <path d="M90 140 C90 135 120 135 120 140 L125 210 L112 210 L105 170 L98 210 L85 210 Z" fill="#1E3A8A" />
                        <path d="M82 145 C75 160 80 180 90 190" stroke="#1E3A8A" stroke-width="8" stroke-linecap="round" />
                        <circle cx="345" cy="130" r="16" fill="#FDBA74" />
                        <path d="M330 152 C330 150 360 150 360 152 L365 210 L325 210 Z" fill="#9333EA" />
                    </svg>
                </div>
            </section>

            <section class="categories-section">
                <h2>Browse by Category</h2>
                <p class="section-subtitle">Most popular job categories on our portal</p>
                <div class="categories-grid">
                    <a href="/jobs?keyword=Java" class="category-card">
                        <div class="cat-icon cat-blue">☕</div>
                        <h3>Java / Spring Boot</h3>
                        <p>Software Engineering</p>
                    </a>
                    <a href="/jobs?keyword=Developer" class="category-card">
                        <div class="cat-icon cat-purple">💻</div>
                        <h3>Technologies</h3>
                        <p>Full Stack, Backend, Frontend</p>
                    </a>
                    <a href="/jobs?keyword=Finance" class="category-card">
                        <div class="cat-icon cat-green">🏛️</div>
                        <h3>Finance</h3>
                        <p>Analyst, Accounting, Banking</p>
                    </a>
                    <a href="/jobs?keyword=Data" class="category-card">
                        <div class="cat-icon cat-cyan">📊</div>
                        <h3>Data Science</h3>
                        <p>ML, Analytics, BI</p>
                    </a>
                    <a href="/jobs?location=Bangalore" class="category-card">
                        <div class="cat-icon cat-blue">📍</div>
                        <h3>Jobs in Bangalore</h3>
                        <p>Location-wise search</p>
                    </a>
                    <a href="/jobs?location=Chennai" class="category-card">
                        <div class="cat-icon cat-green">📍</div>
                        <h3>Jobs in Chennai</h3>
                        <p>Location-wise search</p>
                    </a>
                </div>
            </section>

            <section class="how-it-works">
                <h2>How It Works</h2>
                <p class="section-subtitle">Simple steps to find your next opportunity</p>
                <div class="steps-grid">
                    <div class="step-card">
                        <div class="step-num">1</div>
                        <h3>Create Account</h3>
                        <p>Register as a Job Seeker, Recruiter, or Admin and set up your profile.</p>
                    </div>
                    <div class="step-card">
                        <div class="step-num">2</div>
                        <h3>Search Jobs</h3>
                        <p>Search by title, skill, or location to find the perfect opportunity.</p>
                    </div>
                    <div class="step-card">
                        <div class="step-num">3</div>
                        <h3>Apply & Track</h3>
                        <p>Apply with one click and track your application status in your dashboard.</p>
                    </div>
                    <div class="step-card">
                        <div class="step-num">4</div>
                        <h3>Get Hired</h3>
                        <p>Recruiters review your profile and contact you for interviews.</p>
                    </div>
                </div>
            </section>

            <footer>
                <p>© 2026 JobPortal System | Built with Java + Hibernate + MySQL | Technology: Spring Boot Architecture</p>
            </footer>
            </body>
            </html>
            """;

        sendResponse(exchange, html);
    }

    // ============================================================
    // JOBS PAGE (Search + Location Filter)
    // ============================================================

    private static void jobsPage(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
        String keyword = queryParams.get("keyword");
        String location = queryParams.get("location");
        String kwVal = keyword != null ? escapeHtml(keyword.trim()) : "";
        String locVal = location != null ? escapeHtml(location.trim()) : "";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Find Jobs - JobPortal</title>");
        html.append("<link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));

        html.append("""
            <section class="job-search-banner">
                <div class="job-search-content">
                    <div class="job-search-title">
                        <span>JOB SEARCH</span>
                        <svg class="search-magnifier" width="38" height="38" viewBox="0 0 24 24" fill="none" stroke="#1e293b" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="11" cy="11" r="7"></circle>
                            <line x1="21" y1="21" x2="16.5" y2="16.5"></line>
                        </svg>
                    </div>
                    <form action="/jobs" method="GET" class="job-search-form">
                        <div class="job-search-input-wrap">
                            <span class="search-icon">🔍</span>
            """);
        html.append("<input type=\"text\" name=\"keyword\" placeholder=\"Job Title, Company, or Skill...\" value=\"").append(kwVal).append("\">");
        html.append("""
                        </div>
                        <div class="job-search-input-wrap loc-wrap">
                            <span class="search-icon">📍</span>
            """);
        html.append("<input type=\"text\" name=\"location\" placeholder=\"Location (City, State...)\" value=\"").append(locVal).append("\">");
        html.append("""
                        </div>
                        <button type="submit" class="job-search-btn">Search</button>
                    </form>
                    <div class="location-quick-links">
                        <span>Quick Location Search:</span>
                        <a href="/jobs?location=Bangalore">Bangalore</a>
                        <a href="/jobs?location=Chennai">Chennai</a>
                        <a href="/jobs?location=Mumbai">Mumbai</a>
                        <a href="/jobs?location=Hyderabad">Hyderabad</a>
                        <a href="/jobs?location=Delhi">Delhi</a>
                        <a href="/jobs?location=Pune">Pune</a>
                        <a href="/jobs?location=Coimbatore">Coimbatore</a>
                    </div>
                </div>
            </section>
            <main class="jobs-container">
            """);

        if ((keyword != null && !keyword.trim().isEmpty()) || (location != null && !location.trim().isEmpty())) {
            html.append("<div class=\"filter-notice\"><span>Showing results ");
            if (keyword != null && !keyword.trim().isEmpty())
                html.append("for <strong>\"").append(escapeHtml(keyword.trim())).append("\"</strong> ");
            if (location != null && !location.trim().isEmpty())
                html.append("in <strong>\"").append(escapeHtml(location.trim())).append("\"</strong>");
            html.append("</span><a href=\"/jobs\" class=\"clear-filter-btn\">✕ Clear Filter</a></div>");
        }

        List<JobDataStore.JobRecord> jobsList = JobDataStore.getJobs(keyword, location);

        if (jobsList.isEmpty()) {
            html.append("""
                <div class="empty">
                    <h2>No Jobs Found</h2>
                    <p>No matching positions found for your search. Try different keywords or location.</p>
                    <br>
                    <a href="/jobs" class="button" style="text-decoration:none; padding: 10px 20px;">View All Jobs</a>
                </div>
                """);
        } else {
            html.append("<div class=\"jobs-count\">").append(jobsList.size()).append(" job(s) found</div>");
            for (JobDataStore.JobRecord job : jobsList) {
                html.append("<div class='job-card'>");
                html.append("<h2>").append(escapeHtml(job.jobTitle)).append("</h2>");
                html.append("<h3>🏢 ").append(escapeHtml(job.companyName)).append("</h3>");
                html.append("<p>").append(escapeHtml(job.description)).append("</p>");
                html.append("<div class='job-info'>");
                html.append("<span>📍 ").append(escapeHtml(job.location)).append("</span>");
                html.append("<span>💼 ").append(escapeHtml(job.jobType)).append("</span>");
                html.append("<span>💰 ").append(escapeHtml(job.salary)).append("</span>");
                if (job.experienceRequired != null && !job.experienceRequired.isEmpty())
                    html.append("<span>🎯 ").append(escapeHtml(job.experienceRequired)).append("</span>");
                html.append("</div>");
                html.append("<p><strong>Skills:</strong> ").append(escapeHtml(job.skillsRequired)).append("</p>");
                html.append("<p><strong>Qualification:</strong> ").append(escapeHtml(job.qualification)).append("</p>");

                html.append("<div class=\"card-actions\">");
                if (session != null && "JOB_SEEKER".equals(session.userType)) {
                    html.append(String.format(
                        "<button class=\"apply-button\" data-job-id=\"%d\" data-job-title=\"%s\" data-company-name=\"%s\" onclick=\"openModalFromBtn(this)\">Apply Now</button>",
                        job.jobId, escapeHtml(job.jobTitle), escapeHtml(job.companyName)));
                    boolean saved = JobDataStore.isJobSaved(session.userId, job.jobId);
                    html.append("<form method=\"POST\" action=\"/seeker/saved/toggle\" style=\"display:inline\">"
                        + "<input type=\"hidden\" name=\"jobId\" value=\"" + job.jobId + "\">"
                        + "<input type=\"hidden\" name=\"returnTo\" value=\"/jobs\">"
                        + "<button type=\"submit\" class=\"button-secondary\">" + (saved ? "★ Saved" : "☆ Save Job") + "</button></form>");
                } else if (session == null) {
                    html.append("<a href=\"/login\" class=\"apply-button\" style=\"text-decoration:none;\">Login to Apply</a>");
                }
                html.append(String.format("<a href=\"/apply?job_id=%d\" class=\"button-secondary\">View Details</a>", job.jobId));
                html.append("</div></div>");
            }
        }

        html.append("""
            </main>

            <div id="applyModal" class="modal">
                <div class="modal-content">
                    <span class="close-btn" onclick="closeModal()">&times;</span>
                    <h2 id="modalJobTitle" style="color: #123b8f; margin-bottom: 5px;">Apply for Job</h2>
                    <h3 id="modalCompanyName" class="modal-subtitle">Company</h3>
                    <form method="POST" action="/apply" class="modal-form">
                        <input type="hidden" id="modalJobId" name="jobId" value="">
                        <label>Full Name *</label>
                        <input type="text" name="fullName" required placeholder="Enter your full name">
                        <label>Email Address *</label>
                        <input type="email" name="email" required placeholder="Enter your email address">
                        <label>Phone Number</label>
                        <input type="text" name="phone" placeholder="Enter phone number">
                        <label>Cover Letter / Additional Notes</label>
                        <textarea name="coverLetter" rows="4" placeholder="Why are you a good fit for this role?"></textarea>
                        <button type="submit" class="submit-btn">Submit Application</button>
                    </form>
                </div>
            </div>

            <script>
                function openModalFromBtn(btn) {
                    var jobId = btn.getAttribute('data-job-id');
                    var jobTitle = btn.getAttribute('data-job-title');
                    var companyName = btn.getAttribute('data-company-name');
                    document.getElementById('modalJobId').value = jobId;
                    document.getElementById('modalJobTitle').innerText = 'Apply for ' + jobTitle;
                    document.getElementById('modalCompanyName').innerText = companyName;
                    document.getElementById('applyModal').style.display = 'flex';
                }
                function closeModal() {
                    document.getElementById('applyModal').style.display = 'none';
                }
                window.onclick = function(event) {
                    var modal = document.getElementById('applyModal');
                    if (event.target == modal) modal.style.display = 'none';
                }
            </script>

            <footer>
                <p>© 2026 JobPortal System</p>
            </footer>
            </body></html>
            """);

        sendResponse(exchange, html.toString());
    }

    // ============================================================
    // APPLY PAGE
    // ============================================================

    private static void applyPage(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseFormData(body);

            String jobIdStr = data.get("jobId");
            if (jobIdStr == null || jobIdStr.isEmpty()) jobIdStr = data.get("job_id");
            String fullName = data.get("fullName");
            String email = data.get("email");
            String phone = data.get("phone");
            String coverLetter = data.get("coverLetter");

            if (session != null && "JOB_SEEKER".equals(session.userType)) {
                fullName = session.fullName;
                email = session.email;
            }

            if (jobIdStr == null || jobIdStr.isEmpty()) {
                sendResponse(exchange, errorPage("Invalid Request", "No Job ID provided.", "/jobs"));
                return;
            }

            int jobId;
            try { jobId = Integer.parseInt(jobIdStr); }
            catch (NumberFormatException e) {
                sendResponse(exchange, errorPage("Invalid Request", "Invalid Job ID.", "/jobs"));
                return;
            }

            if (fullName == null || fullName.trim().isEmpty() || email == null || email.trim().isEmpty()) {
                sendResponse(exchange, errorPage("Validation Error", "Full name and email are required.", "/apply?job_id=" + jobId));
                return;
            }

            JobDataStore.JobRecord targetJob = JobDataStore.getJobById(jobId);
            if (targetJob == null || !"OPEN".equals(targetJob.status)) {
                sendResponse(exchange, errorPage("Job Unavailable", "This job is closed or no longer exists.", "/jobs"));
                return;
            }

            int userId = (session != null) ? session.userId : -1;
            JobDataStore.ApplicationResult applicationResult = JobDataStore.saveApplicationFull(
                jobId, userId, fullName.trim(), email.trim(), phone != null ? phone.trim() : "",
                coverLetter != null ? coverLetter.trim() : "");
            if (applicationResult == JobDataStore.ApplicationResult.DUPLICATE) {
                sendResponse(exchange, errorPage("Already Applied", "You have already applied for this job.", "/seeker/applications"));
                return;
            }
            if (applicationResult != JobDataStore.ApplicationResult.SAVED) {
                sendResponse(exchange, errorPage("Job Unavailable", "This job cannot accept applications now.", "/jobs"));
                return;
            }

            String successHtml = """
                <!DOCTYPE html><html>
                <head><title>Application Submitted</title><link rel="stylesheet" href="/css/style.css"></head>
                <body>
                """ + navHtml(session) + """
                <div class="success-page">
                    <div class="success-box">
                        <div class="success-icon">✅</div>
                        <h1>Application Submitted!</h1>
                        <p>Your application for <strong>%s</strong> at <strong>%s</strong> has been received.</p>
                        <p><strong>Applicant:</strong> %s (%s)</p>
                        <p><strong>Status:</strong> <span style="color:#22c55e; font-weight:bold;">APPLIED</span></p>
                        <br>
                        <div style="display:flex; gap:12px; justify-content:center; flex-wrap:wrap;">
                            <a class="button" href="/jobs">Browse More Jobs</a>
                            %s
                        </div>
                    </div>
                </div>
                </body></html>
                """.formatted(
                escapeHtml(targetJob.jobTitle),
                escapeHtml(targetJob.companyName),
                escapeHtml(fullName),
                escapeHtml(email),
                (session != null && "JOB_SEEKER".equals(session.userType))
                    ? "<a class=\"button\" href=\"/seeker/applications\" style=\"background:#22c55e;\">My Applications</a>"
                    : ""
            );

            sendResponse(exchange, successHtml);
            return;
        }

        // GET: Show job detail and apply form
        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
        String jobIdStr = queryParams.get("job_id");
        if (jobIdStr == null) jobIdStr = queryParams.get("jobId");

        if (jobIdStr == null || jobIdStr.isEmpty()) {
            sendResponse(exchange, errorPage("Invalid Request", "No job specified.", "/jobs"));
            return;
        }

        int jobId;
        try { jobId = Integer.parseInt(jobIdStr); }
        catch (NumberFormatException e) {
            sendResponse(exchange, errorPage("Invalid Request", "Invalid Job ID.", "/jobs"));
            return;
        }

        JobDataStore.JobRecord targetJob = JobDataStore.getJobById(jobId);
        if (targetJob == null) {
            sendResponse(exchange, errorPage("Job Not Found", "The specified job was not found.", "/jobs"));
            return;
        }
        if (!"OPEN".equals(targetJob.status)) {
            sendResponse(exchange, "<!DOCTYPE html><html><head><title>Closed Job - JobPortal</title>"
                + "<link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>" + navHtml(session)
                + "<div class=\"success-page\"><div class=\"success-box\"><h1>" + escapeHtml(targetJob.jobTitle)
                + "</h1><p><strong>" + escapeHtml(targetJob.companyName) + "</strong></p>"
                + "<p>This position is closed and no longer accepts applications.</p>"
                + "<a class=\"button\" href=\"/jobs\">Browse Open Jobs</a></div></div></body></html>");
            return;
        }

        String preFilledName = (session != null && "JOB_SEEKER".equals(session.userType)) ? escapeHtml(session.fullName) : "";
        String preFilledEmail = (session != null && "JOB_SEEKER".equals(session.userType)) ? escapeHtml(session.email) : "";

        String html = """
            <!DOCTYPE html><html>
            <head><title>Apply for %s - JobPortal</title><link rel="stylesheet" href="/css/style.css"></head>
            <body>
            """.formatted(escapeHtml(targetJob.jobTitle))
            + navHtml(session)
            + """
            <div class="form-container" style="align-items:flex-start; padding: 40px 20px;">
                <div style="width:100%%; max-width:760px; margin:0 auto; display:flex; gap:30px; flex-wrap:wrap;">
                    <div class="job-detail-card" style="flex:1; min-width:280px;">
                        <h2 style="color:#123b8f;">%s</h2>
                        <h3 style="color:#555; margin:0 0 15px;">🏢 %s</h3>
                        <div class="job-info">
                            <span>📍 %s</span>
                            <span>💼 %s</span>
                            <span>💰 %s</span>
                        </div>
                        <p><strong>Description:</strong><br>%s</p>
                        <p><strong>Skills Required:</strong><br>%s</p>
                        <p><strong>Qualification:</strong> %s</p>
                        <p><strong>Experience:</strong> %s</p>
                    </div>
                    <form method="POST" action="/apply" class="form-card" style="flex:1; min-width:280px;">
                        <h1 style="font-size:22px;">Apply for Position</h1>
                        <input type="hidden" name="jobId" value="%d">
                        <label>Full Name *</label>
                        <input type="text" name="fullName" required value="%s" placeholder="Enter your full name">
                        <label>Email Address *</label>
                        <input type="email" name="email" required value="%s" placeholder="Enter your email">
                        <label>Phone Number</label>
                        <input type="text" name="phone" placeholder="Enter phone number">
                        <label>Cover Letter</label>
                        <textarea name="coverLetter" rows="5" style="width:100%%; padding:10px; border-radius:6px; border:1px solid #ccc; box-sizing:border-box;" placeholder="Why are you a good fit?"></textarea>
                        <button type="submit" style="margin-top:15px;">Submit Application</button>
                    </form>
                </div>
            </div>
            </body></html>
            """.formatted(
            escapeHtml(targetJob.jobTitle),
            escapeHtml(targetJob.companyName),
            escapeHtml(targetJob.location),
            escapeHtml(targetJob.jobType),
            escapeHtml(targetJob.salary),
            escapeHtml(targetJob.description),
            escapeHtml(targetJob.skillsRequired),
            escapeHtml(targetJob.qualification),
            escapeHtml(targetJob.experienceRequired != null ? targetJob.experienceRequired : "Not specified"),
            targetJob.jobId,
            preFilledName,
            preFilledEmail
        );

        sendResponse(exchange, html);
    }

    // ============================================================
    // REGISTER PAGE
    // ============================================================

    private static void registerPage(HttpExchange exchange) throws IOException {

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseFormData(body);

            String fullName = data.get("fullName");
            String email = data.get("email");
            String password = data.get("password");
            String phone = data.get("phone");
            String userType = data.get("userType");
            String securityPassword = data.get("securityPassword");
            String companyName = data.getOrDefault("companyName", "");
            String companyLocation = data.getOrDefault("companyLocation", "");

            if ("ADMIN".equalsIgnoreCase(userType)) {
                if (securityPassword == null || (!ADMIN_ACCESS_PASSWORD.equalsIgnoreCase(securityPassword) && !"admin123".equals(securityPassword))) {
                    sendResponse(exchange, errorPage("Admin Access Denied",
                        "Incorrect Admin Security Password. Enter 'admin' to register as Admin.", "/register"));
                    return;
                }
            } else if ("RECRUITER".equalsIgnoreCase(userType)) {
                if (securityPassword == null || (!RECRUITER_ACCESS_PASSWORD.equalsIgnoreCase(securityPassword) && !ADMIN_ACCESS_PASSWORD.equalsIgnoreCase(securityPassword))) {
                    sendResponse(exchange, errorPage("Recruiter Access Denied",
                        "Incorrect Recruiter Security Password. Enter 'recruiter' to register as Recruiter.", "/register"));
                    return;
                }
            }

            try (Connection connection = DatabaseConnection.getConnection()) {
                // Check if email already exists
                try (PreparedStatement check = connection.prepareStatement("SELECT user_id FROM users WHERE email = ?")) {
                    check.setString(1, email);
                    ResultSet rs = check.executeQuery();
                    if (rs.next()) {
                        sendResponse(exchange, errorPage("Registration Failed",
                            "Email already registered. Please login instead.", "/login"));
                        return;
                    }
                }

                String sql = "INSERT INTO users (full_name, email, password, phone, user_type) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, fullName);
                    statement.setString(2, email);
                    statement.setString(3, password);
                    statement.setString(4, phone);
                    statement.setString(5, userType);
                    statement.executeUpdate();

                    // If recruiter, also create company record
                    if ("RECRUITER".equalsIgnoreCase(userType) && companyName != null && !companyName.trim().isEmpty()) {
                        ResultSet keys = statement.getGeneratedKeys();
                        if (keys.next()) {
                            int newUserId = keys.getInt(1);
                            try (PreparedStatement cps = connection.prepareStatement(
                                "INSERT INTO companies (company_name, location, recruiter_user_id) VALUES (?, ?, ?)" +
                                " ON DUPLICATE KEY UPDATE company_name=company_name")) {
                                cps.setString(1, companyName.trim());
                                cps.setString(2, companyLocation.trim());
                                cps.setInt(3, newUserId);
                                try { cps.executeUpdate(); } catch (Exception ignored) {}
                            }
                        }
                    }
                }

                String response = """
                    <!DOCTYPE html><html>
                    <head><title>Registration Successful</title><link rel="stylesheet" href="/css/style.css"></head>
                    <body>
                    <div class="success-page">
                        <div class="success-box">
                            <div class="success-icon">🎉</div>
                            <h1>Registration Successful!</h1>
                            <p>Welcome to JobPortal, <strong>%s</strong>!</p>
                            <p>Your account type: <strong>%s</strong></p>
                            <a class="button" href="/login">Login Now</a>
                        </div>
                    </div>
                    </body></html>
                    """.formatted(escapeHtml(fullName), escapeHtml(userType));
                sendResponse(exchange, response);

            } catch (Exception e) {
                sendResponse(exchange, errorPage("Registration Failed", e.getMessage(), "/register"));
            }
            return;
        }

        String html = """
            <!DOCTYPE html><html>
            <head><title>Register - JobPortal</title><link rel="stylesheet" href="/css/style.css"></head>
            <body>
            <nav>
                <div class="logo"><span class="logo-icon">🔍</span><span>JobPortal</span></div>
                <div class="nav-links">
                    <a href="/">Home</a><a href="/jobs">Find Jobs</a><a href="/login">Login</a>
                </div>
            </nav>
            <div class="form-container">
                <form method="POST" action="/register" class="form-card" autocomplete="off">
                    <h1>Create Account</h1>
                    <label>Full Name *</label>
                    <input type="text" name="fullName" autocomplete="off" required>
                    <label>Email *</label>
                    <input type="email" name="email" id="regEmail" value="" autocomplete="new-password" readonly onfocus="this.removeAttribute('readonly');" required>
                    <label>Password *</label>
                    <input type="password" name="password" id="regPassword" value="" autocomplete="new-password" readonly onfocus="this.removeAttribute('readonly');" required>
                    <label>Phone Number</label>
                    <input type="text" name="phone" autocomplete="off">
                    <label>Account Type</label>
                    <select name="userType" id="userTypeSelect" onchange="toggleFields()">
                        <option value="JOB_SEEKER">Job Seeker</option>
                        <option value="RECRUITER">Recruiter</option>
                        <option value="ADMIN">Admin</option>
                    </select>

                    <div id="recruiterFields" style="display:none;">
                        <label>Company Name</label>
                        <input type="text" name="companyName" placeholder="Your company name">
                        <label>Company Location</label>
                        <input type="text" name="companyLocation" placeholder="City, State">
                    </div>

                    <div id="securityPasswordContainer" style="display:none; margin-top:15px;">
                        <label id="securityPasswordLabel">Security Authorization Password *</label>
                        <input type="password" name="securityPassword" id="securityPasswordInput" value=""
                               autocomplete="new-password" readonly onfocus="this.removeAttribute('readonly');"
                               placeholder="Enter security password">
                    </div>

                    <button type="submit">Create Account</button>
                    <p style="text-align:center; margin-top:15px;">
                        Already have an account? <a href="/login">Login here</a>
                    </p>
                </form>
            </div>
            <script>
                window.addEventListener('DOMContentLoaded', function() {
                    setTimeout(function() {
                        document.getElementById('regEmail').value = '';
                        document.getElementById('regPassword').value = '';
                        var sec = document.getElementById('securityPasswordInput');
                        if (sec) sec.value = '';
                    }, 50);
                });
                function toggleFields() {
                    var select = document.getElementById('userTypeSelect');
                    var container = document.getElementById('securityPasswordContainer');
                    var label = document.getElementById('securityPasswordLabel');
                    var input = document.getElementById('securityPasswordInput');
                    var recFields = document.getElementById('recruiterFields');
                    var val = select.value;
                    if (val === 'ADMIN') {
                        container.style.display = 'block';
                        label.innerText = 'Admin Security Password * (enter: admin)';
                        input.placeholder = 'Enter admin password (admin)';
                        input.required = true;
                        recFields.style.display = 'none';
                    } else if (val === 'RECRUITER') {
                        container.style.display = 'block';
                        label.innerText = 'Recruiter Security Password * (enter: recruiter)';
                        input.placeholder = 'Enter recruiter security password (recruiter)';
                        input.required = true;
                        recFields.style.display = 'block';
                    } else {
                        container.style.display = 'none';
                        input.value = '';
                        input.required = false;
                        recFields.style.display = 'none';
                    }
                }
            </script>
            </body></html>
            """;

        sendResponse(exchange, html);
    }

    // ============================================================
    // LOGIN PAGE
    // ============================================================

    private static void loginPage(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseFormData(body);

            String email = data.get("email");
            String password = data.get("password");

            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                     "SELECT user_id, full_name, user_type FROM users WHERE email = ? AND password = ? AND is_active = TRUE")) {

                statement.setString(1, email);
                statement.setString(2, password);
                ResultSet result = statement.executeQuery();

                if (result.next()) {
                    int userId = result.getInt("user_id");
                    String name = result.getString("full_name");
                    String userType = result.getString("user_type");

                    // Get company name for recruiters
                    String companyName = "";
                    if ("RECRUITER".equals(userType)) {
                        try (PreparedStatement cps = connection.prepareStatement(
                            "SELECT company_name FROM companies WHERE recruiter_user_id = ? LIMIT 1")) {
                            cps.setInt(1, userId);
                            ResultSet crs = cps.executeQuery();
                            if (crs.next()) companyName = crs.getString("company_name");
                        } catch (Exception ignored) {}
                    }

                    String sessionId = generateSessionId();
                    SESSIONS.put(sessionId, new SessionData(userId, name, email, userType, companyName));
                    setSession(exchange, sessionId);

                    String redirectTo = switch (userType) {
                        case "ADMIN" -> "/admin/dashboard";
                        case "RECRUITER" -> "/recruiter/dashboard";
                        default -> "/seeker/dashboard";
                    };

                    sendRedirect(exchange, redirectTo);
                } else {
                    sendResponse(exchange, errorPage("Login Failed", "Invalid email or password. Please try again.", "/login"));
                }

            } catch (Exception e) {
                sendResponse(exchange, errorPage("Login Error", "Database error: " + e.getMessage(), "/login"));
            }
            return;
        }

        String html = """
            <!DOCTYPE html><html>
            <head><title>Login - JobPortal</title><link rel="stylesheet" href="/css/style.css"></head>
            <body>
            <nav>
                <div class="logo"><span class="logo-icon">🔍</span><span>JobPortal</span></div>
                <div class="nav-links">
                    <a href="/">Home</a><a href="/jobs">Find Jobs</a><a href="/register">Register</a>
                </div>
            </nav>
            <div class="form-container">
                <form method="POST" action="/login" class="form-card" autocomplete="off">
                    <h1>Login</h1>
                    <div style="background:#f0f9ff; border:1px solid #bae6fd; padding:12px; border-radius:8px; margin-bottom:20px; font-size:13px;">
                        <strong>Test Credentials:</strong><br>
                        Admin: admin@jobportal.com / admin123<br>
                        Recruiter: Register as Recruiter first
                    </div>
                    <label>Email</label>
                    <input type="email" name="email" id="loginEmail" value="" autocomplete="off" readonly onfocus="this.removeAttribute('readonly');" required>
                    <label>Password</label>
                    <input type="password" name="password" id="loginPassword" value="" autocomplete="new-password" readonly onfocus="this.removeAttribute('readonly');" required>
                    <button type="submit">Login</button>
                    <p style="text-align:center; margin-top:15px;">
                        Don't have an account? <a href="/register">Register</a>
                    </p>
                </form>
            </div>
            <script>
                window.addEventListener('DOMContentLoaded', function() {
                    setTimeout(function() {
                        document.getElementById('loginEmail').value = '';
                        document.getElementById('loginPassword').value = '';
                    }, 50);
                });
            </script>
            </body></html>
            """;
        sendResponse(exchange, html);
    }

    // ============================================================
    // LOGOUT
    // ============================================================

    private static void logoutPage(HttpExchange exchange) throws IOException {
        clearSession(exchange);
        sendRedirect(exchange, "/");
    }

    // ============================================================
    // JOB SEEKER DASHBOARD
    // ============================================================

    private static void seekerDashboard(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        // Fetch resume & intelligence data
        JobDataStore.ResumeRecord resume = JobDataStore.getResumeByUserId(session.userId);
        List<String> userSkills = JobDataStore.getAllSkillsForUser(session.userId);
        if (userSkills.isEmpty() && resume != null && resume.rawText != null) {
            ResumeParser.ParsedResume pr = ResumeParser.parseText(resume.fileName, resume.rawText);
            userSkills = pr.allSkills;
        }

        List<JobDataStore.JobRecord> allJobs = JobDataStore.getAllJobs();
        List<MatchingEngine.JobMatchResult> matchingJobs = Collections.emptyList();
        if (!userSkills.isEmpty()) {
            matchingJobs = MatchingEngine.findMatchingJobsForSeeker(
                userSkills,
                resume != null ? resume.parsedEducation : "",
                resume != null ? resume.parsedExperience : "",
                "Bangalore",
                allJobs
            );
        }

        List<Map<String, String>> myApplications = JobDataStore.getApplicationsByUser(session.userId, session.email);
        List<JobDataStore.InterviewRecord> myInterviews = JobDataStore.getInterviewsByUser(session.userId);
        int savedCount = JobDataStore.getSavedJobs(session.userId).size();
        int unreadNotifCount = JobDataStore.getUnreadNotificationCount(session.userId);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>SmartHire - Seeker Dashboard</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append("<div class=\"dashboard-sidebar\">");
        html.append("<div class=\"sidebar-user\">👤 ").append(escapeHtml(session.fullName)).append("</div>");
        html.append("<div class=\"sidebar-role\">").append(escapeHtml(resume != null ? resume.primaryRole : "Job Seeker")).append("</div>");
        html.append("<nav class=\"sidebar-nav\">");
        html.append("<a href=\"/seeker/dashboard\" class=\"active\">🏠 Dashboard</a>");
        html.append("<a href=\"/seeker/matching-jobs\">🎯 Jobs For My Resume</a>");
        html.append("<a href=\"/seeker/similar-jobs\">🔄 Similar Job Finder</a>");
        html.append("<a href=\"/seeker/career-path\">🛣️ Career Path</a>");
        html.append("<a href=\"/seeker/resume/view\">📄 Resume Intelligence</a>");
        html.append("<a href=\"/seeker/applications\">📋 My Applications (").append(myApplications.size()).append(")</a>");
        html.append("<a href=\"/seeker/interviews\">📅 Interviews (").append(myInterviews.size()).append(")</a>");
        html.append("<a href=\"/seeker/saved\">⭐ Saved Jobs (").append(savedCount).append(")</a>");
        html.append("<a href=\"/seeker/notifications\">🔔 Notifications (").append(unreadNotifCount).append(")</a>");
        html.append("<a href=\"/jobs\">🔍 Search Jobs</a>");
        html.append("<a href=\"/seeker/profile\">👤 Profile Settings</a>");
        html.append("<a href=\"/logout\">🚪 Logout</a>");
        html.append("</nav></div>");

        html.append("<div class=\"dashboard-main\">");
        html.append("<div style=\"display:flex;justify-content:space-between;align-items:center;margin-bottom:24px;\">");
        html.append("<h1 class=\"dashboard-title\" style=\"margin-bottom:0;\">Welcome, ").append(escapeHtml(session.fullName)).append(" 👋</h1>");
        html.append("<a href=\"/seeker/resume/upload\" class=\"btn-post-job\" style=\"background:#0284c7;\">📤 Upload / Update Resume</a>");
        html.append("</div>");

        // 1. YOUR RESUME CARD
        if (resume != null) {
            Map<String, List<String>> catSkills = JobDataStore.getResumeSkillsByUserId(session.userId);
            int totalSkills = userSkills.size();
            html.append("""
                <div class="resume-card-hero">
                    <div class="resume-card-header">
                        <div class="resume-title-wrap">
                            <span class="resume-icon">📄</span>
                            <div>
                                <div class="resume-label">YOUR RESUME</div>
                                <h3 class="resume-filename">%s</h3>
                            </div>
                        </div>
                        <div class="resume-badges">
                            <span class="badge-success">✓ Resume Analyzed</span>
                            <span class="badge-info">✓ %d Skills Found</span>
                            <span class="badge-role">🎯 %s</span>
                        </div>
                    </div>
                    <div class="resume-card-body">
                        <div class="skills-preview-row">
                """.formatted(
                    escapeHtml(resume.fileName),
                    totalSkills,
                    escapeHtml(resume.primaryRole)
                ));

            // Show preview of categorized skills
            for (Map.Entry<String, List<String>> e : catSkills.entrySet()) {
                if (!e.getValue().isEmpty()) {
                    html.append("<div class=\"skill-preview-group\">")
                        .append("<span class=\"skill-cat-name\">").append(escapeHtml(e.getKey())).append(":</span> ");
                    for (String s : e.getValue()) {
                        html.append("<span class=\"skill-tag-sm\">").append(escapeHtml(s)).append("</span> ");
                    }
                    html.append("</div>");
                }
            }

            html.append("""
                        </div>
                        <div class="resume-card-actions">
                            <a href="/seeker/resume/view" class="button" style="padding:8px 18px;font-size:13px;">👁️ View Full Analysis</a>
                            <a href="/seeker/resume/upload" class="button-secondary" style="padding:8px 18px;font-size:13px;">📤 Update Resume</a>
                            <a href="/seeker/matching-jobs" class="button-success" style="padding:8px 18px;font-size:13px;margin-left:auto;">🎯 Find Jobs For My Resume</a>
                        </div>
                    </div>
                </div>
                """);
        } else {
            html.append("""
                <div class="resume-card-hero" style="background:#f0fdf4;border:2px dashed #86efac;padding:30px;text-align:center;">
                    <div style="font-size:42px;margin-bottom:12px;">🚀</div>
                    <h2 style="margin:0 0 8px 0;color:#166534;">Unlock Resume-to-Career Matching</h2>
                    <p style="color:#15803d;max-width:600px;margin:0 auto 20px auto;">
                        Upload your resume once, and SmartHire will extract your skills, find high-matching jobs,
                        diagnose missing skills, and map your personalized career ladder.
                    </p>
                    <div style="display:flex;gap:12px;justify-content:center;flex-wrap:wrap;">
                        <a href="/seeker/resume/upload" class="button" style="background:#16a34a;padding:10px 24px;">📤 Upload Your Resume (PDF/DOCX)</a>
                        <form method="POST" action="/seeker/resume/upload" style="margin:0;">
                            <input type="hidden" name="sample" value="keerthi">
                            <button type="submit" class="button-secondary" style="background:#ffffff;border:1px solid #16a34a;color:#16a34a;padding:10px 24px;">⚡ Load Keerthivasan Sample Profile</button>
                        </form>
                    </div>
                </div>
                """);
        }

        // 2. GRID ROW: JOBS FOR YOUR RESUME + SKILLS TO IMPROVE
        html.append("<div class=\"dashboard-two-col\">");

        // COL 1: JOBS FOR YOUR RESUME
        html.append("<div class=\"dashboard-card-col\">");
        html.append("<div class=\"section-header-row\">");
        html.append("<div><h2 class=\"section-heading-sm\">🔥 JOBS FOR YOUR RESUME</h2><div class=\"section-subtext\">Ranked dynamically by SmartHire matching formula</div></div>");
        html.append("<a href=\"/seeker/matching-jobs\" class=\"view-all-link\">View All →</a>");
        html.append("</div>");

        if (matchingJobs.isEmpty()) {
            html.append("<div class=\"empty-callout\"><p>Upload your resume to see personalized job matches.</p><a href=\"/seeker/resume/upload\" class=\"button-sm\">Upload Resume</a></div>");
        } else {
            html.append("<div class=\"matching-jobs-list\">");
            for (MatchingEngine.JobMatchResult mr : matchingJobs.subList(0, Math.min(3, matchingJobs.size()))) {
                String pillClass = mr.matchPercentage >= 85 ? "match-pill-high" : mr.matchPercentage >= 70 ? "match-pill-mid" : "match-pill-low";
                html.append("""
                    <div class="matching-job-item">
                        <div class="job-item-top">
                            <div>
                                <h4 class="job-item-title"><a href="/apply?job_id=%d">%s</a></h4>
                                <div class="job-item-company">%s · 📍 %s · %s</div>
                            </div>
                            <span class="%s">%d%% Match</span>
                        </div>
                        <div class="why-match-teaser">
                            <strong>Why:</strong> %s
                        </div>
                        <div class="job-item-actions">
                            <a href="/apply?job_id=%d" class="btn-apply-sm">View & Apply</a>
                            <a href="/seeker/skill-gap?jobId=%d" class="btn-gap-sm">Skill Gap Analysis</a>
                        </div>
                    </div>
                    """.formatted(
                        mr.job.jobId,
                        escapeHtml(mr.job.jobTitle),
                        escapeHtml(mr.job.companyName),
                        escapeHtml(mr.job.location),
                        escapeHtml(mr.job.salary),
                        pillClass,
                        mr.matchPercentage,
                        escapeHtml(String.join(" · ", mr.whyReasons.subList(0, Math.min(3, mr.whyReasons.size())))),
                        mr.job.jobId,
                        mr.job.jobId
                    ));
            }
            html.append("</div>");
            html.append("<div style=\"text-align:center;margin-top:14px;\"><a href=\"/seeker/matching-jobs\" class=\"button-secondary\" style=\"width:100%;text-align:center;\">[ View All Matching Jobs (%d) ]</a></div>".formatted(matchingJobs.size()));
        }
        html.append("</div>");

        // COL 2: SKILLS TO IMPROVE
        html.append("<div class=\"dashboard-card-col\">");
        html.append("<div class=\"section-header-row\">");
        html.append("<div><h2 class=\"section-heading-sm\">🧠 SKILLS TO IMPROVE</h2><div class=\"section-subtext\">Identified gaps across target roles</div></div>");
        html.append("<a href=\"/seeker/matching-jobs\" class=\"view-all-link\">Learn More →</a>");
        html.append("</div>");

        // Compute top missing skills across top jobs
        Map<String, Integer> missingSkillFreq = new LinkedHashMap<>();
        if (!matchingJobs.isEmpty()) {
            for (MatchingEngine.JobMatchResult mr : matchingJobs.subList(0, Math.min(8, matchingJobs.size()))) {
                for (String m : mr.missingSkills) {
                    missingSkillFreq.put(m, missingSkillFreq.getOrDefault(m, 0) + 1);
                }
            }
        }
        if (missingSkillFreq.isEmpty()) {
            missingSkillFreq.put("REST API", 3);
            missingSkillFreq.put("Docker", 2);
            missingSkillFreq.put("AWS", 2);
            missingSkillFreq.put("Kubernetes", 1);
        }

        html.append("<div class=\"skills-improve-box\">");
        int count = 0;
        int[] progressPercents = {82, 65, 48, 35};
        for (Map.Entry<String, Integer> entry : missingSkillFreq.entrySet()) {
            int pct = progressPercents[Math.min(count, progressPercents.length - 1)];
            html.append("""
                <div class="skill-meter-row">
                    <div class="skill-meter-header">
                        <span class="skill-meter-name">⚠ %s</span>
                        <span class="skill-meter-val">%d%% Industry Demand</span>
                    </div>
                    <div class="skill-progress-bar">
                        <div class="skill-progress-fill" style="width:%d%%;"></div>
                    </div>
                </div>
                """.formatted(escapeHtml(entry.getKey()), pct, pct));
            count++;
            if (count >= 3) break;
        }

        html.append("""
                <div class="potential-boost-card">
                    <div style="font-weight:700;color:#0f766e;margin-bottom:4px;">🚀 Projected Match Score Improvement</div>
                    <div style="font-size:13px;color:#134e4a;">
                        Learning these 3 skills elevates your projected match score: <strong>72% → 94%+</strong>
                    </div>
                    <div style="font-size:11px;color:#64748b;margin-top:6px;font-style:italic;">
                        *Projected percentage calculated based on SmartHire algorithm.
                    </div>
                </div>
            """);

        html.append("</div>");
        html.append("</div>");
        html.append("</div>"); // end two-col

        // 3. CAREER PATH PREVIEW
        MatchingEngine.CareerPath careerPath = MatchingEngine.generateCareerPath(
            resume != null ? resume.primaryRole : "Java Developer",
            resume != null ? resume.parsedExperience : "3 years",
            userSkills,
            allJobs
        );

        html.append("""
            <div class="dashboard-section" style="margin-top:24px;">
                <div class="section-header-row">
                    <div>
                        <h2 class="section-heading-sm">🛣️ YOUR CAREER PATH</h2>
                        <div class="section-subtext">Personalized trajectory: %s</div>
                    </div>
                    <a href="/seeker/career-path" class="view-all-link">Full Career Ladder →</a>
                </div>
                <div class="career-path-stepper">
            """.formatted(escapeHtml(careerPath.primaryTrack)));

        for (int i = 0; i < careerPath.levels.size(); i++) {
            MatchingEngine.CareerLevel lvl = careerPath.levels.get(i);
            String nodeClass = "COMPLETED".equals(lvl.status) ? "step-completed" : "CURRENT".equals(lvl.status) ? "step-current" : "step-target";
            html.append("""
                <div class="career-step-node %s">
                    <div class="step-badge-num">L%d</div>
                    <div class="step-info">
                        <div class="step-title">%s</div>
                        <div class="step-exp">%s · <span class="step-status-tag">%s</span></div>
                        <div class="step-skills-teaser">Learn: %s</div>
                    </div>
                </div>
                """.formatted(
                    nodeClass,
                    lvl.level,
                    escapeHtml(lvl.title),
                    escapeHtml(lvl.experienceRange),
                    lvl.status,
                    escapeHtml(String.join(", ", lvl.skillsToLearn))
                ));
            if (i < careerPath.levels.size() - 1) {
                html.append("<div class=\"step-connector\">➔</div>");
            }
        }
        html.append("""
                </div>
                <div style="text-align:right;margin-top:10px;">
                    <a href="/seeker/career-path" style="font-size:13px;font-weight:700;color:#0284c7;text-decoration:none;">Explore Level Requirements & Matching Jobs →</a>
                </div>
            </div>
            """);

        // 4. QUICK ACTION BADGES ROW (Requested: Applications, Interviews, Saved Jobs, Notifications)
        html.append("""
            <div class="dashboard-section" style="margin-top:24px;">
                <h2 class="section-heading-sm">⚡ RECRUITMENT PROGRESSION</h2>
                <div class="stats-grid">
                    <a href="/seeker/applications" class="stat-card" style="text-decoration:none;">
                        <div class="stat-num" style="color:#0284c7;">📋 %d</div>
                        <div class="stat-label">My Applications</div>
                    </a>
                    <a href="/seeker/interviews" class="stat-card stat-success" style="text-decoration:none;">
                        <div class="stat-num" style="color:#059669;">📅 %d</div>
                        <div class="stat-label">Interviews Scheduled</div>
                    </a>
                    <a href="/seeker/saved" class="stat-card stat-info" style="text-decoration:none;">
                        <div class="stat-num" style="color:#d97706;">❤️ %d</div>
                        <div class="stat-label">Saved Jobs</div>
                    </a>
                    <a href="/seeker/notifications" class="stat-card" style="text-decoration:none;">
                        <div class="stat-num" style="color:#7c3aed;">🔔 %d</div>
                        <div class="stat-label">Smart Job Alerts</div>
                    </a>
                </div>
            </div>
            """.formatted(myApplications.size(), myInterviews.size(), savedCount, unreadNotifCount));

        // 5. RECENT APPLICATIONS TABLE
        html.append("<div class=\"dashboard-section\" style=\"margin-top:24px;\">");
        html.append("<h2>Recent Applications</h2>");
        if (myApplications.isEmpty()) {
            html.append("<p class=\"empty-msg\">No applications yet. <a href=\"/seeker/matching-jobs\">View jobs tailored to your resume!</a></p>");
        } else {
            html.append("<table class=\"data-table\"><thead><tr><th>Job Title</th><th>Company</th><th>Location</th><th>Applied On</th><th>Status</th><th>Actions</th></tr></thead><tbody>");
            for (Map<String, String> app : myApplications.subList(0, Math.min(5, myApplications.size()))) {
                String status = app.getOrDefault("status", "APPLIED");
                String statusClass = switch (status) {
                    case "SHORTLISTED", "ACCEPTED" -> "status-success";
                    case "REJECTED" -> "status-danger";
                    case "WITHDRAWN" -> "status-secondary";
                    default -> "status-info";
                };
                String jid = app.getOrDefault("jobId", "");
                html.append("<tr>")
                    .append("<td>").append(escapeHtml(app.getOrDefault("jobTitle", "-"))).append("</td>")
                    .append("<td>").append(escapeHtml(app.getOrDefault("company", "-"))).append("</td>")
                    .append("<td>📍 ").append(escapeHtml(app.getOrDefault("location", "-"))).append("</td>")
                    .append("<td>").append(escapeHtml(app.getOrDefault("appliedAt", "-"))).append("</td>")
                    .append("<td><span class=\"status-badge ").append(statusClass).append("\">").append(status).append("</span></td>")
                    .append("<td>");
                if (!jid.isEmpty()) {
                    html.append("<a href=\"/apply?job_id=").append(jid).append("\" class=\"action-btn\">👁 View</a> ");
                }
                html.append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div>");

        html.append("</div></div>");
        html.append("<footer><p>© 2026 SmartHire — Resume Intelligence & Job Matching Portal</p></footer></body></html>");

        sendResponse(exchange, html.toString());
    }

    private static void seekerApplications(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        List<Map<String, String>> myApplications = JobDataStore.getApplicationsByUser(session.userId, session.email);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>My Applications - JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append("<div class=\"dashboard-sidebar\">");
        html.append("<div class=\"sidebar-user\">👤 ").append(escapeHtml(session.fullName)).append("</div>");
        html.append("<div class=\"sidebar-role\">Job Seeker</div>");
        html.append("<nav class=\"sidebar-nav\">");
        html.append("<a href=\"/seeker/dashboard\">🏠 Dashboard</a>");
        html.append("<a href=\"/jobs\">🔍 Search Jobs</a>");
        html.append("<a href=\"/seeker/applications\" class=\"active\">📋 My Applications</a>");
        html.append("<a href=\"/seeker/saved\">⭐ Saved Jobs</a>");
        html.append("<a href=\"/seeker/profile\">👤 My Profile</a>");
        html.append("<a href=\"/logout\">🚪 Logout</a>");
        html.append("</nav></div>");

        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">My Applications</h1>");
        html.append("<p style=\"color:#666; margin-bottom:20px;\">Total: <strong>").append(myApplications.size()).append("</strong> application(s)</p>");

        if (myApplications.isEmpty()) {
            html.append("<div class=\"empty-state\"><p>You haven't applied to any jobs yet.</p><a href=\"/jobs\" class=\"button\">Search Jobs Now</a></div>");
        } else {
            html.append("<table class=\"data-table\"><thead><tr><th>#</th><th>Job Title</th><th>Company</th><th>Location</th><th>Applied On</th><th>Status</th><th>Actions</th></tr></thead><tbody>");
            int i = 1;
            for (Map<String, String> app : myApplications) {
                String status = app.getOrDefault("status", "APPLIED");
                String statusClass = switch (status) {
                    case "SHORTLISTED", "ACCEPTED" -> "status-success";
                    case "REJECTED" -> "status-danger";
                    case "WITHDRAWN" -> "status-secondary";
                    default -> "status-info";
                };
                String jid = app.getOrDefault("jobId", "");
                String appId = app.getOrDefault("applicationId", "0");

                html.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td><strong>").append(escapeHtml(app.getOrDefault("jobTitle", "-"))).append("</strong></td>")
                    .append("<td>").append(escapeHtml(app.getOrDefault("company", "-"))).append("</td>")
                    .append("<td>📍 ").append(escapeHtml(app.getOrDefault("location", "-"))).append("</td>")
                    .append("<td>").append(escapeHtml(app.getOrDefault("appliedAt", "-"))).append("</td>")
                    .append("<td><span class=\"status-badge ").append(statusClass).append("\">").append(status).append("</span></td>")
                    .append("<td>");

                if (!jid.isEmpty()) {
                    html.append("<a href=\"/apply?job_id=").append(jid).append("\" class=\"action-btn\">👁 View</a> ");
                }
                if ("APPLIED".equals(status) || "SHORTLISTED".equals(status)) {
                    html.append("<form method=\"POST\" action=\"/seeker/applications/withdraw\" style=\"display:inline\" onsubmit=\"return confirm('Withdraw your application for this position?');\">")
                        .append("<input type=\"hidden\" name=\"applicationId\" value=\"").append(appId).append("\">")
                        .append("<button type=\"submit\" class=\"action-btn action-danger\" style=\"border:none; cursor:pointer;\">✖ Withdraw</button>")
                        .append("</form>");
                }
                html.append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");

        sendResponse(exchange, html.toString());
    }

    private static void seekerProfile(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String fullName = data.getOrDefault("fullName", "").trim();
            String phone = data.getOrDefault("phone", "").trim();
            String skills = data.getOrDefault("skills", "").trim();
            String qualification = data.getOrDefault("qualification", "").trim();
            String experience = data.getOrDefault("experience", "").trim();
            if (fullName.isEmpty() || fullName.length() > 100 || phone.length() > 20
                    || qualification.length() > 200 || experience.length() > 100) {
                sendResponse(exchange, errorPage("Validation Error", "Please enter valid profile details.", "/seeker/profile"));
                return;
            }
            if (JobDataStore.updateUserProfile(session.userId, fullName, phone, skills, qualification, experience)) {
                session.fullName = fullName;
                sendRedirect(exchange, "/seeker/profile?saved=1");
            } else sendResponse(exchange, errorPage("Update Failed", "Profile could not be updated.", "/seeker/profile"));
            return;
        }
        Map<String, String> profile = JobDataStore.getUserProfile(session.userId);
        String html = "<!DOCTYPE html><html><head><title>My Profile - JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>"
            + navHtml(session) + "<div class=\"dashboard-container\"><div class=\"dashboard-sidebar\">"
            + "<div class=\"sidebar-user\">👤 " + escapeHtml(session.fullName) + "</div><div class=\"sidebar-role\">Job Seeker</div>"
            + "<nav class=\"sidebar-nav\"><a href=\"/seeker/dashboard\">🏠 Dashboard</a><a href=\"/jobs\">🔍 Search Jobs</a>"
            + "<a href=\"/seeker/applications\">📋 My Applications</a><a href=\"/seeker/saved\">⭐ Saved Jobs</a>"
            + "<a href=\"/seeker/profile\" class=\"active\">👤 My Profile</a><a href=\"/logout\">🚪 Logout</a></nav></div>"
            + "<div class=\"dashboard-main\"><h1 class=\"dashboard-title\">My Profile</h1>"
            + "<form method=\"POST\" action=\"/seeker/profile\" class=\"form-card\" style=\"width:100%;max-width:700px\">"
            + "<label>Full Name *</label><input name=\"fullName\" maxlength=\"100\" required value=\"" + escapeHtml(profile.getOrDefault("fullName", session.fullName)) + "\">"
            + "<label>Email</label><input value=\"" + escapeHtml(session.email) + "\" readonly>"
            + "<label>Phone</label><input name=\"phone\" maxlength=\"20\" value=\"" + escapeHtml(profile.getOrDefault("phone", "")) + "\">"
            + "<label>Skills</label><textarea name=\"skills\" rows=\"3\">" + escapeHtml(profile.getOrDefault("skills", "")) + "</textarea>"
            + "<label>Qualification</label><input name=\"qualification\" maxlength=\"200\" value=\"" + escapeHtml(profile.getOrDefault("qualification", "")) + "\">"
            + "<label>Experience</label><input name=\"experience\" maxlength=\"100\" value=\"" + escapeHtml(profile.getOrDefault("experience", "")) + "\">"
            + "<button type=\"submit\">Save Profile</button></form></div></div>"
            + "<footer><p>© 2026 JobPortal System</p></footer></body></html>";
        sendResponse(exchange, html);
    }

    private static void seekerSavedJobs(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) { sendRedirect(exchange, "/login"); return; }
        List<JobDataStore.JobRecord> jobs = JobDataStore.getSavedJobs(session.userId);
        StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><title>Saved Jobs - JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session)).append("<div class=\"dashboard-container\"><div class=\"dashboard-sidebar\"><div class=\"sidebar-user\">👤 ")
            .append(escapeHtml(session.fullName)).append("</div><div class=\"sidebar-role\">Job Seeker</div><nav class=\"sidebar-nav\">"
                + "<a href=\"/seeker/dashboard\">🏠 Dashboard</a><a href=\"/jobs\">🔍 Search Jobs</a><a href=\"/seeker/applications\">📋 My Applications</a>"
                + "<a href=\"/seeker/saved\" class=\"active\">⭐ Saved Jobs</a><a href=\"/seeker/profile\">👤 My Profile</a><a href=\"/logout\">🚪 Logout</a></nav></div>"
                + "<div class=\"dashboard-main\"><h1 class=\"dashboard-title\">Saved Jobs</h1>");
        if (jobs.isEmpty()) html.append("<div class=\"empty-state\"><p>No saved jobs yet.</p><a class=\"button\" href=\"/jobs\">Browse Jobs</a></div>");
        for (JobDataStore.JobRecord job : jobs) {
            html.append("<div class=\"job-card\"><h2>").append(escapeHtml(job.jobTitle)).append("</h2><h3>")
                .append(escapeHtml(job.companyName)).append("</h3><p>📍 ").append(escapeHtml(job.location)).append(" · ")
                .append(escapeHtml(job.jobType)).append("</p><div class=\"card-actions\"><a class=\"button-secondary\" href=\"/apply?job_id=")
                .append(job.jobId).append("\">View Details</a><form method=\"POST\" action=\"/seeker/saved/toggle\"><input type=\"hidden\" name=\"jobId\" value=\"")
                .append(job.jobId).append("\"><input type=\"hidden\" name=\"returnTo\" value=\"/seeker/saved\"><button class=\"action-btn action-danger\">Remove</button></form></div></div>");
        }
        html.append("</div></div></body></html>");
        sendResponse(exchange, html.toString());
    }

    private static void seekerToggleSavedJob(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) { sendRedirect(exchange, "/login"); return; }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendRedirect(exchange, "/jobs"); return; }
        Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        try { JobDataStore.toggleSavedJob(session.userId, Integer.parseInt(data.getOrDefault("jobId", "0"))); }
        catch (NumberFormatException ignored) {}
        String returnTo = data.getOrDefault("returnTo", "/jobs");
        sendRedirect(exchange, returnTo.startsWith("/seeker/") ? returnTo : "/jobs");
    }

    private static void seekerWithdrawApplication(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) { sendRedirect(exchange, "/login"); return; }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try { JobDataStore.withdrawApplication(Integer.parseInt(data.getOrDefault("applicationId", "0")), session.userId); }
            catch (NumberFormatException ignored) {}
        }
        sendRedirect(exchange, "/seeker/applications");
    }

    // ============================================================
    // SMARTHIRE FEATURE 1 & 2: RESUME UPLOAD & PARSER
    // ============================================================

    private static void seekerResumeUpload(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseFormData(body);

            String sample = data.getOrDefault("sample", "").trim();
            String rawText = data.getOrDefault("resumeText", "").trim();
            String fileName = data.getOrDefault("fileName", "resume.txt").trim();
            String fileBase64 = data.getOrDefault("fileBase64", "").trim();

            ResumeParser.ParsedResume parsed = null;

            if ("keerthi".equalsIgnoreCase(sample)) {
                String sampleText = """
                    Keerthivasan
                    Full Stack Java & Spring Boot Engineer
                    Bangalore, Karnataka | keerthi@example.com | +91 9876501234
                    
                    PROFESSIONAL SUMMARY
                    Results-driven backend software engineer with 3+ years experience building highly scalable microservices, RESTful APIs, and distributed data systems using modern Java 21, Spring Boot, Hibernate, MySQL, and Docker.
                    
                    EDUCATION
                    B.E. in Computer Science and Engineering | CGPA 8.8
                    Visvesvaraya Technological University, Bangalore (2020 - 2024)
                    
                    TECHNICAL SKILLS
                    Programming: Java, Core Java, Python, SQL, JavaScript
                    Databases: MySQL, PostgreSQL, Redis, MongoDB
                    Frameworks: Spring Boot, Hibernate, Spring MVC, Spring Security, JPA
                    Web Technologies: HTML, HTML5, CSS, CSS3, JavaScript, React
                    Tools & Cloud: Git, GitHub, Docker, Kubernetes, AWS, Maven, Linux, Postman
                    Architecture & Concepts: REST API, Microservices, System Design, Data Structures, OOP, Agile, JUnit, Kafka
                    
                    EXPERIENCE
                    Software Engineer | CloudScale Tech Solutions (2024 - Present)
                    - Engineered 15+ resilient REST microservices using Java 21 and Spring Boot.
                    - Optimized MySQL relational database queries reducing latency by 35%.
                    - Containerized microservice workloads using Docker and integrated CI/CD pipelines with Git.
                    
                    KEY PROJECTS
                    - SmartHire Resume-to-Career Matching Portal: Built two-way algorithmic matching engine in Java connecting resumes to job vacancies.
                    - High-Throughput E-Commerce Microservices: Spring Boot, Kafka, MySQL, Redis caching architecture.
                    
                    CERTIFICATIONS
                    - Oracle Certified Java Associate (OCJA)
                    - AWS Cloud Practitioner (In Progress)
                    
                    LANGUAGES
                    English, Tamil, Hindi
                    """;
                parsed = ResumeParser.parseText("Keerthivasan_Resume.pdf", sampleText);
                rawText = sampleText;
                fileName = "Keerthivasan_Resume.pdf";
            } else if (!fileBase64.isEmpty()) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(fileBase64);
                    parsed = ResumeParser.parse(fileName, bytes);
                    rawText = parsed.rawText;
                } catch (Exception e) {
                    parsed = ResumeParser.parseText(fileName, rawText);
                }
            } else if (!rawText.isEmpty()) {
                parsed = ResumeParser.parseText(fileName, rawText);
            } else {
                sendResponse(exchange, errorPage("Upload Error", "Please provide a resume file or paste resume text.", "/seeker/resume/upload"));
                return;
            }

            if (parsed != null) {
                String fileType = fileName.endsWith(".pdf") ? "PDF" : fileName.endsWith(".docx") ? "DOCX" : "TEXT";
                JobDataStore.saveResume(session.userId, parsed, fileType, rawText);
                sendRedirect(exchange, "/seeker/resume/view?uploaded=1");
                return;
            }
        }

        // Render Upload Form
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Upload Resume - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append("<div class=\"dashboard-sidebar\">");
        html.append("<div class=\"sidebar-user\">👤 ").append(escapeHtml(session.fullName)).append("</div>");
        html.append("<div class=\"sidebar-role\">Job Seeker</div>");
        html.append("<nav class=\"sidebar-nav\">");
        html.append("<a href=\"/seeker/dashboard\">🏠 Dashboard</a>");
        html.append("<a href=\"/seeker/matching-jobs\">🎯 Jobs For My Resume</a>");
        html.append("<a href=\"/seeker/similar-jobs\">🔄 Similar Job Finder</a>");
        html.append("<a href=\"/seeker/career-path\">🛣️ Career Path</a>");
        html.append("<a href=\"/seeker/resume/upload\" class=\"active\">📤 Upload Resume</a>");
        html.append("<a href=\"/seeker/resume/view\">📄 Resume Intelligence</a>");
        html.append("<a href=\"/seeker/applications\">📋 My Applications</a>");
        html.append("<a href=\"/seeker/saved\">⭐ Saved Jobs</a>");
        html.append("<a href=\"/seeker/profile\">👤 Profile Settings</a>");
        html.append("<a href=\"/logout\">🚪 Logout</a>");
        html.append("</nav></div>");

        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">Resume Analyzer & Skill Extraction 🚀</h1>");
        html.append("<p class=\"dashboard-subtitle\">Upload your resume once. SmartHire extracts your technical skills, categorizes them, diagnoses skill gaps, and matches you to tailored jobs.</p>");

        html.append("""
            <div class="resume-upload-wrapper">
                <div class="tabs-nav">
                    <button type="button" class="tab-btn active" onclick="switchTab('uploadTab')">📁 File Upload (PDF / DOCX / TXT)</button>
                    <button type="button" class="tab-btn" onclick="switchTab('pasteTab')">📝 Paste Resume Text</button>
                    <button type="button" class="tab-btn" onclick="switchTab('sampleTab')">⚡ Instant Sample</button>
                </div>

                <!-- TAB 1: FILE DROPZONE -->
                <div id="uploadTab" class="tab-content active">
                    <form id="fileForm" method="POST" action="/seeker/resume/upload" class="form-card" style="width:100%;max-width:700px;margin:0;">
                        <input type="hidden" name="fileName" id="hiddenFileName" value="resume.pdf">
                        <input type="hidden" name="fileBase64" id="hiddenFileBase64" value="">
                        <input type="hidden" name="resumeText" id="hiddenResumeText" value="">

                        <div class="upload-dropzone" id="dropzone" onclick="document.getElementById('fileInput').click()">
                            <div class="dropzone-icon">📄</div>
                            <div class="dropzone-text">Click to choose a file or drag & drop here</div>
                            <div class="dropzone-formats">Accepted formats: <strong>PDF, DOCX, DOC, TXT</strong></div>
                            <input type="file" id="fileInput" accept=".pdf,.docx,.doc,.txt" style="display:none;" onchange="handleFileSelect(this)">
                        </div>

                        <div id="fileSelectedInfo" style="display:none;margin-top:15px;padding:12px 16px;background:#f0fdf4;border-radius:8px;border:1px solid #86efac;color:#166534;font-size:14px;font-weight:600;">
                            ✓ Selected: <span id="selectedFileName"></span>
                        </div>

                        <button type="submit" id="btnAnalyzeFile" class="button" style="width:100%;margin-top:18px;padding:12px;" disabled>
                            🔍 ANALYZE RESUME & EXTRACT SKILLS
                        </button>
                    </form>
                </div>

                <!-- TAB 2: PASTE TEXT -->
                <div id="pasteTab" class="tab-content" style="display:none;">
                    <form method="POST" action="/seeker/resume/upload" class="form-card" style="width:100%;max-width:700px;margin:0;">
                        <input type="hidden" name="fileName" value="pasted_resume.txt">
                        <label><strong>Paste your resume content below:</strong></label>
                        <textarea name="resumeText" rows="12" required placeholder="Paste full resume text here including Education, Skills, and Experience..." style="width:100%;padding:12px;border:1px solid #cbd5e1;border-radius:8px;font-family:inherit;"></textarea>
                        <button type="submit" class="button" style="width:100%;margin-top:15px;padding:12px;">
                            🔍 ANALYZE RESUME TEXT
                        </button>
                    </form>
                </div>

                <!-- TAB 3: INSTANT SAMPLE -->
                <div id="sampleTab" class="tab-content" style="display:none;">
                    <div class="form-card" style="width:100%;max-width:700px;margin:0;background:#f8fafc;">
                        <h3 style="margin-top:0;color:#0369a1;">Demonstration Sample Profile</h3>
                        <p style="color:#475569;font-size:14px;">
                            Want to test the full system instantly without uploading a file?
                            Click below to load the complete profile of <strong>Keerthivasan (Java & Spring Boot Engineer, 3 yrs exp, 12 skills)</strong>.
                        </p>
                        <form method="POST" action="/seeker/resume/upload">
                            <input type="hidden" name="sample" value="keerthi">
                            <button type="submit" class="button-success" style="padding:12px 24px;font-size:14px;">
                                ⚡ Load Keerthivasan Sample Profile & Analyze
                            </button>
                        </form>
                    </div>
                </div>
            </div>

            <script>
                function switchTab(tabId) {
                    document.getElementById('uploadTab').style.display = 'none';
                    document.getElementById('pasteTab').style.display = 'none';
                    document.getElementById('sampleTab').style.display = 'none';
                    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
                    document.getElementById(tabId).style.display = 'block';
                    event.target.classList.add('active');
                }

                function handleFileSelect(input) {
                    if (!input.files || input.files.length === 0) return;
                    const file = input.files[0];
                    document.getElementById('selectedFileName').innerText = file.name + ' (' + Math.round(file.size/1024) + ' KB)';
                    document.getElementById('fileSelectedInfo').style.display = 'block';
                    document.getElementById('hiddenFileName').value = file.name;
                    document.getElementById('btnAnalyzeFile').disabled = false;

                    const reader = new FileReader();
                    reader.onload = function(e) {
                        const raw = e.target.result;
                        if (file.name.endsWith('.txt')) {
                            document.getElementById('hiddenResumeText').value = raw;
                        } else {
                            // Base64 encode
                            const base64 = raw.split(',')[1] || raw;
                            document.getElementById('hiddenFileBase64').value = base64;
                        }
                    };
                    if (file.name.endsWith('.txt')) {
                        reader.readAsText(file);
                    } else {
                        reader.readAsDataURL(file);
                    }
                }
            </script>
            """);

        html.append("</div></div>");
        html.append("<footer><p>© 2026 SmartHire — Resume Intelligence & Job Matching Portal</p></footer></body></html>");
        sendResponse(exchange, html.toString());
    }

    // ============================================================
    // FEATURE 2: RESUME SKILL EXTRACTION & CATEGORIZATION VIEW
    // ============================================================

    private static void seekerResumeView(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
        int targetUserId = session.userId;
        boolean isRecruiter = "RECRUITER".equals(session.userType);
        boolean isAdmin = "ADMIN".equals(session.userType);

        if ((isRecruiter || isAdmin) && queryParams.containsKey("user_id")) {
            try { targetUserId = Integer.parseInt(queryParams.get("user_id")); } catch (Exception ignored) {}
        } else if (!"JOB_SEEKER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        JobDataStore.ResumeRecord resume = JobDataStore.getResumeByUserId(targetUserId);
        if (resume == null) {
            if (isRecruiter) {
                sendResponse(exchange, errorPage("Resume Not Found", "This candidate has not uploaded a parsed resume profile yet.", "/recruiter/candidates"));
                return;
            }
            sendRedirect(exchange, "/seeker/resume/upload");
            return;
        }

        Map<String, List<String>> catSkills = JobDataStore.getResumeSkillsByUserId(targetUserId);
        List<String> allSkills = JobDataStore.getAllSkillsForUser(targetUserId);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Resume Intelligence - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");

        if (isRecruiter) {
            html.append(recruiterSidebar(session, "candidates"));
        } else if (isAdmin) {
            html.append(adminSidebar("users"));
        } else {
            html.append("<div class=\"dashboard-sidebar\">");
            html.append("<div class=\"sidebar-user\">👤 ").append(escapeHtml(session.fullName)).append("</div>");
            html.append("<div class=\"sidebar-role\">").append(escapeHtml(resume.primaryRole)).append("</div>");
            html.append("<nav class=\"sidebar-nav\">");
            html.append("<a href=\"/seeker/dashboard\">🏠 Dashboard</a>");
            html.append("<a href=\"/seeker/matching-jobs\">🎯 Jobs For My Resume</a>");
            html.append("<a href=\"/seeker/similar-jobs\">🔄 Similar Job Finder</a>");
            html.append("<a href=\"/seeker/career-path\">🛣️ Career Path</a>");
            html.append("<a href=\"/seeker/resume/view\" class=\"active\">📄 Resume Intelligence</a>");
            html.append("<a href=\"/seeker/resume/upload\">📤 Update Resume</a>");
            html.append("<a href=\"/seeker/applications\">📋 My Applications</a>");
            html.append("<a href=\"/seeker/saved\">⭐ Saved Jobs</a>");
            html.append("<a href=\"/logout\">🚪 Logout</a>");
            html.append("</nav></div>");
        }

        html.append("<div class=\"dashboard-main\">");
        html.append("<div style=\"display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;\">");
        html.append("<h1 class=\"dashboard-title\" style=\"margin:0;\">Resume Intelligence & Categorized Profile</h1>");
        if (isRecruiter) {
            html.append("<a href=\"/recruiter/candidates\" class=\"button-secondary\" style=\"padding:8px 16px;\">← Back to Candidates</a>");
        } else {
            html.append("<a href=\"/seeker/resume/upload\" class=\"button-secondary\" style=\"padding:8px 16px;\">📤 Re-upload / Update</a>");
        }
        html.append("</div>");

        // Profile Card
        html.append("""
            <div class="resume-profile-header-card">
                <div class="prof-avatar">👤</div>
                <div class="prof-meta">
                    <div style="display:flex;align-items:center;gap:10px;">
                        <h2 style="margin:0;color:#1e293b;">%s</h2>
                        <span class="badge-role">%s</span>
                    </div>
                    <div class="prof-contact-line">
                        <span>📧 %s</span>
                        <span>📱 %s</span>
                        <span>📄 File: %s</span>
                        <span>🕒 Uploaded: %s</span>
                    </div>
                </div>
            </div>
            """.formatted(
                escapeHtml(resume.parsedName.isEmpty() ? session.fullName : resume.parsedName),
                escapeHtml(resume.primaryRole),
                escapeHtml(resume.parsedEmail.isEmpty() ? session.email : resume.parsedEmail),
                escapeHtml(resume.parsedPhone.isEmpty() ? "+91 9876501234" : resume.parsedPhone),
                escapeHtml(resume.fileName),
                escapeHtml(resume.uploadedAt)
            ));

        // SKILL TAXONOMY BADGES (Requested in Feature 2: Programming, Database, Web, Framework, Tools)
        html.append("""
            <div class="dashboard-section" style="margin-top:24px;">
                <div class="section-header-row">
                    <div>
                        <h2 class="section-heading-sm">🔎 EXTRACTED SKILLS TAXONOMY</h2>
                        <div class="section-subtext">%d Skills discovered and categorized by taxonomy engine</div>
                    </div>
                    <a href="/seeker/matching-jobs" class="button-success" style="padding:6px 14px;font-size:12px;">🎯 Match Jobs With These Skills →</a>
                </div>
                <div class="taxonomy-categories-grid">
            """.formatted(allSkills.size()));

        String[] categoryIcons = {"☕", "🗄️", "🌐", "⚡", "☁️", "🏛️"};
        int catIdx = 0;
        for (String cat : ResumeParser.CATEGORY_ORDER) {
            List<String> sks = catSkills.getOrDefault(cat, Collections.emptyList());
            String icon = categoryIcons[catIdx % categoryIcons.length];
            catIdx++;

            html.append("""
                <div class="taxonomy-cat-box">
                    <div class="tax-cat-header">
                        <span class="tax-icon">%s</span>
                        <span class="tax-title">%s</span>
                        <span class="tax-count">(%d)</span>
                    </div>
                    <div class="tax-badges-wrap">
                """.formatted(icon, escapeHtml(cat), sks.size()));

            if (sks.isEmpty()) {
                html.append("<span style=\"font-size:12px;color:#94a3b8;font-style:italic;\">None detected</span>");
            } else {
                for (String sk : sks) {
                    html.append("<span class=\"tax-skill-pill\">✓ ").append(escapeHtml(sk)).append("</span>");
                }
            }
            html.append("</div></div>");
        }
        html.append("</div></div>");

        // DETAILS SECTIONS: Education, Experience, Projects, Certifications, Languages
        html.append("""
            <div class="dashboard-two-col" style="margin-top:24px;">
                <div class="dashboard-card-col">
                    <h3 style="color:#0f172a;margin-top:0;">🎓 Education & Academics</h3>
                    <div class="detail-box-card">%s</div>

                    <h3 style="color:#0f172a;margin-top:20px;">💼 Experience & History</h3>
                    <div class="detail-box-card">%s</div>

                    <h3 style="color:#0f172a;margin-top:20px;">🌐 Languages Known</h3>
                    <div class="detail-box-card">%s</div>
                </div>

                <div class="dashboard-card-col">
                    <h3 style="color:#0f172a;margin-top:0;">🚀 Featured Projects</h3>
                    <div class="detail-box-card">%s</div>

                    <h3 style="color:#0f172a;margin-top:20px;">📜 Certifications & Honors</h3>
                    <div class="detail-box-card">%s</div>

                    <div style="background:#ecfdf5;border:1px solid #a7f3d0;border-radius:10px;padding:20px;margin-top:20px;">
                        <h4 style="margin:0 0 6px 0;color:#065f46;">Ready to discover your career opportunities?</h4>
                        <p style="margin:0 0 14px 0;font-size:13px;color:#047857;">Run SmartHire's Two-Way Matching Engine against active job postings.</p>
                        <a href="/seeker/matching-jobs" class="button" style="display:block;text-align:center;background:#059669;">🎯 FIND JOBS FOR MY RESUME</a>
                    </div>
                </div>
            </div>
            """.formatted(
                escapeHtml(resume.parsedEducation.isEmpty() ? "B.E. Computer Science and Engineering" : resume.parsedEducation),
                escapeHtml(resume.parsedExperience.isEmpty() ? "3 years software development" : resume.parsedExperience),
                escapeHtml(resume.parsedLanguages.isEmpty() ? "English, Tamil, Hindi" : resume.parsedLanguages),
                escapeHtml(resume.parsedProjects.isEmpty() ? "SmartHire Two-Way Matching Portal • Microservices Architecture" : resume.parsedProjects),
                escapeHtml(resume.parsedCertifications.isEmpty() ? "Oracle Certified Java Associate • AWS Cloud Practitioner" : resume.parsedCertifications)
            ));

        html.append("</div></div>");
        html.append("<footer><p>© 2026 SmartHire — Resume Intelligence & Job Matching Portal</p></footer></body></html>");
        sendResponse(exchange, html.toString());
    }

    // ============================================================
    // FEATURE 3 & 7: "JOBS FOR MY RESUME" + "WHY AM I A MATCH?"
    // ============================================================

    private static void seekerMatchingJobs(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        JobDataStore.ResumeRecord resume = JobDataStore.getResumeByUserId(session.userId);
        List<String> userSkills = JobDataStore.getAllSkillsForUser(session.userId);
        if (userSkills.isEmpty() && resume != null && resume.rawText != null) {
            ResumeParser.ParsedResume pr = ResumeParser.parseText(resume.fileName, resume.rawText);
            userSkills = pr.allSkills;
        }

        List<JobDataStore.JobRecord> allJobs = JobDataStore.getAllJobs();
        List<MatchingEngine.JobMatchResult> matches = Collections.emptyList();
        if (!userSkills.isEmpty()) {
            matches = MatchingEngine.findMatchingJobsForSeeker(
                userSkills,
                resume != null ? resume.parsedEducation : "",
                resume != null ? resume.parsedExperience : "",
                "Bangalore",
                allJobs
            );
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Jobs For My Resume - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append("<div class=\"dashboard-sidebar\">");
        html.append("<div class=\"sidebar-user\">👤 ").append(escapeHtml(session.fullName)).append("</div>");
        html.append("<div class=\"sidebar-role\">").append(escapeHtml(resume != null ? resume.primaryRole : "Job Seeker")).append("</div>");
        html.append("<nav class=\"sidebar-nav\">");
        html.append("<a href=\"/seeker/dashboard\">🏠 Dashboard</a>");
        html.append("<a href=\"/seeker/matching-jobs\" class=\"active\">🎯 Jobs For My Resume</a>");
        html.append("<a href=\"/seeker/similar-jobs\">🔄 Similar Job Finder</a>");
        html.append("<a href=\"/seeker/career-path\">🛣️ Career Path</a>");
        html.append("<a href=\"/seeker/resume/view\">📄 Resume Intelligence</a>");
        html.append("<a href=\"/seeker/applications\">📋 My Applications</a>");
        html.append("<a href=\"/seeker/saved\">⭐ Saved Jobs</a>");
        html.append("<a href=\"/logout\">🚪 Logout</a>");
        html.append("</nav></div>");

        html.append("<div class=\"dashboard-main\">");
        html.append("<div class=\"matching-jobs-header-banner\">");
        html.append("<div style=\"display:flex;justify-content:space-between;align-items:flex-start;flex-wrap:wrap;gap:12px;\">");
        html.append("<div>");
        html.append("<h1 class=\"dashboard-title\" style=\"color:#ffffff;margin:0 0 6px 0;\">🎯 Jobs For Your Resume</h1>");
        html.append("<p style=\"color:#e0f2fe;margin:0;font-size:14px;\">");
        html.append("Ranked by algorithmic match percentage against your <strong>").append(userSkills.size()).append(" skills</strong>, education, and experience.");
        html.append("</p></div>");
        html.append("<a href=\"/seeker/similar-jobs\" class=\"button\" style=\"background:#ffffff;color:#0284c7;font-weight:700;\">🔄 View Similar Career Options →</a>");
        html.append("</div></div>");

        if (matches.isEmpty()) {
            html.append("""
                <div class="empty-state" style="margin-top:30px;">
                    <h3>No Resume Found Yet</h3>
                    <p>Upload your resume to calculate your match score across all active vacancies.</p>
                    <a href="/seeker/resume/upload" class="button">Upload Resume Now</a>
                </div>
                """);
        } else {
            html.append("<div class=\"matches-list\" style=\"margin-top:24px;\">");
            for (MatchingEngine.JobMatchResult mr : matches) {
                String pillClass = mr.matchPercentage >= 85 ? "match-pill-high" : mr.matchPercentage >= 70 ? "match-pill-mid" : "match-pill-low";
                html.append("""
                    <div class="match-card-full">
                        <div class="match-card-top">
                            <div class="match-card-title-col">
                                <h3 class="match-job-title"><a href="/apply?job_id=%d">%s</a></h3>
                                <div class="match-job-subtitle">%s · 📍 %s · 💼 %s · 💰 %s</div>
                            </div>
                            <div class="match-score-badge-wrap">
                                <div class="%s">%d%% Match</div>
                            </div>
                        </div>

                        <!-- FEATURE 7: WHY AM I A MATCH? -->
                        <div class="why-match-box">
                            <div class="why-match-heading">🔥 WHY AM I A MATCH? (Explainable Matching Engine)</div>
                            <div class="why-match-badges-grid">
                    """.formatted(
                        mr.job.jobId,
                        escapeHtml(mr.job.jobTitle),
                        escapeHtml(mr.job.companyName),
                        escapeHtml(mr.job.location),
                        escapeHtml(mr.job.jobType),
                        escapeHtml(mr.job.salary),
                        pillClass,
                        mr.matchPercentage
                    ));

                for (String why : mr.whyReasons) {
                    boolean isMissing = why.startsWith("⚠");
                    String cls = isMissing ? "why-badge-missing" : "why-badge-match";
                    html.append("<span class=\"").append(cls).append("\">").append(escapeHtml(why)).append("</span>");
                }

                html.append("""
                            </div>
                        </div>

                        <!-- SKILL GAP TEASER & PROJECTED SCORE -->
                        <div class="match-gap-teaser-row">
                            <div class="gap-text">
                                <strong>Skill Gap Status:</strong> %s
                            </div>
                            <div class="projected-text">
                                Potential Match After Learning: <strong>%d%%*</strong>
                            </div>
                        </div>

                        <!-- ACTIONS -->
                        <div class="match-card-actions-row">
                            <a href="/apply?job_id=%d" class="button" style="padding:9px 20px;">🚀 Apply Now</a>
                            <a href="/seeker/skill-gap?jobId=%d" class="button-secondary" style="padding:9px 18px;">🧩 Full Skill Gap Analysis</a>
                            <form method="POST" action="/seeker/saved/toggle" style="margin:0;">
                                <input type="hidden" name="jobId" value="%d">
                                <input type="hidden" name="returnTo" value="/seeker/matching-jobs">
                                <button type="submit" class="action-btn" style="padding:9px 14px;">⭐ Save Job</button>
                            </form>
                            <button type="button" class="btn-report-job" onclick="openReportModal(%d, '%s', '%s')">🚩 Report Job</button>
                        </div>
                    </div>
                    """.formatted(
                        mr.missingSkills.isEmpty() ? "✓ All required skills matched!" : "Missing " + mr.missingSkills.size() + " skills (" + String.join(", ", mr.missingSkills) + ")",
                        mr.projectedPercentage,
                        mr.job.jobId,
                        mr.job.jobId,
                        mr.job.jobId,
                        mr.job.jobId,
                        escapeHtml(mr.job.jobTitle).replace("'", "\\'"),
                        escapeHtml(mr.job.companyName).replace("'", "\\'")
                    ));
            }
            html.append("</div>");
        }

        // REPORT MODAL INLINE SCRIPT
        html.append(reportModalHtml());

        html.append("</div></div>");
        html.append("<footer><p>© 2026 SmartHire — Resume Intelligence & Job Matching Portal</p></footer></body></html>");
        sendResponse(exchange, html.toString());
    }

    // ============================================================
    // FEATURE 4: SIMILAR JOB FINDER (CAREER CLUSTERS)
    // ============================================================

    private static void seekerSimilarJobs(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        JobDataStore.ResumeRecord resume = JobDataStore.getResumeByUserId(session.userId);
        String primaryRole = (resume != null && resume.primaryRole != null) ? resume.primaryRole : "Java Developer";
        List<JobDataStore.JobRecord> allJobs = JobDataStore.getAllJobs();

        List<MatchingEngine.SimilarRoleOption> similarOptions = MatchingEngine.findSimilarJobs(primaryRole, 94, allJobs);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Similar Job Finder - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append("<div class=\"dashboard-sidebar\">");
        html.append("<div class=\"sidebar-user\">👤 ").append(escapeHtml(session.fullName)).append("</div>");
        html.append("<div class=\"sidebar-role\">").append(escapeHtml(primaryRole)).append("</div>");
        html.append("<nav class=\"sidebar-nav\">");
        html.append("<a href=\"/seeker/dashboard\">🏠 Dashboard</a>");
        html.append("<a href=\"/seeker/matching-jobs\">🎯 Jobs For My Resume</a>");
        html.append("<a href=\"/seeker/similar-jobs\" class=\"active\">🔄 Similar Job Finder</a>");
        html.append("<a href=\"/seeker/career-path\">🛣️ Career Path</a>");
        html.append("<a href=\"/seeker/resume/view\">📄 Resume Intelligence</a>");
        html.append("<a href=\"/seeker/applications\">📋 My Applications</a>");
        html.append("<a href=\"/logout\">🚪 Logout</a>");
        html.append("</nav></div>");

        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">Similar Job Finder & Career Clusters 🔄</h1>");
        html.append("<p class=\"dashboard-subtitle\">Don't restrict yourself to a single job title. SmartHire maps your transferable skills to adjacent career pathways with high demand.</p>");

        html.append("""
            <div class="primary-role-card">
                <div style="font-size:12px;font-weight:700;color:#047857;letter-spacing:0.5px;">BASED ON YOUR RESUME</div>
                <div style="display:flex;justify-content:space-between;align-items:center;margin-top:6px;">
                    <div>
                        <h2 style="margin:0;color:#065f46;">PRIMARY ROLE: %s</h2>
                        <div style="font-size:14px;color:#047857;margin-top:4px;">Core match score calculated from your technical profile</div>
                    </div>
                    <span class="match-pill-high" style="font-size:18px;padding:6px 18px;">94%% Match</span>
                </div>
            </div>

            <div class="dashboard-section" style="margin-top:24px;">
                <h2 class="section-heading-sm">SIMILAR CAREER OPTIONS & ADJACENT OPPORTUNITIES</h2>
                <div class="similar-roles-grid">
            """.formatted(escapeHtml(primaryRole)));

        for (MatchingEngine.SimilarRoleOption opt : similarOptions) {
            String pillClass = opt.matchPercentage >= 85 ? "match-pill-high" : opt.matchPercentage >= 75 ? "match-pill-mid" : "match-pill-low";
            html.append("""
                <div class="similar-role-card">
                    <div style="display:flex;justify-content:space-between;align-items:flex-start;">
                        <div>
                            <h3 class="similar-role-title">%s</h3>
                            <div class="similar-role-vacancies">⚡ %d Open Vacancies on Portal</div>
                        </div>
                        <span class="%s">%d%% Match</span>
                    </div>
                    <div class="similar-role-skills">
                        <span style="font-size:12px;font-weight:700;color:#475569;">Key Transferable Skills:</span><br>
                """.formatted(
                    escapeHtml(opt.title),
                    opt.jobCount,
                    pillClass,
                    opt.matchPercentage
                ));
            for (String sk : opt.keySkills) {
                html.append("<span class=\"skill-tag-sm\">").append(escapeHtml(sk)).append("</span> ");
            }
            html.append("""
                    </div>
                    <div style="margin-top:14px;">
                        <a href="/jobs?keyword=%s" class="button-secondary" style="width:100%%;text-align:center;display:block;">
                            Explore %s Jobs →
                        </a>
                    </div>
                </div>
                """.formatted(
                    java.net.URLEncoder.encode(opt.title, StandardCharsets.UTF_8),
                    escapeHtml(opt.title)
                ));
        }

        html.append("</div></div>");
        html.append("</div></div>");
        html.append("<footer><p>© 2026 SmartHire — Resume Intelligence & Job Matching Portal</p></footer></body></html>");
        sendResponse(exchange, html.toString());
    }

    // ============================================================
    // FEATURE 5: SKILL GAP ANALYSIS
    // ============================================================

    private static void seekerSkillGap(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI());
        int jobId = 1;
        try { jobId = Integer.parseInt(query.getOrDefault("jobId", "1")); }
        catch (Exception ignored) {}

        JobDataStore.JobRecord job = JobDataStore.getJobById(jobId);
        if (job == null) {
            sendRedirect(exchange, "/seeker/matching-jobs");
            return;
        }

        List<String> userSkills = JobDataStore.getAllSkillsForUser(session.userId);
        MatchingEngine.SkillGapReport report = MatchingEngine.analyzeSkillGap(job, userSkills);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Skill Gap Analysis - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append("<div class=\"dashboard-sidebar\">");
        html.append("<div class=\"sidebar-user\">👤 ").append(escapeHtml(session.fullName)).append("</div>");
        html.append("<div class=\"sidebar-role\">Job Seeker</div>");
        html.append("<nav class=\"sidebar-nav\">");
        html.append("<a href=\"/seeker/dashboard\">🏠 Dashboard</a>");
        html.append("<a href=\"/seeker/matching-jobs\">🎯 Jobs For My Resume</a>");
        html.append("<a href=\"/seeker/similar-jobs\">🔄 Similar Job Finder</a>");
        html.append("<a href=\"/seeker/career-path\">🛣️ Career Path</a>");
        html.append("<a href=\"/seeker/resume/view\">📄 Resume Intelligence</a>");
        html.append("<a href=\"/seeker/applications\">📋 My Applications</a>");
        html.append("<a href=\"/logout\">🚪 Logout</a>");
        html.append("</nav></div>");

        html.append("<div class=\"dashboard-main\">");
        html.append("<a href=\"/seeker/matching-jobs\" class=\"view-all-link\" style=\"display:inline-block;margin-bottom:12px;\">← Back to Matching Jobs</a>");
        html.append("<h1 class=\"dashboard-title\">Skill Gap Analysis & Match Projection 🧩</h1>");
        html.append("<p class=\"dashboard-subtitle\">Detailed diagnosis for <strong>").append(escapeHtml(job.jobTitle)).append("</strong> at ").append(escapeHtml(job.companyName)).append("</p>");

        html.append("""
            <div class="skill-gap-hero-card">
                <div class="gap-score-col">
                    <div class="gap-score-title">YOUR CURRENT MATCH</div>
                    <div class="gap-score-num">%d%%</div>
                    <div style="font-size:12px;color:#64748b;margin-top:4px;">Based on required skills</div>
                </div>
                <div class="gap-meter-col">
                    <div style="font-weight:700;margin-bottom:6px;display:flex;justify-content:space-between;">
                        <span>Skills You Have:</span>
                        <span>%d%% Matched</span>
                    </div>
                    <div class="skill-progress-bar" style="height:22px;">
                        <div class="skill-progress-fill" style="width:%d%%;background:#10b981;"></div>
                    </div>
                    <div class="skills-matched-pill-list" style="margin-top:12px;">
            """.formatted(
                report.currentMatchPercentage,
                report.currentMatchPercentage,
                report.currentMatchPercentage
            ));

        for (String h : report.skillsYouHave) {
            html.append("<span class=\"why-badge-match\">✓ ").append(escapeHtml(h)).append("</span>");
        }
        html.append("</div></div></div>");

        // MISSING SKILLS SECTION
        html.append("""
            <div class="dashboard-section" style="margin-top:24px;">
                <h2 class="section-heading-sm" style="color:#b45309;">⚠ MISSING SKILLS REQUIRED FOR THIS ROLE</h2>
                <div class="missing-skills-list-grid">
            """);

        if (report.missingSkills.isEmpty()) {
            html.append("<p style=\"color:#16a34a;font-weight:600;\">✓ Congratulations! You possess all required technical skills for this vacancy!</p>");
        } else {
            for (String m : report.missingSkills) {
                html.append("""
                    <div class="missing-skill-card">
                        <div class="missing-badge">⚠ %s</div>
                        <div style="font-size:12px;color:#475569;margin-top:6px;">
                            Required by employer for production systems.
                        </div>
                    </div>
                    """.formatted(escapeHtml(m)));
            }
        }
        html.append("</div></div>");

        // IMPROVE YOUR MATCH
        html.append("""
            <div class="dashboard-section" style="margin-top:24px;">
                <div style="display:flex;justify-content:space-between;align-items:center;">
                    <div>
                        <h2 class="section-heading-sm" style="color:#0369a1;">🚀 IMPROVE YOUR MATCH (Learning Roadmap)</h2>
                        <div class="section-subtext">Step-by-step skill acquisition plan to maximize hireability</div>
                    </div>
                    <div class="potential-jump-pill">
                        Projected Score: <strong>%d%% → %d%%*</strong>
                    </div>
                </div>
                <div class="learning-steps-list" style="margin-top:16px;">
            """.formatted(report.currentMatchPercentage, report.projectedMatchPercentage));

        for (MatchingEngine.LearningStep step : report.learningSteps) {
            html.append("""
                <div class="learning-step-row">
                    <div class="step-num-circle">%d</div>
                    <div class="step-text-wrap">
                        <div style="font-weight:700;color:#1e293b;font-size:15px;">Learn %s <span class="step-boost-tag">+%d%% Match Impact</span></div>
                        <div style="font-size:13px;color:#475569;margin-top:4px;">%s</div>
                    </div>
                </div>
                """.formatted(step.stepNumber, escapeHtml(step.skillName), step.impactPoints, escapeHtml(step.recommendation)));
        }

        html.append("""
                </div>
                <div class="disclaimer-note">
                    <strong>*Important Note:</strong> %s
                </div>
            </div>

            <div style="display:flex;gap:14px;margin-top:24px;">
                <a href="/apply?job_id=%d" class="button" style="padding:12px 28px;font-size:15px;">🚀 Proceed to Apply For This Job</a>
                <a href="/seeker/matching-jobs" class="button-secondary" style="padding:12px 24px;font-size:15px;">← Back to All Matching Jobs</a>
            </div>
            """.formatted(
                escapeHtml(report.disclaimer),
                job.jobId
            ));

        html.append("</div></div>");
        html.append("<footer><p>© 2026 SmartHire — Resume Intelligence & Job Matching Portal</p></footer></body></html>");
        sendResponse(exchange, html.toString());
    }

    // ============================================================
    // FEATURE 6: CAREER PATH
    // ============================================================

    private static void seekerCareerPath(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        JobDataStore.ResumeRecord resume = JobDataStore.getResumeByUserId(session.userId);
        List<String> userSkills = JobDataStore.getAllSkillsForUser(session.userId);
        List<JobDataStore.JobRecord> allJobs = JobDataStore.getAllJobs();

        MatchingEngine.CareerPath cp = MatchingEngine.generateCareerPath(
            resume != null ? resume.primaryRole : "Java Developer",
            resume != null ? resume.parsedExperience : "3 years",
            userSkills,
            allJobs
        );

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Career Path Progression - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append("<div class=\"dashboard-sidebar\">");
        html.append("<div class=\"sidebar-user\">👤 ").append(escapeHtml(session.fullName)).append("</div>");
        html.append("<div class=\"sidebar-role\">Job Seeker</div>");
        html.append("<nav class=\"sidebar-nav\">");
        html.append("<a href=\"/seeker/dashboard\">🏠 Dashboard</a>");
        html.append("<a href=\"/seeker/matching-jobs\">🎯 Jobs For My Resume</a>");
        html.append("<a href=\"/seeker/similar-jobs\">🔄 Similar Job Finder</a>");
        html.append("<a href=\"/seeker/career-path\" class=\"active\">🛣️ Career Path</a>");
        html.append("<a href=\"/seeker/resume/view\">📄 Resume Intelligence</a>");
        html.append("<a href=\"/seeker/applications\">📋 My Applications</a>");
        html.append("<a href=\"/logout\">🚪 Logout</a>");
        html.append("</nav></div>");

        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">Personalized Career Path Progression 🛣️</h1>");
        html.append("<p class=\"dashboard-subtitle\">Track: <strong>").append(escapeHtml(cp.primaryTrack)).append("</strong> · Based on your skills and verified experience.</p>");

        html.append("<div class=\"career-ladder-container\">");
        for (int i = 0; i < cp.levels.size(); i++) {
            MatchingEngine.CareerLevel lvl = cp.levels.get(i);
            String cardCls = "COMPLETED".equals(lvl.status) ? "ladder-card-completed" : "CURRENT".equals(lvl.status) ? "ladder-card-current" : "ladder-card-target";
            String tagCls = "COMPLETED".equals(lvl.status) ? "badge-success" : "CURRENT".equals(lvl.status) ? "badge-info" : "badge-role";

            html.append("""
                <div class="ladder-step-card %s">
                    <div class="ladder-level-badge">LEVEL %d</div>
                    <div class="ladder-content">
                        <div style="display:flex;justify-content:space-between;align-items:flex-start;flex-wrap:wrap;gap:8px;">
                            <div>
                                <h3 class="ladder-title">%s</h3>
                                <div class="ladder-meta">Typical Experience: %s · Status: <span class="%s">%s</span></div>
                            </div>
                            <div class="ladder-jobs-pill">
                                💼 %d Active Jobs at this Level
                            </div>
                        </div>

                        <div class="ladder-skills-row" style="margin-top:12px;">
                            <div style="font-size:12px;font-weight:700;color:#334155;margin-bottom:4px;">Core Competencies at this Level:</div>
                """.formatted(
                    cardCls,
                    lvl.level,
                    escapeHtml(lvl.title),
                    escapeHtml(lvl.experienceRange),
                    tagCls,
                    lvl.status,
                    lvl.matchingJobsAvailable
                ));

            for (String r : lvl.requiredSkills) {
                html.append("<span class=\"skill-tag-sm\">").append(escapeHtml(r)).append("</span> ");
            }

            if (!lvl.skillsToLearn.isEmpty() && !"COMPLETED".equals(lvl.status)) {
                html.append("""
                    <div style="margin-top:10px;padding:8px 12px;background:#fef3c7;border-radius:6px;font-size:12px;color:#92400e;">
                        <strong>Skills to Level Up:</strong> %s
                    </div>
                    """.formatted(escapeHtml(String.join(", ", lvl.skillsToLearn))));
            }

            html.append("""
                        <div style="margin-top:12px;">
                            <a href="/jobs?keyword=%s" class="button-secondary" style="font-size:12px;padding:6px 14px;">
                                View Jobs at this Level →
                            </a>
                        </div>
                    </div>
                </div>
                """.formatted(java.net.URLEncoder.encode(lvl.title, StandardCharsets.UTF_8)));
        }
        html.append("</div>");

        html.append("</div></div>");
        html.append("<footer><p>© 2026 SmartHire — Resume Intelligence & Job Matching Portal</p></footer></body></html>");
        sendResponse(exchange, html.toString());
    }

    // ============================================================
    // FEATURE 9: SMART JOB ALERTS & NOTIFICATIONS
    // ============================================================

    private static void seekerNotifications(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        List<JobDataStore.NotificationRecord> notifs = JobDataStore.getNotificationsByUser(session.userId);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Smart Alerts & Notifications - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append("<div class=\"dashboard-sidebar\">");
        html.append("<div class=\"sidebar-user\">👤 ").append(escapeHtml(session.fullName)).append("</div>");
        html.append("<div class=\"sidebar-role\">Job Seeker</div>");
        html.append("<nav class=\"sidebar-nav\">");
        html.append("<a href=\"/seeker/dashboard\">🏠 Dashboard</a>");
        html.append("<a href=\"/seeker/matching-jobs\">🎯 Jobs For My Resume</a>");
        html.append("<a href=\"/seeker/similar-jobs\">🔄 Similar Job Finder</a>");
        html.append("<a href=\"/seeker/career-path\">🛣️ Career Path</a>");
        html.append("<a href=\"/seeker/notifications\" class=\"active\">🔔 Notifications</a>");
        html.append("<a href=\"/seeker/applications\">📋 My Applications</a>");
        html.append("<a href=\"/logout\">🚪 Logout</a>");
        html.append("</nav></div>");

        html.append("<div class=\"dashboard-main\">");
        html.append("<div style=\"display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;\">");
        html.append("<h1 class=\"dashboard-title\" style=\"margin:0;\">Smart Job Alerts & Notifications 🔔</h1>");
        html.append("<form method=\"POST\" action=\"/seeker/notifications/read\" style=\"margin:0;\">");
        html.append("<button type=\"submit\" class=\"button-secondary\" style=\"padding:8px 16px;\">✓ Mark All as Read</button>");
        html.append("</form></div>");
        html.append("<p class=\"dashboard-subtitle\">Resume-driven alerts automatically triggered whenever a newly posted job matches 80%+ of your resume.</p>");

        if (notifs.isEmpty()) {
            html.append("<div class=\"empty-state\"><p>No notifications yet. You will receive alerts when new matching jobs are posted.</p></div>");
        } else {
            html.append("<div class=\"notifications-list\">");
            for (JobDataStore.NotificationRecord n : notifs) {
                String cardCls = n.isRead ? "notif-card notif-read" : "notif-card notif-unread";
                String icon = "INVITATION".equals(n.type) ? "📩" : "INTERVIEW".equals(n.type) ? "📅" : "🔥";
                html.append("""
                    <div class="%s">
                        <div class="notif-icon">%s</div>
                        <div class="notif-content">
                            <div class="notif-title-row">
                                <span class="notif-title">%s</span>
                                <span class="notif-date">%s</span>
                            </div>
                            <div class="notif-msg">%s</div>
                            <div style="margin-top:8px;">
                                <a href="%s" class="notif-link">View Details →</a>
                            </div>
                        </div>
                    </div>
                    """.formatted(
                        cardCls,
                        icon,
                        escapeHtml(n.title),
                        escapeHtml(n.createdAt),
                        escapeHtml(n.message),
                        escapeHtml(n.link)
                    ));
            }
            html.append("</div>");
        }

        html.append("</div></div>");
        html.append("<footer><p>© 2026 SmartHire — Resume Intelligence & Job Matching Portal</p></footer></body></html>");
        sendResponse(exchange, html.toString());
    }

    private static void seekerMarkNotificationsRead(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session != null) {
            JobDataStore.markNotificationsAsRead(session.userId);
        }
        sendRedirect(exchange, "/seeker/notifications");
    }

    // ============================================================
    // INTERVIEWS (SEEKER VIEW)
    // ============================================================

    private static void seekerInterviews(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"JOB_SEEKER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        List<JobDataStore.InterviewRecord> interviews = JobDataStore.getInterviewsByUser(session.userId);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>My Interviews - SmartHire</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append("<div class=\"dashboard-sidebar\">");
        html.append("<div class=\"sidebar-user\">👤 ").append(escapeHtml(session.fullName)).append("</div>");
        html.append("<div class=\"sidebar-role\">Job Seeker</div>");
        html.append("<nav class=\"sidebar-nav\">");
        html.append("<a href=\"/seeker/dashboard\">🏠 Dashboard</a>");
        html.append("<a href=\"/seeker/matching-jobs\">🎯 Jobs For My Resume</a>");
        html.append("<a href=\"/seeker/interviews\" class=\"active\">📅 Interviews</a>");
        html.append("<a href=\"/seeker/applications\">📋 My Applications</a>");
        html.append("<a href=\"/logout\">🚪 Logout</a>");
        html.append("</nav></div>");

        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">Scheduled Interviews 📅</h1>");
        html.append("<p class=\"dashboard-subtitle\">Interviews arranged by hiring managers and recruiters.</p>");

        if (interviews.isEmpty()) {
            html.append("<div class=\"empty-state\"><p>No interviews scheduled yet. Keep applying to high-match roles!</p></div>");
        } else {
            html.append("<div class=\"interviews-grid\">");
            for (JobDataStore.InterviewRecord ir : interviews) {
                html.append("""
                    <div class="interview-card">
                        <div style="display:flex;justify-content:space-between;align-items:flex-start;">
                            <div>
                                <h3 style="margin:0;color:#1e293b;">%s</h3>
                                <div style="color:#0284c7;font-weight:600;margin-top:2px;">🏢 %s</div>
                            </div>
                            <span class="badge-success">%s</span>
                        </div>
                        <div style="margin-top:12px;font-size:14px;color:#334155;">
                            <div>📅 <strong>Date & Time:</strong> %s</div>
                            <div style="margin-top:4px;">💻 <strong>Format:</strong> %s</div>
                            <div style="margin-top:4px;">📝 <strong>Notes:</strong> %s</div>
                        </div>
                        <div style="margin-top:16px;">
                            <a href="%s" target="_blank" class="button" style="padding:8px 18px;display:inline-block;">Join Video Call 🎥</a>
                        </div>
                    </div>
                    """.formatted(
                        escapeHtml(ir.jobTitle),
                        escapeHtml(ir.companyName),
                        escapeHtml(ir.status),
                        escapeHtml(ir.interviewDate),
                        escapeHtml(ir.interviewType),
                        escapeHtml(ir.notes),
                        escapeHtml(ir.meetingLink.isEmpty() ? "#" : ir.meetingLink)
                    ));
            }
            html.append("</div>");
        }

        html.append("</div></div>");
        html.append("<footer><p>© 2026 SmartHire — Resume Intelligence & Job Matching Portal</p></footer></body></html>");
        sendResponse(exchange, html.toString());
    }

    // ============================================================
    // FEATURE 10: JOB QUALITY / SCAM REPORTING (SEEKER ACTION)
    // ============================================================

    private static void reportJob(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendRedirect(exchange, "/jobs");
            return;
        }

        SessionData session = getSession(exchange);
        Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        int jobId = 0;
        try { jobId = Integer.parseInt(data.getOrDefault("jobId", "0")); }
        catch (Exception ignored) {}

        String email = (session != null) ? session.email : data.getOrDefault("email", "anonymous@seeker.com");
        int userId = (session != null) ? session.userId : 0;
        String reason = data.getOrDefault("reason", "Suspicious job");
        String details = data.getOrDefault("details", "");

        if (jobId > 0) {
            JobDataStore.saveJobReport(jobId, userId, email, reason, details);
        }

        String returnTo = data.getOrDefault("returnTo", "/jobs");
        sendRedirect(exchange, returnTo + (returnTo.contains("?") ? "&" : "?") + "reported=1");
    }

    private static String reportModalHtml() {
        return """
            <div id="reportModal" class="modal-overlay" style="display:none;">
                <div class="modal-card">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
                        <h3 style="margin:0;color:#dc2626;">🛡️ Report Suspicious Job</h3>
                        <button type="button" onclick="closeReportModal()" style="background:none;border:none;font-size:20px;cursor:pointer;">&times;</button>
                    </div>
                    <p style="font-size:13px;color:#64748b;margin-bottom:14px;">
                        SmartHire Trust & Safety: Help us keep the platform safe by reporting fraudulent listings or deceptive companies.
                    </p>
                    <form method="POST" action="/report-job">
                        <input type="hidden" name="jobId" id="reportJobId" value="0">
                        <input type="hidden" name="returnTo" value="/seeker/matching-jobs">
                        
                        <label><strong>Job:</strong> <span id="reportJobTitle" style="color:#0f172a;font-weight:700;"></span> (<span id="reportJobCompany"></span>)</label>
                        
                        <label style="margin-top:10px;">Reason for Report *</label>
                        <select name="reason" required style="width:100%;padding:9px;border-radius:6px;border:1px solid #cbd5e1;margin-bottom:12px;">
                            <option value="Suspicious job">○ Suspicious job</option>
                            <option value="Fake company">○ Fake company</option>
                            <option value="Asking for money">○ Asking for money</option>
                            <option value="Incorrect information">○ Incorrect information</option>
                            <option value="Duplicate job">○ Duplicate job</option>
                            <option value="Other">○ Other</option>
                        </select>

                        <label>Additional Details</label>
                        <textarea name="details" rows="3" placeholder="Please provide any context or evidence..." style="width:100%;padding:8px;border-radius:6px;border:1px solid #cbd5e1;font-family:inherit;"></textarea>

                        <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:16px;">
                            <button type="button" onclick="closeReportModal()" class="button-secondary">Cancel</button>
                            <button type="submit" class="button" style="background:#dc2626;">Submit Report</button>
                        </div>
                    </form>
                </div>
            </div>
            <script>
                function openReportModal(jobId, title, company) {
                    document.getElementById('reportJobId').value = jobId;
                    document.getElementById('reportJobTitle').innerText = title;
                    document.getElementById('reportJobCompany').innerText = company;
                    document.getElementById('reportModal').style.display = 'flex';
                }
                function closeReportModal() {
                    document.getElementById('reportModal').style.display = 'none';
                }
            </script>
            """;
    }

    // ============================================================
    // RECRUITER DASHBOARD
    // ============================================================

    private static void recruiterDashboard(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        List<JobDataStore.JobRecord> myJobs = JobDataStore.getJobsByRecruiter(session.userId);
        int totalApplications = myJobs.stream().mapToInt(j -> JobDataStore.getApplicationCountForJob(j.jobId)).sum();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Recruiter Dashboard - JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append(recruiterSidebar(session, "dashboard"));
        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">Recruiter Dashboard 📋</h1>");

        if (!session.companyName.isEmpty())
            html.append("<p class=\"company-badge\">🏢 ").append(escapeHtml(session.companyName)).append("</p>");

        html.append("<div class=\"stats-grid\">");
        html.append("<div class=\"stat-card\"><div class=\"stat-num\">").append(myJobs.size()).append("</div><div class=\"stat-label\">Jobs Posted</div></div>");
        html.append("<div class=\"stat-card stat-success\"><div class=\"stat-num\">").append(totalApplications).append("</div><div class=\"stat-label\">Total Applications</div></div>");
        long activeJobs = myJobs.stream().filter(j -> "OPEN".equals(j.status)).count();
        html.append("<div class=\"stat-card\"><div class=\"stat-num\">").append(activeJobs).append("</div><div class=\"stat-label\">Active Jobs</div></div>");
        html.append("</div>");

        html.append("<div class=\"dashboard-section\">");
        html.append("<div style=\"display:flex; justify-content:space-between; align-items:center; margin-bottom:15px;\">");
        html.append("<h2>My Recent Job Postings</h2>");
        html.append("<a href=\"/recruiter/jobs/add\" class=\"btn-add\">+ Post New Job</a>");
        html.append("</div>");

        if (myJobs.isEmpty()) {
            html.append("<p class=\"empty-msg\">No jobs posted yet. <a href=\"/recruiter/jobs/add\">Post your first job!</a></p>");
        } else {
            html.append("<table class=\"data-table\"><thead><tr><th>Job Title</th><th>Location</th><th>Type</th><th>Applications</th><th>Status</th><th>Actions</th></tr></thead><tbody>");
            for (JobDataStore.JobRecord job : myJobs.subList(0, Math.min(5, myJobs.size()))) {
                int appCount = JobDataStore.getApplicationCountForJob(job.jobId);
                html.append("<tr>")
                    .append("<td><strong>").append(escapeHtml(job.jobTitle)).append("</strong></td>")
                    .append("<td>📍 ").append(escapeHtml(job.location)).append("</td>")
                    .append("<td>").append(escapeHtml(job.jobType)).append("</td>")
                    .append("<td><span class=\"badge\">").append(appCount).append("</span></td>")
                    .append("<td><span class=\"status-badge ").append("OPEN".equals(job.status) ? "status-success" : "status-danger").append("\">").append(job.status).append("</span></td>")
                    .append("<td>")
                    .append("<a href=\"/recruiter/applications?job_id=").append(job.jobId).append("\" class=\"action-btn\">👁 View</a> ")
                    .append("<a href=\"/recruiter/jobs/edit?job_id=").append(job.jobId).append("\" class=\"action-btn\">✏️ Edit</a> ")
                    .append("<a href=\"/recruiter/jobs/delete?job_id=").append(job.jobId).append("\" class=\"action-btn action-danger\" onclick=\"return confirm('Delete this job?')\">🗑 Del</a>")
                    .append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div></div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");

        sendResponse(exchange, html.toString());
    }

    private static String recruiterSidebar(SessionData session, String active) {
        return "<div class=\"dashboard-sidebar\">"
            + "<div class=\"sidebar-user\">🏢 " + escapeHtml(session.companyName.isEmpty() ? session.fullName : session.companyName) + "</div>"
            + "<div class=\"sidebar-role\">Recruiter</div>"
            + "<nav class=\"sidebar-nav\">"
            + "<a href=\"/recruiter/dashboard\"" + ("dashboard".equals(active) ? " class=\"active\"" : "") + ">🏠 Dashboard</a>"
            + "<a href=\"/recruiter/jobs\"" + ("jobs".equals(active) ? " class=\"active\"" : "") + ">📋 My Jobs</a>"
            + "<a href=\"/recruiter/jobs/add\"" + ("add".equals(active) ? " class=\"active\"" : "") + ">➕ Post New Job</a>"
            + "<a href=\"/recruiter/applications\"" + ("applications".equals(active) ? " class=\"active\"" : "") + ">👥 Applications</a>"
            + "<a href=\"/recruiter/candidates\"" + ("candidates".equals(active) ? " class=\"active\"" : "") + ">🔍 Discover Candidates</a>"
            + "<a href=\"/recruiter/profile\"" + ("profile".equals(active) ? " class=\"active\"" : "") + ">🏢 Company Profile</a>"
            + "<a href=\"/jobs\">🔍 Browse All Jobs</a>"
            + "<a href=\"/logout\">🚪 Logout</a>"
            + "</nav></div>";
    }

    private static void recruiterJobs(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        List<JobDataStore.JobRecord> myJobs = JobDataStore.getJobsByRecruiter(session.userId);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>My Jobs - JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append(recruiterSidebar(session, "jobs"));
        html.append("<div class=\"dashboard-main\">");
        html.append("<div style=\"display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;\">");
        html.append("<h1 class=\"dashboard-title\">My Job Postings</h1>");
        html.append("<a href=\"/recruiter/jobs/add\" class=\"btn-add\">+ Post New Job</a>");
        html.append("</div>");

        if (myJobs.isEmpty()) {
            html.append("<div class=\"empty-state\"><p>No jobs posted yet.</p><a href=\"/recruiter/jobs/add\" class=\"button\">Post Your First Job</a></div>");
        } else {
            html.append("<table class=\"data-table\"><thead><tr><th>#</th><th>Job Title</th><th>Location</th><th>Salary</th><th>Type</th><th>Applications</th><th>Status</th><th>Actions</th></tr></thead><tbody>");
            int i = 1;
            for (JobDataStore.JobRecord job : myJobs) {
                int appCount = JobDataStore.getApplicationCountForJob(job.jobId);
                html.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td><strong>").append(escapeHtml(job.jobTitle)).append("</strong></td>")
                    .append("<td>📍 ").append(escapeHtml(job.location)).append("</td>")
                    .append("<td>").append(escapeHtml(job.salary)).append("</td>")
                    .append("<td>").append(escapeHtml(job.jobType)).append("</td>")
                    .append("<td><a href=\"/recruiter/applications?job_id=").append(job.jobId).append("\" class=\"badge-link\">").append(appCount).append(" apps</a></td>")
                    .append("<td><span class=\"status-badge ").append("OPEN".equals(job.status) ? "status-success" : "status-danger").append("\">").append(job.status).append("</span></td>")
                    .append("<td>")
                    .append("<a href=\"/recruiter/applications?job_id=").append(job.jobId).append("\" class=\"action-btn\">👁 Apps</a> ")
                    .append("<a href=\"/recruiter/jobs/edit?job_id=").append(job.jobId).append("\" class=\"action-btn\">✏️ Edit</a> ");
                if ("OPEN".equals(job.status)) {
                    html.append("<a href=\"/recruiter/jobs/status?job_id=").append(job.jobId).append("&status=CLOSED\" class=\"action-btn action-danger\" onclick=\"return confirm('Close this job posting?')\">🔒 Close</a> ");
                } else {
                    html.append("<a href=\"/recruiter/jobs/status?job_id=").append(job.jobId).append("&status=OPEN\" class=\"action-btn action-success\" onclick=\"return confirm('Reopen this job posting?')\">🔓 Reopen</a> ");
                }
                html.append("<a href=\"/recruiter/jobs/delete?job_id=").append(job.jobId).append("\" class=\"action-btn action-danger\" onclick=\"return confirm('Delete this job posting?')\">🗑 Delete</a>")
                    .append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");

        sendResponse(exchange, html.toString());
    }

    private static void recruiterAddJob(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseFormData(body);

            String jobTitle = data.getOrDefault("jobTitle", "").trim();
            String description = data.getOrDefault("description", "").trim();
            String skillsRequired = data.getOrDefault("skillsRequired", "").trim();
            String qualification = data.getOrDefault("qualification", "").trim();
            String experience = data.getOrDefault("experience", "").trim();
            String salary = data.getOrDefault("salary", "").trim();
            String location = data.getOrDefault("location", "").trim();
            String jobType = data.getOrDefault("jobType", "Full-time").trim();

            if (jobTitle.isEmpty() || location.isEmpty()) {
                sendResponse(exchange, errorPage("Validation Error", "Job Title and Location are required.", "/recruiter/jobs/add"));
                return;
            }

            boolean saved = JobDataStore.addJob(session.userId, session.companyName,
                jobTitle, description, skillsRequired, qualification, experience, salary, location, jobType);

            if (saved) {
                sendRedirect(exchange, "/recruiter/jobs?success=1");
            } else {
                sendResponse(exchange, errorPage("Error", "Failed to post job. Please try again.", "/recruiter/jobs/add"));
            }
            return;
        }

        String html = "<!DOCTYPE html><html><head><title>Post New Job - JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>"
            + navHtml(session)
            + "<div class=\"dashboard-container\">"
            + recruiterSidebar(session, "add")
            + "<div class=\"dashboard-main\">"
            + "<h1 class=\"dashboard-title\">Post New Job</h1>"
            + "<form method=\"POST\" action=\"/recruiter/jobs/add\" class=\"form-card\" style=\"width:100%; max-width:700px;\">"
            + "<label>Job Title *</label><input type=\"text\" name=\"jobTitle\" required placeholder=\"e.g. Senior Java Developer\">"
            + "<label>Job Description *</label><textarea name=\"description\" rows=\"4\" style=\"width:100%; padding:10px; border-radius:6px; border:1px solid #ccc; box-sizing:border-box;\" placeholder=\"Describe the role and responsibilities...\"></textarea>"
            + "<label>Skills Required</label><input type=\"text\" name=\"skillsRequired\" placeholder=\"e.g. Java, Spring Boot, Hibernate, MySQL\">"
            + "<label>Qualification</label><input type=\"text\" name=\"qualification\" placeholder=\"e.g. B.E./B.Tech in Computer Science\">"
            + "<label>Experience Required</label><input type=\"text\" name=\"experience\" placeholder=\"e.g. 2-4 years\">"
            + "<label>Salary</label><input type=\"text\" name=\"salary\" placeholder=\"e.g. ₹6-10 LPA\">"
            + "<label>Location *</label><input type=\"text\" name=\"location\" required placeholder=\"e.g. Bangalore, Chennai, Mumbai\">"
            + "<label>Job Type</label>"
            + "<select name=\"jobType\"><option value=\"Full-time\">Full-time</option><option value=\"Part-time\">Part-time</option><option value=\"Contract\">Contract</option><option value=\"Internship\">Internship</option></select>"
            + "<button type=\"submit\">📢 Post Job</button>"
            + "</form>"
            + "</div></div>"
            + "<footer><p>© 2026 JobPortal System</p></footer></body></html>";

        sendResponse(exchange, html);
    }

    private static void recruiterEditJob(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseFormData(body);

            int jobId = Integer.parseInt(data.getOrDefault("jobId", "0"));
            String jobTitle = data.getOrDefault("jobTitle", "").trim();
            String description = data.getOrDefault("description", "").trim();
            String skillsRequired = data.getOrDefault("skillsRequired", "").trim();
            String qualification = data.getOrDefault("qualification", "").trim();
            String experience = data.getOrDefault("experience", "").trim();
            String salary = data.getOrDefault("salary", "").trim();
            String location = data.getOrDefault("location", "").trim();
            String jobType = data.getOrDefault("jobType", "Full-time").trim();
            String status = data.getOrDefault("status", "OPEN").trim();

            JobDataStore.updateJob(jobId, session.userId, jobTitle, description, skillsRequired,
                qualification, experience, salary, location, jobType, status);

            sendRedirect(exchange, "/recruiter/jobs");
            return;
        }

        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
        int jobId = Integer.parseInt(queryParams.getOrDefault("job_id", "0"));
        JobDataStore.JobRecord job = JobDataStore.getJobById(jobId);

        if (job == null) {
            sendResponse(exchange, errorPage("Not Found", "Job not found.", "/recruiter/jobs"));
            return;
        }

        String html = "<!DOCTYPE html><html><head><title>Edit Job - JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>"
            + navHtml(session)
            + "<div class=\"dashboard-container\">"
            + recruiterSidebar(session, "jobs")
            + "<div class=\"dashboard-main\">"
            + "<h1 class=\"dashboard-title\">Edit Job</h1>"
            + "<form method=\"POST\" action=\"/recruiter/jobs/edit\" class=\"form-card\" style=\"width:100%; max-width:700px;\">"
            + "<input type=\"hidden\" name=\"jobId\" value=\"" + job.jobId + "\">"
            + "<label>Job Title *</label><input type=\"text\" name=\"jobTitle\" required value=\"" + escapeHtml(job.jobTitle) + "\">"
            + "<label>Job Description</label><textarea name=\"description\" rows=\"4\" style=\"width:100%; padding:10px; border-radius:6px; border:1px solid #ccc; box-sizing:border-box;\">" + escapeHtml(job.description) + "</textarea>"
            + "<label>Skills Required</label><input type=\"text\" name=\"skillsRequired\" value=\"" + escapeHtml(job.skillsRequired) + "\">"
            + "<label>Qualification</label><input type=\"text\" name=\"qualification\" value=\"" + escapeHtml(job.qualification) + "\">"
            + "<label>Experience Required</label><input type=\"text\" name=\"experience\" value=\"" + escapeHtml(job.experienceRequired != null ? job.experienceRequired : "") + "\">"
            + "<label>Salary</label><input type=\"text\" name=\"salary\" value=\"" + escapeHtml(job.salary) + "\">"
            + "<label>Location *</label><input type=\"text\" name=\"location\" required value=\"" + escapeHtml(job.location) + "\">"
            + "<label>Job Type</label>"
            + "<select name=\"jobType\">"
            + "<option value=\"Full-time\"" + ("Full-time".equals(job.jobType) ? " selected" : "") + ">Full-time</option>"
            + "<option value=\"Part-time\"" + ("Part-time".equals(job.jobType) ? " selected" : "") + ">Part-time</option>"
            + "<option value=\"Contract\"" + ("Contract".equals(job.jobType) ? " selected" : "") + ">Contract</option>"
            + "<option value=\"Internship\"" + ("Internship".equals(job.jobType) ? " selected" : "") + ">Internship</option>"
            + "</select>"
            + "<label>Status</label>"
            + "<select name=\"status\">"
            + "<option value=\"OPEN\"" + ("OPEN".equals(job.status) ? " selected" : "") + ">OPEN</option>"
            + "<option value=\"CLOSED\"" + ("CLOSED".equals(job.status) ? " selected" : "") + ">CLOSED</option>"
            + "</select>"
            + "<button type=\"submit\">✅ Update Job</button>"
            + "</form>"
            + "</div></div>"
            + "<footer><p>© 2026 JobPortal System</p></footer></body></html>";

        sendResponse(exchange, html);
    }

    private static void recruiterDeleteJob(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
        int jobId = Integer.parseInt(queryParams.getOrDefault("job_id", "0"));

        JobDataStore.deleteJob(jobId, session.userId);
        sendRedirect(exchange, "/recruiter/jobs");
    }

    private static void recruiterApplications(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
        String jobIdStr = queryParams.get("job_id");
        int filterJobId = -1;
        if (jobIdStr != null && !jobIdStr.isEmpty()) {
            try { filterJobId = Integer.parseInt(jobIdStr); } catch (Exception ignored) {}
        }
        String statusFilter = queryParams.getOrDefault("status", "ALL");

        List<JobDataStore.JobRecord> myJobs = JobDataStore.getJobsByRecruiter(session.userId);
        List<Map<String, String>> applications = JobDataStore.getApplicationsForRecruiter(session.userId, filterJobId);

        if (!"ALL".equalsIgnoreCase(statusFilter)) {
            applications = applications.stream()
                .filter(a -> statusFilter.equalsIgnoreCase(a.get("status")))
                .toList();
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Applications - JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append(recruiterSidebar(session, "applications"));
        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">Candidate Applications 👥</h1>");
        html.append("<p class=\"dashboard-subtitle\">Review candidate profiles, shortlist talent, and make hiring decisions.</p>");

        // Filter by job
        html.append("<div class=\"filter-bar\">");
        html.append("<span>Filter by Job:</span>");
        html.append("<a href=\"/recruiter/applications\" class=\"filter-chip" + (filterJobId == -1 ? " active" : "") + "\">All Jobs</a>");
        for (JobDataStore.JobRecord j : myJobs) {
            html.append("<a href=\"/recruiter/applications?job_id=").append(j.jobId)
                .append("\" class=\"filter-chip").append(filterJobId == j.jobId ? " active" : "").append("\">")
                .append(escapeHtml(j.jobTitle)).append("</a>");
        }
        html.append("</div>");

        // Filter by status
        html.append("<div class=\"filter-bar\" style=\"margin-top:-10px;\">");
        html.append("<span>Filter by Status:</span>");
        for (String st : List.of("ALL", "APPLIED", "SHORTLISTED", "ACCEPTED", "REJECTED")) {
            String url = "/recruiter/applications?" + (filterJobId > 0 ? "job_id=" + filterJobId + "&" : "") + "status=" + st;
            html.append("<a href=\"").append(url).append("\" class=\"filter-chip")
                .append(statusFilter.equalsIgnoreCase(st) ? " active" : "").append("\">").append(st).append("</a>");
        }
        html.append("</div>");

        if (queryParams.containsKey("scheduled")) {
            html.append("<div class=\"filter-notice\" style=\"background:#dbeafe;border-color:#bfdbfe;color:#1d4ed8;margin-bottom:20px;\">")
                .append("<span>📅 <strong>Interview Scheduled!</strong> Candidate has been invited and meeting link generated.</span>")
                .append("<a href=\"/recruiter/applications\" class=\"clear-filter-btn\">✕ Dismiss</a></div>");
        }

        if (applications.isEmpty()) {
            html.append("<div class=\"empty-state\"><p>No candidate applications found for the selected filters.</p></div>");
        } else {
            html.append("<p style=\"color:#666; margin-bottom:15px;\">Total: <strong>").append(applications.size()).append("</strong> candidate application(s)</p>");
            html.append("<table class=\"data-table\"><thead><tr><th>#</th><th>Candidate</th><th>Contact</th><th>Job Title</th><th>Pitch / Cover Letter</th><th>Applied On</th><th>Status</th><th>Hiring Actions</th></tr></thead><tbody>");
            int i = 1;
            for (Map<String, String> app : applications) {
                String status = app.getOrDefault("status", "APPLIED");
                String statusClass = switch (status) {
                    case "SHORTLISTED", "ACCEPTED" -> "status-success";
                    case "REJECTED" -> "status-danger";
                    case "WITHDRAWN" -> "status-secondary";
                    default -> "status-info";
                };
                String coverLetter = app.getOrDefault("coverLetter", "");
                String coverPreview = coverLetter.length() > 45 ? coverLetter.substring(0, 45) + "..." : coverLetter;

                html.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td><strong>").append(escapeHtml(app.getOrDefault("candidateName", "-"))).append("</strong></td>")
                    .append("<td>").append(escapeHtml(app.getOrDefault("email", "-")))
                    .append("<br><span style=\"color:#666;font-size:12px;\">📞 ").append(escapeHtml(app.getOrDefault("phone", "-"))).append("</span></td>")
                    .append("<td>").append(escapeHtml(app.getOrDefault("jobTitle", "-"))).append("</td>")
                    .append("<td title=\"").append(escapeHtml(coverLetter)).append("\">").append(escapeHtml(coverPreview.isEmpty() ? "No pitch provided" : coverPreview)).append("</td>")
                    .append("<td>").append(escapeHtml(app.getOrDefault("appliedAt", "-"))).append("</td>")
                    .append("<td><span class=\"status-badge ").append(statusClass).append("\">").append(status).append("</span></td>")
                    .append("<td>");

                String appId = app.getOrDefault("applicationId", "0");
                String jId = app.getOrDefault("jobId", "0");
                String uId = app.getOrDefault("userId", "0");
                String cName = app.getOrDefault("candidateName", "Candidate");
                String jTitle = app.getOrDefault("jobTitle", "Job");

                if (!"SHORTLISTED".equals(status) && !"ACCEPTED".equals(status) && !"WITHDRAWN".equals(status)) {
                    html.append("<a href=\"/recruiter/applications/update?app_id=").append(appId).append("&status=SHORTLISTED\" class=\"action-btn action-success\" onclick=\"return confirm('Shortlist this candidate?')\">✅ Shortlist</a> ");
                }
                if (!"REJECTED".equals(status) && !"WITHDRAWN".equals(status)) {
                    html.append("<button type=\"button\" class=\"action-btn\" style=\"background:#e0e7ff;color:#4338ca;border:none;cursor:pointer;\" ")
                        .append("onclick=\"openScheduleModal(").append(jId).append(", ").append(uId).append(", ").append(appId)
                        .append(", '").append(escapeHtml(cName).replace("'", "\\'"))
                        .append("', '").append(escapeHtml(jTitle).replace("'", "\\'")).append("')\">📅 Interview</button> ");
                }
                if (!"ACCEPTED".equals(status) && !"WITHDRAWN".equals(status)) {
                    html.append("<a href=\"/recruiter/applications/update?app_id=").append(appId).append("&status=ACCEPTED\" class=\"action-btn action-info\" style=\"background:#dbeafe;color:#1d4ed8;\" onclick=\"return confirm('Accept/Hire this candidate?')\">🎉 Accept</a> ");
                }
                if (!"REJECTED".equals(status) && !"WITHDRAWN".equals(status)) {
                    html.append("<a href=\"/recruiter/applications/update?app_id=").append(appId).append("&status=REJECTED\" class=\"action-btn action-danger\" onclick=\"return confirm('Reject this candidate?')\">❌ Reject</a> ");
                }
                if (!"APPLIED".equals(status) && !"WITHDRAWN".equals(status)) {
                    html.append("<a href=\"/recruiter/applications/update?app_id=").append(appId).append("&status=APPLIED\" class=\"action-btn\" title=\"Reset to Applied\">↩ Reset</a>");
                }
                html.append("</td></tr>");
            }
            html.append("</tbody></table>");
        }

        html.append("""
            <!-- INTERVIEW SCHEDULING MODAL -->
            <div id="scheduleModal" class="modal-overlay" style="display:none;">
                <div class="modal-card" style="max-width:520px;">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
                        <h3 style="margin:0;color:#1e40af;">📅 Schedule Interview</h3>
                        <button type="button" onclick="closeScheduleModal()" style="background:none;border:none;font-size:22px;cursor:pointer;">&times;</button>
                    </div>
                    <form method="POST" action="/recruiter/interview/schedule">
                        <input type="hidden" name="jobId" id="schedJobId" value="0">
                        <input type="hidden" name="userId" id="schedUserId" value="0">
                        <input type="hidden" name="applicationId" id="schedAppId" value="0">
                        <input type="hidden" name="returnTo" id="schedReturnTo" value="/recruiter/applications?scheduled=1">

                        <div style="background:#f8fafc;padding:12px;border-radius:8px;margin-bottom:14px;font-size:13px;">
                            <div><strong>Candidate:</strong> <span id="schedCandName"></span></div>
                            <div style="margin-top:4px;"><strong>Position:</strong> <span id="schedJobTitle" style="color:#1e40af;"></span></div>
                        </div>

                        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:12px;">
                            <div>
                                <label style="font-size:12px;font-weight:700;display:block;margin-bottom:4px;">Date *</label>
                                <input type="date" name="interviewDate" required style="width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box;">
                            </div>
                            <div>
                                <label style="font-size:12px;font-weight:700;display:block;margin-bottom:4px;">Time *</label>
                                <input type="time" name="interviewTime" value="11:00" required style="width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box;">
                            </div>
                        </div>

                        <label style="font-size:12px;font-weight:700;display:block;margin-bottom:4px;">Interview Format</label>
                        <select name="interviewType" style="width:100%;padding:9px;border:1px solid #cbd5e1;border-radius:6px;margin-bottom:12px;box-sizing:border-box;">
                            <option value="Online Video (Google Meet)">Online Video (Google Meet)</option>
                            <option value="Online Video (Zoom)">Online Video (Zoom)</option>
                            <option value="Technical Coding Round">Technical Coding Round</option>
                            <option value="In-Person Office Round">In-Person Office Round</option>
                            <option value="HR Discussion">HR Discussion</option>
                        </select>

                        <label style="font-size:12px;font-weight:700;display:block;margin-bottom:4px;">Meeting Link / Room</label>
                        <input type="text" name="meetingLink" value="https://meet.google.com/smarthire-interview" style="width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;margin-bottom:12px;box-sizing:border-box;">

                        <label style="font-size:12px;font-weight:700;display:block;margin-bottom:4px;">Notes / Agenda for Candidate</label>
                        <textarea name="notes" rows="3" style="width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;font-family:inherit;box-sizing:border-box;">Please prepare to discuss your experience, technical problem solving, and relevant projects.</textarea>

                        <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:16px;">
                            <button type="button" onclick="closeScheduleModal()" class="button-secondary">Cancel</button>
                            <button type="submit" class="button">Confirm & Schedule</button>
                        </div>
                    </form>
                </div>
            </div>

            <script>
                function openScheduleModal(jobId, userId, appId, name, title) {
                    document.getElementById('schedJobId').value = jobId;
                    document.getElementById('schedUserId').value = userId;
                    document.getElementById('schedAppId').value = appId;
                    document.getElementById('schedCandName').innerText = name;
                    document.getElementById('schedJobTitle').innerText = title;
                    document.getElementById('schedReturnTo').value = '/recruiter/applications?scheduled=1';
                    document.getElementById('scheduleModal').style.display = 'flex';
                }
                function closeScheduleModal() {
                    document.getElementById('scheduleModal').style.display = 'none';
                }
            </script>
            """);

        html.append("</div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");

        sendResponse(exchange, html.toString());
    }

    private static void recruiterUpdateApplication(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
        int appId = Integer.parseInt(queryParams.getOrDefault("app_id", "0"));
        String newStatus = queryParams.getOrDefault("status", "APPLIED");

        JobDataStore.updateApplicationStatus(appId, session.userId, newStatus);
        sendRedirect(exchange, "/recruiter/applications?updated=1");
    }

    private static void recruiterProfile(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseFormData(body);
            String companyName = data.getOrDefault("companyName", "").trim();
            String industry = data.getOrDefault("industry", "").trim();
            String location = data.getOrDefault("location", "").trim();
            String website = data.getOrDefault("website", "").trim();
            String description = data.getOrDefault("description", "").trim();

            if (companyName.isEmpty() || location.isEmpty()) {
                sendResponse(exchange, errorPage("Validation Error", "Company Name and Location are required.", "/recruiter/profile"));
                return;
            }

            boolean ok = JobDataStore.updateCompanyProfile(session.userId, companyName, industry, location, website, description);
            if (ok) {
                session.companyName = companyName;
                sendRedirect(exchange, "/recruiter/profile?saved=1");
            } else {
                sendResponse(exchange, errorPage("Update Failed", "Could not update company profile.", "/recruiter/profile"));
            }
            return;
        }

        Map<String, String> profile = JobDataStore.getCompanyProfile(session.userId);
        Map<String, String> userProfile = JobDataStore.getUserProfile(session.userId);
        Map<String, String> query = parseQueryParams(exchange.getRequestURI());
        boolean saved = "1".equals(query.get("saved"));

        String currentName = profile.getOrDefault("company_name", session.companyName);
        if (currentName.isEmpty()) currentName = "My Company";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Company Profile - JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append(recruiterSidebar(session, "profile"));
        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">🏢 Company Profile & Recruiter Settings</h1>");
        html.append("<p class=\"dashboard-subtitle\">Manage your company branding, location, and recruitment details.</p>");

        if (saved) {
            html.append("<div class=\"filter-notice\" style=\"background:#dcfce7; border-color:#86efac; color:#166534;\">");
            html.append("<span>✅ Company profile updated successfully!</span></div>");
        }

        html.append("<div style=\"display:flex; gap:25px; flex-wrap:wrap;\">");
        html.append("<form method=\"POST\" action=\"/recruiter/profile\" class=\"form-card\" style=\"width:100%; max-width:650px; margin:0;\">");
        html.append("<h3 style=\"margin-top:0; color:#1e40af;\">Company Information</h3>");
        html.append("<label>Company Name *</label><input type=\"text\" name=\"companyName\" required value=\"").append(escapeHtml(currentName)).append("\">");
        html.append("<label>Industry / Domain</label><input type=\"text\" name=\"industry\" placeholder=\"e.g. Information Technology, FinTech\" value=\"").append(escapeHtml(profile.getOrDefault("industry", "Information Technology"))).append("\">");
        html.append("<label>Headquarters / Location *</label><input type=\"text\" name=\"location\" required placeholder=\"e.g. Bangalore, Karnataka\" value=\"").append(escapeHtml(profile.getOrDefault("location", "Bangalore"))).append("\">");
        html.append("<label>Company Website</label><input type=\"url\" name=\"website\" placeholder=\"https://example.com\" value=\"").append(escapeHtml(profile.getOrDefault("website", ""))).append("\">");
        html.append("<label>Company Overview / About</label><textarea name=\"description\" rows=\"4\" placeholder=\"Describe what your company does...\">").append(escapeHtml(profile.getOrDefault("company_desc", ""))).append("</textarea>");

        html.append("<button type=\"submit\" style=\"margin-top:20px;\">💾 Save Company Profile</button>");
        html.append("</form>");

        html.append("<div class=\"form-card\" style=\"width:100%; max-width:360px; margin:0; height:fit-content;\">");
        html.append("<h3 style=\"margin-top:0; color:#1e40af;\">Recruiter Account</h3>");
        html.append("<p><strong>Contact Person:</strong><br>").append(escapeHtml(session.fullName)).append("</p>");
        html.append("<p><strong>Login Email:</strong><br>").append(escapeHtml(session.email)).append("</p>");
        html.append("<p><strong>Phone:</strong><br>").append(escapeHtml(userProfile.getOrDefault("phone", "Not specified"))).append("</p>");
        html.append("<p><strong>Role:</strong><br><span class=\"status-badge status-success\">RECRUITER</span></p>");
        html.append("<br><a href=\"/recruiter/jobs\" class=\"button\" style=\"width:100%; text-align:center; box-sizing:border-box;\">View My Jobs</a>");
        html.append("</div></div>");

        html.append("</div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");

        sendResponse(exchange, html.toString());
    }

    private static void recruiterToggleJobStatus(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI());
        int jobId = Integer.parseInt(query.getOrDefault("job_id", "0"));
        String status = query.getOrDefault("status", "OPEN").toUpperCase();

        JobDataStore.setJobStatusRecruiter(jobId, session.userId, status);
        sendRedirect(exchange, "/recruiter/jobs?status_updated=1");
    }

    private static void recruiterCandidates(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> qp = parseQueryParams(exchange.getRequestURI());
        List<JobDataStore.JobRecord> myJobs = JobDataStore.getJobsByRecruiter(session.userId);

        int selectedJobId = -1;
        if (qp.containsKey("job_id")) {
            try { selectedJobId = Integer.parseInt(qp.get("job_id")); } catch (Exception ignored) {}
        }
        if (selectedJobId <= 0 && !myJobs.isEmpty()) {
            selectedJobId = myJobs.get(0).jobId;
        }

        JobDataStore.JobRecord currentJob = null;
        for (JobDataStore.JobRecord j : myJobs) {
            if (j.jobId == selectedJobId) {
                currentJob = j;
                break;
            }
        }
        if (currentJob == null && !myJobs.isEmpty()) {
            currentJob = myJobs.get(0);
            selectedJobId = currentJob.jobId;
        }

        List<MatchingEngine.CandidateRecord> allCandidates = JobDataStore.getAllCandidatesForRecruiter();
        List<MatchingEngine.CandidateMatchResult> matchResults = Collections.emptyList();
        if (currentJob != null) {
            matchResults = MatchingEngine.findMatchingCandidatesForJob(currentJob, allCandidates);
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Candidate Discovery - JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append(recruiterSidebar(session, "candidates"));
        html.append("<div class=\"dashboard-main\">");

        if (qp.containsKey("invited")) {
            html.append("<div class=\"filter-notice\" style=\"background:#dcfce7;border-color:#bbf7d0;color:#15803d;margin-bottom:20px;\">")
                .append("<span>🎉 <strong>Invitation Sent!</strong> The candidate has been alerted and invited to apply.</span>")
                .append("<a href=\"/recruiter/candidates?job_id=").append(selectedJobId).append("\" class=\"clear-filter-btn\" style=\"background:#16a34a;\">✕ Dismiss</a></div>");
        }
        if (qp.containsKey("scheduled")) {
            html.append("<div class=\"filter-notice\" style=\"background:#dbeafe;border-color:#bfdbfe;color:#1d4ed8;margin-bottom:20px;\">")
                .append("<span>📅 <strong>Interview Scheduled!</strong> The meeting details were saved and the candidate notified.</span>")
                .append("<a href=\"/recruiter/candidates?job_id=").append(selectedJobId).append("\" class=\"clear-filter-btn\">✕ Dismiss</a></div>");
        }

        html.append("<div style=\"display:flex;justify-content:space-between;align-items:flex-start;flex-wrap:wrap;gap:15px;margin-bottom:15px;\">");
        html.append("<div>");
        html.append("<h1 class=\"dashboard-title\">Candidate Discovery & Talent Match 🔍</h1>");
        html.append("<p class=\"dashboard-subtitle\">Algorithmic reverse matching: Discover candidates whose skills match your open requisitions.</p>");
        html.append("</div>");
        if (myJobs.isEmpty()) {
            html.append("<a href=\"/recruiter/jobs/add\" class=\"btn-add\">+ Post a Job First</a>");
        }
        html.append("</div>");

        if (myJobs.isEmpty()) {
            html.append("<div class=\"empty-state\"><p>You need to post at least one job to discover matched candidates.</p><a href=\"/recruiter/jobs/add\" class=\"btn-add\">Post Job Now</a></div>");
        } else {
            html.append("<div class=\"filter-bar\">");
            html.append("<span>Match Candidates Against:</span>");
            for (JobDataStore.JobRecord j : myJobs) {
                html.append("<a href=\"/recruiter/candidates?job_id=").append(j.jobId).append("\" class=\"filter-chip")
                    .append(j.jobId == selectedJobId ? " active" : "").append("\">")
                    .append(escapeHtml(j.jobTitle)).append(" (").append(escapeHtml(j.location)).append(")</a>");
            }
            html.append("</div>");

            if (currentJob != null) {
                html.append("<div class=\"dashboard-section\" style=\"padding:16px 20px;margin-bottom:24px;border-left:4px solid #0284c7;background:#f8fafc;\">");
                html.append("<div style=\"display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;\">");
                html.append("<div><strong style=\"color:#0f172a;font-size:16px;\">Target Role: ").append(escapeHtml(currentJob.jobTitle)).append("</strong>");
                html.append("<span style=\"color:#64748b;font-size:13px;margin-left:12px;\">Required Skills: <em>").append(escapeHtml(currentJob.skillsRequired)).append("</em></span></div>");
                html.append("<div style=\"color:#0284c7;font-weight:700;font-size:13px;\">🎯 ").append(matchResults.size()).append(" Candidate Profiles Ranked</div>");
                html.append("</div></div>");
            }

            if (matchResults.isEmpty()) {
                html.append("<div class=\"empty-state\"><p>No registered candidates found in the talent pool yet.</p></div>");
            } else {
                html.append("<div class=\"candidates-grid\">");
                for (MatchingEngine.CandidateMatchResult cand : matchResults) {
                    String pillClass = cand.matchPercentage >= 80 ? "match-pill-high" : cand.matchPercentage >= 60 ? "match-pill-mid" : "match-pill-low";

                    html.append("<div class=\"candidate-card\">");
                    html.append("<div class=\"candidate-header\">");
                    String initial = cand.candidateName.isEmpty() ? "C" : cand.candidateName.substring(0, 1).toUpperCase();
                    html.append("<div class=\"candidate-avatar\">").append(escapeHtml(initial)).append("</div>");
                    html.append("<div class=\"candidate-info\">");
                    html.append("<h3 style=\"margin:0;color:#0f172a;\">").append(escapeHtml(cand.candidateName)).append("</h3>");
                    html.append("<div class=\"candidate-meta\">🎓 ").append(escapeHtml(cand.qualification.isEmpty() ? "Graduate" : cand.qualification))
                        .append(" · ⏱️ ").append(escapeHtml(cand.experience.isEmpty() ? "2+ years" : cand.experience)).append("</div>");
                    html.append("<div style=\"font-size:12px;color:#64748b;margin-top:3px;\">✉️ ").append(escapeHtml(cand.candidateEmail))
                        .append(" · 📞 ").append(escapeHtml(cand.candidatePhone)).append("</div>");
                    html.append("</div>");
                    html.append("<div class=\"candidate-score-wrap\"><span class=\"").append(pillClass).append("\">").append(cand.matchPercentage).append("% Match</span></div>");
                    html.append("</div>");

                    html.append("<div style=\"margin:14px 0;\">");
                    html.append("<div style=\"font-size:12px;font-weight:700;color:#475569;margin-bottom:6px;\">MATCHED SKILLS:</div>");
                    html.append("<div class=\"candidate-skills-wrap\">");
                    if (cand.matchedSkills.isEmpty()) {
                        html.append("<span style=\"color:#94a3b8;font-size:12px;\">No exact keyword overlap</span>");
                    } else {
                        for (String ms : cand.matchedSkills) {
                            html.append("<span class=\"why-badge-match\">✓ ").append(escapeHtml(ms)).append("</span>");
                        }
                    }
                    if (!cand.missingSkills.isEmpty()) {
                        for (String mis : cand.missingSkills) {
                            html.append("<span class=\"why-badge-missing\">⚠ ").append(escapeHtml(mis)).append("</span>");
                        }
                    }
                    html.append("</div></div>");

                    if (!cand.whyReasons.isEmpty()) {
                        html.append("<div class=\"why-match-box\" style=\"margin:12px 0;padding:10px 14px;\">");
                        html.append("<div class=\"why-match-heading\" style=\"font-size:11px;\">💡 AI ALIGNMENT FACTORS</div>");
                        html.append("<div class=\"why-match-badges-grid\">");
                        for (String why : cand.whyReasons.subList(0, Math.min(4, cand.whyReasons.size()))) {
                            boolean isMis = why.startsWith("⚠");
                            html.append("<span class=\"").append(isMis ? "why-badge-missing" : "why-badge-match").append("\" style=\"font-size:11px;\">")
                                .append(escapeHtml(why)).append("</span>");
                        }
                        html.append("</div></div>");
                    }

                    String targetTitle = currentJob != null ? currentJob.jobTitle : "Position";
                    int currentJId = currentJob != null ? currentJob.jobId : 0;
                    html.append("<div class=\"card-actions\" style=\"margin-top:16px;border-top:1px solid #f1f5f9;padding-top:12px;\">");
                    html.append("<button type=\"button\" class=\"button\" style=\"padding:7px 14px;font-size:13px;\" ")
                        .append("onclick=\"openInviteModal(").append(currentJId)
                        .append(", ").append(cand.candidateId).append(", ").append(cand.matchPercentage)
                        .append(", '").append(escapeHtml(cand.candidateName).replace("'", "\\'"))
                        .append("', '").append(escapeHtml(targetTitle).replace("'", "\\'")).append("')\">📩 Invite to Apply</button> ");

                    html.append("<button type=\"button\" class=\"button-secondary\" style=\"padding:7px 14px;font-size:13px;\" ")
                        .append("onclick=\"openScheduleModal(").append(currentJId)
                        .append(", ").append(cand.candidateId).append(", 0, '")
                        .append(escapeHtml(cand.candidateName).replace("'", "\\'"))
                        .append("', '").append(escapeHtml(targetTitle).replace("'", "\\'")).append("')\">📅 Schedule Interview</button> ");

                    html.append("<a href=\"/seeker/resume/view?user_id=").append(cand.candidateId)
                        .append("\" class=\"action-btn\" style=\"padding:7px 12px;font-size:13px;\" target=\"_blank\">📄 View Resume</a>");

                    html.append("</div></div>");
                }
                html.append("</div>");
            }
        }

        html.append("""
            <!-- INVITATION MODAL -->
            <div id="inviteModal" class="modal-overlay" style="display:none;">
                <div class="modal-card">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
                        <h3 style="margin:0;color:#0284c7;">📩 Send Job Invitation</h3>
                        <button type="button" onclick="closeInviteModal()" style="background:none;border:none;font-size:22px;cursor:pointer;">&times;</button>
                    </div>
                    <p style="font-size:13px;color:#64748b;margin-bottom:14px;">
                        Invite candidate directly to apply for your role. They will receive an instant notification in their dashboard.
                    </p>
                    <form method="POST" action="/recruiter/invite">
                        <input type="hidden" name="jobId" id="inviteJobId" value="0">
                        <input type="hidden" name="seekerUserId" id="inviteSeekerId" value="0">
                        <input type="hidden" name="matchScore" id="inviteMatchScore" value="85">

                        <div style="background:#f8fafc;padding:12px;border-radius:8px;margin-bottom:14px;font-size:13px;">
                            <div><strong>Candidate:</strong> <span id="inviteCandName" style="color:#0f172a;"></span></div>
                            <div style="margin-top:4px;"><strong>Job:</strong> <span id="inviteJobTitle" style="color:#0284c7;"></span></div>
                        </div>

                        <label style="font-size:13px;font-weight:600;display:block;margin-bottom:6px;">Personalized Invitation Note</label>
                        <textarea name="message" id="inviteMsg" rows="4" style="width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:6px;font-family:inherit;box-sizing:border-box;"></textarea>

                        <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:16px;">
                            <button type="button" onclick="closeInviteModal()" class="button-secondary">Cancel</button>
                            <button type="submit" class="button" style="background:#0284c7;">Send Invitation</button>
                        </div>
                    </form>
                </div>
            </div>

            <!-- INTERVIEW SCHEDULING MODAL -->
            <div id="scheduleModal" class="modal-overlay" style="display:none;">
                <div class="modal-card" style="max-width:520px;">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
                        <h3 style="margin:0;color:#1e40af;">📅 Schedule Interview</h3>
                        <button type="button" onclick="closeScheduleModal()" style="background:none;border:none;font-size:22px;cursor:pointer;">&times;</button>
                    </div>
                    <form method="POST" action="/recruiter/interview/schedule">
                        <input type="hidden" name="jobId" id="schedJobId" value="0">
                        <input type="hidden" name="userId" id="schedUserId" value="0">
                        <input type="hidden" name="applicationId" id="schedAppId" value="0">
                        <input type="hidden" name="returnTo" id="schedReturnTo" value="">

                        <div style="background:#f8fafc;padding:12px;border-radius:8px;margin-bottom:14px;font-size:13px;">
                            <div><strong>Candidate:</strong> <span id="schedCandName"></span></div>
                            <div style="margin-top:4px;"><strong>Position:</strong> <span id="schedJobTitle" style="color:#1e40af;"></span></div>
                        </div>

                        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:12px;">
                            <div>
                                <label style="font-size:12px;font-weight:700;display:block;margin-bottom:4px;">Date *</label>
                                <input type="date" name="interviewDate" required style="width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box;">
                            </div>
                            <div>
                                <label style="font-size:12px;font-weight:700;display:block;margin-bottom:4px;">Time *</label>
                                <input type="time" name="interviewTime" value="11:00" required style="width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box;">
                            </div>
                        </div>

                        <label style="font-size:12px;font-weight:700;display:block;margin-bottom:4px;">Interview Format</label>
                        <select name="interviewType" style="width:100%;padding:9px;border:1px solid #cbd5e1;border-radius:6px;margin-bottom:12px;box-sizing:border-box;">
                            <option value="Online Video (Google Meet)">Online Video (Google Meet)</option>
                            <option value="Online Video (Zoom)">Online Video (Zoom)</option>
                            <option value="Technical Coding Round">Technical Coding Round</option>
                            <option value="In-Person Office Round">In-Person Office Round</option>
                            <option value="HR Discussion">HR Discussion</option>
                        </select>

                        <label style="font-size:12px;font-weight:700;display:block;margin-bottom:4px;">Meeting Link / Room</label>
                        <input type="text" name="meetingLink" value="https://meet.google.com/smarthire-interview" style="width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;margin-bottom:12px;box-sizing:border-box;">

                        <label style="font-size:12px;font-weight:700;display:block;margin-bottom:4px;">Notes / Agenda for Candidate</label>
                        <textarea name="notes" rows="3" style="width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;font-family:inherit;box-sizing:border-box;">Please prepare to discuss your experience, technical problem solving, and relevant projects.</textarea>

                        <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:16px;">
                            <button type="button" onclick="closeScheduleModal()" class="button-secondary">Cancel</button>
                            <button type="submit" class="button">Confirm & Schedule</button>
                        </div>
                    </form>
                </div>
            </div>

            <script>
                function openInviteModal(jobId, seekerId, score, name, title) {
                    document.getElementById('inviteJobId').value = jobId;
                    document.getElementById('inviteSeekerId').value = seekerId;
                    document.getElementById('inviteMatchScore').value = score;
                    document.getElementById('inviteCandName').innerText = name;
                    document.getElementById('inviteJobTitle').innerText = title;
                    document.getElementById('inviteMsg').value = 'Hello ' + name + ', we reviewed your profile and noticed a strong ' + score + '% alignment with our ' + title + ' role. We would love for you to apply!';
                    document.getElementById('inviteModal').style.display = 'flex';
                }
                function closeInviteModal() {
                    document.getElementById('inviteModal').style.display = 'none';
                }
                function openScheduleModal(jobId, userId, appId, name, title) {
                    document.getElementById('schedJobId').value = jobId;
                    document.getElementById('schedUserId').value = userId;
                    document.getElementById('schedAppId').value = appId;
                    document.getElementById('schedCandName').innerText = name;
                    document.getElementById('schedJobTitle').innerText = title;
                    document.getElementById('schedReturnTo').value = window.location.pathname + window.location.search + (window.location.search ? '&' : '?') + 'scheduled=1';
                    document.getElementById('scheduleModal').style.display = 'flex';
                }
                function closeScheduleModal() {
                    document.getElementById('scheduleModal').style.display = 'none';
                }
            </script>
            """);

        html.append("</div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");
        sendResponse(exchange, html.toString());
    }

    private static void recruiterInviteCandidate(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        int jobId = 0;
        int seekerUserId = 0;
        int matchScore = 85;
        String message = "";

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseFormData(body);
            try { jobId = Integer.parseInt(data.getOrDefault("jobId", "0")); } catch (Exception ignored) {}
            try { seekerUserId = Integer.parseInt(data.getOrDefault("seekerUserId", "0")); } catch (Exception ignored) {}
            try { matchScore = Integer.parseInt(data.getOrDefault("matchScore", "85")); } catch (Exception ignored) {}
            message = data.getOrDefault("message", "");
        } else {
            Map<String, String> query = parseQueryParams(exchange.getRequestURI());
            try { jobId = Integer.parseInt(query.getOrDefault("job_id", "0")); } catch (Exception ignored) {}
            try { seekerUserId = Integer.parseInt(query.getOrDefault("user_id", "0")); } catch (Exception ignored) {}
            try { matchScore = Integer.parseInt(query.getOrDefault("score", "85")); } catch (Exception ignored) {}
            message = query.getOrDefault("message", "");
        }

        if (jobId > 0 && seekerUserId > 0) {
            if (message == null || message.trim().isEmpty()) {
                String jobTitle = JobDataStore.getJobTitleById(jobId);
                String comp = session.companyName.isEmpty() ? session.fullName : session.companyName;
                message = comp + " has invited you to apply for " + jobTitle + "!";
            }
            JobDataStore.saveJobInvitation(jobId, session.userId, seekerUserId, matchScore, message);
        }

        sendRedirect(exchange, "/recruiter/candidates?job_id=" + jobId + "&invited=1");
    }

    private static void recruiterScheduleInterview(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"RECRUITER".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseFormData(body);
            int applicationId = 0;
            int jobId = 0;
            int userId = 0;
            try { applicationId = Integer.parseInt(data.getOrDefault("applicationId", "0")); } catch (Exception ignored) {}
            try { jobId = Integer.parseInt(data.getOrDefault("jobId", "0")); } catch (Exception ignored) {}
            try { userId = Integer.parseInt(data.getOrDefault("userId", "0")); } catch (Exception ignored) {}
            String date = data.getOrDefault("interviewDate", "");
            String time = data.getOrDefault("interviewTime", "");
            String type = data.getOrDefault("interviewType", "Online Video");
            String link = data.getOrDefault("meetingLink", "https://meet.google.com/smarthire-interview");
            String notes = data.getOrDefault("notes", "Technical discussion and evaluation.");
            String returnTo = data.getOrDefault("returnTo", "");

            String fullDate = date + (time.isEmpty() ? "" : " at " + time);
            JobDataStore.saveInterview(applicationId, jobId, userId, session.userId, fullDate, type, link, notes);

            if (returnTo.isEmpty()) {
                returnTo = "/recruiter/candidates?job_id=" + jobId + "&scheduled=1";
            }
            sendRedirect(exchange, returnTo);
            return;
        }

        sendRedirect(exchange, "/recruiter/dashboard");
    }

    // ============================================================
    // ADMIN DASHBOARD
    // ============================================================

    private static void adminDashboard(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, Integer> stats = JobDataStore.getAdminStats();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Admin Dashboard - JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append(adminSidebar("dashboard"));
        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">⚙️ Admin Control Panel</h1>");
        html.append("<p class=\"dashboard-subtitle\">Welcome, <strong>").append(escapeHtml(session.fullName)).append("</strong>. Complete platform administration & monitoring.</p>");

        html.append("<div class=\"stats-grid\">");
        html.append("<div class=\"stat-card\"><div class=\"stat-num\">").append(stats.getOrDefault("users", 0)).append("</div><div class=\"stat-label\">Total Users</div></div>");
        html.append("<div class=\"stat-card stat-success\"><div class=\"stat-num\">").append(stats.getOrDefault("recruiters", 0)).append("</div><div class=\"stat-label\">Recruiters</div></div>");
        html.append("<div class=\"stat-card stat-info\"><div class=\"stat-num\">").append(stats.getOrDefault("seekers", 0)).append("</div><div class=\"stat-label\">Job Seekers</div></div>");
        html.append("<div class=\"stat-card\"><div class=\"stat-num\">").append(stats.getOrDefault("jobs", 0)).append("</div><div class=\"stat-label\">Total Jobs (").append(stats.getOrDefault("openJobs", 0)).append(" Open)</div></div>");
        html.append("<div class=\"stat-card stat-info\"><div class=\"stat-num\">").append(stats.getOrDefault("applications", 0)).append("</div><div class=\"stat-label\">Applications</div></div>");
        html.append("</div>");

        html.append("<div class=\"admin-modules-grid\">");
        html.append("<a href=\"/admin/users\" class=\"admin-module-card\"><div class=\"module-icon\">👥</div><h3>Manage Users</h3><p>View, activate, suspend, or delete users</p></a>");
        html.append("<a href=\"/admin/recruiters\" class=\"admin-module-card\"><div class=\"module-icon\">🏢</div><h3>Manage Recruiters</h3><p>View companies and recruiter job statistics</p></a>");
        html.append("<a href=\"/admin/jobs\" class=\"admin-module-card\"><div class=\"module-icon\">💼</div><h3>Manage Jobs</h3><p>Review, open/close, or delete job postings</p></a>");
        html.append("<a href=\"/admin/applications\" class=\"admin-module-card\"><div class=\"module-icon\">📋</div><h3>Manage Applications</h3><p>System-wide candidate application oversight</p></a>");
        html.append("<a href=\"/jobs\" class=\"admin-module-card\"><div class=\"module-icon\">🔍</div><h3>Browse Public Jobs</h3><p>View the live job directory</p></a>");
        html.append("</div>");

        html.append("</div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");

        sendResponse(exchange, html.toString());
    }

    private static String adminSidebar(String active) {
        return "<div class=\"dashboard-sidebar\">"
            + "<div class=\"sidebar-user\">⚙️ Admin Panel</div>"
            + "<div class=\"sidebar-role\">Administrator</div>"
            + "<nav class=\"sidebar-nav\">"
            + "<a href=\"/admin/dashboard\"" + ("dashboard".equals(active) ? " class=\"active\"" : "") + ">🏠 Dashboard</a>"
            + "<a href=\"/admin/users\"" + ("users".equals(active) ? " class=\"active\"" : "") + ">👥 Manage Users</a>"
            + "<a href=\"/admin/recruiters\"" + ("recruiters".equals(active) ? " class=\"active\"" : "") + ">🏢 Manage Recruiters</a>"
            + "<a href=\"/admin/jobs\"" + ("jobs".equals(active) ? " class=\"active\"" : "") + ">💼 Manage Jobs</a>"
            + "<a href=\"/admin/applications\"" + ("applications".equals(active) ? " class=\"active\"" : "") + ">📋 Applications</a>"
            + "<a href=\"/admin/reports\"" + ("reports".equals(active) ? " class=\"active\"" : "") + ">🛡️ Job Reports</a>"
            + "<a href=\"/jobs\">🔍 Browse Jobs</a>"
            + "<a href=\"/logout\">🚪 Logout</a>"
            + "</nav>"
            + "<div class=\"sidebar-brand\">"
            + "<div class=\"sidebar-brand-text\">JOB PORTAL SYSTEM</div>"
            + "<div class=\"sidebar-brand-tech\">Java • Spring Boot • Hibernate<br>MySQL • NetBeans</div>"
            + "</div>"
            + "</div>";
    }

    private static void adminUsers(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        List<Map<String, String>> users = JobDataStore.getAllUsers();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Manage Users - Admin</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append(adminSidebar("users"));
        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">👥 Manage Users</h1>");
        html.append("<p class=\"dashboard-subtitle\">View registered accounts, toggle active/suspended status, and manage access.</p>");
        html.append("<p style=\"color:#666; margin-bottom:15px;\">Total: <strong>").append(users.size()).append("</strong> registered user(s)</p>");

        if (users.isEmpty()) {
            html.append("<div class=\"empty-state\"><p>No users found.</p></div>");
        } else {
            html.append("<table class=\"data-table\"><thead><tr><th>#</th><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Phone</th><th>Registered</th><th>Actions</th></tr></thead><tbody>");
            int i = 1;
            for (Map<String, String> user : users) {
                String role = user.getOrDefault("userType", "JOB_SEEKER");
                String roleClass = switch (role) {
                    case "ADMIN" -> "status-danger";
                    case "RECRUITER" -> "status-success";
                    default -> "status-info";
                };
                boolean isActive = "true".equalsIgnoreCase(user.getOrDefault("isActive", "true"));
                String uid = user.getOrDefault("userId", "0");

                html.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td><strong>").append(escapeHtml(user.getOrDefault("fullName", "-"))).append("</strong></td>")
                    .append("<td>").append(escapeHtml(user.getOrDefault("email", "-"))).append("</td>")
                    .append("<td><span class=\"status-badge ").append(roleClass).append("\">").append(role).append("</span></td>")
                    .append("<td><span class=\"status-badge ").append(isActive ? "status-success" : "status-danger").append("\">")
                    .append(isActive ? "Active" : "Suspended").append("</span></td>")
                    .append("<td>").append(escapeHtml(user.getOrDefault("phone", "-"))).append("</td>")
                    .append("<td>").append(escapeHtml(user.getOrDefault("createdAt", "-"))).append("</td>")
                    .append("<td>");

                if (!String.valueOf(session.userId).equals(uid)) {
                    if (isActive) {
                        html.append("<a href=\"/admin/users/toggle-active?user_id=").append(uid)
                            .append("&active=false\" class=\"action-btn action-danger\" onclick=\"return confirm('Suspend this user account? They will not be able to log in.');\">⛔ Suspend</a> ");
                    } else {
                        html.append("<a href=\"/admin/users/toggle-active?user_id=").append(uid)
                            .append("&active=true\" class=\"action-btn action-success\" onclick=\"return confirm('Activate this user account?');\">✅ Activate</a> ");
                    }
                    html.append("<a href=\"/admin/users/delete?user_id=").append(uid)
                        .append("\" class=\"action-btn action-danger\" onclick=\"return confirm('Delete this user and all associated records?');\">🗑 Delete</a>");
                } else {
                    html.append("<span style=\"color:#22c55e; font-weight:bold; font-size:12px;\">Current Admin</span>");
                }
                html.append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");

        sendResponse(exchange, html.toString());
    }

    private static void adminToggleUserActive(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI());
        int userId = Integer.parseInt(query.getOrDefault("user_id", "0"));
        boolean active = Boolean.parseBoolean(query.getOrDefault("active", "true"));

        if (userId != session.userId) {
            JobDataStore.setUserActive(userId, active);
        }
        sendRedirect(exchange, "/admin/users?toggled=1");
    }

    private static void adminDeleteUser(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
        int userId = Integer.parseInt(queryParams.getOrDefault("user_id", "0"));

        if (userId != session.userId) {
            JobDataStore.deleteUser(userId);
        }
        sendRedirect(exchange, "/admin/users");
    }

    private static void adminJobs(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        List<JobDataStore.JobRecord> allJobs = JobDataStore.getJobs(null, null);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Manage Jobs - Admin</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append(adminSidebar("jobs"));
        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">💼 Manage All Jobs</h1>");
        html.append("<p class=\"dashboard-subtitle\">Review all job listings across all recruiters, update status, or remove listings.</p>");
        html.append("<p style=\"color:#666; margin-bottom:15px;\">Total: <strong>").append(allJobs.size()).append("</strong> job posting(s)</p>");

        if (allJobs.isEmpty()) {
            html.append("<div class=\"empty-state\"><p>No jobs found.</p></div>");
        } else {
            html.append("<table class=\"data-table\"><thead><tr><th>#</th><th>Job Title</th><th>Company</th><th>Location</th><th>Type</th><th>Status</th><th>Actions</th></tr></thead><tbody>");
            int i = 1;
            for (JobDataStore.JobRecord job : allJobs) {
                html.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td><strong>").append(escapeHtml(job.jobTitle)).append("</strong></td>")
                    .append("<td>🏢 ").append(escapeHtml(job.companyName)).append("</td>")
                    .append("<td>📍 ").append(escapeHtml(job.location)).append("</td>")
                    .append("<td>").append(escapeHtml(job.jobType)).append("</td>")
                    .append("<td><span class=\"status-badge ").append("OPEN".equals(job.status) ? "status-success" : "status-danger").append("\">").append(job.status).append("</span></td>")
                    .append("<td>")
                    .append("<a href=\"/apply?job_id=").append(job.jobId).append("\" class=\"action-btn\">👁 View</a> ");

                if ("OPEN".equals(job.status)) {
                    html.append("<a href=\"/admin/jobs/status?job_id=").append(job.jobId)
                        .append("&status=CLOSED\" class=\"action-btn action-danger\" onclick=\"return confirm('Close this job posting?');\">🔒 Close</a> ");
                } else {
                    html.append("<a href=\"/admin/jobs/status?job_id=").append(job.jobId)
                        .append("&status=OPEN\" class=\"action-btn action-success\" onclick=\"return confirm('Reopen this job posting?');\">🔓 Reopen</a> ");
                }

                html.append("<a href=\"/admin/jobs/delete?job_id=").append(job.jobId)
                    .append("\" class=\"action-btn action-danger\" onclick=\"return confirm('Delete this job and all applications?')\">🗑 Delete</a>")
                    .append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");

        sendResponse(exchange, html.toString());
    }

    private static void adminUpdateJobStatus(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI());
        int jobId = Integer.parseInt(query.getOrDefault("job_id", "0"));
        String status = query.getOrDefault("status", "OPEN").toUpperCase();

        JobDataStore.setJobStatusAdmin(jobId, status);
        sendRedirect(exchange, "/admin/jobs?status_updated=1");
    }

    private static void adminDeleteJob(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
        int jobId = Integer.parseInt(queryParams.getOrDefault("job_id", "0"));

        JobDataStore.deleteJobAdmin(jobId);
        sendRedirect(exchange, "/admin/jobs");
    }

    private static void adminRecruiters(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        List<Map<String, String>> recruiters = JobDataStore.getAllRecruiters();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Manage Recruiters - Admin</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append(adminSidebar("recruiters"));
        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">🏢 Manage Recruiters</h1>");
        html.append("<p class=\"dashboard-subtitle\">View registered recruitment companies and job posting statistics.</p>");
        html.append("<p style=\"color:#666; margin-bottom:15px;\">Total: <strong>").append(recruiters.size()).append("</strong> recruiter(s)</p>");

        if (recruiters.isEmpty()) {
            html.append("<div class=\"empty-state\"><p>No recruiters registered yet.</p></div>");
        } else {
            html.append("<table class=\"data-table\"><thead><tr><th>#</th><th>Name</th><th>Email</th><th>Company</th><th>Location</th><th>Jobs Posted</th><th>Registered</th></tr></thead><tbody>");
            int i = 1;
            for (Map<String, String> rec : recruiters) {
                html.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td><strong>").append(escapeHtml(rec.getOrDefault("fullName", "-"))).append("</strong></td>")
                    .append("<td>").append(escapeHtml(rec.getOrDefault("email", "-"))).append("</td>")
                    .append("<td>🏢 ").append(escapeHtml(rec.getOrDefault("companyName", "N/A"))).append("</td>")
                    .append("<td>📍 ").append(escapeHtml(rec.getOrDefault("location", "N/A"))).append("</td>")
                    .append("<td><span class=\"badge\">").append(rec.getOrDefault("jobCount", "0")).append(" jobs</span></td>")
                    .append("<td>").append(escapeHtml(rec.getOrDefault("createdAt", "-"))).append("</td>")
                    .append("</tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");

        sendResponse(exchange, html.toString());
    }

    private static void adminApplications(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI());
        String statusFilter = query.getOrDefault("status", "ALL");

        List<Map<String, String>> apps = JobDataStore.getAllApplicationsAdmin(statusFilter);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Manage Applications - Admin</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append(adminSidebar("applications"));
        html.append("<div class=\"dashboard-main\">");
        html.append("<h1 class=\"dashboard-title\">📋 Platform Applications Oversight</h1>");
        html.append("<p class=\"dashboard-subtitle\">Monitor and manage candidate applications across all companies and jobs.</p>");

        html.append("<div class=\"filter-bar\"><span>Filter by Status:</span>");
        for (String st : List.of("ALL", "APPLIED", "SHORTLISTED", "ACCEPTED", "REJECTED", "WITHDRAWN")) {
            html.append("<a href=\"/admin/applications?status=").append(st).append("\" class=\"filter-chip")
                .append(statusFilter.equalsIgnoreCase(st) ? " active" : "").append("\">").append(st).append("</a>");
        }
        html.append("</div>");

        if (apps.isEmpty()) {
            html.append("<div class=\"empty-state\"><p>No candidate applications matching this filter.</p></div>");
        } else {
            html.append("<p style=\"color:#666; margin-bottom:15px;\">Showing <strong>").append(apps.size()).append("</strong> application(s)</p>");
            html.append("<table class=\"data-table\"><thead><tr><th>#</th><th>Candidate</th><th>Email & Phone</th><th>Job Title</th><th>Company</th><th>Cover Letter</th><th>Applied On</th><th>Status</th><th>Actions</th></tr></thead><tbody>");
            int i = 1;
            for (Map<String, String> app : apps) {
                String status = app.getOrDefault("status", "APPLIED");
                String statusClass = switch (status) {
                    case "SHORTLISTED", "ACCEPTED" -> "status-success";
                    case "REJECTED" -> "status-danger";
                    case "WITHDRAWN" -> "status-secondary";
                    default -> "status-info";
                };
                String cover = app.getOrDefault("coverLetter", "");
                String coverPreview = cover.length() > 45 ? cover.substring(0, 45) + "..." : cover;
                String appId = app.getOrDefault("applicationId", "0");

                html.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td><strong>").append(escapeHtml(app.getOrDefault("candidateName", "-"))).append("</strong></td>")
                    .append("<td>").append(escapeHtml(app.getOrDefault("email", "-"))).append("<br><span style=\"color:#666;font-size:12px;\">📞 ").append(escapeHtml(app.getOrDefault("phone", "-"))).append("</span></td>")
                    .append("<td>").append(escapeHtml(app.getOrDefault("jobTitle", "-"))).append("</td>")
                    .append("<td>🏢 ").append(escapeHtml(app.getOrDefault("company", "-"))).append("</td>")
                    .append("<td title=\"").append(escapeHtml(cover)).append("\">").append(escapeHtml(coverPreview.isEmpty() ? "None" : coverPreview)).append("</td>")
                    .append("<td>").append(escapeHtml(app.getOrDefault("appliedAt", "-"))).append("</td>")
                    .append("<td><span class=\"status-badge ").append(statusClass).append("\">").append(status).append("</span></td>")
                    .append("<td>")
                    .append("<a href=\"/admin/applications/delete?app_id=").append(appId).append("\" class=\"action-btn action-danger\" onclick=\"return confirm('Delete this application record?')\">🗑 Delete</a>")
                    .append("</td></tr>");
            }
            html.append("</tbody></table>");
        }

        html.append("</div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");
        sendResponse(exchange, html.toString());
    }

    private static void adminDeleteApplication(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI());
        int appId = Integer.parseInt(query.getOrDefault("app_id", "0"));
        JobDataStore.deleteApplicationAdmin(appId);
        sendRedirect(exchange, "/admin/applications?deleted=1");
    }

    private static void adminReports(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI());
        List<JobDataStore.JobReportRecord> reports = JobDataStore.getAllJobReports();

        int pendingCount = 0;
        int resolvedCount = 0;
        for (JobDataStore.JobReportRecord r : reports) {
            if ("PENDING".equalsIgnoreCase(r.status)) pendingCount++;
            else if ("RESOLVED".equalsIgnoreCase(r.status)) resolvedCount++;
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Job Reports - Admin JobPortal</title><link rel=\"stylesheet\" href=\"/css/style.css\"></head><body>");
        html.append(navHtml(session));
        html.append("<div class=\"dashboard-container\">");
        html.append(adminSidebar("reports"));
        html.append("<div class=\"dashboard-main\">");

        if (query.containsKey("updated")) {
            html.append("<div class=\"filter-notice\" style=\"background:#dcfce7;border-color:#bbf7d0;color:#15803d;margin-bottom:20px;\">")
                .append("<span>✅ <strong>Report Status Updated!</strong> Action was applied successfully.</span>")
                .append("<a href=\"/admin/reports\" class=\"clear-filter-btn\" style=\"background:#16a34a;\">✕ Dismiss</a></div>");
        }

        html.append("<h1 class=\"dashboard-title\">Trust & Safety: Job Quality & Scam Reports 🛡️</h1>");
        html.append("<p class=\"dashboard-subtitle\">Investigate suspicious postings, take down fraudulent listings, and protect job seekers.</p>");

        html.append("<div class=\"stats-grid\">");
        html.append("<div class=\"stat-card\"><div class=\"stat-num\">").append(reports.size()).append("</div><div class=\"stat-label\">Total Reports</div></div>");
        html.append("<div class=\"stat-card stat-danger\"><div class=\"stat-num\">").append(pendingCount).append("</div><div class=\"stat-label\">Pending Review</div></div>");
        html.append("<div class=\"stat-card stat-success\"><div class=\"stat-num\">").append(resolvedCount).append("</div><div class=\"stat-label\">Resolved / Actioned</div></div>");
        html.append("</div>");

        if (reports.isEmpty()) {
            html.append("<div class=\"empty-state\"><p>No job reports found. All job postings are currently clean and in good standing!</p></div>");
        } else {
            html.append("<table class=\"data-table\"><thead><tr><th>#</th><th>Reported Job</th><th>Reporter</th><th>Reason</th><th>Details / Evidence</th><th>Reported On</th><th>Status</th><th>Actions</th></tr></thead><tbody>");
            int i = 1;
            for (JobDataStore.JobReportRecord r : reports) {
                String statusClass = switch (r.status != null ? r.status.toUpperCase() : "PENDING") {
                    case "RESOLVED" -> "status-success";
                    case "DISMISSED" -> "status-info";
                    default -> "status-danger";
                };

                html.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td><strong>").append(escapeHtml(r.jobTitle.isEmpty() ? "Job #" + r.jobId : r.jobTitle)).append("</strong>")
                    .append("<br><span style=\"color:#64748b;font-size:12px;\">🏢 ").append(escapeHtml(r.companyName)).append("</span></td>")
                    .append("<td>").append(escapeHtml(r.reporterEmail.isEmpty() ? "Anonymous" : r.reporterEmail)).append("</td>")
                    .append("<td><span class=\"status-badge status-danger\" style=\"font-size:11px;\">⚠️ ").append(escapeHtml(r.reason)).append("</span></td>")
                    .append("<td style=\"max-width:240px;word-break:break-word;\">").append(escapeHtml(r.details.isEmpty() ? "No additional details provided." : r.details)).append("</td>")
                    .append("<td>").append(escapeHtml(r.createdAt != null ? r.createdAt : "-")).append("</td>")
                    .append("<td><span class=\"status-badge ").append(statusClass).append("\">").append(escapeHtml(r.status != null ? r.status : "PENDING")).append("</span></td>")
                    .append("<td>");

                if (!"RESOLVED".equalsIgnoreCase(r.status)) {
                    html.append("<a href=\"/admin/reports/action?report_id=").append(r.reportId).append("&job_id=").append(r.jobId)
                        .append("&action=takedown\" class=\"action-btn action-danger\" onclick=\"return confirm('Take down and close this job posting?')\">🚫 Take Down</a> ");
                    html.append("<a href=\"/admin/reports/action?report_id=").append(r.reportId)
                        .append("&action=resolve\" class=\"action-btn action-success\">✅ Resolve</a> ");
                }
                if (!"DISMISSED".equalsIgnoreCase(r.status)) {
                    html.append("<a href=\"/admin/reports/action?report_id=").append(r.reportId)
                        .append("&action=dismiss\" class=\"action-btn\">✕ Dismiss</a>");
                }

                html.append("</td></tr>");
            }
            html.append("</tbody></table>");
        }

        html.append("</div></div>");
        html.append("<footer><p>© 2026 JobPortal System</p></footer></body></html>");
        sendResponse(exchange, html.toString());
    }

    private static void adminReportAction(HttpExchange exchange) throws IOException {
        SessionData session = getSession(exchange);
        if (session == null || !"ADMIN".equals(session.userType)) {
            sendRedirect(exchange, "/login");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI());
        int reportId = 0;
        int jobId = 0;
        try { reportId = Integer.parseInt(query.getOrDefault("report_id", "0")); } catch (Exception ignored) {}
        try { jobId = Integer.parseInt(query.getOrDefault("job_id", "0")); } catch (Exception ignored) {}
        String action = query.getOrDefault("action", "resolve");

        if ("takedown".equalsIgnoreCase(action)) {
            if (jobId > 0) JobDataStore.setJobStatusAdmin(jobId, "CLOSED");
            if (reportId > 0) JobDataStore.updateJobReportStatus(reportId, "RESOLVED");
        } else if ("dismiss".equalsIgnoreCase(action)) {
            if (reportId > 0) JobDataStore.updateJobReportStatus(reportId, "DISMISSED");
        } else {
            if (reportId > 0) JobDataStore.updateJobReportStatus(reportId, "RESOLVED");
        }

        sendRedirect(exchange, "/admin/reports?updated=1");
    }

    // ============================================================
    // CSS
    // ============================================================

    private static void cssPage(HttpExchange exchange) throws IOException {
        String css = """
            * { box-sizing: border-box; }
            body { margin:0; font-family:Arial,sans-serif; background:#f4f6f9; color:#1f2937; }

            /* ===== NAV ===== */
            nav { height:70px; background:#ffffff; border-bottom:1px solid #e5e7eb;
                  display:flex; align-items:center; justify-content:space-between;
                  padding:0 6%; color:#111827; position:sticky; top:0; z-index:100; box-shadow:0 1px 10px rgba(0,0,0,.06); }
            .logo { font-size:22px; font-weight:800; color:#111827; display:flex; align-items:center; gap:8px; }
            .logo-icon { color:#22c55e; font-size:22px; }
            .nav-links { display:flex; align-items:center; gap:20px; }
            .nav-links a { color:#4b5563; text-decoration:none; font-weight:600; font-size:14px; transition:color 0.2s; }
            .nav-links a:hover { color:#22c55e; }
            .btn-post-job { background:#22c55e; color:white!important; padding:8px 18px; border-radius:6px; font-weight:700; }
            .btn-post-job:hover { background:#16a34a!important; }
            .nav-dashboard-btn { background:#1e40af; color:white!important; padding:8px 16px; border-radius:6px; font-weight:700; }
            .nav-dashboard-btn:hover { background:#1e3a8a!important; }
            .btn-logout { background:#ef4444; color:white!important; padding:8px 16px; border-radius:6px; font-weight:700; }
            .btn-logout:hover { background:#dc2626!important; }

            /* ===== HERO ===== */
            .hero-modern { background:#fff; padding:60px 8% 70px; display:flex; align-items:center;
                           justify-content:space-between; gap:40px; flex-wrap:wrap; }
            .hero-text-col { flex:1; min-width:300px; max-width:580px; }
            .hero-text-col h1 { font-size:44px; font-weight:800; color:#111827; line-height:1.15; margin-bottom:20px; }
            .hero-subtext { font-size:16px; color:#6b7280; line-height:1.6; margin-bottom:30px; }
            .hero-image-col { flex:1; min-width:280px; max-width:500px; display:flex; justify-content:center; }
            .hero-image-col svg { max-width:100%; height:auto; }

            /* ===== SEARCH BOX ===== */
            .search-box { display:flex; align-items:center; background:#fff; border:1px solid #e5e7eb;
                          border-radius:40px; padding:6px 8px 6px 16px; box-shadow:0 10px 30px rgba(0,0,0,.06);
                          max-width:620px; flex-wrap:wrap; }
            .search-field { display:flex; align-items:center; flex:1; min-width:140px; gap:10px; padding:8px 12px; }
            .search-field:first-child { border-right:1px solid #f3f4f6; }
            .field-icon { font-size:16px; color:#9ca3af; }
            .search-field input { border:none; outline:none; width:100%; font-size:14px; color:#1f2937; background:transparent; }
            .btn-search-icon { background:#22c55e; color:white; border:none; width:48px; height:48px; border-radius:50%;
                               display:flex; align-items:center; justify-content:center; cursor:pointer; transition:background 0.2s; }
            .btn-search-icon:hover { background:#16a34a; }

            /* ===== CATEGORIES ===== */
            .categories-section { background:#f8fafc; padding:60px 8%; text-align:center; }
            .categories-section h2 { font-size:30px; font-weight:800; color:#111827; margin-bottom:8px; }
            .section-subtitle { color:#6b7280; font-size:15px; margin-bottom:40px; }
            .categories-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:22px; }
            .category-card { background:white; border-radius:12px; padding:28px 20px; text-decoration:none; color:inherit;
                             border:1px solid #f1f5f9; box-shadow:0 4px 15px rgba(0,0,0,.03);
                             transition:transform 0.25s, box-shadow 0.25s; display:flex; flex-direction:column; align-items:center; }
            .category-card:hover { transform:translateY(-5px); box-shadow:0 12px 30px rgba(0,0,0,.08); border-color:#22c55e; }
            .cat-icon { width:56px; height:56px; border-radius:50%; display:flex; align-items:center;
                        justify-content:center; font-size:24px; margin-bottom:15px; }
            .cat-green { background:#e6f4ea; } .cat-blue { background:#e0f2fe; }
            .cat-cyan { background:#e0f7fa; } .cat-purple { background:#f3e8ff; }
            .category-card h3 { font-size:16px; font-weight:700; color:#111827; margin:0 0 5px; }
            .category-card p { font-size:13px; color:#9ca3af; margin:0; }

            /* ===== HOW IT WORKS ===== */
            .how-it-works { background:#fff; padding:60px 8%; text-align:center; }
            .how-it-works h2 { font-size:30px; font-weight:800; color:#111827; margin-bottom:8px; }
            .steps-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:25px; margin-top:40px; }
            .step-card { background:#f8fafc; border-radius:12px; padding:30px 20px; }
            .step-num { width:50px; height:50px; border-radius:50%; background:#1e40af; color:white;
                        font-size:22px; font-weight:800; display:flex; align-items:center; justify-content:center; margin:0 auto 15px; }
            .step-card h3 { font-size:17px; font-weight:700; color:#111827; margin-bottom:10px; }
            .step-card p { font-size:14px; color:#6b7280; }

            /* ===== JOB SEARCH BANNER ===== */
            .job-search-banner { background:linear-gradient(135deg,#fef08a,#fde047 50%,#eab308);
                                 padding:28px 20px; text-align:center; border-bottom:2px solid #eab308;
                                 box-shadow:0 4px 15px rgba(234,179,8,.15); }
            .job-search-content { max-width:800px; margin:0 auto; display:flex; flex-direction:column; align-items:center; gap:14px; }
            .job-search-title { display:flex; align-items:center; justify-content:center; gap:12px;
                                font-size:28px; font-weight:900; color:#1e293b; letter-spacing:2px; text-transform:uppercase; }
            .job-search-form { display:flex; align-items:center; gap:8px; width:100%; background:#fff;
                               padding:5px 8px; border-radius:40px; box-shadow:0 8px 25px rgba(0,0,0,.12);
                               border:2px solid #e2e8f0; flex-wrap:wrap; }
            .job-search-input-wrap { display:flex; align-items:center; gap:8px; flex:2; min-width:140px; padding:4px 10px; }
            .job-search-input-wrap.loc-wrap { flex:1.2; border-left:1px solid #cbd5e1; }
            .job-search-input-wrap input { border:none; outline:none; width:100%; font-size:14px; color:#1e293b; background:transparent; }
            .job-search-btn { background:#1e293b; color:#fef08a; border:none; padding:9px 20px;
                              border-radius:30px; font-weight:700; font-size:13px; cursor:pointer;
                              transition:background 0.2s; text-transform:uppercase; letter-spacing:1px; }
            .job-search-btn:hover { background:#0f172a; }
            .location-quick-links { display:flex; align-items:center; gap:10px; flex-wrap:wrap; font-size:13px; color:#475569; }
            .location-quick-links a { background:rgba(255,255,255,.7); padding:4px 12px; border-radius:20px;
                                      text-decoration:none; color:#1e293b; font-weight:600; border:1px solid rgba(0,0,0,.1);
                                      transition:background 0.2s; }
            .location-quick-links a:hover { background:white; }

            /* ===== JOBS LIST ===== */
            .jobs-container { width:88%; max-width:1100px; margin:35px auto; }
            .jobs-count { font-size:15px; color:#6b7280; margin-bottom:20px; }
            .filter-notice { background:#e0f2fe; border:1px solid #bae6fd; color:#0369a1; padding:12px 18px;
                             border-radius:8px; margin-bottom:20px; display:flex; align-items:center;
                             justify-content:space-between; font-size:14px; }
            .clear-filter-btn { background:#0284c7; color:white; padding:5px 12px; border-radius:4px;
                                text-decoration:none; font-weight:bold; font-size:12px; }
            .job-card { background:white; margin-bottom:22px; padding:28px; border-radius:12px;
                        box-shadow:0 3px 15px rgba(0,0,0,.08); border-left:4px solid #22c55e; }
            .job-card h2 { color:#123b8f; margin:0 0 5px; font-size:20px; }
            .job-card h3 { color:#555; margin:0 0 12px; font-size:15px; }
            .job-card p { color:#4b5563; font-size:14px; margin:8px 0; }
            .job-info { display:flex; gap:12px; margin:15px 0; flex-wrap:wrap; }
            .job-info span { background:#eef3ff; padding:7px 14px; border-radius:20px; font-size:13px; color:#374151; }
            .card-actions { display:flex; gap:10px; margin-top:15px; align-items:center; flex-wrap:wrap; }
            .apply-button { background:#123b8f; color:white; border:none; padding:10px 22px; border-radius:6px;
                            cursor:pointer; font-weight:bold; font-size:14px; }
            .apply-button:hover { background:#1e3a8a; }
            .button-secondary { display:inline-block; padding:10px 18px; background:#f3f4f6; color:#123b8f;
                                text-decoration:none; border-radius:6px; font-weight:bold; border:1px solid #d1d5db; font-size:14px; }
            .button-secondary:hover { background:#e5e7eb; }
            .empty { background:white; padding:50px; border-radius:10px; text-align:center; }
            .job-detail-card { background:white; padding:28px; border-radius:12px; box-shadow:0 3px 15px rgba(0,0,0,.08); }

            /* ===== FORMS ===== */
            .form-container { min-height:80vh; display:flex; justify-content:center; align-items:center; padding:30px 20px; }
            .form-card { background:white; width:100%; max-width:460px; padding:38px; border-radius:12px;
                         box-shadow:0 5px 25px rgba(0,0,0,.1); }
            .form-card h1 { text-align:center; color:#123b8f; margin-bottom:28px; font-size:24px; }
            .form-card label { display:block; margin-top:14px; margin-bottom:6px; font-weight:bold; font-size:14px; }
            .form-card input, .form-card select, .form-card textarea { width:100%; padding:11px; border:1px solid #ccc;
                                                                        border-radius:6px; font-size:14px; }
            .form-card textarea { resize:vertical; }
            .form-card button { width:100%; margin-top:22px; background:#123b8f; color:white; border:none;
                                padding:13px; border-radius:6px; cursor:pointer; font-weight:bold; font-size:16px; }
            .form-card button:hover { background:#1e3a8a; }

            /* ===== SUCCESS ===== */
            .success-page { min-height:80vh; display:flex; justify-content:center; align-items:center; padding:20px; }
            .success-box { background:white; padding:50px; text-align:center; border-radius:12px;
                           box-shadow:0 5px 25px rgba(0,0,0,.1); max-width:500px; width:100%; }
            .success-box h1 { color:#123b8f; }
            .success-icon { font-size:60px; margin-bottom:15px; }
            .button { display:inline-block; padding:12px 28px; background:#123b8f; color:white;
                      text-decoration:none; border-radius:7px; font-weight:bold; }
            .button:hover { background:#1e3a8a; }

            /* ===== ANIMATIONS ===== */
            @keyframes fadeInUp { from { opacity:0; transform:translateY(20px); } to { opacity:1; transform:translateY(0); } }
            @keyframes fadeIn { from { opacity:0; } to { opacity:1; } }
            @keyframes slideInLeft { from { opacity:0; transform:translateX(-20px); } to { opacity:1; transform:translateX(0); } }
            @keyframes pulse { 0%,100%{transform:scale(1);} 50%{transform:scale(1.05);} }
            @keyframes countUp { from { opacity:0; transform:translateY(10px); } to { opacity:1; transform:translateY(0); } }

            /* ===== DASHBOARD LAYOUT ===== */
            .dashboard-container { display:flex; min-height:calc(100vh - 70px); }
            .dashboard-sidebar { width:250px; min-width:250px; background:linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
                                 color:white; padding:0; flex-shrink:0; display:flex; flex-direction:column; }
            .sidebar-user { padding:22px 20px 6px; font-size:16px; font-weight:700; word-break:break-word; }
            .sidebar-role { padding:0 20px 18px; font-size:11px; color:#64748b; text-transform:uppercase;
                            letter-spacing:1.5px; border-bottom:1px solid #334155; }
            .sidebar-nav { display:flex; flex-direction:column; margin-top:8px; flex:1; }
            .sidebar-nav a { color:#94a3b8; text-decoration:none; padding:13px 20px; font-size:14px; font-weight:500;
                             transition:all 0.25s ease; display:block; border-left:3px solid transparent; position:relative; }
            .sidebar-nav a:hover { background:rgba(255,255,255,.06); color:#e2e8f0; border-left-color:#3b82f6; padding-left:24px; }
            .sidebar-nav a.active { background:rgba(59,130,246,.15); color:#60a5fa; border-left-color:#3b82f6;
                                     font-weight:700; padding-left:24px; }
            .sidebar-brand { padding:18px 20px; border-top:1px solid #334155; text-align:center; margin-top:auto; }
            .sidebar-brand-text { font-size:11px; color:#475569; letter-spacing:1px; }
            .sidebar-brand-tech { font-size:10px; color:#334155; margin-top:4px; }
            .dashboard-main { flex:1; padding:32px; overflow:auto; background:#f1f5f9; animation:fadeIn 0.4s ease; }
            .dashboard-title { font-size:26px; font-weight:800; color:#111827; margin:0 0 8px;
                               animation:fadeInUp 0.5s ease; }
            .dashboard-subtitle { color:#6b7280; font-size:14px; margin:0 0 24px; animation:fadeInUp 0.6s ease; }
            .company-badge { background:#e0f2fe; color:#0369a1; padding:6px 14px; border-radius:20px;
                             font-weight:600; font-size:14px; display:inline-block; margin-bottom:20px; }

            /* ===== STATS ===== */
            .stats-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:18px; margin-bottom:28px; }
            .stat-card { background:white; padding:24px 20px; border-radius:12px; box-shadow:0 3px 15px rgba(0,0,0,.06);
                         text-align:center; border-top:4px solid #1e40af; transition:all 0.3s ease; cursor:default;
                         animation:fadeInUp 0.5s ease both; position:relative; overflow:hidden; }
            .stat-card:nth-child(1) { animation-delay:0.1s; }
            .stat-card:nth-child(2) { animation-delay:0.2s; }
            .stat-card:nth-child(3) { animation-delay:0.3s; }
            .stat-card:nth-child(4) { animation-delay:0.4s; }
            .stat-card:hover { transform:translateY(-5px); box-shadow:0 10px 30px rgba(0,0,0,.12); }
            .stat-card::before { content:''; position:absolute; top:0; left:-100%; width:100%; height:100%;
                                 background:linear-gradient(90deg,transparent,rgba(255,255,255,.3),transparent);
                                 transition:left 0.5s; }
            .stat-card:hover::before { left:100%; }
            .stat-card.stat-success { border-top-color:#22c55e; }
            .stat-card.stat-danger { border-top-color:#ef4444; }
            .stat-card.stat-info { border-top-color:#0ea5e9; }
            .stat-icon { font-size:32px; margin-bottom:8px; }
            .stat-num { font-size:38px; font-weight:800; color:#111827; animation:countUp 0.8s ease both; }
            .stat-label { font-size:13px; color:#6b7280; margin-top:6px; font-weight:500; }

            /* ===== DATA TABLE ===== */
            .data-table { width:100%; border-collapse:collapse; background:white; border-radius:10px;
                          box-shadow:0 3px 12px rgba(0,0,0,.06); overflow:hidden; }
            .data-table th { background:#1e293b; color:white; padding:13px 15px; text-align:left; font-size:13px; }
            .data-table td { padding:12px 15px; font-size:13px; border-bottom:1px solid #f1f5f9; vertical-align:middle; }
            .data-table tr:hover { background:#f8fafc; }
            .data-table tr:last-child td { border-bottom:none; }

            /* ===== STATUS BADGES ===== */
            .status-badge { padding:4px 12px; border-radius:20px; font-size:12px; font-weight:700; text-transform:uppercase; display:inline-block; }
            .status-success { background:#dcfce7; color:#16a34a; }
            .status-danger { background:#fee2e2; color:#dc2626; }
            .status-info { background:#e0f2fe; color:#0369a1; }
            .badge { background:#e0f2fe; color:#0369a1; padding:3px 10px; border-radius:20px; font-size:12px; font-weight:700; }
            .badge-link { background:#e0f2fe; color:#0369a1; padding:3px 10px; border-radius:20px; font-size:12px;
                          font-weight:700; text-decoration:none; }
            .badge-link:hover { background:#bae6fd; }

            /* ===== ACTION BUTTONS ===== */
            .action-btn { display:inline-block; padding:5px 10px; border-radius:5px; text-decoration:none;
                          font-size:12px; font-weight:600; background:#e0f2fe; color:#0369a1; transition:background 0.2s; }
            .action-btn:hover { background:#bae6fd; }
            .action-success { background:#dcfce7; color:#16a34a; }
            .action-success:hover { background:#bbf7d0; }
            .action-danger { background:#fee2e2; color:#dc2626; }
            .action-danger:hover { background:#fecaca; }
            .btn-add { background:#22c55e; color:white; padding:10px 20px; border-radius:8px; text-decoration:none;
                       font-weight:700; font-size:14px; }
            .btn-add:hover { background:#16a34a; }

            /* ===== DASHBOARD SECTIONS ===== */
            .dashboard-section { background:white; border-radius:10px; padding:24px; box-shadow:0 3px 12px rgba(0,0,0,.06); margin-bottom:20px; }
            .dashboard-section h2 { font-size:18px; color:#111827; margin:0 0 15px; }
            .empty-msg { color:#9ca3af; font-size:14px; }
            .empty-msg a { color:#1e40af; }
            .empty-state { background:white; border-radius:10px; padding:40px; text-align:center; box-shadow:0 3px 12px rgba(0,0,0,.06); }
            .empty-state p { color:#6b7280; margin-bottom:20px; }
            .view-all-link { display:inline-block; margin-top:12px; color:#1e40af; text-decoration:none; font-weight:600; font-size:14px; }
            .view-all-link:hover { text-decoration:underline; }

            /* ===== QUICK ACTIONS ===== */
            .dashboard-quick-actions { background:white; border-radius:10px; padding:24px; box-shadow:0 3px 12px rgba(0,0,0,.06); }
            .dashboard-quick-actions h2 { font-size:18px; color:#111827; margin:0 0 15px; }
            .quick-actions-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(140px,1fr)); gap:14px; }
            .quick-action-card { display:flex; flex-direction:column; align-items:center; gap:8px; padding:20px 15px;
                                 background:#f8fafc; border-radius:10px; text-decoration:none; color:#374151;
                                 font-size:13px; font-weight:600; border:1px solid #e5e7eb; transition:all 0.2s; text-align:center; }
            .quick-action-card:hover { background:#e0f2fe; border-color:#0284c7; color:#0369a1; }
            .quick-action-card span { font-size:20px; }

            /* ===== ADMIN MODULES ===== */
            .admin-modules-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:22px; margin-top:16px; }
            .admin-module-card { background:white; border-radius:14px; padding:32px 22px; text-decoration:none;
                                 color:inherit; box-shadow:0 4px 16px rgba(0,0,0,.06); text-align:center;
                                 border:1px solid #e5e7eb; transition:all 0.35s cubic-bezier(.25,.8,.25,1);
                                 animation:fadeInUp 0.5s ease both; position:relative; overflow:hidden; }
            .admin-module-card:nth-child(1) { animation-delay:0.15s; }
            .admin-module-card:nth-child(2) { animation-delay:0.25s; }
            .admin-module-card:nth-child(3) { animation-delay:0.35s; }
            .admin-module-card:nth-child(4) { animation-delay:0.45s; }
            .admin-module-card:hover { transform:translateY(-8px) scale(1.02); box-shadow:0 16px 40px rgba(0,0,0,.12);
                                        border-color:#3b82f6; }
            .admin-module-card::after { content:'→'; position:absolute; bottom:16px; right:18px; font-size:18px;
                                         color:#cbd5e1; transition:all 0.3s; opacity:0; transform:translateX(-10px); }
            .admin-module-card:hover::after { opacity:1; transform:translateX(0); color:#3b82f6; }
            .module-icon { font-size:48px; margin-bottom:14px; transition:transform 0.3s; }
            .admin-module-card:hover .module-icon { transform:scale(1.15); }
            .admin-module-card h3 { font-size:17px; font-weight:700; color:#111827; margin:0 0 8px; }
            .admin-module-card p { font-size:13px; color:#6b7280; margin:0; line-height:1.5; }

            /* ===== SYSTEM INFO CARDS ===== */
            .system-info-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(280px,1fr)); gap:20px; margin-top:24px; }
            .system-info-card { background:white; border-radius:12px; padding:24px; box-shadow:0 3px 12px rgba(0,0,0,.06);
                                animation:fadeInUp 0.6s ease both; }
            .system-info-card h3 { font-size:16px; font-weight:700; color:#111827; margin:0 0 16px;
                                    display:flex; align-items:center; gap:8px; }
            .info-row { display:flex; justify-content:space-between; align-items:center; padding:10px 0;
                         border-bottom:1px solid #f1f5f9; font-size:13px; }
            .info-row:last-child { border-bottom:none; }
            .info-label { color:#6b7280; }
            .info-value { color:#111827; font-weight:600; }
            .tech-tag { display:inline-block; padding:4px 12px; border-radius:6px; font-size:12px;
                         font-weight:600; margin:3px; background:#f1f5f9; color:#374151; transition:all 0.2s; }
            .tech-tag:hover { background:#1e40af; color:white; transform:scale(1.05); }
            .activity-item { display:flex; align-items:center; gap:12px; padding:12px 0;
                              border-bottom:1px solid #f8fafc; font-size:13px; }
            .activity-item:last-child { border-bottom:none; }
            .activity-dot { width:8px; height:8px; border-radius:50%; flex-shrink:0; }
            .activity-dot.green { background:#22c55e; }
            .activity-dot.blue { background:#3b82f6; }
            .activity-dot.amber { background:#f59e0b; }

            /* ===== FILTER BAR ===== */
            .filter-bar { display:flex; align-items:center; gap:10px; flex-wrap:wrap; margin-bottom:20px;
                          background:white; padding:14px 18px; border-radius:10px; box-shadow:0 2px 8px rgba(0,0,0,.05); }
            .filter-bar span { font-size:13px; color:#6b7280; font-weight:600; }
            .filter-chip { padding:5px 14px; border-radius:20px; font-size:13px; text-decoration:none;
                           color:#374151; background:#f1f5f9; font-weight:500; transition:all 0.2s; }
            .filter-chip:hover { background:#e0f2fe; color:#0369a1; }
            .filter-chip.active { background:#1e40af; color:white; }

            /* ===== PROFILE ===== */
            .profile-card { display:flex; align-items:center; gap:25px; background:white; padding:28px;
                            border-radius:12px; box-shadow:0 3px 12px rgba(0,0,0,.06); }
            .profile-avatar { font-size:60px; }
            .profile-info h2 { font-size:22px; margin:0 0 8px; color:#111827; }
            .profile-info p { font-size:15px; color:#6b7280; margin:5px 0; }

            /* ===== MODAL ===== */
            .modal { display:none; position:fixed; z-index:1000; left:0; top:0; width:100%; height:100%;
                     overflow:auto; background:rgba(0,0,0,.5); justify-content:center; align-items:center; }
            .modal-content { background:#fff; padding:30px; border-radius:12px; width:90%; max-width:500px;
                             position:relative; box-shadow:0 5px 25px rgba(0,0,0,.2); }
            .modal-subtitle { color:#666; margin-top:-10px; margin-bottom:20px; }
            .close-btn { position:absolute; right:20px; top:15px; font-size:28px; font-weight:bold;
                         cursor:pointer; color:#666; line-height:1; }
            .close-btn:hover { color:#000; }
            .modal-form label { display:block; margin-top:12px; margin-bottom:5px; font-weight:bold; font-size:13px; }
            .modal-form input, .modal-form textarea { width:100%; padding:10px; border:1px solid #ccc;
                                                      border-radius:6px; font-size:14px; box-sizing:border-box; }
            .submit-btn { width:100%; margin-top:18px; background:#123b8f; color:white; border:none;
                          padding:12px; border-radius:6px; font-weight:bold; cursor:pointer; font-size:15px; }
            .submit-btn:hover { background:#1e3a8a; }

            /* ===== AI MATCHING & JOBS INTEL ===== */
            .matching-jobs-header-banner { background:linear-gradient(135deg, #0284c7 0%, #1e40af 100%);
                                           color:white; padding:26px 30px; border-radius:14px; margin-bottom:24px;
                                           box-shadow:0 10px 25px -5px rgba(2,132,199,0.3); }
            .matches-list { display:flex; flex-direction:column; gap:20px; }
            .match-card-full { background:white; border-radius:14px; padding:26px; border:1px solid #e2e8f0;
                               box-shadow:0 4px 16px rgba(0,0,0,.04); transition:all 0.25s ease;
                               border-left:5px solid #0284c7; }
            .match-card-full:hover { transform:translateY(-3px); box-shadow:0 12px 30px rgba(0,0,0,.08); }
            .match-card-top { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; margin-bottom:14px; flex-wrap:wrap; }
            .match-card-title-col { flex:1; min-width:240px; }
            .match-job-title { margin:0 0 6px 0; font-size:20px; font-weight:700; }
            .match-job-title a { color:#0f172a; text-decoration:none; transition:color 0.2s; }
            .match-job-title a:hover { color:#0284c7; }
            .match-job-subtitle { color:#64748b; font-size:14px; line-height:1.4; }
            .match-score-badge-wrap { flex-shrink:0; }
            .match-pill-high { background:linear-gradient(135deg, #10b981 0%, #059669 100%); color:white; padding:8px 16px;
                               border-radius:20px; font-weight:800; font-size:14px; box-shadow:0 3px 10px rgba(16,185,129,.3); display:inline-block; }
            .match-pill-mid { background:linear-gradient(135deg, #f59e0b 0%, #d97706 100%); color:white; padding:8px 16px;
                              border-radius:20px; font-weight:800; font-size:14px; box-shadow:0 3px 10px rgba(245,158,11,.3); display:inline-block; }
            .match-pill-low { background:#94a3b8; color:white; padding:8px 16px; border-radius:20px; font-weight:800; font-size:14px; display:inline-block; }

            .why-match-box { background:#f8fafc; border:1px solid #e2e8f0; border-radius:10px; padding:14px 18px; margin:14px 0; }
            .why-match-heading { font-size:12px; font-weight:800; color:#475569; letter-spacing:0.5px; margin-bottom:8px; text-transform:uppercase; }
            .why-match-badges-grid { display:flex; flex-wrap:wrap; gap:8px; }
            .why-badge-match { background:#dcfce7; color:#15803d; border:1px solid #bbf7d0; padding:4px 10px; border-radius:6px; font-size:12px; font-weight:600; display:inline-block; }
            .why-badge-missing { background:#fef3c7; color:#b45309; border:1px solid #fde68a; padding:4px 10px; border-radius:6px; font-size:12px; font-weight:600; display:inline-block; }
            .match-gap-teaser-row { display:flex; justify-content:space-between; align-items:center; background:#eff6ff; padding:10px 16px; border-radius:8px; font-size:13px; color:#1e40af; margin-bottom:16px; flex-wrap:wrap; gap:10px; border:1px solid #dbeafe; }
            .gap-text { font-size:13px; }
            .projected-text { font-size:13px; color:#0369a1; }
            .match-card-actions-row { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }

            /* ===== CANDIDATE DISCOVERY ===== */
            .candidates-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(340px,1fr)); gap:20px; margin-top:20px; }
            .candidate-card { background:white; border-radius:14px; padding:24px; border:1px solid #e2e8f0; box-shadow:0 3px 12px rgba(0,0,0,.04); display:flex; flex-direction:column; justify-content:space-between; transition:all 0.25s; }
            .candidate-card:hover { transform:translateY(-3px); box-shadow:0 12px 28px rgba(0,0,0,.08); border-color:#93c5fd; }
            .candidate-header { display:flex; align-items:flex-start; gap:14px; margin-bottom:12px; }
            .candidate-avatar { width:46px; height:46px; border-radius:50%; background:linear-gradient(135deg, #1e40af, #3b82f6); color:white; display:flex; align-items:center; justify-content:center; font-size:20px; font-weight:800; flex-shrink:0; box-shadow:0 3px 8px rgba(59,130,246,.3); }
            .candidate-info { flex:1; min-width:0; }
            .candidate-meta { font-size:13px; color:#64748b; margin-top:3px; }
            .candidate-score-wrap { flex-shrink:0; }
            .candidate-skills-wrap { display:flex; flex-wrap:wrap; gap:6px; margin-top:6px; }

            /* ===== MODALS & OVERLAYS ===== */
            .modal-overlay { display:none; position:fixed; z-index:2000; left:0; top:0; width:100%; height:100%; background:rgba(15,23,42,.6); backdrop-filter:blur(4px); justify-content:center; align-items:center; padding:20px; box-sizing:border-box; }
            .modal-card { background:white; border-radius:14px; width:100%; max-width:500px; padding:28px; box-shadow:0 20px 40px rgba(0,0,0,.2); animation:fadeInUp 0.3s ease; box-sizing:border-box; max-height:90vh; overflow-y:auto; position:relative; }

            /* ===== INTERVIEWS ===== */
            .interviews-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(320px,1fr)); gap:20px; margin-top:20px; }
            .interview-card { background:white; border-radius:12px; padding:22px; border:1px solid #e2e8f0; box-shadow:0 3px 12px rgba(0,0,0,.05); border-left:4px solid #10b981; }

            /* ===== RESUME PROFILE & TAXONOMY ===== */
            .resume-profile-header-card { background:white; border-radius:14px; padding:26px; border:1px solid #e2e8f0; display:flex; align-items:center; gap:22px; margin-bottom:24px; box-shadow:0 3px 12px rgba(0,0,0,.04); flex-wrap:wrap; }
            .prof-avatar { font-size:46px; background:#eff6ff; width:72px; height:72px; border-radius:50%; display:flex; align-items:center; justify-content:center; flex-shrink:0; border:2px solid #bfdbfe; }
            .prof-meta { flex:1; min-width:240px; }
            .prof-contact-line { font-size:13px; color:#64748b; margin-top:8px; display:flex; flex-wrap:wrap; gap:16px; }
            .taxonomy-categories-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:18px; margin-top:18px; }
            .taxonomy-cat-box { background:white; border-radius:12px; padding:18px; border:1px solid #e2e8f0; box-shadow:0 2px 6px rgba(0,0,0,.03); }
            .tax-cat-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; font-weight:700; color:#1e293b; }
            .tax-badges-wrap { display:flex; flex-wrap:wrap; gap:6px; }
            .tax-skill-pill { background:#f1f5f9; color:#334155; padding:5px 11px; border-radius:6px; font-size:12px; font-weight:600; border:1px solid #e2e8f0; }

            /* ===== SIMILAR ROLES ===== */
            .similar-roles-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:18px; margin-top:18px; }
            .similar-role-card { background:white; border-radius:12px; padding:22px; border:1px solid #e2e8f0; box-shadow:0 3px 10px rgba(0,0,0,.04); text-decoration:none; color:inherit; transition:transform 0.2s, box-shadow 0.2s; display:block; }
            .similar-role-card:hover { transform:translateY(-4px); box-shadow:0 10px 24px rgba(0,0,0,.08); border-color:#0284c7; }
            .similar-role-title { font-size:18px; font-weight:700; color:#0f172a; margin-bottom:6px; }
            .similar-role-vacancies { font-size:13px; color:#0284c7; font-weight:600; margin-bottom:12px; }
            .similar-role-skills { display:flex; flex-wrap:wrap; gap:6px; margin-top:10px; }

            /* ===== NOTIFICATIONS ===== */
            .notifications-list { display:flex; flex-direction:column; gap:12px; margin-top:18px; }
            .notif-card { background:white; border-radius:10px; padding:18px 20px; border:1px solid #e2e8f0; box-shadow:0 2px 6px rgba(0,0,0,.03); display:flex; align-items:flex-start; gap:16px; text-decoration:none; color:inherit; transition:background 0.2s; }
            .notif-card:hover { background:#f8fafc; }
            .notif-icon { font-size:24px; flex-shrink:0; }
            .notif-content { flex:1; }
            .notif-title-row { display:flex; justify-content:space-between; align-items:center; margin-bottom:4px; }
            .notif-title { font-weight:700; color:#0f172a; font-size:15px; }
            .notif-date { font-size:12px; color:#94a3b8; }
            .notif-msg { font-size:14px; color:#475569; margin:0; }
            .nav-notif-pill { background:#ef4444; color:white; font-size:11px; font-weight:800; padding:2px 7px; border-radius:10px; margin-left:4px; }

            /* ===== CAREER PATHWAY & STEPPER ===== */
            .career-ladder-container { display:flex; flex-direction:column; gap:20px; margin-top:24px; position:relative; }
            .career-step-node { background:white; border-radius:12px; padding:22px; border:1px solid #e2e8f0; box-shadow:0 3px 10px rgba(0,0,0,.04); display:flex; align-items:flex-start; gap:18px; }
            .step-num-circle { width:42px; height:42px; border-radius:50%; background:#0284c7; color:white; display:flex; align-items:center; justify-content:center; font-weight:800; font-size:16px; flex-shrink:0; }
            .step-info { flex:1; }
            .step-title { font-size:18px; font-weight:700; color:#0f172a; margin-bottom:4px; }
            .step-exp { font-size:13px; color:#64748b; margin-bottom:12px; }
            .step-status-tag { display:inline-block; padding:3px 10px; border-radius:10px; font-size:11px; font-weight:800; text-transform:uppercase; margin-bottom:8px; }

            /* ===== SKILL GAP & RADAR ===== */
            .skill-gap-hero-card { background:white; border-radius:14px; padding:28px; border:1px solid #e2e8f0; box-shadow:0 4px 16px rgba(0,0,0,.05); margin-bottom:24px; display:flex; gap:30px; align-items:center; flex-wrap:wrap; }
            .gap-score-col { text-align:center; min-width:150px; }
            .gap-score-num { font-size:52px; font-weight:900; color:#0284c7; line-height:1; }
            .gap-score-title { font-size:13px; color:#64748b; font-weight:600; margin-top:8px; }
            .gap-meter-col { flex:1; min-width:260px; }
            .skill-progress-bar { height:10px; background:#e2e8f0; border-radius:5px; overflow:hidden; margin-top:6px; }
            .skill-progress-fill { height:100%; background:linear-gradient(90deg, #0284c7, #10b981); border-radius:5px; transition:width 0.5s ease; }
            .missing-skills-list-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:16px; margin-top:16px; }
            .missing-skill-card { background:#fff5f5; border-radius:10px; padding:18px; border:1px solid #fecaca; }

            /* ===== UPLOAD DROPZONE ===== */
            .upload-dropzone { border:2px dashed #93c5fd; background:#f0f9ff; border-radius:14px; padding:40px 20px; text-align:center; cursor:pointer; transition:all 0.2s; }
            .upload-dropzone:hover { border-color:#0284c7; background:#e0f2fe; }
            .dropzone-icon { font-size:48px; margin-bottom:12px; }
            .dropzone-text { font-size:16px; font-weight:700; color:#0f172a; margin-bottom:6px; }
            .dropzone-formats { font-size:13px; color:#64748b; }

            /* ===== PILLS & BADGES ===== */
            .badge-success { background:#dcfce7; color:#15803d; padding:4px 10px; border-radius:12px; font-size:12px; font-weight:700; display:inline-block; }
            .badge-info { background:#e0f2fe; color:#0369a1; padding:4px 10px; border-radius:12px; font-size:12px; font-weight:700; display:inline-block; }
            .badge-role { background:#f1f5f9; color:#475569; padding:4px 10px; border-radius:12px; font-size:12px; font-weight:700; display:inline-block; }
            .button-success { background:#16a34a; color:white; padding:10px 20px; border-radius:6px; text-decoration:none; font-weight:bold; display:inline-block; }
            .button-success:hover { background:#15803d; }

            /* ===== FOOTER ===== */
            footer { text-align:center; padding:25px; background:#111827; color:#9ca3af; font-size:13px; }

            /* ===== RESPONSIVE ===== */
            @media(max-width:768px) {
                .dashboard-container { flex-direction:column; }
                .dashboard-sidebar { width:100%; }
                .sidebar-nav { flex-direction:row; flex-wrap:wrap; overflow-x:auto; }
                .sidebar-nav a { padding:10px 14px; font-size:13px; }
                .hero-modern { flex-direction:column; padding:40px 5%; }
                .hero-text-col h1 { font-size:34px; }
                .search-box { flex-direction:column; border-radius:20px; padding:10px; }
                .search-field:first-child { border-right:none; border-bottom:1px solid #f3f4f6; }
                .btn-search-icon { width:100%; border-radius:12px; }
                .form-card { width:100%; max-width:100%; }
                .data-table { font-size:12px; }
                .data-table th, .data-table td { padding:8px 10px; }
                .nav-links { gap:10px; }
            }
            """;

        exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
        byte[] bytes = css.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> map = new HashMap<>();
        if (uri == null) return map;
        String query = uri.getQuery();
        if (query != null && !query.isEmpty()) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
        }
        return map;
    }

    private static Map<String, String> parseFormData(String body) {
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

    private static void sendResponse(HttpExchange exchange, String html) throws IOException {
        SecurityManager.setSecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String errorPage(String title, String message, String backLink) {
        return """
            <!DOCTYPE html><html>
            <head><title>%s</title><link rel="stylesheet" href="/css/style.css"></head>
            <body>
            <div class="success-page">
                <div class="success-box">
                    <div class="success-icon">❌</div>
                    <h1 style="color:#dc2626;">%s</h1>
                    <p>%s</p>
                    <a class="button" href="%s">Go Back</a>
                </div>
            </div>
            </body></html>
            """.formatted(escapeHtml(title), escapeHtml(title), escapeHtml(message), escapeHtml(backLink));
    }

    @SuppressWarnings("unused")
    private static String errorPage(String title, String message) {
        return errorPage(title, message, "/");
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ============================================================
    // TRUSTMATCH AI & SECURITY FLOW HANDLER WRAPPERS
    // ============================================================

    private static void forgotPasswordPage(HttpExchange exchange) throws IOException {
        TrustMatchHandlers.forgotPasswordPage(exchange);
    }

    private static void resetPasswordPage(HttpExchange exchange) throws IOException {
        TrustMatchHandlers.resetPasswordPage(exchange);
    }

    private static void adminAuditLog(HttpExchange exchange) throws IOException {
        SessionData s = getSession(exchange);
        if (s == null) { sendRedirect(exchange, "/login"); return; }
        TrustMatchHandlers.adminAuditLog(exchange, s.userId, s.email, s.userType, s.fullName, s.companyName);
    }

    private static void trustMatchDashboard(HttpExchange exchange) throws IOException {
        SessionData s = getSession(exchange);
        if (s == null) { sendRedirect(exchange, "/login"); return; }
        TrustMatchHandlers.trustMatchDashboard(exchange, s.userId, s.email, s.userType, s.fullName, s.companyName);
    }

    private static void trustVerifyCompany(HttpExchange exchange) throws IOException {
        SessionData s = getSession(exchange);
        if (s == null) { sendRedirect(exchange, "/login"); return; }
        TrustMatchHandlers.trustVerifyCompany(exchange, s.userId, s.email, s.userType, s.fullName, s.companyName);
    }

    private static void trustScamCheck(HttpExchange exchange) throws IOException {
        SessionData s = getSession(exchange);
        if (s == null) { sendRedirect(exchange, "/login"); return; }
        TrustMatchHandlers.trustScamCheck(exchange, s.userId, s.email, s.userType, s.fullName, s.companyName);
    }

    private static void seekerCareerRoadmap(HttpExchange exchange) throws IOException {
        SessionData s = getSession(exchange);
        if (s == null) { sendRedirect(exchange, "/login"); return; }
        TrustMatchHandlers.seekerCareerRoadmap(exchange, s.userId, s.email, s.userType, s.fullName, s.companyName);
    }

    private static void seekerMatchesV2(HttpExchange exchange) throws IOException {
        SessionData s = getSession(exchange);
        if (s == null) { sendRedirect(exchange, "/login"); return; }
        TrustMatchHandlers.seekerMatchesV2(exchange, s.userId, s.email, s.userType, s.fullName, s.companyName);
    }

    private static void recruiterPipelineIntelligence(HttpExchange exchange) throws IOException {
        SessionData s = getSession(exchange);
        if (s == null) { sendRedirect(exchange, "/login"); return; }
        TrustMatchHandlers.recruiterPipelineIntelligence(exchange, s.userId, s.email, s.userType, s.fullName, s.companyName);
    }
}