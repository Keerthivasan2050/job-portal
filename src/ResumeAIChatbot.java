import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ResumeAIChatbot - HTTP Handlers & Global Floating Widget Injector
 * =================================================================
 * Provides:
 *  - Global floating widget injection into all WebServer & TrustMatch HTML pages
 *  - REST API endpoints:
 *      POST /api/chat/message     -> Conversational interaction & bullet transformation
 *      POST /api/chat/evaluate    -> Full resume parsing, ATS scoring & audit
 *      GET  /api/chat/profile     -> Checks active logged-in user resume
 *      POST /api/chat/sample      -> Loads sample resumes (Keerthivasan, Priya, etc.)
 *      GET  /css/ai-chatbot.css   -> Chatbot styling & animations
 *      GET  /js/ai-chatbot.js     -> Chatbot client runtime
 */
public class ResumeAIChatbot {

    // ============================================================
    // HTTP API HANDLERS
    // ============================================================

    public static void handleChatMessage(HttpExchange exchange) throws IOException {
        SecurityManager.setSecurityHeaders(exchange);
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            Map<String, String> data = parseFormData(exchange);
            String message = data.getOrDefault("message", "").trim();
            String resumeText = data.getOrDefault("resumeText", "").trim();
            String fileName = data.getOrDefault("fileName", "resume.txt").trim();
            String fileBase64 = data.getOrDefault("fileBase64", "").trim();
            String targetRole = data.getOrDefault("targetRole", "").trim();

            WebServer.SessionData session = WebServer.getSession(exchange);

            ResumeParser.ParsedResume resume = null;

            // 1. If base64 file uploaded
            if (!fileBase64.isEmpty()) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(fileBase64);
                    resume = ResumeParser.parse(fileName, bytes);
                } catch (Exception ignored) {}
            }

            // 2. If raw resume text passed
            if (resume == null && !resumeText.isEmpty()) {
                resume = ResumeParser.parseText(fileName, resumeText);
            }

            // 3. Fallback to logged-in user's saved resume
            if (resume == null && session != null) {
                JobDataStore.ResumeRecord rec = JobDataStore.getResumeByUserId(session.userId);
                if (rec != null && rec.rawText != null && !rec.rawText.isBlank()) {
                    resume = ResumeParser.parseText(rec.fileName != null ? rec.fileName : "Profile_Resume.pdf", rec.rawText);
                }
            }

            if (resume != null && !targetRole.isEmpty()) {
                resume.primaryRole = targetRole;
            }

            ResumeAIEngine.ChatResponse response = ResumeAIEngine.processChat(message, resume, fileName, session);

            StringBuilder json = new StringBuilder("{");
            json.append("\"success\":true,");
            json.append("\"replyHtml\":\"").append(escapeJson(response.replyHtml)).append("\",");
            json.append("\"overallScore\":").append(response.overallScore).append(",");
            json.append("\"atsScore\":").append(response.atsScore).append(",");
            json.append("\"detectedRole\":\"").append(escapeJson(response.detectedRole)).append("\",");
            json.append("\"quickChips\":[");
            for (int i = 0; i < response.quickChips.size(); i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(escapeJson(response.quickChips.get(i))).append("\"");
            }
            json.append("]}");

            sendJson(exchange, 200, json.toString());
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    public static void handleEvaluate(HttpExchange exchange) throws IOException {
        SecurityManager.setSecurityHeaders(exchange);
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            Map<String, String> data = parseFormData(exchange);
            String resumeText = data.getOrDefault("resumeText", "").trim();
            String fileName = data.getOrDefault("fileName", "resume.txt").trim();
            String fileBase64 = data.getOrDefault("fileBase64", "").trim();
            String targetRole = data.getOrDefault("targetRole", "").trim();

            ResumeParser.ParsedResume resume = null;

            if (!fileBase64.isEmpty()) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(fileBase64);
                    resume = ResumeParser.parse(fileName, bytes);
                } catch (Exception e) {
                    resume = ResumeParser.parseText(fileName, resumeText);
                }
            } else if (!resumeText.isEmpty()) {
                resume = ResumeParser.parseText(fileName, resumeText);
            } else {
                // Check logged in user
                WebServer.SessionData session = WebServer.getSession(exchange);
                if (session != null) {
                    JobDataStore.ResumeRecord rec = JobDataStore.getResumeByUserId(session.userId);
                    if (rec != null && rec.rawText != null) {
                        resume = ResumeParser.parseText(rec.fileName, rec.rawText);
                    }
                }
            }

            if (resume == null || (resume.rawText != null && resume.rawText.isBlank())) {
                sendJson(exchange, 400, "{\"error\":\"No resume text or file provided\"}");
                return;
            }

            ResumeAIEngine.ResumeAudit audit = ResumeAIEngine.evaluate(resume, targetRole);
            String auditHtml = ResumeAIEngine.renderAuditHtml(audit, resume);

            StringBuilder json = new StringBuilder("{");
            json.append("\"success\":true,");
            json.append("\"replyHtml\":\"").append(escapeJson(auditHtml)).append("\",");
            json.append("\"overallScore\":").append(audit.overallScore).append(",");
            json.append("\"atsScore\":").append(audit.atsScore).append(",");
            json.append("\"scoreTier\":\"").append(escapeJson(audit.scoreTier)).append("\",");
            json.append("\"detectedRole\":\"").append(escapeJson(audit.detectedRole)).append("\",");
            json.append("\"roleMatchPct\":").append(audit.roleMatchPct).append(",");
            json.append("\"salaryBenchmark\":\"").append(escapeJson(audit.salaryBenchmark)).append("\",");
            json.append("\"detectedSkillsCount\":").append(audit.detectedSkills.size()).append(",");
            json.append("\"missingTier1Count\":").append(audit.missingTier1Skills.size()).append(",");
            json.append("\"quantifiedMetrics\":").append(audit.quantifiedMetricCount).append(",");
            json.append("\"quickChips\":[\"🎯 Deep ATS Scan\",\"✨ STAR Bullet Rewriter\",\"💡 High-Value Skills\",\"🎤 Technical Interview Q&A\"]");
            json.append("}");

            sendJson(exchange, 200, json.toString());
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    public static void handleProfileResume(HttpExchange exchange) throws IOException {
        SecurityManager.setSecurityHeaders(exchange);
        WebServer.SessionData session = WebServer.getSession(exchange);

        if (session == null) {
            sendJson(exchange, 200, "{\"isLoggedIn\":false,\"hasResume\":false}");
            return;
        }

        JobDataStore.ResumeRecord rec = JobDataStore.getResumeByUserId(session.userId);
        if (rec == null || rec.rawText == null || rec.rawText.isBlank()) {
            sendJson(exchange, 200, "{\"isLoggedIn\":true,\"hasResume\":false,\"userName\":\"" + escapeJson(session.fullName) + "\"}");
            return;
        }

        ResumeParser.ParsedResume parsed = ResumeParser.parseText(rec.fileName, rec.rawText);

        StringBuilder json = new StringBuilder("{");
        json.append("\"isLoggedIn\":true,");
        json.append("\"hasResume\":true,");
        json.append("\"userName\":\"").append(escapeJson(session.fullName)).append("\",");
        json.append("\"fileName\":\"").append(escapeJson(rec.fileName)).append("\",");
        json.append("\"primaryRole\":\"").append(escapeJson(rec.primaryRole)).append("\",");
        json.append("\"skillsCount\":").append(parsed.allSkills.size()).append(",");
        json.append("\"education\":\"").append(escapeJson(rec.parsedEducation)).append("\",");
        json.append("\"experience\":\"").append(escapeJson(rec.parsedExperience)).append("\"");
        json.append("}");

        sendJson(exchange, 200, json.toString());
    }

    public static void handleSampleResume(HttpExchange exchange) throws IOException {
        SecurityManager.setSecurityHeaders(exchange);
        Map<String, String> data = parseFormData(exchange);
        String sampleId = data.getOrDefault("sample", "keerthi").trim().toLowerCase();

        String fileName;
        String rawText;

        if ("priya".equals(sampleId)) {
            fileName = "Priya_Patel_Frontend_Resume.pdf";
            rawText = """
                Priya Patel
                Frontend & React Engineer
                Bangalore, Karnataka | priya@example.com | +91 9876543211 | linkedin.com/in/priyapatel
                
                PROFESSIONAL SUMMARY
                Frontend Engineer with 2+ years of experience engineering responsive, component-driven web applications using React, TypeScript, Redux, and Tailwind CSS.
                
                TECHNICAL SKILLS
                Languages: JavaScript, TypeScript, HTML5, CSS3, SQL
                Frameworks & Libraries: React, Next.js, Redux, Tailwind CSS, Bootstrap
                Tools & Platforms: Git, GitHub, Vite, Webpack, Figma, Jest, Postman
                Concepts: Responsive Design, REST APIs, Component Lifecycle, UI/UX, Performance
                
                EXPERIENCE
                Frontend Engineer | WebSphere Dynamics (2024 - Present)
                - Developed responsive user interfaces with React and Tailwind CSS.
                - Reduced page load times by 30% through image optimization and bundle splitting.
                - Built reusable UI design system used by 6 frontend engineers.
                
                PROJECTS
                - Interactive Analytics Dashboard: Next.js, Chart.js, Tailwind CSS with real-time API polling.
                - E-Commerce Mobile Web App: React, Redux Toolkit, Stripe payment gateway integration.
                
                EDUCATION
                B.Tech in Information Technology | CGPA 8.5 (2020 - 2024)
                """;
        } else {
            fileName = "Keerthivasan_Resume.pdf";
            rawText = """
                Keerthivasan
                Full Stack Java & Spring Boot Engineer
                Bangalore, Karnataka | keerthi@example.com | +91 9876501234 | linkedin.com/in/keerthivasan
                
                PROFESSIONAL SUMMARY
                Results-driven backend software engineer with 3+ years experience building highly scalable microservices, RESTful APIs, and distributed data systems using modern Java 21, Spring Boot, Hibernate, MySQL, and Docker.
                
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
                
                EDUCATION
                B.E. in Computer Science and Engineering | CGPA 8.8 (2020 - 2024)
                
                CERTIFICATIONS
                - Oracle Certified Java Associate (OCJA)
                - AWS Cloud Practitioner (In Progress)
                """;
        }

        ResumeParser.ParsedResume parsed = ResumeParser.parseText(fileName, rawText);
        ResumeAIEngine.ResumeAudit audit = ResumeAIEngine.evaluate(parsed, null);
        String auditHtml = ResumeAIEngine.renderAuditHtml(audit, parsed);

        StringBuilder json = new StringBuilder("{");
        json.append("\"success\":true,");
        json.append("\"sampleName\":\"").append(escapeJson(fileName)).append("\",");
        json.append("\"replyHtml\":\"").append(escapeJson(auditHtml)).append("\",");
        json.append("\"overallScore\":").append(audit.overallScore).append(",");
        json.append("\"atsScore\":").append(audit.atsScore).append(",");
        json.append("\"scoreTier\":\"").append(escapeJson(audit.scoreTier)).append("\",");
        json.append("\"detectedRole\":\"").append(escapeJson(audit.detectedRole)).append("\",");
        json.append("\"roleMatchPct\":").append(audit.roleMatchPct).append(",");
        json.append("\"quickChips\":[\"🎯 Deep ATS Scan\",\"✨ STAR Bullet Rewriter\",\"💡 High-Value Skills\",\"🎤 Technical Interview Q&A\"]");
        json.append("}");

        sendJson(exchange, 200, json.toString());
    }

    public static void handleCss(HttpExchange exchange) throws IOException {
        SecurityManager.setStaticResourceHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
        byte[] bytes = CHATBOT_CSS.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    public static void handleJs(HttpExchange exchange) throws IOException {
        SecurityManager.setStaticResourceHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/javascript; charset=UTF-8");
        byte[] bytes = CHATBOT_JS.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    // ============================================================
    // GLOBAL WIDGET INJECTION HELPER
    // ============================================================

    public static String injectWidget(String html, WebServer.SessionData session) {
        if (html == null || html.contains("id=\"ai-resume-coach-widget\"")) {
            return html;
        }

        String userName = (session != null) ? session.fullName : "";
        String userRole = (session != null) ? session.userType : "";
        boolean isLoggedIn = (session != null);

        StringBuilder widget = new StringBuilder();
        widget.append("\n<!-- SmartHire AI Resume Chatbot Widget -->\n");
        widget.append("<link rel=\"stylesheet\" href=\"/css/ai-chatbot.css\">\n");
        widget.append("<div id=\"ai-resume-coach-widget\" ")
              .append("data-logged-in=\"").append(isLoggedIn).append("\" ")
              .append("data-user-name=\"").append(ResumeAIEngine.escapeHtml(userName)).append("\" ")
              .append("data-user-role=\"").append(ResumeAIEngine.escapeHtml(userRole)).append("\">\n");

        // Floating Trigger Button
        widget.append("""
            <div id="ai-chat-launcher" class="ai-launcher-btn" onclick="toggleAiChat()" title="Ask AI Resume Coach">
                <div class="ai-launcher-glow"></div>
                <div class="ai-launcher-emoji">🤖</div>
                <span class="ai-launcher-pill">
                    <span class="ai-pulse-dot"></span>
                    <span class="ai-launcher-text">AI Resume Coach</span>
                    <span class="ai-sparkle-icon">✨</span>
                </span>
            </div>
        """);

        // Chatbot Modal Container
        widget.append("""
            <div id="ai-chat-modal" class="ai-chat-modal" style="display:none;">
                <div class="ai-modal-header">
                    <div class="ai-header-left">
                        <div class="ai-header-avatar">🤖<span class="ai-online-beacon"></span></div>
                        <div class="ai-header-info">
                            <div class="ai-header-title">SmartHire AI Resume Coach</div>
                            <div class="ai-header-subtitle">Trained ATS & Executive Career Intelligence</div>
                        </div>
                    </div>
                    <div class="ai-header-actions">
                        <button class="ai-header-btn" onclick="toggleChatExpand()" title="Toggle Studio/Expand View">🖥️</button>
                        <button class="ai-header-btn" onclick="clearAiChat()" title="Reset / New Chat">🔄</button>
                        <button class="ai-header-btn ai-close-btn" onclick="toggleAiChat()" title="Minimize">✕</button>
                    </div>
                </div>

                <!-- Active Resume Notification Banner -->
                <div id="ai-profile-banner" class="ai-profile-banner" style="display:none;">
                    <div class="ai-banner-content">
                        <span>📄 <strong id="ai-banner-filename">Resume</strong> detected</span>
                        <button class="btn-banner-audit" onclick="auditProfileResume()">⚡ Audit Now</button>
                    </div>
                </div>

                <!-- Chat Messages Scrollable Area -->
                <div id="ai-chat-body" class="ai-chat-body">
                    <!-- Loaded dynamically via JS -->
                </div>

                <!-- Drag & Drop Overlay -->
                <div id="ai-drop-overlay" class="ai-drop-overlay" style="display:none;">
                    <div class="drop-box">
                        <div class="drop-icon">📂</div>
                        <div class="drop-text">Drop your Resume here (PDF, DOCX, TXT)</div>
                        <div class="drop-sub">Instant AI Scoring & STAR Transformation</div>
                    </div>
                </div>

                <!-- Quick Action Chips Bar -->
                <div id="ai-quick-chips" class="ai-quick-chips-bar">
                    <button class="chip-item" onclick="sendChatPrompt('Audit my resume')">📊 Full Resume Audit</button>
                    <button class="chip-item" onclick="sendChatPrompt('Check ATS score')">🎯 Check ATS Score</button>
                    <button class="chip-item" onclick="sendChatPrompt('Rewrite bullet: Worked on backend APIs and fixed bugs')">✨ Advance Bullets (STAR)</button>
                    <button class="chip-item" onclick="sendChatPrompt('What skills should I learn?')">💡 Missing Skills</button>
                    <button class="chip-item" onclick="sendChatPrompt('Quiz me with interview questions')">🎤 Mock Interview Q&A</button>
                    <button class="chip-item" onclick="sendChatPrompt('Write my executive summary')">📝 Executive Summary</button>
                    <button class="chip-item" onclick="loadSampleProfile('keerthi')">⚡ Load Keerthi Sample</button>
                </div>

                <!-- Input Area -->
                <div class="ai-chat-input-area">
                    <input type="file" id="ai-file-input" style="display:none;" accept=".pdf,.docx,.txt,.md" onchange="handleAiFileSelect(event)">
                    <button class="ai-btn-upload" onclick="document.getElementById('ai-file-input').click()" title="Upload Resume (PDF, DOCX, TXT)">📎</button>
                    <textarea id="ai-chat-input" placeholder="Ask AI coach, paste resume text, or type 'rewrite: ...'" rows="1" onkeydown="handleAiInputKeyDown(event)"></textarea>
                    <button id="ai-btn-send" class="ai-btn-send" onclick="sendUserMessage()" title="Send Message">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                            <line x1="22" y1="2" x2="11" y2="13"></line>
                            <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
                        </svg>
                    </button>
                </div>
            </div>
        """);

        widget.append("</div>\n");
        widget.append("<script src=\"/js/ai-chatbot.js\"></script>\n");

        // Insert right before </body> or </html>
        if (html.contains("</body>")) {
            return html.replace("</body>", widget.toString() + "</body>");
        } else if (html.contains("</html>")) {
            return html.replace("</html>", widget.toString() + "</html>");
        } else {
            return html + widget.toString();
        }
    }

    // ============================================================
    // HTTP HELPERS & SERIALIZATION
    // ============================================================

    private static Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = new HashMap<>();
        if (body.isEmpty()) return data;

        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    data.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                             URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
        return data;
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        SecurityManager.setSecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    // ============================================================
    // EMBEDDED CSS (Served via /css/ai-chatbot.css)
    // ============================================================

    public static final String CHATBOT_CSS = """
        /* ============================================================
           SmartHire AI Resume Coach - Modern Floating Widget Styles
           ============================================================ */

        /* Floating Trigger Launcher Button */
        .ai-launcher-btn {
            position: fixed;
            bottom: 26px;
            right: 26px;
            z-index: 99999;
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 8px 18px 8px 10px;
            background: linear-gradient(135deg, #4338ca 0%, #6366f1 50%, #8b5cf6 100%);
            border-radius: 50px;
            box-shadow: 0 10px 30px -5px rgba(99, 102, 241, 0.5), 0 0 0 1px rgba(255,255,255,0.25);
            cursor: pointer;
            user-select: none;
            transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
            animation: floatLauncher 3.5s ease-in-out infinite;
        }

        .ai-launcher-btn:hover {
            transform: translateY(-4px) scale(1.04);
            box-shadow: 0 16px 40px -5px rgba(99, 102, 241, 0.65), 0 0 0 2px rgba(255,255,255,0.4);
        }

        @keyframes floatLauncher {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-7px); }
        }

        .ai-launcher-glow {
            position: absolute;
            inset: -4px;
            border-radius: 54px;
            background: linear-gradient(135deg, #6366f1, #a855f7);
            z-index: -1;
            opacity: 0.4;
            filter: blur(8px);
            animation: pulseGlow 2.5s infinite;
        }

        @keyframes pulseGlow {
            0%, 100% { opacity: 0.3; transform: scale(0.98); }
            50% { opacity: 0.65; transform: scale(1.04); }
        }

        .ai-launcher-emoji {
            width: 44px;
            height: 44px;
            background: #ffffff;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 26px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            transition: transform 0.3s ease;
        }

        .ai-launcher-btn:hover .ai-launcher-emoji {
            transform: rotate(12deg) scale(1.1);
        }

        .ai-launcher-pill {
            display: flex;
            align-items: center;
            gap: 7px;
            color: #ffffff;
            font-weight: 700;
            font-size: 14px;
            letter-spacing: -0.2px;
        }

        .ai-pulse-dot {
            width: 8px;
            height: 8px;
            background: #22c55e;
            border-radius: 50%;
            box-shadow: 0 0 8px #22c55e;
            animation: beaconPulse 1.8s infinite;
        }

        @keyframes beaconPulse {
            0%, 100% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.4; transform: scale(0.85); }
        }

        .ai-sparkle-icon {
            font-size: 14px;
            animation: spinSparkle 4s linear infinite;
        }

        @keyframes spinSparkle {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }

        /* Modal Chat Window */
        .ai-chat-modal {
            position: fixed;
            bottom: 24px;
            right: 24px;
            z-index: 100000;
            width: 460px;
            height: 670px;
            max-width: calc(100vw - 32px);
            max-height: calc(100vh - 40px);
            background: #ffffff;
            border-radius: 20px;
            box-shadow: 0 25px 65px -12px rgba(15, 23, 42, 0.35), 0 0 0 1px rgba(0, 0, 0, 0.08);
            display: flex;
            flex-direction: column;
            overflow: hidden;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            transition: width 0.3s cubic-bezier(0.16, 1, 0.3, 1), height 0.3s cubic-bezier(0.16, 1, 0.3, 1);
            animation: openModal 0.35s cubic-bezier(0.16, 1, 0.3, 1);
        }

        .ai-chat-modal.expanded {
            width: 780px;
            height: 760px;
        }

        @keyframes openModal {
            from { opacity: 0; transform: translateY(24px) scale(0.94); }
            to { opacity: 1; transform: translateY(0) scale(1); }
        }

        /* Header */
        .ai-modal-header {
            background: linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #4338ca 100%);
            color: #ffffff;
            padding: 14px 18px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            border-bottom: 1px solid rgba(255,255,255,0.1);
            user-select: none;
        }

        .ai-header-left {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .ai-header-avatar {
            width: 40px;
            height: 40px;
            background: rgba(255,255,255,0.15);
            backdrop-filter: blur(6px);
            border: 1px solid rgba(255,255,255,0.25);
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 22px;
            position: relative;
        }

        .ai-online-beacon {
            position: absolute;
            bottom: -2px;
            right: -2px;
            width: 10px;
            height: 10px;
            background: #10b981;
            border: 2px solid #1e1b4b;
            border-radius: 50%;
        }

        .ai-header-title {
            font-weight: 800;
            font-size: 15px;
            letter-spacing: -0.3px;
        }

        .ai-header-subtitle {
            font-size: 11px;
            color: #c7d2fe;
            font-weight: 500;
        }

        .ai-header-actions {
            display: flex;
            gap: 6px;
        }

        .ai-header-btn {
            background: rgba(255,255,255,0.12);
            border: none;
            color: #ffffff;
            width: 32px;
            height: 32px;
            border-radius: 8px;
            cursor: pointer;
            font-size: 13px;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: background 0.2s;
        }

        .ai-header-btn:hover {
            background: rgba(255,255,255,0.25);
        }

        .ai-close-btn:hover {
            background: #ef4444 !important;
        }

        /* Profile Banner */
        .ai-profile-banner {
            background: #eff6ff;
            border-bottom: 1px solid #bfdbfe;
            padding: 8px 16px;
        }

        .ai-banner-content {
            display: flex;
            align-items: center;
            justify-content: space-between;
            font-size: 12px;
            color: #1e40af;
        }

        .btn-banner-audit {
            background: #2563eb;
            color: white;
            border: none;
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 11px;
            font-weight: 700;
            cursor: pointer;
            transition: background 0.2s;
        }

        .btn-banner-audit:hover {
            background: #1d4ed8;
        }

        /* Chat Body */
        .ai-chat-body {
            flex: 1;
            overflow-y: auto;
            padding: 16px;
            background: #f8fafc;
            display: flex;
            flex-direction: column;
            gap: 14px;
            scroll-behavior: smooth;
        }

        /* Message Bubbles */
        .ai-msg-row {
            display: flex;
            gap: 10px;
            max-width: 100%;
        }

        .ai-msg-row.user {
            justify-content: flex-end;
        }

        .ai-msg-bubble {
            max-width: 88%;
            border-radius: 16px;
            padding: 12px 16px;
            font-size: 13.5px;
            line-height: 1.5;
            box-shadow: 0 2px 6px rgba(0,0,0,0.04);
            word-break: break-word;
        }

        .ai-msg-row.user .ai-msg-bubble {
            background: linear-gradient(135deg, #4f46e5 0%, #6366f1 100%);
            color: #ffffff;
            border-bottom-right-radius: 4px;
        }

        .ai-msg-row.bot .ai-msg-bubble {
            background: #ffffff;
            color: #1e293b;
            border: 1px solid #e2e8f0;
            border-bottom-left-radius: 4px;
        }

        .ai-msg-avatar {
            width: 32px;
            height: 32px;
            border-radius: 10px;
            background: #e0e7ff;
            color: #4338ca;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 17px;
            flex-shrink: 0;
        }

        /* Typing Indicator */
        .ai-typing-indicator {
            display: flex;
            align-items: center;
            gap: 5px;
            padding: 8px 12px;
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 14px;
            width: fit-content;
        }

        .ai-typing-dot {
            width: 7px;
            height: 7px;
            background: #6366f1;
            border-radius: 50%;
            animation: bounceDot 1.4s infinite;
        }

        .ai-typing-dot:nth-child(2) { animation-delay: 0.2s; }
        .ai-typing-dot:nth-child(3) { animation-delay: 0.4s; }

        @keyframes bounceDot {
            0%, 60%, 100% { transform: translateY(0); }
            30% { transform: translateY(-5px); }
        }

        /* Drag & Drop Overlay */
        .ai-drop-overlay {
            position: absolute;
            inset: 60px 0 0 0;
            background: rgba(255,255,255,0.92);
            backdrop-filter: blur(4px);
            z-index: 10;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 24px;
        }

        .drop-box {
            border: 3px dashed #6366f1;
            border-radius: 16px;
            width: 100%;
            height: 100%;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            background: #eef2ff;
            text-align: center;
            padding: 20px;
        }

        .drop-icon { font-size: 46px; margin-bottom: 10px; }
        .drop-text { font-size: 16px; font-weight: 800; color: #3730a3; }
        .drop-sub { font-size: 12px; color: #6366f1; margin-top: 4px; }

        /* Quick Action Chips Bar */
        .ai-quick-chips-bar {
            display: flex;
            gap: 8px;
            padding: 8px 14px;
            background: #ffffff;
            border-top: 1px solid #e2e8f0;
            overflow-x: auto;
            white-space: nowrap;
            scrollbar-width: none;
        }

        .ai-quick-chips-bar::-webkit-scrollbar { display: none; }

        .chip-item {
            background: #f1f5f9;
            color: #334155;
            border: 1px solid #cbd5e1;
            padding: 6px 12px;
            border-radius: 20px;
            font-size: 11.5px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
            flex-shrink: 0;
        }

        .chip-item:hover {
            background: #e0e7ff;
            color: #4338ca;
            border-color: #a5b4fc;
        }

        /* Input Area */
        .ai-chat-input-area {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 10px 14px;
            background: #ffffff;
            border-top: 1px solid #e2e8f0;
        }

        .ai-btn-upload {
            background: #f1f5f9;
            border: 1px solid #cbd5e1;
            color: #475569;
            width: 36px;
            height: 36px;
            border-radius: 10px;
            cursor: pointer;
            font-size: 16px;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: all 0.2s;
            flex-shrink: 0;
        }

        .ai-btn-upload:hover {
            background: #e2e8f0;
            color: #1e293b;
        }

        #ai-chat-input {
            flex: 1;
            border: 1px solid #cbd5e1;
            border-radius: 12px;
            padding: 9px 12px;
            font-size: 13px;
            font-family: inherit;
            resize: none;
            outline: none;
            max-height: 90px;
            line-height: 1.4;
            transition: border-color 0.2s;
        }

        #ai-chat-input:focus {
            border-color: #6366f1;
            box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
        }

        .ai-btn-send {
            background: linear-gradient(135deg, #4f46e5 0%, #6366f1 100%);
            border: none;
            color: white;
            width: 38px;
            height: 38px;
            border-radius: 12px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: transform 0.2s, box-shadow 0.2s;
            flex-shrink: 0;
        }

        .ai-btn-send:hover {
            transform: scale(1.05);
            box-shadow: 0 4px 12px rgba(79, 70, 229, 0.35);
        }

        /* ------------------------------------------------------------
           Rich Audit Card Styles
           ------------------------------------------------------------ */
        .audit-container { display: flex; flex-direction: column; gap: 12px; }

        .audit-hero {
            background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
            border: 1px solid #e2e8f0;
            border-radius: 14px;
            padding: 14px;
        }

        .audit-badge-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 8px;
            margin-bottom: 12px;
            flex-wrap: wrap;
        }

        .role-badge {
            background: #ede9fe;
            color: #6d28d9;
            font-weight: 800;
            font-size: 12px;
            padding: 4px 10px;
            border-radius: 8px;
        }

        .salary-badge {
            background: #ecfdf5;
            color: #047857;
            font-weight: 700;
            font-size: 11px;
            padding: 4px 8px;
            border-radius: 6px;
        }

        .score-dials-row {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 8px;
            margin-bottom: 10px;
        }

        .dial-card {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 10px;
            padding: 10px;
            text-align: center;
        }

        .dial-number {
            font-size: 24px;
            font-weight: 900;
            line-height: 1;
        }

        .dial-number span {
            font-size: 12px;
            font-weight: 600;
            color: #94a3b8;
        }

        .dial-label {
            font-size: 11px;
            color: #64748b;
            font-weight: 600;
            margin-top: 4px;
        }

        .tier-badge {
            background: #ffffff;
            padding: 8px 12px;
            border-radius: 8px;
            font-weight: 700;
            font-size: 12px;
            color: #1e293b;
        }

        .quick-metrics-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 8px;
        }

        .metric-box {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 8px;
            padding: 8px 10px;
            font-size: 11.5px;
            color: #475569;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        .wrap-title {
            margin: 12px 0 6px 0;
            font-size: 13px;
            color: #0f172a;
            font-weight: 800;
        }

        /* Section Details */
        .audit-section-detail {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 10px;
            margin-bottom: 6px;
            overflow: hidden;
        }

        .audit-section-detail summary {
            padding: 10px 14px;
            font-size: 12.5px;
            font-weight: 700;
            cursor: pointer;
            display: flex;
            justify-content: space-between;
            align-items: center;
            background: #fafafa;
        }

        .detail-body {
            padding: 12px 14px;
            font-size: 12px;
            color: #334155;
            line-height: 1.5;
            border-top: 1px solid #f1f5f9;
        }

        .rewrite-callout {
            background: #f0fdf4;
            border: 1px solid #bbf7d0;
            border-radius: 8px;
            padding: 8px 12px;
            margin-top: 8px;
            font-size: 11.5px;
            color: #166534;
        }

        /* STAR Card */
        .star-card {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 12px;
            padding: 12px;
            margin-top: 8px;
        }

        .star-badge {
            background: #fdf4ff;
            color: #a21caf;
            font-weight: 700;
            font-size: 11px;
            padding: 3px 8px;
            border-radius: 6px;
            display: inline-block;
            margin-bottom: 8px;
        }

        .before-box, .after-box {
            padding: 8px 10px;
            border-radius: 8px;
            margin-bottom: 8px;
            font-size: 12px;
        }

        .before-box {
            background: #fff1f2;
            border-left: 3px solid #f43f5e;
            color: #881337;
        }

        .after-box {
            background: #f0fdf4;
            border-left: 3px solid #22c55e;
            color: #14532d;
        }

        .box-label { font-weight: 700; font-size: 10.5px; margin-bottom: 3px; }

        .btn-copy-bullet {
            background: #15803d;
            color: white;
            border: none;
            padding: 5px 10px;
            border-radius: 6px;
            font-size: 11px;
            font-weight: 700;
            cursor: pointer;
            margin-top: 6px;
            transition: background 0.2s;
        }

        .btn-copy-bullet:hover { background: #166534; }

        .impact-tip { font-size: 11px; color: #64748b; margin-top: 6px; }

        /* Matched Jobs */
        .matched-jobs-callout {
            background: #f8fafc;
            border: 1px solid #cbd5e1;
            border-radius: 12px;
            padding: 12px;
        }

        .matched-job-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 8px 0;
            border-bottom: 1px solid #e2e8f0;
            font-size: 12px;
        }

        .matched-job-row:last-child { border-bottom: none; }

        .btn-apply-mini {
            background: #4f46e5;
            color: white;
            padding: 5px 10px;
            border-radius: 6px;
            text-decoration: none;
            font-size: 11px;
            font-weight: 700;
            white-space: nowrap;
        }

        .btn-apply-mini:hover { background: #4338ca; }

        /* General Chat Card */
        .chat-card {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 12px;
            padding: 14px;
            font-size: 13px;
        }

        .card-icon { font-size: 28px; margin-bottom: 6px; }

        .skill-pill-missing {
            background: #fee2e2;
            color: #991b1b;
            padding: 3px 8px;
            border-radius: 6px;
            font-size: 11px;
            font-weight: 700;
            display: inline-block;
            margin: 2px;
        }

        .skill-pill-edge {
            background: #ede9fe;
            color: #5b21b6;
            padding: 3px 8px;
            border-radius: 6px;
            font-size: 11px;
            font-weight: 700;
            display: inline-block;
            margin: 2px;
        }

        .summary-box {
            background: #f8fafc;
            border: 1px solid #e2e8f0;
            border-radius: 8px;
            padding: 10px;
            font-size: 12px;
            line-height: 1.5;
        }

        .command-grid {
            display: grid;
            grid-template-columns: 1fr;
            gap: 6px;
            margin-top: 8px;
        }

        .command-item {
            background: #f8fafc;
            padding: 6px 10px;
            border-radius: 6px;
            font-size: 12px;
            border-left: 3px solid #6366f1;
        }

        .saved-resume-pill-box {
            display: flex;
            align-items: center;
            justify-content: space-between;
            background: #f1f5f9;
            border-radius: 8px;
            padding: 8px 12px;
            margin-top: 10px;
            font-size: 12px;
        }

        .btn-audit-now {
            background: #4f46e5;
            color: white;
            border: none;
            padding: 5px 12px;
            border-radius: 6px;
            font-size: 11px;
            font-weight: 700;
            cursor: pointer;
        }

        .btn-audit-now:hover { background: #4338ca; }

        /* Responsive */
        @media (max-width: 600px) {
            .ai-chat-modal {
                inset: 0 !important;
                width: 100vw !important;
                height: 100vh !important;
                max-width: 100vw !important;
                max-height: 100vh !important;
                border-radius: 0 !important;
            }
            .ai-launcher-btn {
                bottom: 16px;
                right: 16px;
                padding: 6px 12px 6px 8px;
            }
            .ai-launcher-text { display: none; }
        }
    """;

    // ============================================================
    // EMBEDDED JAVASCRIPT (Served via /js/ai-chatbot.js)
    // ============================================================

    public static final String CHATBOT_JS = """
        // ============================================================
        // SmartHire AI Resume Coach Client Runtime
        // ============================================================

        let chatHistory = [];
        let isChatOpen = false;
        let activeResumeContext = null;

        document.addEventListener('DOMContentLoaded', () => {
            initChatbot();
        });

        function initChatbot() {
            // Restore session
            const savedState = sessionStorage.getItem('smarthire_ai_chat_open');
            if (savedState === 'true') {
                toggleAiChat(true);
            }

            const savedHistory = sessionStorage.getItem('smarthire_ai_chat_history');
            if (savedHistory) {
                try {
                    chatHistory = JSON.parse(savedHistory);
                    renderChatMessages();
                } catch (e) {
                    initWelcomeGreeting();
                }
            } else {
                initWelcomeGreeting();
            }

            // Check if logged in user has a profile resume
            checkUserProfileResume();

            // Drag and drop setup
            setupDragAndDrop();
        }

        function initWelcomeGreeting() {
            const container = document.getElementById('ai-resume-coach-widget');
            const userName = container ? container.getAttribute('data-user-name') : '';
            const greeting = userName ? 'Hello ' + userName + '!' : 'Hello there!';

            const welcomeHtml = `
                <div class="chat-card ai-welcome-card">
                    <div class="card-icon">🚀</div>
                    <h3>${greeting} I'm your AI Resume Coach.</h3>
                    <p>I'm trained on ATS screening engines, top tech job specs, and the Google STAR method to help you land high-paying software and engineering roles.</p>
                    <p style="font-size:12px;color:#64748b;margin-top:6px;">
                        Attach your resume (PDF, DOCX, TXT) with 📎 or paste resume text to test & evaluate instantly!
                    </p>
                </div>
            `;
            chatHistory = [{ sender: 'bot', html: welcomeHtml }];
            saveAndRender();
        }

        function toggleAiChat(forceOpen) {
            const modal = document.getElementById('ai-chat-modal');
            const launcher = document.getElementById('ai-chat-launcher');
            if (!modal) return;

            if (forceOpen === true || modal.style.display === 'none') {
                modal.style.display = 'flex';
                launcher.style.display = 'none';
                isChatOpen = true;
                sessionStorage.setItem('smarthire_ai_chat_open', 'true');
                setTimeout(() => {
                    const input = document.getElementById('ai-chat-input');
                    if (input) input.focus();
                    scrollToBottom();
                }, 100);
            } else {
                modal.style.display = 'none';
                launcher.style.display = 'flex';
                isChatOpen = false;
                sessionStorage.setItem('smarthire_ai_chat_open', 'false');
            }
        }

        function openAiChatbot() {
            toggleAiChat(true);
        }

        window.toggleHighContrast = function() {
            var isHC = document.body.classList.toggle('high-contrast');
            try { localStorage.setItem('smarthire_high_contrast', isHC ? 'true' : 'false'); } catch(e) {}
            showA11yToast(isHC ? '👁️ High Contrast Mode: ON' : '👁️ High Contrast Mode: OFF');
        };

        window.adjustFontSize = function(delta) {
            var cur = window._currentFontZoom || 100;
            cur += (delta * 10);
            if (cur < 70) cur = 70;
            if (cur > 160) cur = 160;
            window._currentFontZoom = cur;
            document.documentElement.style.fontSize = cur + '%';
            try { localStorage.setItem('smarthire_font_zoom', cur); } catch(e) {}
            showA11yToast('Text Size: ' + cur + '%');
        };

        function showA11yToast(msg) {
            var t = document.getElementById('smarthire-a11y-toast');
            if (!t) {
                t = document.createElement('div');
                t.id = 'smarthire-a11y-toast';
                t.style.cssText = 'position:fixed;top:80px;left:50%;transform:translateX(-50%);background:#0f172a;color:#ffffff;padding:8px 18px;border-radius:24px;font-size:13px;font-weight:700;z-index:999999;box-shadow:0 10px 25px rgba(0,0,0,0.3);transition:opacity 0.3s;border:1px solid #38bdf8;pointer-events:none;';
                document.body.appendChild(t);
            }
            t.textContent = msg;
            t.style.opacity = '1';
            clearTimeout(window._a11yToastTimer);
            window._a11yToastTimer = setTimeout(function() { if (t) t.style.opacity = '0'; }, 1600);
        }

        function toggleChatExpand() {
            const modal = document.getElementById('ai-chat-modal');
            if (modal) modal.classList.toggle('expanded');
        }

        function clearAiChat() {
            sessionStorage.removeItem('smarthire_ai_chat_history');
            initWelcomeGreeting();
        }

        function scrollToBottom() {
            const body = document.getElementById('ai-chat-body');
            if (body) body.scrollTop = body.scrollHeight;
        }

        function saveAndRender() {
            sessionStorage.setItem('smarthire_ai_chat_history', JSON.stringify(chatHistory));
            renderChatMessages();
        }

        function renderChatMessages() {
            const body = document.getElementById('ai-chat-body');
            if (!body) return;
            body.innerHTML = '';

            chatHistory.forEach(msg => {
                const row = document.createElement('div');
                row.className = 'ai-msg-row ' + msg.sender;

                if (msg.sender === 'bot') {
                    const avatar = document.createElement('div');
                    avatar.className = 'ai-msg-avatar';
                    avatar.innerText = '🤖';
                    row.appendChild(avatar);
                }

                const bubble = document.createElement('div');
                bubble.className = 'ai-msg-bubble';
                bubble.innerHTML = msg.html;
                row.appendChild(bubble);

                body.appendChild(row);
            });

            scrollToBottom();
        }

        function showTypingIndicator() {
            const body = document.getElementById('ai-chat-body');
            if (!body) return;
            const existing = document.getElementById('ai-typing');
            if (existing) return;

            const row = document.createElement('div');
            row.id = 'ai-typing';
            row.className = 'ai-msg-row bot';
            row.innerHTML = `
                <div class="ai-msg-avatar">🤖</div>
                <div class="ai-typing-indicator">
                    <div class="ai-typing-dot"></div>
                    <div class="ai-typing-dot"></div>
                    <div class="ai-typing-dot"></div>
                </div>
            `;
            body.appendChild(row);
            scrollToBottom();
        }

        function removeTypingIndicator() {
            const existing = document.getElementById('ai-typing');
            if (existing) existing.remove();
        }

        function checkUserProfileResume() {
            fetch('/api/chat/profile')
                .then(r => r.json())
                .then(data => {
                    if (data && data.hasResume) {
                        activeResumeContext = data;
                        const banner = document.getElementById('ai-profile-banner');
                        const fn = document.getElementById('ai-banner-filename');
                        if (banner && fn) {
                            fn.innerText = data.fileName || 'Active Resume';
                            banner.style.display = 'block';
                        }
                    }
                })
                .catch(() => {});
        }

        function auditProfileResume() {
            showTypingIndicator();
            const params = new URLSearchParams();
            params.append('message', 'Audit my saved profile resume');

            fetch('/api/chat/evaluate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(r => r.json())
            .then(res => {
                removeTypingIndicator();
                if (res.success) {
                    chatHistory.push({ sender: 'bot', html: res.replyHtml });
                    saveAndRender();
                }
            })
            .catch(() => removeTypingIndicator());
        }

        function loadSampleProfile(sampleId) {
            showTypingIndicator();
            const params = new URLSearchParams();
            params.append('sample', sampleId);

            fetch('/api/chat/sample', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(r => r.json())
            .then(res => {
                removeTypingIndicator();
                if (res.success) {
                    chatHistory.push({ sender: 'user', html: '⚡ Load sample: ' + res.sampleName });
                    chatHistory.push({ sender: 'bot', html: res.replyHtml });
                    saveAndRender();
                }
            })
            .catch(() => removeTypingIndicator());
        }

        function triggerChatAction(action) {
            if (action === 'audit') {
                auditProfileResume();
            }
        }

        function sendChatPrompt(promptText) {
            const input = document.getElementById('ai-chat-input');
            if (input) input.value = promptText;
            sendUserMessage();
        }

        function handleAiInputKeyDown(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendUserMessage();
            }
        }

        function sendUserMessage() {
            const input = document.getElementById('ai-chat-input');
            if (!input) return;
            const text = input.value.trim();
            if (!text) return;

            chatHistory.push({ sender: 'user', html: escapeHtml(text).replace(/\\n/g, '<br>') });
            saveAndRender();
            input.value = '';

            showTypingIndicator();

            const params = new URLSearchParams();
            params.append('message', text);

            fetch('/api/chat/message', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(r => r.json())
            .then(res => {
                removeTypingIndicator();
                if (res.success) {
                    chatHistory.push({ sender: 'bot', html: res.replyHtml });
                    saveAndRender();
                } else {
                    chatHistory.push({ sender: 'bot', html: '<p style="color:#ef4444;">⚠️ ' + escapeHtml(res.error || 'Server error') + '</p>' });
                    saveAndRender();
                }
            })
            .catch(err => {
                removeTypingIndicator();
                chatHistory.push({ sender: 'bot', html: '<p style="color:#ef4444;">⚠️ Network error communicating with AI server.</p>' });
                saveAndRender();
            });
        }

        function handleAiFileSelect(event) {
            const file = event.target.files[0];
            if (!file) return;
            processResumeFile(file);
        }

        function processResumeFile(file) {
            chatHistory.push({ sender: 'user', html: '📄 Uploading resume: <strong>' + escapeHtml(file.name) + '</strong> (' + Math.round(file.size / 1024) + ' KB)' });
            saveAndRender();
            showTypingIndicator();

            const reader = new FileReader();
            reader.onload = function(e) {
                const raw = e.target.result;
                const params = new URLSearchParams();
                params.append('fileName', file.name);

                if (file.name.endsWith('.txt') || file.name.endsWith('.md')) {
                    params.append('resumeText', raw);
                } else {
                    const base64 = raw.split(',')[1] || raw;
                    params.append('fileBase64', base64);
                }

                fetch('/api/chat/evaluate', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: params.toString()
                })
                .then(r => r.json())
                .then(res => {
                    removeTypingIndicator();
                    if (res.success) {
                        chatHistory.push({ sender: 'bot', html: res.replyHtml });
                        saveAndRender();
                    } else {
                        chatHistory.push({ sender: 'bot', html: '<p style="color:#ef4444;">⚠️ Could not parse file: ' + escapeHtml(res.error) + '</p>' });
                        saveAndRender();
                    }
                })
                .catch(() => {
                    removeTypingIndicator();
                    chatHistory.push({ sender: 'bot', html: '<p style="color:#ef4444;">⚠️ File upload processing failed.</p>' });
                    saveAndRender();
                });
            };

            if (file.name.endsWith('.txt') || file.name.endsWith('.md')) {
                reader.readAsText(file);
            } else {
                reader.readAsDataURL(file);
            }
        }

        function setupDragAndDrop() {
            const modal = document.getElementById('ai-chat-modal');
            const overlay = document.getElementById('ai-drop-overlay');
            if (!modal || !overlay) return;

            ['dragenter', 'dragover'].forEach(eventName => {
                modal.addEventListener(eventName, (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    overlay.style.display = 'flex';
                }, false);
            });

            ['dragleave', 'drop'].forEach(eventName => {
                modal.addEventListener(eventName, (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    if (eventName === 'drop' || e.target === overlay) {
                        overlay.style.display = 'none';
                    }
                }, false);
            });

            modal.addEventListener('drop', (e) => {
                const dt = e.dataTransfer;
                const files = dt.files;
                if (files && files.length > 0) {
                    processResumeFile(files[0]);
                }
            }, false);
        }

        function copyText(btn, text) {
            navigator.clipboard.writeText(text).then(() => {
                const orig = btn.innerText;
                btn.innerText = '✓ Copied!';
                btn.style.background = '#059669';
                setTimeout(() => {
                    btn.innerText = orig;
                    btn.style.background = '';
                }, 2000);
            }).catch(() => {
                const ta = document.createElement('textarea');
                ta.value = text;
                document.body.appendChild(ta);
                ta.select();
                document.execCommand('copy');
                document.body.removeChild(ta);
                btn.innerText = '✓ Copied!';
                setTimeout(() => { btn.innerText = '📋 Copy Advanced Bullet'; }, 2000);
            });
        }

        function escapeHtml(str) {
            if (!str) return '';
            return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
        }
    """;
}
