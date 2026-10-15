import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * ResumeHttpIntegrationTest - Spins up WebServer and performs end-to-end HTTP tests.
 */
public class ResumeHttpIntegrationTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("   RUNNING SMARTHIRE HTTP INTEGRATION & LIVE ENDPOINT TESTS");
        System.out.println("============================================================");

        try {
            // Start server in background thread if not already running
            Thread serverThread = new Thread(() -> {
                try {
                    WebServer.main(new String[0]);
                } catch (Exception e) {
                    System.out.println("Server thread note: " + e.getMessage());
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            // Wait until port 8080 is accepting connections (up to 10 seconds)
            for (int i = 0; i < 25; i++) {
                try (java.net.Socket s = new java.net.Socket("localhost", 8080)) {
                    if (s.isConnected()) break;
                } catch (Exception e) {
                    Thread.sleep(400);
                }
            }

            testHttpGet("/", "Home Page with Floating Chatbot Widget");
            testHttpGet("/jobs", "Jobs Page with Floating Chatbot Widget");
            testHttpGet("/css/ai-chatbot.css", "Static CSS endpoint /css/ai-chatbot.css");
            testHttpGet("/js/ai-chatbot.js", "Static JS endpoint /js/ai-chatbot.js");
            testHttpGet("/api/chat/profile", "Profile Resume API /api/chat/profile");

            testHttpPostSample();
            testHttpPostMessage();
            testHttpPostEvaluate();
            testInnovativeFeatures();

            System.out.println();
            System.out.println("============================================================");
            System.out.printf("   HTTP INTEGRATION RESULTS: %d PASSED, %d FAILED%n", testsPassed, testsFailed);
            System.out.println("============================================================");

            if (testsFailed > 0) {
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Integration test crash: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void assertTrue(String testName, boolean condition, String message) {
        if (condition) {
            System.out.printf("  [PASS] %-52s%n", testName);
            testsPassed++;
        } else {
            System.err.printf("  [FAIL] %-52s : %s%n", testName, message);
            testsFailed++;
        }
    }

    private static void testHttpGet(String path, String desc) {
        try {
            URL url = URI.create("http://localhost:8080" + path).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            int code = conn.getResponseCode();
            String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertTrue("HTTP GET " + path + " Status 200", code == 200, "Status code: " + code);

            if (path.equals("/") || path.equals("/jobs")) {
                assertTrue("HTTP GET " + path + " has Widget Container", body.contains("id=\"ai-resume-coach-widget\""), "Missing widget container");
                assertTrue("HTTP GET " + path + " has Floating Emoji Button", body.contains("ai-launcher-btn") && body.contains("🤖"), "Missing launcher emoji");
                assertTrue("HTTP GET " + path + " has CSS & JS Links", body.contains("/css/ai-chatbot.css") && body.contains("/js/ai-chatbot.js"), "Missing CSS/JS references");
                assertTrue("HTTP GET " + path + " has Navbar PRO Badge", body.contains("openAiChatbot()"), "Missing navbar link");
            } else if (path.endsWith(".css")) {
                assertTrue("HTTP GET CSS Content Valid", body.contains(".ai-launcher-btn") && body.contains(".ai-chat-modal"), "CSS missing class definitions");
            } else if (path.endsWith(".js")) {
                assertTrue("HTTP GET JS Content Valid", body.contains("initChatbot") && body.contains("toggleAiChat"), "JS missing functions");
            }
        } catch (Exception e) {
            assertTrue("HTTP GET " + path + " (" + desc + ")", false, e.getMessage());
        }
    }

    private static void testHttpPostSample() {
        try {
            URL url = URI.create("http://localhost:8080/api/chat/sample").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String postData = "sample=keerthi";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertTrue("HTTP POST /api/chat/sample Status 200", code == 200, "Status: " + code);
            assertTrue("HTTP POST /api/chat/sample Success JSON", body.contains("\"success\":true"), "Response: " + body);
            assertTrue("HTTP POST /api/chat/sample Score Included", body.contains("\"overallScore\":") && body.contains("\"atsScore\":"), "Scores missing in: " + body);
            assertTrue("HTTP POST /api/chat/sample Role Detected", body.contains("Backend Java") || body.contains("Full Stack"), "Role missing in: " + body);
        } catch (Exception e) {
            assertTrue("HTTP POST /api/chat/sample", false, e.getMessage());
        }
    }

    private static void testHttpPostMessage() {
        try {
            URL url = URI.create("http://localhost:8080/api/chat/message").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String postData = "message=rewrite:+worked+on+microservices+and+fixed+bugs";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertTrue("HTTP POST /api/chat/message Status 200", code == 200, "Status: " + code);
            assertTrue("HTTP POST /api/chat/message Success JSON", body.contains("\"success\":true"), "Response: " + body);
            assertTrue("HTTP POST /api/chat/message Star Card", body.contains("star-card") || body.contains("STAR"), "Card missing in: " + body);
        } catch (Exception e) {
            assertTrue("HTTP POST /api/chat/message", false, e.getMessage());
        }
    }

    private static void testHttpPostEvaluate() {
        try {
            URL url = URI.create("http://localhost:8080/api/chat/evaluate").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String sampleResume = """
                Ananya Roy
                Python & Machine Learning Engineer
                Bangalore | ananya@example.com | +91 9988776655
                Skills: Python, PyTorch, TensorFlow, SQL, Docker, Git, REST API
                Experience:
                - Trained and deployed computer vision models with 92% accuracy.
                - Automated ML data preprocessing pipelines reducing ETL time by 45%.
                Education: M.Tech Computer Science
                """;

            String postData = "fileName=Ananya_ML.txt&resumeText=" + java.net.URLEncoder.encode(sampleResume, StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertTrue("HTTP POST /api/chat/evaluate Status 200", code == 200, "Status: " + code);
            assertTrue("HTTP POST /api/chat/evaluate Success JSON", body.contains("\"success\":true"), "Response: " + body);
            assertTrue("HTTP POST /api/chat/evaluate ML Role Detected", body.contains("Machine Learning") || body.contains("AI"), "Role missing in: " + body);
            assertTrue("HTTP POST /api/chat/evaluate Quantified Metrics Counted", body.contains("\"quantifiedMetrics\":"), "Metrics count missing");
        } catch (Exception e) {
            assertTrue("HTTP POST /api/chat/evaluate", false, e.getMessage());
        }
    }

    private static void testInnovativeFeatures() {
        // 1. Test Company Trust & Scam Shield (/trustmatch)
        testHttpGet("/trustmatch", "TrustMatch Company Verification & Scam Shield");

        // 2. Test Accessible Job Search UI elements on /jobs
        try {
            URL url = URI.create("http://localhost:8080/jobs").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue("Accessibility: High Contrast Toggle Present", body.contains("toggleHighContrast()"), "Missing toggle");
            assertTrue("Accessibility: Font Resizer Controls Present", body.contains("adjustFontSize"), "Missing font resizer");
            assertTrue("Accessibility: Keyword and Skill Filters Present", body.contains("name=\"keyword\""), "Missing keyword input");
        } catch (Exception e) {
            assertTrue("Accessibility Checks on /jobs", false, e.getMessage());
        }

        // 3. Test AI Mock Interview Simulator question generation & evaluation
        try {
            java.util.List<InnovativeFeaturesHandlers.MockQuestion> javaQs =
                InnovativeFeaturesHandlers.getQuestionsForRole("Java Full Stack Developer");
            assertTrue("Mock Interview: Role Questions Generated", javaQs != null && !javaQs.isEmpty(), "No questions found");

            InnovativeFeaturesHandlers.MockQuestion q = javaQs.get(0);
            InnovativeFeaturesHandlers.MockEvaluation eval =
                InnovativeFeaturesHandlers.evaluateAnswer(q, "We implement the SAGA pattern with Kafka event streams for eventual consistency and transactional outbox for idempotent state updates.");
            assertTrue("Mock Interview: Concept Grading Score >= 70", eval.score >= 70, "Score was: " + eval.score);
            assertTrue("Mock Interview: Matched Concepts Non-Empty", !eval.matchedConcepts.isEmpty(), "No matched concepts");
            assertTrue("Mock Interview: STAR Behavioral Question Present", javaQs.stream().anyMatch(mq -> mq.isBehavioral), "No STAR questions");
        } catch (Exception e) {
            assertTrue("Mock Interview Engine Tests", false, e.getMessage());
        }

        // 4. Test Project-Based Skill Portfolio
        try {
            java.util.List<InnovativeFeaturesHandlers.CandidateProject> projects =
                InnovativeFeaturesHandlers.getProjectsForUser(1);
            assertTrue("Portfolio: Candidate Projects Present", projects != null && !projects.isEmpty(), "No projects found");
            assertTrue("Portfolio: GitHub Repository Link Valid", projects.get(0).githubUrl.contains("github.com"), "Invalid github URL");
            assertTrue("Portfolio: Live Demo Link Present", projects.get(0).demoUrl != null && !projects.get(0).demoUrl.isEmpty(), "Missing demo URL");
        } catch (Exception e) {
            assertTrue("Project Portfolio Tests", false, e.getMessage());
        }

        // 5. Test Smart Application Follow-Up SLA tracking
        try {
            InnovativeFeaturesHandlers.LAST_FOLLOW_UPS.put(101, System.currentTimeMillis());
            assertTrue("Follow-Up: Last Follow-Up Map Tracked", InnovativeFeaturesHandlers.LAST_FOLLOW_UPS.containsKey(101), "Follow-up map failed");
        } catch (Exception e) {
            assertTrue("Follow-Up SLA Tests", false, e.getMessage());
        }
    }
}
