import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class WebServer {

    private static final int PORT = 8080;
    private static final String ADMIN_ACCESS_PASSWORD = "admin";
    private static final String RECRUITER_ACCESS_PASSWORD = "recruiter";

    public static void main(String[] args) throws Exception {

        HttpServer server =
                HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/", WebServer::homePage);
        server.createContext("/jobs", WebServer::jobsPage);
        server.createContext("/apply", WebServer::applyPage);
        server.createContext("/register", WebServer::registerPage);
        server.createContext("/login", WebServer::loginPage);
        server.createContext("/css/style.css", WebServer::cssPage);

        server.setExecutor(null);

        System.out.println();
        System.out.println("======================================");
        System.out.println("          JOB PORTAL SYSTEM");
        System.out.println("======================================");
        System.out.println("Web server started successfully!");
        System.out.println();
        System.out.println("Open your browser:");
        System.out.println("http://localhost:8080");
        System.out.println();
        System.out.println("Press Ctrl+C to stop the server.");

        server.start();
    }

    // ==========================================
    // HOME PAGE
    // ==========================================

    private static void homePage(HttpExchange exchange) throws IOException {

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>JobPortal - Find the job that fits your life</title>
                    <link rel="stylesheet" href="/css/style.css">
                </head>

                <body>

                <nav>
                    <div class="logo">
                        <span class="logo-icon">🔍</span>
                        <span>JobPortal</span>
                    </div>

                    <div class="nav-links">
                        <a href="/">Home</a>
                        <a href="/jobs">Find Jobs</a>
                        <a href="/login">Login</a>
                        <a href="/register">Register</a>
                        <a href="/register" class="btn-post-job">POST A JOB</a>
                    </div>
                </nav>

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
                                <input type="text" name="keyword" placeholder="Job Title or Company Name">
                            </div>
                            <div class="search-field">
                                <span class="field-icon">📍</span>
                                <input type="text" name="location" placeholder="Locations">
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
                            <!-- Background elements -->
                            <rect x="20" y="40" width="460" height="300" rx="16" fill="#F8FAFC" />
                            <!-- Desk & Office scene -->
                            <rect x="180" y="220" width="260" height="90" rx="10" fill="#E2E8F0" />
                            <rect x="195" y="200" width="120" height="25" rx="4" fill="#94A3B8" />
                            <!-- Computer Monitor -->
                            <rect x="220" y="140" width="70" height="50" rx="6" fill="#1E293B" />
                            <rect x="225" y="145" width="60" height="40" rx="3" fill="#38BDF8" />
                            <rect x="250" y="190" width="10" height="12" fill="#64748B" />
                            <!-- Clock on wall -->
                            <circle cx="410" cy="100" r="22" fill="#FFFFFF" stroke="#CBD5E1" stroke-width="3" />
                            <polyline points="410,88 410,100 418,100" stroke="#475569" stroke-width="3" stroke-linecap="round" />
                            <!-- Chart Board -->
                            <rect x="290" y="70" width="90" height="65" rx="6" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2" />
                            <polyline points="305,115 325,95 345,105 365,85" stroke="#22C55E" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
                            <!-- Plant -->
                            <circle cx="435" cy="270" r="18" fill="#EF4444" />
                            <path d="M435,252 C420,225 410,210 420,195 C435,210 435,235 435,252 Z" fill="#22C55E" />
                            <path d="M435,252 C450,225 460,210 450,195 C435,210 435,235 435,252 Z" fill="#16A34A" />
                            <!-- Candidate (Standing Left) -->
                            <circle cx="105" cy="115" r="18" fill="#FCA5A5" />
                            <path d="M90 140 C90 135 120 135 120 140 L125 210 L112 210 L105 170 L98 210 L85 210 Z" fill="#1E3A8A" />
                            <path d="M82 145 C75 160 80 180 90 190" stroke="#1E3A8A" stroke-width="8" stroke-linecap="round" />
                            <!-- Recruiter (Sitting Right at desk) -->
                            <circle cx="345" cy="130" r="16" fill="#FDBA74" />
                            <path d="M330 152 C330 150 360 150 360 152 L365 210 L325 210 Z" fill="#9333EA" />
                        </svg>
                    </div>

                </section>

                <section class="categories-section">
                    <h2>Browse Categories</h2>
                    <p class="section-subtitle">Most popular categories of portal, sorted by popularity</p>

                    <div class="categories-grid">
                        <a href="/jobs?keyword=Finance" class="category-card">
                            <div class="cat-icon cat-green">🏛️</div>
                            <h3>Finance</h3>
                            <p>(4,286 jobs)</p>
                        </a>

                        <a href="/jobs?keyword=Sales" class="category-card">
                            <div class="cat-icon cat-blue">🌐</div>
                            <h3>Sale/Marketing</h3>
                            <p>(2,000 jobs)</p>
                        </a>

                        <a href="/jobs?keyword=Education" class="category-card">
                            <div class="cat-icon cat-cyan">📖</div>
                            <h3>Education/Training</h3>
                            <p>(1,450 jobs)</p>
                        </a>

                        <a href="/jobs?keyword=Developer" class="category-card">
                            <div class="cat-icon cat-purple">💻</div>
                            <h3>Technologies</h3>
                            <p>(5,100 jobs)</p>
                        </a>
                    </div>
                </section>

                <footer>
                    <p>© 2026 JobPortal System</p>
                </footer>

                </body>
                </html>
                """;

        sendResponse(exchange, html);
    }

    // ==========================================
    // JOBS PAGE
    // ==========================================

    private static void jobsPage(HttpExchange exchange)
            throws IOException {

        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
        String keyword = queryParams.get("keyword");
        String location = queryParams.get("location");
        String kwVal = keyword != null ? escapeHtml(keyword.trim()) : "";
        String locVal = location != null ? escapeHtml(location.trim()) : "";

        StringBuilder jobsHtml = new StringBuilder();

        jobsHtml.append("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Available Jobs - JobPortal</title>
                    <link rel="stylesheet" href="/css/style.css">
                </head>

                <body>

                <nav>
                    <div class="logo">
                        <span class="logo-icon">🔍</span>
                        <span>JobPortal</span>
                    </div>

                    <div class="nav-links">
                        <a href="/">Home</a>
                        <a href="/jobs">Find Jobs</a>
                        <a href="/login">Login</a>
                        <a href="/register">Register</a>
                        <a href="/register" class="btn-post-job">POST A JOB</a>
                    </div>
                </nav>

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
                                <input type="text" name="keyword" placeholder="Job Title or Company..." value="%s">
                            </div>
                            <div class="job-search-input-wrap loc-wrap">
                                <span class="search-icon">📍</span>
                                <input type="text" name="location" placeholder="Location..." value="%s">
                            </div>
                            <button type="submit" class="job-search-btn">Search</button>
                        </form>
                    </div>
                </section>

                <main class="jobs-container">
                """.formatted(kwVal, locVal));

        if ((keyword != null && !keyword.trim().isEmpty()) || (location != null && !location.trim().isEmpty())) {
            jobsHtml.append("<div class=\"filter-notice\">");
            jobsHtml.append("<span>Showing search results ");
            if (keyword != null && !keyword.trim().isEmpty()) {
                jobsHtml.append("for <strong>\"").append(escapeHtml(keyword.trim())).append("\"</strong> ");
            }
            if (location != null && !location.trim().isEmpty()) {
                jobsHtml.append("in <strong>\"").append(escapeHtml(location.trim())).append("\"</strong>");
            }
            jobsHtml.append("</span>");
            jobsHtml.append("<a href=\"/jobs\" class=\"clear-filter-btn\">✕ Clear Filter</a>");
            jobsHtml.append("</div>");
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    j.job_id,
                    j.job_title,
                    j.description,
                    j.skills_required,
                    j.qualification,
                    j.experience_required,
                    j.salary,
                    j.location,
                    j.job_type,
                    c.company_name
                FROM jobs j
                INNER JOIN companies c
                    ON j.company_id = c.company_id
                WHERE j.status = 'OPEN'
                """);

        java.util.List<String> params = new java.util.ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (LOWER(j.job_title) LIKE ? OR LOWER(j.description) LIKE ? OR LOWER(j.skills_required) LIKE ? OR LOWER(c.company_name) LIKE ?)");
            String kw = "%" + keyword.trim().toLowerCase() + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        if (location != null && !location.trim().isEmpty()) {
            sql.append(" AND LOWER(j.location) LIKE ?");
            params.add("%" + location.trim().toLowerCase() + "%");
        }
        sql.append(" ORDER BY j.posted_date DESC");

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }

            try (ResultSet result = statement.executeQuery()) {

                boolean found = false;

            while (result.next()) {

                found = true;

                jobsHtml.append("<div class='job-card'>");

                jobsHtml.append("<h2>")
                        .append(escapeHtml(
                                result.getString("job_title")))
                        .append("</h2>");

                jobsHtml.append("<h3>")
                        .append(escapeHtml(
                                result.getString("company_name")))
                        .append("</h3>");

                jobsHtml.append("<p>")
                        .append(escapeHtml(
                                result.getString("description")))
                        .append("</p>");

                jobsHtml.append("<div class='job-info'>");

                jobsHtml.append("<span>📍 ")
                        .append(escapeHtml(
                                result.getString("location")))
                        .append("</span>");

                jobsHtml.append("<span>💼 ")
                        .append(escapeHtml(
                                result.getString("job_type")))
                        .append("</span>");

                jobsHtml.append("<span>💰 ")
                        .append(escapeHtml(
                                result.getString("salary")))
                        .append("</span>");

                jobsHtml.append("</div>");

                jobsHtml.append("<p><strong>Skills:</strong> ")
                        .append(escapeHtml(
                                result.getString("skills_required")))
                        .append("</p>");

                jobsHtml.append("<p><strong>Qualification:</strong> ")
                        .append(escapeHtml(
                                result.getString("qualification")))
                        .append("</p>");

                int jobId = result.getInt("job_id");
                String jobTitle = result.getString("job_title");
                String companyName = result.getString("company_name");

                jobsHtml.append(String.format("""
                        <div class="card-actions">
                            <button class="apply-button" data-job-id="%d" data-job-title="%s" data-company-name="%s" onclick="openModalFromBtn(this)">Apply Now</button>
                            <a href="/apply?job_id=%d" class="button-secondary">View & Apply</a>
                        </div>
                        """,
                        jobId,
                        escapeHtml(jobTitle),
                        escapeHtml(companyName),
                        jobId
                ));

                jobsHtml.append("</div>");
            }

            if (!found) {

                jobsHtml.append("""
                        <div class="empty">

                            <h2>No Jobs Available</h2>

                            <p>
                                There are currently no open jobs.
                            </p>

                        </div>
                        """);
            }
        }
    } catch (Exception e) {

            jobsHtml.append("""
                    <div class="empty">
                        <h2>Unable to load jobs</h2>
                    """);

            jobsHtml.append("<p>")
                    .append(escapeHtml(e.getMessage()))
                    .append("</p>");

            jobsHtml.append("</div>");
        }

        jobsHtml.append("""
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
                        if (event.target == modal) {
                            modal.style.display = 'none';
                        }
                    }
                </script>

                <footer>
                    <p>© 2026 JobPortal System</p>
                </footer>

                </body>
                </html>
                """);

        sendResponse(exchange, jobsHtml.toString());
    }

    // ==========================================
    // APPLY PAGE / ENDPOINT
    // ==========================================

    private static void applyPage(HttpExchange exchange) throws IOException {

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {

            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            Map<String, String> data = parseFormData(body);

            String jobIdStr = data.get("jobId");
            if (jobIdStr == null || jobIdStr.isEmpty()) {
                jobIdStr = data.get("job_id");
            }
            String fullName = data.get("fullName");
            String email = data.get("email");
            String phone = data.get("phone");
            String coverLetter = data.get("coverLetter");

            if (jobIdStr == null || jobIdStr.isEmpty()) {
                sendResponse(exchange, errorPage("Invalid Request", "No Job ID provided.", "/jobs"));
                return;
            }

            int jobId;
            try {
                jobId = Integer.parseInt(jobIdStr);
            } catch (NumberFormatException e) {
                sendResponse(exchange, errorPage("Invalid Request", "Invalid Job ID format.", "/jobs"));
                return;
            }

            if (fullName == null || fullName.trim().isEmpty() || email == null || email.trim().isEmpty()) {
                sendResponse(exchange, errorPage("Validation Error", "Full name and email are required.", "/apply?job_id=" + jobId));
                return;
            }

            String jobTitle = "";
            String companyName = "";

            String jobSql = """
                    SELECT j.job_title, c.company_name
                    FROM jobs j
                    INNER JOIN companies c ON j.company_id = c.company_id
                    WHERE j.job_id = ?
                    """;

            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(jobSql)) {

                statement.setInt(1, jobId);
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    jobTitle = rs.getString("job_title");
                    companyName = rs.getString("company_name");
                } else {
                    sendResponse(exchange, errorPage("Job Not Found", "The job you are applying for does not exist.", "/jobs"));
                    return;
                }

            } catch (Exception e) {
                sendResponse(exchange, errorPage("Database Error", e.getMessage(), "/jobs"));
                return;
            }

            int userId = -1;

            try (Connection connection = DatabaseConnection.getConnection()) {

                // 1. Find or create user
                String findUserSql = "SELECT user_id FROM users WHERE email = ?";
                try (PreparedStatement statement = connection.prepareStatement(findUserSql)) {
                    statement.setString(1, email);
                    ResultSet rs = statement.executeQuery();
                    if (rs.next()) {
                        userId = rs.getInt("user_id");
                    }
                }

                if (userId == -1) {
                    String createUserSql = """
                            INSERT INTO users (full_name, email, password, phone, user_type)
                            VALUES (?, ?, 'password123', ?, 'JOB_SEEKER')
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(createUserSql, Statement.RETURN_GENERATED_KEYS)) {
                        statement.setString(1, fullName);
                        statement.setString(2, email);
                        statement.setString(3, phone != null ? phone : "");
                        statement.executeUpdate();
                        ResultSet generatedKeys = statement.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            userId = generatedKeys.getInt(1);
                        }
                    }
                }

                if (userId == -1) {
                    sendResponse(exchange, errorPage("Error", "Could not verify user account.", "/jobs"));
                    return;
                }

                // 2. Check duplicate application
                String checkAppSql = "SELECT application_id FROM applications WHERE job_id = ? AND user_id = ?";
                try (PreparedStatement statement = connection.prepareStatement(checkAppSql)) {
                    statement.setInt(1, jobId);
                    statement.setInt(2, userId);
                    ResultSet rs = statement.executeQuery();
                    if (rs.next()) {
                        sendResponse(exchange, errorPage("Already Applied", "You have already submitted an application for " + jobTitle + ".", "/jobs"));
                        return;
                    }
                }

                // 3. Insert application
                String insertAppSql = """
                        INSERT INTO applications (job_id, user_id, cover_letter, application_status, applied_at)
                        VALUES (?, ?, ?, 'APPLIED', NOW())
                        """;
                try (PreparedStatement statement = connection.prepareStatement(insertAppSql)) {
                    statement.setInt(1, jobId);
                    statement.setInt(2, userId);
                    statement.setString(3, coverLetter);
                    statement.executeUpdate();
                }

                String successHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <title>Application Submitted</title>
                            <link rel="stylesheet" href="/css/style.css">
                        </head>
                        <body>
                        <nav>
                            <div class="logo">JobPortal</div>
                            <div class="nav-links">
                                <a href="/">Home</a>
                                <a href="/jobs">Find Jobs</a>
                            </div>
                        </nav>
                        <div class="success-page">
                            <div class="success-box">
                                <h1>Application Submitted!</h1>
                                <p>Your application for <strong>%s</strong> at <strong>%s</strong> has been successfully received.</p>
                                <p><strong>Applicant:</strong> %s (%s)</p>
                                <p><strong>Status:</strong> <span style="color: #123b8f; font-weight: bold;">APPLIED</span></p>
                                <br>
                                <a class="button" href="/jobs">Browse More Jobs</a>
                            </div>
                        </div>
                        </body>
                        </html>
                        """.formatted(
                        escapeHtml(jobTitle),
                        escapeHtml(companyName),
                        escapeHtml(fullName),
                        escapeHtml(email)
                );

                sendResponse(exchange, successHtml);
                return;

            } catch (Exception e) {
                sendResponse(exchange, errorPage("Application Error", e.getMessage(), "/jobs"));
                return;
            }
        }

        // GET request handling for /apply?job_id=X
        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
        String jobIdStr = queryParams.get("job_id");
        if (jobIdStr == null || jobIdStr.isEmpty()) {
            jobIdStr = queryParams.get("jobId");
        }

        if (jobIdStr == null || jobIdStr.isEmpty()) {
            sendResponse(exchange, errorPage("Invalid Request", "No job specified to apply for.", "/jobs"));
            return;
        }

        int jobId;
        try {
            jobId = Integer.parseInt(jobIdStr);
        } catch (NumberFormatException e) {
            sendResponse(exchange, errorPage("Invalid Request", "Invalid Job ID.", "/jobs"));
            return;
        }

        String jobTitle = "";
        String companyName = "";
        String location = "";
        String salary = "";
        String jobType = "";

        String sql = """
                SELECT j.job_title, j.salary, j.location, j.job_type, c.company_name
                FROM jobs j
                INNER JOIN companies c ON j.company_id = c.company_id
                WHERE j.job_id = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, jobId);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                jobTitle = rs.getString("job_title");
                companyName = rs.getString("company_name");
                location = rs.getString("location");
                salary = rs.getString("salary");
                jobType = rs.getString("job_type");
            } else {
                sendResponse(exchange, errorPage("Job Not Found", "The specified job was not found.", "/jobs"));
                return;
            }

        } catch (Exception e) {
            sendResponse(exchange, errorPage("Error Loading Job", e.getMessage(), "/jobs"));
            return;
        }

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Apply for %s - JobPortal</title>
                    <link rel="stylesheet" href="/css/style.css">
                </head>
                <body>
                <nav>
                    <div class="logo">JobPortal</div>
                    <div class="nav-links">
                        <a href="/">Home</a>
                        <a href="/jobs">Find Jobs</a>
                    </div>
                </nav>
                <div class="form-container">
                    <form method="POST" action="/apply" class="form-card" style="width: 550px;">
                        <h1>Apply for Position</h1>
                        <h2 style="color: #123b8f; margin-bottom: 5px; text-align: center;">%s</h2>
                        <h3 style="color: #666; margin-top: 0; margin-bottom: 20px; text-align: center;">%s</h3>
                        <p style="background: #f8fafc; padding: 12px; border-radius: 6px; font-size: 14px;">
                            📍 %s | 💼 %s | 💰 %s
                        </p>
                        <input type="hidden" name="jobId" value="%d">

                        <label>Full Name *</label>
                        <input type="text" name="fullName" required placeholder="Enter your full name">

                        <label>Email Address *</label>
                        <input type="email" name="email" required placeholder="Enter your email address">

                        <label>Phone Number</label>
                        <input type="text" name="phone" placeholder="Enter phone number">

                        <label>Cover Letter / Additional Info</label>
                        <textarea name="coverLetter" rows="4" style="width:100%%; padding:10px; border-radius:6px; border:1px solid #ccc;" placeholder="Why are you a good fit for this role?"></textarea>

                        <button type="submit">Submit Application</button>
                    </form>
                </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(jobTitle),
                escapeHtml(jobTitle),
                escapeHtml(companyName),
                escapeHtml(location),
                escapeHtml(jobType),
                escapeHtml(salary),
                jobId
        );

        sendResponse(exchange, html);
    }

    private static Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> queryPairs = new HashMap<>();
        if (uri == null) return queryPairs;
        String query = uri.getQuery();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    queryPairs.put(key, value);
                }
            }
        }
        return queryPairs;
    }

    // ==========================================
    // REGISTER PAGE
    // ==========================================

    private static void registerPage(HttpExchange exchange)
            throws IOException {

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {

            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            Map<String, String> data =
                    parseFormData(body);

            String fullName = data.get("fullName");
            String email = data.get("email");
            String password = data.get("password");
            String phone = data.get("phone");
            String userType = data.get("userType");
            String securityPassword = data.get("securityPassword");

            if ("ADMIN".equalsIgnoreCase(userType)) {
                if (securityPassword == null || (!ADMIN_ACCESS_PASSWORD.equalsIgnoreCase(securityPassword) && !"admin123".equals(securityPassword))) {
                    sendResponse(exchange,
                            errorPage("Admin Access Denied",
                                    "Incorrect Admin Security Password. You must enter 'admin' to register as an Admin.",
                                    "/register"));
                    return;
                }
            } else if ("RECRUITER".equalsIgnoreCase(userType)) {
                if (securityPassword == null || (!RECRUITER_ACCESS_PASSWORD.equalsIgnoreCase(securityPassword) && !ADMIN_ACCESS_PASSWORD.equalsIgnoreCase(securityPassword))) {
                    sendResponse(exchange,
                            errorPage("Recruiter Access Denied",
                                    "Incorrect Recruiter Security Password. You must enter a valid security password to register as a Recruiter.",
                                    "/register"));
                    return;
                }
            }

            String sql = """
                    INSERT INTO users
                    (full_name, email, password, phone, user_type)
                    VALUES (?, ?, ?, ?, ?)
                    """;

            try (Connection connection =
                         DatabaseConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setString(1, fullName);
                statement.setString(2, email);
                statement.setString(3, password);
                statement.setString(4, phone);
                statement.setString(5, userType);

                statement.executeUpdate();

                String response = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <title>Registration Successful</title>
                            <link rel="stylesheet"
                                  href="/css/style.css">
                        </head>

                        <body>

                        <div class="success-page">

                            <div class="success-box">

                                <h1>Registration Successful!</h1>

                                <p>
                                    Welcome to JobPortal,
                                    %s!
                                </p>

                                <a class="button"
                                   href="/login">
                                    Login Now
                                </a>

                            </div>

                        </div>

                        </body>
                        </html>
                        """.formatted(
                                escapeHtml(fullName)
                        );

                sendResponse(exchange, response);
                return;

            } catch (Exception e) {

                sendResponse(exchange,
                        errorPage(
                                "Registration Failed",
                                e.getMessage(),
                                "/register"));

                return;
            }
        }

        String html = """

                <!DOCTYPE html>
                <html>

                <head>
                    <title>Register - JobPortal</title>
                    <link rel="stylesheet"
                          href="/css/style.css">
                </head>

                <body>

                <nav>
                    <div class="logo">JobPortal</div>

                    <div class="nav-links">
                        <a href="/">Home</a>
                        <a href="/jobs">Find Jobs</a>
                        <a href="/login">Login</a>
                    </div>
                </nav>

                <div class="form-container">

                    <form method="POST"
                          action="/register"
                          class="form-card"
                          autocomplete="off">

                        <h1>Create Account</h1>

                        <label>Full Name</label>

                        <input type="text"
                               name="fullName"
                               autocomplete="off"
                               required>

                        <label>Email</label>

                        <input type="email"
                               name="email"
                               id="regEmail"
                               value=""
                               autocomplete="new-password"
                               readonly
                               onfocus="this.removeAttribute('readonly');"
                               required>

                        <label>Password</label>

                        <input type="password"
                               name="password"
                               id="regPassword"
                               value=""
                               autocomplete="new-password"
                               readonly
                               onfocus="this.removeAttribute('readonly');"
                               required>

                        <label>Phone Number</label>

                        <input type="text"
                               name="phone"
                               autocomplete="off">

                        <label>User Type</label>

                        <select name="userType" id="userTypeSelect" onchange="toggleSecurityPassword()">

                            <option value="JOB_SEEKER">
                                Job Seeker
                            </option>

                            <option value="RECRUITER">
                                Recruiter
                            </option>

                            <option value="ADMIN">
                                Admin
                            </option>

                        </select>

                        <div id="securityPasswordContainer" style="display: none; margin-top: 15px;">
                            <label id="securityPasswordLabel">Security Authorization Password *</label>
                            <input type="password"
                                   name="securityPassword"
                                   id="securityPasswordInput"
                                   value=""
                                   autocomplete="new-password"
                                   readonly
                                   onfocus="this.removeAttribute('readonly');"
                                   placeholder="Enter security password">
                        </div>

                        <button type="submit">
                            Create Account
                        </button>

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

                    function toggleSecurityPassword() {
                        var select = document.getElementById('userTypeSelect');
                        var container = document.getElementById('securityPasswordContainer');
                        var label = document.getElementById('securityPasswordLabel');
                        var input = document.getElementById('securityPasswordInput');
                        var val = select.value;
                        if (val === 'ADMIN') {
                            container.style.display = 'block';
                            label.innerText = 'Admin Security Password *';
                            input.placeholder = 'Enter admin password (admin)';
                            input.required = true;
                        } else if (val === 'RECRUITER') {
                            container.style.display = 'block';
                            label.innerText = 'Recruiter Security Password *';
                            input.placeholder = 'Enter recruiter security password';
                            input.required = true;
                        } else {
                            container.style.display = 'none';
                            input.value = '';
                            input.required = false;
                        }
                    }
                </script>

                </body>
                </html>             </html>

                """;

        sendResponse(exchange, html);
    }

    // ==========================================
    // LOGIN PAGE
    // ==========================================

    private static void loginPage(HttpExchange exchange)
            throws IOException {

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {

            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            Map<String, String> data =
                    parseFormData(body);

            String email = data.get("email");
            String password = data.get("password");

            String userType = data.get("userType");
String adminAccessPassword = data.get("adminAccessPassword");

if ("ADMIN".equals(userType)
        && !ADMIN_ACCESS_PASSWORD.equals(adminAccessPassword)) {

    sendResponse(exchange,
            errorPage("Admin Access Denied",
                    "Incorrect admin access password.",
                    "/login"));

    return;
}

            String sql = """
                    SELECT user_id, full_name, user_type
                    FROM users
                    WHERE email = ?
                    AND password = ?
                    """;

            try (Connection connection =
                         DatabaseConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setString(1, email);
                statement.setString(2, password);

                ResultSet result =
                        statement.executeQuery();

                if (result.next()) {

                    String name =
                            result.getString("full_name");

                    userType =
                            result.getString("user_type");

                    String html = """
                            <!DOCTYPE html>
                            <html>

                            <head>
                                <title>Login Successful</title>
                                <link rel="stylesheet"
                                      href="/css/style.css">
                            </head>

                            <body>

                            <nav>
                                <div class="logo">
                                    JobPortal
                                </div>

                                <div class="nav-links">
                                    <a href="/">Home</a>
                                    <a href="/jobs">Find Jobs</a>
                                </div>
                            </nav>

                            <div class="success-page">

                                <div class="success-box">

                                    <h1>
                                        Login Successful
                                    </h1>

                                    <h2>
                                        Welcome, %s!
                                    </h2>

                                    <p>
                                        Account Type:
                                        <strong>%s</strong>
                                    </p>

                                    <a class="button"
                                       href="/jobs">
                                        Browse Jobs
                                    </a>

                                </div>

                            </div>

                            </body>
                            </html>
                            """.formatted(
                                    escapeHtml(name),
                                    escapeHtml(userType)
                            );

                    sendResponse(exchange, html);

                } else {

                    sendResponse(exchange,
                            errorPage(
                                    "Login Failed",
                                    "Invalid email or password."
                            ));
                }

            } catch (Exception e) {

                sendResponse(exchange,
                        errorPage(
                                "Login Error",
                                e.getMessage()));
            }

            return;
        }

        String html = """

                <!DOCTYPE html>
                <html>

                <head>
                    <title>Login - JobPortal</title>
                    <link rel="stylesheet"
                          href="/css/style.css">
                </head>

                <body>

                <nav>
                    <div class="logo">JobPortal</div>

                    <div class="nav-links">
                        <a href="/">Home</a>
                        <a href="/jobs">Find Jobs</a>
                        <a href="/register">Register</a>
                    </div>
                </nav>

                <div class="form-container">

                    <form method="POST"
                          action="/login"
                          class="form-card"
                          autocomplete="off">

                        <h1>Login</h1>

                        <label>Email</label>

                        <input type="email"
                               name="email"
                               id="loginEmail"
                               value=""
                               autocomplete="off"
                               readonly
                               onfocus="this.removeAttribute('readonly');"
                               required>

                        <label>Password</label>

                        <input type="password"
                               name="password"
                               id="loginPassword"
                               value=""
                               autocomplete="new-password"
                               readonly
                               onfocus="this.removeAttribute('readonly');"
                               required>

                        <button type="submit">
                            Login
                        </button>

                        <p>
                            Don't have an account?
                            <a href="/register">
                                Register
                            </a>
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

                </body>
                </html>

                """;

        sendResponse(exchange, html);
    }

    // ==========================================
    // CSS
    // ==========================================

    private static void cssPage(HttpExchange exchange)
            throws IOException {

        String css = """

                * {
                    box-sizing: border-box;
                }

                body {
                    margin: 0;
                    font-family: Arial, sans-serif;
                    background: #f4f6f9;
                    color: #1f2937;
                }

                nav {
                    height: 75px;
                    background: #ffffff;
                    border-bottom: 1px solid #e5e7eb;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    padding: 0 8%;
                    color: #111827;
                }

                .logo {
                    font-size: 26px;
                    font-weight: 800;
                    color: #111827;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }

                .logo-icon {
                    color: #22c55e;
                    font-size: 24px;
                }

                .nav-links {
                    display: flex;
                    align-items: center;
                    gap: 25px;
                }

                .nav-links a {
                    color: #4b5563;
                    text-decoration: none;
                    font-weight: 600;
                    font-size: 15px;
                    transition: color 0.2s;
                }

                .nav-links a:hover {
                    color: #22c55e;
                }

                .btn-post-job {
                    background: #22c55e;
                    color: white !important;
                    padding: 10px 22px;
                    border-radius: 6px;
                    font-weight: 700;
                    text-decoration: none;
                    transition: background 0.2s;
                }

                .btn-post-job:hover {
                    background: #16a34a;
                }

                .hero-modern {
                    background: #ffffff;
                    padding: 60px 8% 80px 8%;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 40px;
                    flex-wrap: wrap;
                }

                .hero-text-col {
                    flex: 1;
                    min-width: 320px;
                    max-width: 580px;
                }

                .hero-text-col h1 {
                    font-size: 48px;
                    font-weight: 800;
                    color: #111827;
                    line-height: 1.15;
                    margin-bottom: 20px;
                }

                .hero-subtext {
                    font-size: 16px;
                    color: #6b7280;
                    line-height: 1.6;
                    margin-bottom: 35px;
                }

                .search-box {
                    display: flex;
                    align-items: center;
                    background: #ffffff;
                    border: 1px solid #e5e7eb;
                    border-radius: 40px;
                    padding: 6px 8px 6px 16px;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.06);
                    max-width: 620px;
                    flex-wrap: wrap;
                }

                .search-field {
                    display: flex;
                    align-items: center;
                    flex: 1;
                    min-width: 150px;
                    gap: 10px;
                    padding: 8px 12px;
                }

                .search-field:first-child {
                    border-right: 1px solid #f3f4f6;
                }

                .field-icon {
                    font-size: 16px;
                    color: #9ca3af;
                }

                .search-field input {
                    border: none;
                    outline: none;
                    width: 100%;
                    font-size: 14px;
                    color: #1f2937;
                    background: transparent;
                }

                .btn-search-icon {
                    background: #22c55e;
                    color: white;
                    border: none;
                    width: 48px;
                    height: 48px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    cursor: pointer;
                    transition: background 0.2s, transform 0.1s;
                }

                .btn-search-icon:hover {
                    background: #16a34a;
                    transform: scale(1.05);
                }

                .hero-image-col {
                    flex: 1;
                    min-width: 320px;
                    max-width: 520px;
                    display: flex;
                    justify-content: center;
                }

                .hero-image-col svg {
                    max-width: 100%;
                    height: auto;
                }

                .categories-section {
                    background: #f8fafc;
                    padding: 70px 8%;
                    text-align: center;
                }

                .categories-section h2 {
                    font-size: 32px;
                    font-weight: 800;
                    color: #111827;
                    margin-bottom: 8px;
                }

                .section-subtitle {
                    color: #6b7280;
                    font-size: 15px;
                    margin-bottom: 45px;
                }

                .categories-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
                    gap: 25px;
                }

                .category-card {
                    background: white;
                    border-radius: 12px;
                    padding: 30px 20px;
                    text-decoration: none;
                    color: inherit;
                    border: 1px solid #f1f5f9;
                    box-shadow: 0 4px 15px rgba(0,0,0,0.03);
                    transition: transform 0.25s, box-shadow 0.25s, border-color 0.25s;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                }

                .category-card:hover {
                    transform: translateY(-6px);
                    box-shadow: 0 12px 30px rgba(0,0,0,0.08);
                    border-color: #22c55e;
                }

                .cat-icon {
                    width: 60px;
                    height: 60px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 26px;
                    margin-bottom: 18px;
                }

                .cat-green { background: #e6f4ea; color: #22c55e; }
                .cat-blue { background: #e0f2fe; color: #0284c7; }
                .cat-cyan { background: #e0f7fa; color: #00acc1; }
                .cat-purple { background: #f3e8ff; color: #9333ea; }

                .category-card h3 {
                    font-size: 17px;
                    font-weight: 700;
                    color: #111827;
                    margin: 0 0 6px 0;
                }

                .category-card p {
                    font-size: 13px;
                    color: #9ca3af;
                    margin: 0;
                }

                .filter-notice {
                    background: #e0f2fe;
                    border: 1px solid #bae6fd;
                    color: #0369a1;
                    padding: 14px 20px;
                    border-radius: 8px;
                    margin-bottom: 25px;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    font-size: 15px;
                }

                .clear-filter-btn {
                    background: #0284c7;
                    color: white;
                    padding: 6px 14px;
                    border-radius: 4px;
                    text-decoration: none;
                    font-weight: bold;
                    font-size: 13px;
                }

                .hero {
                    min-height: 450px;
                    background:
                        linear-gradient(
                            rgba(18,59,143,.92),
                            rgba(18,59,143,.92)
                        );

                    display: flex;
                    align-items: center;
                    justify-content: center;
                    text-align: center;
                    color: white;
                }

                .hero h1 {
                    font-size: 55px;
                    margin-bottom: 15px;
                }

                .hero p {
                    font-size: 20px;
                    margin-bottom: 35px;
                }

                .button {
                    display: inline-block;
                    padding: 14px 30px;
                    background: white;
                    color: #123b8f;
                    text-decoration: none;
                    border-radius: 7px;
                    font-weight: bold;
                }

                .features {
                    display: grid;
                    grid-template-columns:
                        repeat(3, 1fr);

                    gap: 25px;
                    padding: 60px 8%;
                }

                .feature {
                    background: white;
                    padding: 30px;
                    border-radius: 10px;
                    box-shadow:
                        0 3px 15px
                        rgba(0,0,0,.08);
                }

                .job-search-banner {
                    background: linear-gradient(135deg, #fef08a 0%, #fde047 50%, #eab308 100%);
                    padding: 30px 20px;
                    text-align: center;
                    border-bottom: 2px solid #eab308;
                    box-shadow: 0 4px 15px rgba(234, 179, 8, 0.15);
                }

                .job-search-content {
                    max-width: 680px;
                    margin: 0 auto;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    gap: 16px;
                }

                .job-search-title {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 12px;
                    font-size: 32px;
                    font-weight: 900;
                    color: #1e293b;
                    letter-spacing: 2px;
                    font-family: 'Arial Black', Impact, sans-serif;
                    text-transform: uppercase;
                }

                .search-magnifier {
                    flex-shrink: 0;
                }

                .job-search-form {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    width: 100%;
                    background: #ffffff;
                    padding: 6px 10px;
                    border-radius: 40px;
                    box-shadow: 0 8px 25px rgba(0, 0, 0, 0.12);
                    border: 2px solid #e2e8f0;
                    flex-wrap: wrap;
                }

                .job-search-input-wrap {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    flex: 2;
                    min-width: 160px;
                    padding: 4px 10px;
                }

                .job-search-input-wrap.loc-wrap {
                    flex: 1.2;
                    border-left: 1px solid #cbd5e1;
                }

                .job-search-input-wrap input {
                    border: none;
                    outline: none;
                    width: 100%;
                    font-size: 14px;
                    color: #1e293b;
                    background: transparent;
                    font-weight: 500;
                }

                .job-search-btn {
                    background: #1e293b;
                    color: #fef08a;
                    border: none;
                    padding: 10px 22px;
                    border-radius: 30px;
                    font-weight: 700;
                    font-size: 13px;
                    cursor: pointer;
                    transition: background 0.2s, transform 0.1s;
                    text-transform: uppercase;
                    letter-spacing: 1px;
                }

                .job-search-btn:hover {
                    background: #0f172a;
                    transform: scale(1.03);
                }

                .page-header {
                    background: #123b8f;
                    color: white;
                    text-align: center;
                    padding: 55px;
                }

                .page-header h1 {
                    font-size: 42px;
                    margin: 0;
                }

                .jobs-container {
                    width: 85%;
                    max-width: 1100px;
                    margin: 40px auto;
                }

                .job-card {
                    background: white;
                    margin-bottom: 25px;
                    padding: 30px;
                    border-radius: 12px;
                    box-shadow:
                        0 3px 15px
                        rgba(0,0,0,.08);
                }

                .job-card h2 {
                    color: #123b8f;
                }

                .job-card h3 {
                    color: #555;
                }

                .job-info {
                    display: flex;
                    gap: 20px;
                    margin: 20px 0;
                    flex-wrap: wrap;
                }

                .job-info span {
                    background: #eef3ff;
                    padding: 9px 14px;
                    border-radius: 20px;
                }

                .apply-button,
                .form-card button {
                    background: #123b8f;
                    color: white;
                    border: none;
                    padding: 13px 25px;
                    border-radius: 6px;
                    cursor: pointer;
                    font-weight: bold;
                }

                .empty {
                    background: white;
                    padding: 50px;
                    border-radius: 10px;
                    text-align: center;
                }

                .form-container {
                    min-height: 80vh;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                }

                .form-card {
                    background: white;
                    width: 450px;
                    padding: 40px;
                    border-radius: 12px;
                    box-shadow:
                        0 5px 25px
                        rgba(0,0,0,.1);
                }

                .form-card h1 {
                    text-align: center;
                    color: #123b8f;
                    margin-bottom: 30px;
                }

                .form-card label {
                    display: block;
                    margin-top: 15px;
                    margin-bottom: 7px;
                    font-weight: bold;
                }

                .form-card input,
                .form-card select {
                    width: 100%;
                    padding: 12px;
                    border: 1px solid #ccc;
                    border-radius: 6px;
                    font-size: 15px;
                }

                .form-card button {
                    width: 100%;
                    margin-top: 25px;
                    font-size: 16px;
                }

                .success-page {
                    min-height: 80vh;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                }

                .success-box {
                    background: white;
                    padding: 50px;
                    text-align: center;
                    border-radius: 12px;
                    box-shadow:
                        0 5px 25px
                        rgba(0,0,0,.1);
                }

                .success-box h1 {
                    color: #123b8f;
                }

                footer {
                    text-align: center;
                    padding: 30px;
                    background: #111827;
                    color: white;
                }

                .card-actions {
                    display: flex;
                    gap: 12px;
                    margin-top: 15px;
                    align-items: center;
                }

                .button-secondary {
                    display: inline-block;
                    padding: 13px 20px;
                    background: #f3f4f6;
                    color: #123b8f;
                    text-decoration: none;
                    border-radius: 6px;
                    font-weight: bold;
                    border: 1px solid #d1d5db;
                }

                .button-secondary:hover {
                    background: #e5e7eb;
                }

                .modal {
                    display: none;
                    position: fixed;
                    z-index: 1000;
                    left: 0;
                    top: 0;
                    width: 100%;
                    height: 100%;
                    overflow: auto;
                    background-color: rgba(0,0,0,0.5);
                    justify-content: center;
                    align-items: center;
                }

                .modal-content {
                    background-color: #fff;
                    padding: 30px;
                    border-radius: 12px;
                    width: 90%;
                    max-width: 500px;
                    position: relative;
                    box-shadow: 0 5px 25px rgba(0,0,0,0.2);
                }

                .modal-subtitle {
                    color: #666;
                    margin-top: -10px;
                    margin-bottom: 20px;
                }

                .close-btn {
                    position: absolute;
                    right: 20px;
                    top: 15px;
                    font-size: 28px;
                    font-weight: bold;
                    cursor: pointer;
                    color: #666;
                }

                .close-btn:hover {
                    color: #000;
                }

                .modal-form label {
                    display: block;
                    margin-top: 12px;
                    margin-bottom: 5px;
                    font-weight: bold;
                }

                .modal-form input,
                .modal-form textarea {
                    width: 100%;
                    padding: 10px;
                    border: 1px solid #ccc;
                    border-radius: 6px;
                    font-size: 14px;
                }

                .modal-form button.submit-btn {
                    width: 100%;
                    margin-top: 20px;
                    background: #123b8f;
                    color: white;
                    border: none;
                    padding: 12px;
                    border-radius: 6px;
                    font-weight: bold;
                    cursor: pointer;
                    font-size: 16px;
                }

                @media(max-width: 700px) {

                    .hero-modern {
                        flex-direction: column;
                        padding: 40px 5%;
                    }

                    .categories-grid {
                        grid-template-columns: 1fr 1fr;
                    }

                    .search-box {
                        flex-direction: column;
                        border-radius: 20px;
                        padding: 10px;
                    }

                    .search-field:first-child {
                        border-right: none;
                        border-bottom: 1px solid #f3f4f6;
                    }

                    .btn-search-icon {
                        width: 100%;
                        border-radius: 12px;
                    }

                    .features {
                        grid-template-columns: 1fr;
                    }

                    .nav-links {
                        gap: 10px;
                    }

                    .hero h1 {
                        font-size: 38px;
                    }

                    .form-card {
                        width: 90%;
                    }
                }

                """;

        exchange.getResponseHeaders()
                .set("Content-Type", "text/css; charset=UTF-8");

        byte[] bytes =
                css.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(
                200,
                bytes.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(bytes);
        }
    }

    // ==========================================
    // FORM DATA
    // ==========================================

    private static Map<String, String>
    parseFormData(String body) {

        Map<String, String> data =
                new HashMap<>();

        if (body == null || body.isEmpty()) {
            return data;
        }

        String[] pairs =
                body.split("&");

        for (String pair : pairs) {

            String[] keyValue =
                    pair.split("=", 2);

            if (keyValue.length == 2) {

                String key =
                        URLDecoder.decode(
                                keyValue[0],
                                StandardCharsets.UTF_8
                        );

                String value =
                        URLDecoder.decode(
                                keyValue[1],
                                StandardCharsets.UTF_8
                        );

                data.put(key, value);
            }
        }

        return data;
    }

    // ==========================================
    // RESPONSE
    // ==========================================

    private static void sendResponse(
            HttpExchange exchange,
            String html)
            throws IOException {

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "text/html; charset=UTF-8"
                );

        byte[] bytes =
                html.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(
                200,
                bytes.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(bytes);
        }
    }

    // ==========================================
    // ERROR PAGE
    // ==========================================

    private static String errorPage(
            String title,
            String message) {
        return errorPage(title, message, "/");
    }

    private static String errorPage(
            String title,
            String message,
            String backLink) {

        return """
                <!DOCTYPE html>

                <html>

                <head>
                    <title>%s</title>
                    <link rel="stylesheet"
                          href="/css/style.css">
                </head>

                <body>

                <div class="success-page">

                    <div class="success-box">

                        <h1>%s</h1>

                        <p>%s</p>

                        <a class="button"
                           href="%s">
                            Go Back
                        </a>

                    </div>

                </div>

                </body>

                </html>
                """.formatted(
                        escapeHtml(title),
                        escapeHtml(title),
                        escapeHtml(message),
                        escapeHtml(backLink)
                );
    }

    // ==========================================
    // HTML SECURITY
    // ==========================================

    private static String escapeHtml(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}