/**
 * ResumeAITestSuite - Comprehensive Verification Suite for AI Resume Chatbot & Engine
 */
public class ResumeAITestSuite {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("   RUNNING SMARTHIRE AI RESUME CHATBOT DEEP VERIFICATION");
        System.out.println("============================================================");

        try {
            // Initialize in-memory database store
            JobDataStore.initializeDatabaseIfAvailable();

            testResumeEvaluationKeerthi();
            testResumeEvaluationPriya();
            testResumeEvaluationWeakResume();
            testSingleBulletTransformation();
            testChatIntentRecognition();
            testPastedResumeDirectAudit();
            testWidgetHtmlInjection();
            testEdgeCasesAndResilience();

            System.out.println();
            System.out.println("============================================================");
            System.out.printf("   TEST RUN COMPLETE: %d PASSED, %d FAILED%n", testsPassed, testsFailed);
            System.out.println("============================================================");

            if (testsFailed > 0) {
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Fatal test suite crash: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void assertTrue(String testName, boolean condition, String message) {
        if (condition) {
            System.out.printf("  [PASS] %-48s%n", testName);
            testsPassed++;
        } else {
            System.err.printf("  [FAIL] %-48s : %s%n", testName, message);
            testsFailed++;
        }
    }

    private static void testResumeEvaluationKeerthi() {
        System.out.println("\n--- 1. Testing Resume Evaluation on Keerthivasan Profile ---");
        String text = """
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
            Visvesvaraya Technological University, Bangalore
            
            CERTIFICATIONS
            - Oracle Certified Java Associate (OCJA)
            - AWS Cloud Practitioner (In Progress)
            """;

        ResumeParser.ParsedResume parsed = ResumeParser.parseText("Keerthivasan_Resume.pdf", text);
        ResumeAIEngine.ResumeAudit audit = ResumeAIEngine.evaluate(parsed, null);

        assertTrue("Keerthi: Role Detected", "Backend Java & Spring Boot Engineer".equals(audit.detectedRole) || "Full Stack Software Engineer".equals(audit.detectedRole), "Role: " + audit.detectedRole);
        assertTrue("Keerthi: ATS Score >= 80", audit.atsScore >= 80, "ATS Score was: " + audit.atsScore);
        assertTrue("Keerthi: Overall Score >= 80", audit.overallScore >= 80, "Overall Score was: " + audit.overallScore);
        assertTrue("Keerthi: Detected Skills Found", audit.detectedSkills.contains("Java") && audit.detectedSkills.contains("Spring Boot"), "Skills: " + audit.detectedSkills);
        assertTrue("Keerthi: Impact Verbs Detected", audit.detectedImpactVerbs.contains("Engineered") || audit.detectedImpactVerbs.contains("Optimized"), "Verbs: " + audit.detectedImpactVerbs);
        assertTrue("Keerthi: Metrics Counted", audit.quantifiedMetricCount >= 3, "Metrics count: " + audit.quantifiedMetricCount);
        assertTrue("Keerthi: STAR Rewrites Generated", !audit.starRewrites.isEmpty(), "No STAR rewrites");
        assertTrue("Keerthi: Executive Summaries Generated", audit.executiveSummaries.size() == 3, "Summaries count: " + audit.executiveSummaries.size());
        assertTrue("Keerthi: Interview Questions Generated", !audit.interviewQuestions.isEmpty(), "Interview questions empty");
        assertTrue("Keerthi: Matched Jobs Present", !audit.matchedJobs.isEmpty(), "No matched jobs found");
    }

    private static void testResumeEvaluationPriya() {
        System.out.println("\n--- 2. Testing Resume Evaluation on Priya Patel (Frontend) ---");
        String text = """
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

        ResumeParser.ParsedResume parsed = ResumeParser.parseText("Priya_Patel_Frontend_Resume.pdf", text);
        ResumeAIEngine.ResumeAudit audit = ResumeAIEngine.evaluate(parsed, null);

        assertTrue("Priya: Role Detected as Frontend", audit.detectedRole.contains("Frontend") || audit.detectedRole.contains("React"), "Role: " + audit.detectedRole);
        assertTrue("Priya: ATS Score >= 75", audit.atsScore >= 75, "ATS Score was: " + audit.atsScore);
        assertTrue("Priya: Skills include React & TypeScript", audit.detectedSkills.contains("React") && audit.detectedSkills.contains("TypeScript"), "Skills: " + audit.detectedSkills);
        assertTrue("Priya: Interview Questions contain React/Frontend", audit.interviewQuestions.stream().anyMatch(q -> q.question.toLowerCase().contains("react") || q.question.toLowerCase().contains("rendering")), "Interview questions: " + audit.interviewQuestions.size());
    }

    private static void testResumeEvaluationWeakResume() {
        System.out.println("\n--- 3. Testing Resume Evaluation on Weak / Passive Resume ---");
        String weakText = """
            John Doe
            Email: john@example.com
            
            Experience:
            - Worked on backend APIs and helped with bug fixes.
            - Responsible for testing software and did database changes.
            - Assisted in making the website faster.
            
            Skills: Java, HTML
            """;

        ResumeParser.ParsedResume parsed = ResumeParser.parseText("weak.txt", weakText);
        ResumeAIEngine.ResumeAudit audit = ResumeAIEngine.evaluate(parsed, null);

        assertTrue("Weak Resume: Weak phrases flagged", !audit.detectedWeakPhrases.isEmpty(), "Weak phrases count: " + audit.detectedWeakPhrases.size());
        assertTrue("Weak Resume: Contains 'worked on' flag", audit.detectedWeakPhrases.contains("worked on") || audit.detectedWeakPhrases.contains("responsible for"), "Phrases: " + audit.detectedWeakPhrases);
        assertTrue("Weak Resume: ATS Warnings Generated", !audit.atsWarnings.isEmpty(), "Warnings count: " + audit.atsWarnings.size());
        assertTrue("Weak Resume: Missing Tier-1 Skills Found", !audit.missingTier1Skills.isEmpty(), "Missing Tier 1 count: " + audit.missingTier1Skills.size());
        assertTrue("Weak Resume: Score reflected accurately (<75)", audit.overallScore < 75, "Score: " + audit.overallScore);
    }

    private static void testSingleBulletTransformation() {
        System.out.println("\n--- 4. Testing Single Bullet STAR Transformation ---");
        ResumeAIEngine.StarRewrite rw1 = ResumeAIEngine.rewriteSingleBullet("worked on backend apis and fixed bugs", "Backend Engineer");
        assertTrue("STAR: Starts with active bullet", rw1.upgraded.startsWith("• Architected") || rw1.upgraded.startsWith("• Engineered"), "Bullet: " + rw1.upgraded);
        assertTrue("STAR: Contains quantifiable metrics (%, latency, users)", rw1.upgraded.contains("%") || rw1.upgraded.contains("latency") || rw1.upgraded.contains("uptime"), "Bullet: " + rw1.upgraded);
        assertTrue("STAR: Method note mentions Google X-Y-Z / STAR", rw1.framework.contains("STAR") || rw1.framework.contains("Google"), "Framework: " + rw1.framework);

        ResumeAIEngine.StarRewrite rw2 = ResumeAIEngine.rewriteSingleBullet("made frontend user interface using react and css", "Frontend Engineer");
        assertTrue("STAR Frontend: Mentions performance / load times", rw2.upgraded.toLowerCase().contains("performance") || rw2.upgraded.toLowerCase().contains("load") || rw2.upgraded.toLowerCase().contains("lighthouse"), "Bullet: " + rw2.upgraded);

        ResumeAIEngine.StarRewrite rw3 = ResumeAIEngine.rewriteSingleBullet("responsible for deploying code on aws", "DevOps Engineer");
        assertTrue("STAR DevOps: Mentions automation / pipeline / cycle time", rw3.upgraded.toLowerCase().contains("pipeline") || rw3.upgraded.toLowerCase().contains("cycle") || rw3.upgraded.toLowerCase().contains("ci/cd"), "Bullet: " + rw3.upgraded);
    }

    private static void testChatIntentRecognition() {
        System.out.println("\n--- 5. Testing Conversational Intent NLP Processor ---");
        ResumeParser.ParsedResume dummy = ResumeParser.parseText("sample.txt", "Keerthi\nkeerthi@example.com\nJava, Spring Boot, MySQL, Docker\n3 years experience");

        // Intent: Full Audit
        ResumeAIEngine.ChatResponse rAudit = ResumeAIEngine.processChat("Audit my resume and give me a score", dummy, "sample.txt", null);
        assertTrue("Intent: Full Audit recognized", rAudit.overallScore > 0 && rAudit.replyHtml.contains("audit-container"), "Audit response didn't contain container");

        // Intent: ATS deep dive
        ResumeAIEngine.ChatResponse rAts = ResumeAIEngine.processChat("How do I beat ATS and what is my ATS score?", dummy, "sample.txt", null);
        assertTrue("Intent: ATS deep dive recognized", rAts.replyHtml.contains("ATS Scanner") || rAts.replyHtml.contains("Applicant Tracking Systems"), "ATS response mismatch");

        // Intent: Rewrite bullet
        ResumeAIEngine.ChatResponse rRewrite = ResumeAIEngine.processChat("rewrite: responsible for maintaining database queries", dummy, "sample.txt", null);
        assertTrue("Intent: Bullet rewrite recognized", rRewrite.replyHtml.contains("star-card") && rRewrite.replyHtml.contains("Before") && rRewrite.replyHtml.contains("After"), "Rewrite response mismatch");

        // Intent: Skill gap
        ResumeAIEngine.ChatResponse rSkills = ResumeAIEngine.processChat("What skills should I learn next for my role?", dummy, "sample.txt", null);
        assertTrue("Intent: Skill gap recognized", rSkills.replyHtml.contains("Skill Gap") || rSkills.replyHtml.contains("Demanded"), "Skill gap response mismatch");

        // Intent: Interview Q&A
        ResumeAIEngine.ChatResponse rInterview = ResumeAIEngine.processChat("Give me mock interview questions for my resume", dummy, "sample.txt", null);
        assertTrue("Intent: Interview prep recognized", rInterview.replyHtml.contains("Technical Interview") && rInterview.replyHtml.contains("Model Answer"), "Interview prep response mismatch");

        // Intent: Executive Summary
        ResumeAIEngine.ChatResponse rSummary = ResumeAIEngine.processChat("Write an executive summary for my resume", dummy, "sample.txt", null);
        assertTrue("Intent: Summary generator recognized", rSummary.replyHtml.contains("Executive Summar"), "Summary response mismatch");

        // Intent: Jobs & Salary
        ResumeAIEngine.ChatResponse rJobs = ResumeAIEngine.processChat("What jobs fit my profile and what is the salary?", dummy, "sample.txt", null);
        assertTrue("Intent: Jobs & salary recognized", rJobs.replyHtml.contains("SmartHire Live Vacancies") || rJobs.replyHtml.contains("Salary"), "Jobs response mismatch");

        // Intent: Help menu
        ResumeAIEngine.ChatResponse rHelp = ResumeAIEngine.processChat("help", null, null, null);
        assertTrue("Intent: Help menu recognized", rHelp.replyHtml.contains("Available Commands"), "Help response mismatch");
    }

    private static void testPastedResumeDirectAudit() {
        System.out.println("\n--- 6. Testing Direct Pasted Multi-line Resume in Chat ---");
        String pastedMsg = """
            Rahul Sharma
            Email: rahul@example.com | Phone: 9876543210
            Full Stack Developer with 2 years of experience.
            Skills: Java, Spring Boot, React, MySQL, Git
            Experience:
            - Built web application microservices in Java.
            - Handled database optimization and API routes.
            Education: B.Tech Computer Science
            """;

        ResumeAIEngine.ChatResponse response = ResumeAIEngine.processChat(pastedMsg, null, null, null);
        assertTrue("Pasted Resume: Automatically Audited", response.overallScore > 0, "Score: " + response.overallScore);
        assertTrue("Pasted Resume: Contains scorecard UI", response.replyHtml.contains("audit-hero") && response.replyHtml.contains("dial-number"), "Reply HTML lacked scorecard");
        assertTrue("Pasted Resume: Quick chips generated", !response.quickChips.isEmpty(), "Chips empty");
    }

    private static void testWidgetHtmlInjection() {
        System.out.println("\n--- 7. Testing Global Floating Widget HTML Injection ---");
        String sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Test Page</title></head>
            <body>
            <h1>Welcome to SmartHire</h1>
            <footer>Footer content</footer>
            </body>
            </html>
            """;

        WebServer.SessionData session = new WebServer.SessionData(4, "Keerthivasan", "keerthi@example.com", "JOB_SEEKER", "");
        String injected = ResumeAIChatbot.injectWidget(sampleHtml, session);

        assertTrue("Injection: Contains Widget Root", injected.contains("id=\"ai-resume-coach-widget\""), "Widget root missing");
        assertTrue("Injection: Contains Floating Launcher 🤖", injected.contains("ai-launcher-btn") && injected.contains("🤖"), "Launcher button missing");
        assertTrue("Injection: Contains Modal Chat Window", injected.contains("id=\"ai-chat-modal\""), "Chat modal missing");
        assertTrue("Injection: Injects CSS Link", injected.contains("/css/ai-chatbot.css"), "CSS link missing");
        assertTrue("Injection: Injects JS Script", injected.contains("/js/ai-chatbot.js"), "JS script missing");
        assertTrue("Injection: Propagates User Name", injected.contains("data-user-name=\"Keerthivasan\""), "User name missing in data attr");
        assertTrue("Injection: Injected before </body>", injected.indexOf("ai-resume-coach-widget") < injected.indexOf("</body>"), "Wrong placement");

        // Test double-injection prevention
        String doubleInjected = ResumeAIChatbot.injectWidget(injected, session);
        int count = doubleInjected.split("id=\"ai-resume-coach-widget\"").length - 1;
        assertTrue("Injection: No double injection", count == 1, "Count was: " + count);
    }

    private static void testEdgeCasesAndResilience() {
        System.out.println("\n--- 8. Testing Edge Cases & Resilience ---");

        // Null inputs
        ResumeAIEngine.ResumeAudit nullAudit = ResumeAIEngine.evaluate(null, null);
        assertTrue("Resilience: Null resume evaluate doesn't crash", nullAudit != null, "Null evaluate returned null");

        // Empty message
        ResumeAIEngine.ChatResponse emptyResp = ResumeAIEngine.processChat("", null, null, null);
        assertTrue("Resilience: Empty message handled gracefully", emptyResp != null && !emptyResp.replyHtml.isEmpty(), "Empty message failed");

        // Single bullet with null
        ResumeAIEngine.StarRewrite nullRewrite = ResumeAIEngine.rewriteSingleBullet(null, null);
        assertTrue("Resilience: Null bullet rewrite handled", nullRewrite != null && !nullRewrite.upgraded.isEmpty(), "Null rewrite failed");

        // Special characters & quotes in chat input
        ResumeAIEngine.ChatResponse quoteResp = ResumeAIEngine.processChat("rewrite: did \"SQL\" queries & 'MongoDB' updates <script>alert(1)</script>", null, null, null);
        assertTrue("Resilience: Special characters handled safely", !quoteResp.replyHtml.contains("<script>alert(1)</script>"), "XSS script was unescaped");

        // HTML injection on empty HTML string
        String emptyHtmlInjected = ResumeAIChatbot.injectWidget("", null);
        assertTrue("Resilience: Empty HTML inject handled", emptyHtmlInjected.contains("ai-resume-coach-widget"), "Empty HTML inject failed");
    }
}
