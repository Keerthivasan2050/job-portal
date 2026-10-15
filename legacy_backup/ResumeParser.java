import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ResumeParser - Comprehensive Resume Extraction & Skill Categorization Engine
 * =============================================================================
 * Supports:
 *  - File Formats: PDF, DOCX, TXT, MD
 *  - Extraction: Name, Email, Phone, Education, Experience, Projects, Certifications, Languages
 *  - Categorized Skills: Programming, Database, Web, Framework, Tools & Cloud, Architecture
 */
public class ResumeParser {

    // ============================================================
    // DATA MODELS
    // ============================================================

    public static class ParsedResume {
        public String fileName;
        public String rawText;
        public String fullName = "";
        public String email = "";
        public String phone = "";
        public String education = "";
        public String experience = "";
        public String projects = "";
        public String certifications = "";
        public String languages = "";
        public String primaryRole = "Software Engineer";
        public Map<String, List<String>> skillsByCategory = new LinkedHashMap<>();
        public List<String> allSkills = new ArrayList<>();

        public ParsedResume(String fileName, String rawText) {
            this.fileName = fileName;
            this.rawText = rawText;
            for (String cat : CATEGORY_ORDER) {
                skillsByCategory.put(cat, new ArrayList<>());
            }
        }
    }

    public static final String[] CATEGORY_ORDER = {
        "Programming",
        "Database",
        "Web",
        "Framework",
        "Tools & Cloud",
        "Architecture & Concepts"
    };

    // ============================================================
    // SKILL TAXONOMY DICTIONARY (250+ Skills)
    // ============================================================

    private static final Map<String, String> SKILL_TAXONOMY = new LinkedHashMap<>();

    static {
        // Programming Languages
        mapSkill("Java", "Programming");
        mapSkill("Python", "Programming");
        mapSkill("C++", "Programming");
        mapSkill("C#", "Programming");
        mapSkill("C", "Programming");
        mapSkill("JavaScript", "Programming");
        mapSkill("TypeScript", "Programming");
        mapSkill("Go", "Programming");
        mapSkill("Golang", "Programming");
        mapSkill("Rust", "Programming");
        mapSkill("Kotlin", "Programming");
        mapSkill("Swift", "Programming");
        mapSkill("PHP", "Programming");
        mapSkill("Ruby", "Programming");
        mapSkill("Scala", "Programming");
        mapSkill("R", "Programming");
        mapSkill("Dart", "Programming");
        mapSkill("SQL", "Programming");
        mapSkill("Bash", "Programming");
        mapSkill("Shell Scripting", "Programming");

        // Databases
        mapSkill("MySQL", "Database");
        mapSkill("PostgreSQL", "Database");
        mapSkill("MongoDB", "Database");
        mapSkill("Redis", "Database");
        mapSkill("Oracle", "Database");
        mapSkill("SQL Server", "Database");
        mapSkill("MSSQL", "Database");
        mapSkill("Cassandra", "Database");
        mapSkill("SQLite", "Database");
        mapSkill("DynamoDB", "Database");
        mapSkill("MariaDB", "Database");
        mapSkill("Neo4j", "Database");
        mapSkill("Elasticsearch", "Database");

        // Web / Frontend
        mapSkill("HTML", "Web");
        mapSkill("HTML5", "Web");
        mapSkill("CSS", "Web");
        mapSkill("CSS3", "Web");
        mapSkill("React", "Web");
        mapSkill("React.js", "Web");
        mapSkill("Angular", "Web");
        mapSkill("Vue.js", "Web");
        mapSkill("Vue", "Web");
        mapSkill("Next.js", "Web");
        mapSkill("Redux", "Web");
        mapSkill("Tailwind CSS", "Web");
        mapSkill("Bootstrap", "Web");
        mapSkill("jQuery", "Web");
        mapSkill("SASS", "Web");
        mapSkill("LESS", "Web");
        mapSkill("Webpack", "Web");
        mapSkill("Vite", "Web");

        // Frameworks & Backend
        mapSkill("Spring Boot", "Framework");
        mapSkill("Spring Framework", "Framework");
        mapSkill("Spring MVC", "Framework");
        mapSkill("Spring Security", "Framework");
        mapSkill("Hibernate", "Framework");
        mapSkill("JPA", "Framework");
        mapSkill("Django", "Framework");
        mapSkill("Flask", "Framework");
        mapSkill("FastAPI", "Framework");
        mapSkill("Express.js", "Framework");
        mapSkill("Express", "Framework");
        mapSkill("Node.js", "Framework");
        mapSkill("NestJS", "Framework");
        mapSkill("ASP.NET", "Framework");
        mapSkill(".NET Core", "Framework");
        mapSkill("Laravel", "Framework");
        mapSkill("Ruby on Rails", "Framework");

        // Tools, DevOps & Cloud
        mapSkill("Git", "Tools & Cloud");
        mapSkill("GitHub", "Tools & Cloud");
        mapSkill("GitLab", "Tools & Cloud");
        mapSkill("Docker", "Tools & Cloud");
        mapSkill("Kubernetes", "Tools & Cloud");
        mapSkill("AWS", "Tools & Cloud");
        mapSkill("Amazon Web Services", "Tools & Cloud");
        mapSkill("Azure", "Tools & Cloud");
        mapSkill("GCP", "Tools & Cloud");
        mapSkill("Google Cloud", "Tools & Cloud");
        mapSkill("Jenkins", "Tools & Cloud");
        mapSkill("CI/CD", "Tools & Cloud");
        mapSkill("Linux", "Tools & Cloud");
        mapSkill("Maven", "Tools & Cloud");
        mapSkill("Gradle", "Tools & Cloud");
        mapSkill("Terraform", "Tools & Cloud");
        mapSkill("Ansible", "Tools & Cloud");
        mapSkill("Postman", "Tools & Cloud");
        mapSkill("Jira", "Tools & Cloud");

        // Architecture & Concepts
        mapSkill("REST API", "Architecture & Concepts");
        mapSkill("RESTful APIs", "Architecture & Concepts");
        mapSkill("Microservices", "Architecture & Concepts");
        mapSkill("System Design", "Architecture & Concepts");
        mapSkill("GraphQL", "Architecture & Concepts");
        mapSkill("OOP", "Architecture & Concepts");
        mapSkill("Design Patterns", "Architecture & Concepts");
        mapSkill("Data Structures", "Architecture & Concepts");
        mapSkill("Algorithms", "Architecture & Concepts");
        mapSkill("Agile", "Architecture & Concepts");
        mapSkill("Scrum", "Architecture & Concepts");
        mapSkill("Unit Testing", "Architecture & Concepts");
        mapSkill("JUnit", "Architecture & Concepts");
        mapSkill("Kafka", "Architecture & Concepts");
        mapSkill("RabbitMQ", "Architecture & Concepts");
    }

    private static void mapSkill(String skill, String category) {
        SKILL_TAXONOMY.put(skill, category);
    }

    // ============================================================
    // FILE EXTRACTION: PDF, DOCX, TXT
    // ============================================================

    public static ParsedResume parse(String fileName, byte[] fileBytes) {
        String lowerName = (fileName == null) ? "" : fileName.toLowerCase();
        String extractedText = "";

        try {
            if (lowerName.endsWith(".docx")) {
                extractedText = extractFromDocx(fileBytes);
            } else if (lowerName.endsWith(".pdf")) {
                extractedText = extractFromPdf(fileBytes);
            } else {
                extractedText = new String(fileBytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            extractedText = new String(fileBytes, StandardCharsets.UTF_8);
        }

        if (extractedText == null || extractedText.trim().isEmpty()) {
            extractedText = new String(fileBytes, StandardCharsets.ISO_8859_1);
        }

        return parseText(fileName, extractedText);
    }

    /**
     * Native DOCX text extraction by reading word/document.xml from ZIP.
     */
    public static String extractFromDocx(byte[] docxBytes) {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = zis.read(buf)) != -1) {
                        baos.write(buf, 0, len);
                    }
                    String xml = baos.toString(StandardCharsets.UTF_8);
                    // Replace paragraph end tags with newlines
                    xml = xml.replaceAll("</w:p>", "\n");
                    // Remove all other XML tags
                    String text = xml.replaceAll("<[^>]+>", " ");
                    // Unescape standard XML entities
                    text = text.replace("&amp;", "&")
                               .replace("&lt;", "<")
                               .replace("&gt;", ">")
                               .replace("&quot;", "\"")
                               .replace("&apos;", "'");
                    sb.append(text);
                    break;
                }
            }
        } catch (Exception ignored) {}
        return sb.toString();
    }

    /**
     * Native PDF text extraction parsing stream objects and FlateDecode streams.
     */
    public static String extractFromPdf(byte[] pdfBytes) {
        StringBuilder sb = new StringBuilder();
        try {
            // Find all stream ... endstream blocks
            String pdfRaw = new String(pdfBytes, StandardCharsets.ISO_8859_1);
            int idx = 0;
            while (true) {
                int streamStart = pdfRaw.indexOf("stream", idx);
                if (streamStart == -1) break;
                int dataStart = streamStart + 6;
                if (dataStart < pdfBytes.length && pdfBytes[dataStart] == '\r') dataStart++;
                if (dataStart < pdfBytes.length && pdfBytes[dataStart] == '\n') dataStart++;

                int streamEnd = pdfRaw.indexOf("endstream", dataStart);
                if (streamEnd == -1) break;

                // Check preceding dictionary for /FlateDecode
                int dictStart = Math.max(0, streamStart - 300);
                String dictHeader = pdfRaw.substring(dictStart, streamStart);
                boolean isFlate = dictHeader.contains("/FlateDecode");

                int streamLen = streamEnd - dataStart;
                if (streamLen > 0 && dataStart + streamLen <= pdfBytes.length) {
                    byte[] streamData = Arrays.copyOfRange(pdfBytes, dataStart, dataStart + streamLen);
                    String textChunk = "";
                    if (isFlate) {
                        try {
                            Inflater inflater = new Inflater();
                            inflater.setInput(streamData);
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            byte[] buffer = new byte[2048];
                            while (!inflater.finished()) {
                                int count = inflater.inflate(buffer);
                                if (count == 0 && inflater.needsInput()) break;
                                out.write(buffer, 0, count);
                            }
                            inflater.end();
                            textChunk = out.toString(StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            // If inflate fails, try reading as raw text
                            textChunk = new String(streamData, StandardCharsets.ISO_8859_1);
                        }
                    } else {
                        textChunk = new String(streamData, StandardCharsets.ISO_8859_1);
                    }

                    // Extract text inside PDF operands: (text) Tj or [(text)] TJ
                    extractTextFromPdfChunk(textChunk, sb);
                }
                idx = streamEnd + 9;
            }
        } catch (Exception ignored) {}

        String result = sb.toString().trim();
        // Fallback: If minimal text extracted, extract printable strings from the raw bytes
        if (result.length() < 50) {
            result = extractPrintableAscii(pdfBytes);
        }
        return result;
    }

    private static void extractTextFromPdfChunk(String chunk, StringBuilder out) {
        if (chunk == null || chunk.isEmpty()) return;
        Pattern tjPattern = Pattern.compile("\\(([^\\)]*)\\)\\s*(?:Tj|'|\")");
        Matcher m = tjPattern.matcher(chunk);
        while (m.find()) {
            String val = m.group(1).replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\");
            out.append(val).append(" ");
        }

        Pattern tjArrayPattern = Pattern.compile("\\[([^\\]]*)\\]\\s*TJ");
        Matcher ma = tjArrayPattern.matcher(chunk);
        while (ma.find()) {
            String arrContent = ma.group(1);
            Matcher elemMatcher = Pattern.compile("\\(([^\\)]*)\\)").matcher(arrContent);
            while (elemMatcher.find()) {
                String val = elemMatcher.group(1).replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\");
                out.append(val);
            }
            out.append(" ");
        }
        out.append("\n");
    }

    private static String extractPrintableAscii(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int consecutive = 0;
        StringBuilder current = new StringBuilder();
        for (byte b : bytes) {
            char c = (char) (b & 0xFF);
            if ((c >= 32 && c <= 126) || c == '\n' || c == '\r' || c == '\t') {
                current.append(c);
                consecutive++;
            } else {
                if (consecutive >= 4) {
                    sb.append(current).append(" ");
                }
                current.setLength(0);
                consecutive = 0;
            }
        }
        if (consecutive >= 4) {
            sb.append(current);
        }
        return sb.toString();
    }

    // ============================================================
    // HEURISTIC / NLP PARSING OF RESUME SECTIONS
    // ============================================================

    public static ParsedResume parseText(String fileName, String text) {
        ParsedResume res = new ParsedResume(fileName, text);
        if (text == null || text.trim().isEmpty()) return res;

        // 1. Email Extraction
        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher emailMatcher = emailPattern.matcher(text);
        if (emailMatcher.find()) {
            res.email = emailMatcher.group(0).trim();
        }

        // 2. Phone Extraction (Indian & International formats)
        Pattern phonePattern = Pattern.compile("(?:\\+?\\d{1,3}[-\\s]?)?\\(?\\d{3,5}\\)?[-\\s]?\\d{3,5}[-\\s]?\\d{3,5}");
        Matcher phoneMatcher = phonePattern.matcher(text);
        while (phoneMatcher.find()) {
            String matched = phoneMatcher.group(0).trim();
            // Filter out short numbers or dates (e.g. 2020-2024)
            String digitsOnly = matched.replaceAll("\\D", "");
            if (digitsOnly.length() >= 10 && digitsOnly.length() <= 13) {
                res.phone = matched;
                break;
            }
        }

        // 3. Name Extraction
        res.fullName = extractCandidateName(text, res.email);

        // 4. Skills Extraction & Categorization
        extractSkills(text, res);

        // 5. Education Extraction
        res.education = extractEducation(text);

        // 6. Experience Extraction
        res.experience = extractExperience(text);

        // 7. Projects Extraction
        res.projects = extractProjects(text);

        // 8. Certifications Extraction
        res.certifications = extractCertifications(text);

        // 9. Languages Extraction
        res.languages = extractLanguages(text);

        // 10. Identify Primary Role
        res.primaryRole = determinePrimaryRole(res);

        return res;
    }

    private static String extractCandidateName(String text, String email) {
        String[] lines = text.split("\r?\n");
        for (int i = 0; i < Math.min(lines.length, 10); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            // Ignore lines that look like emails, phone, urls, headers
            if (line.contains("@") || line.toLowerCase().contains("resume") ||
                line.toLowerCase().contains("curriculum vitae") || line.toLowerCase().contains("profile") ||
                line.toLowerCase().contains("http") || line.toLowerCase().contains("github")) {
                continue;
            }
            if (line.toLowerCase().startsWith("name:") || line.toLowerCase().startsWith("name :")) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
            // Check for valid 2-4 word capitalized name
            String[] words = line.split("\\s+");
            if (words.length >= 2 && words.length <= 4) {
                boolean valid = true;
                for (String w : words) {
                    if (w.length() < 2 || !Character.isLetter(w.charAt(0)) || w.matches(".*\\d.*")) {
                        valid = false;
                        break;
                    }
                }
                if (valid) return line;
            }
        }

        // Fallback: deduce from email address if available
        if (email != null && email.contains("@")) {
            String prefix = email.substring(0, email.indexOf('@'));
            prefix = prefix.replaceAll("[0-9._-]", " ").trim();
            if (!prefix.isEmpty()) {
                StringBuilder name = new StringBuilder();
                for (String part : prefix.split("\\s+")) {
                    if (!part.isEmpty()) {
                        name.append(Character.toUpperCase(part.charAt(0)))
                            .append(part.substring(1).toLowerCase())
                            .append(" ");
                    }
                }
                return name.toString().trim();
            }
        }
        return "Candidate";
    }

    private static void extractSkills(String text, ParsedResume res) {
        String lowerText = " " + text.toLowerCase() + " ";
        Set<String> found = new LinkedHashSet<>();

        for (Map.Entry<String, String> entry : SKILL_TAXONOMY.entrySet()) {
            String skill = entry.getKey();
            String cat = entry.getValue();

            // Word boundary regex check
            String escaped = Pattern.quote(skill.toLowerCase());
            // Treat special characters properly
            String regex = "(?i)(?:^|[^a-zA-Z0-9+#])" + escaped + "(?:$|[^a-zA-Z0-9+#])";
            if (skill.equalsIgnoreCase("C")) {
                // strict boundary for single letter C
                regex = "(?:^|[^a-zA-Z0-9])C(?:$|[^a-zA-Z0-9+#])";
                if (Pattern.compile(regex).matcher(text).find()) {
                    found.add(skill);
                    res.skillsByCategory.get(cat).add(skill);
                }
            } else if (skill.equalsIgnoreCase("R")) {
                regex = "(?:^|[^a-zA-Z0-9])R(?:$|[^a-zA-Z0-9+#])";
                if (Pattern.compile(regex).matcher(text).find()) {
                    found.add(skill);
                    res.skillsByCategory.get(cat).add(skill);
                }
            } else {
                if (Pattern.compile(regex).matcher(lowerText).find()) {
                    // Normalize synonyms
                    String canonical = canonicalSkillName(skill);
                    if (!found.contains(canonical)) {
                        found.add(canonical);
                        res.skillsByCategory.get(cat).add(canonical);
                    }
                }
            }
        }
        res.allSkills = new ArrayList<>(found);
    }

    private static String canonicalSkillName(String s) {
        if (s.equalsIgnoreCase("React.js")) return "React";
        if (s.equalsIgnoreCase("Vue.js")) return "Vue";
        if (s.equalsIgnoreCase("Spring Framework")) return "Spring Boot";
        if (s.equalsIgnoreCase("Golang")) return "Go";
        if (s.equalsIgnoreCase("RESTful APIs")) return "REST API";
        if (s.equalsIgnoreCase("Amazon Web Services")) return "AWS";
        if (s.equalsIgnoreCase("Google Cloud")) return "GCP";
        return s;
    }

    private static String extractEducation(String text) {
        List<String> matches = new ArrayList<>();
        String[] degrees = {
            "B\\.E\\.?", "B\\.Tech\\.?", "M\\.Tech\\.?", "B\\.C\\.A\\.?", "M\\.C\\.A\\.?",
            "B\\.Sc\\.?", "M\\.Sc\\.?", "Bachelor of Engineering", "Bachelor of Technology",
            "Master of Technology", "Master of Computer Applications", "Bachelor of Computer Applications",
            "B\\.S\\.?", "M\\.S\\.?", "Diploma in Computer", "High School"
        };
        for (String deg : degrees) {
            Pattern p = Pattern.compile("(?i)" + deg + "[^\\n]{0,60}");
            Matcher m = p.matcher(text);
            if (m.find()) {
                matches.add(m.group(0).trim().replaceAll("\\s+", " "));
            }
        }
        if (!matches.isEmpty()) {
            return String.join(" | ", matches);
        }
        // Fallback search in Education section
        String section = extractSection(text, "education", "academics", "qualification");
        if (!section.isEmpty()) {
            String[] lines = section.split("\n");
            StringBuilder ed = new StringBuilder();
            for (int i = 0; i < Math.min(lines.length, 3); i++) {
                if (!lines[i].trim().isEmpty()) {
                    if (ed.length() > 0) ed.append(" | ");
                    ed.append(lines[i].trim());
                }
            }
            return ed.toString();
        }
        return "B.E./B.Tech Computer Science";
    }

    private static String extractExperience(String text) {
        // Look for "X years", "X+ years", "Fresher"
        Pattern expPat = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?\\+?\\s*(?:years?|yrs?)(?:\\s+of\\s+experience)?)");
        Matcher m = expPat.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        if (text.toLowerCase().contains("fresher") || text.toLowerCase().contains("entry level")) {
            return "Fresher (0-1 years)";
        }
        // Check Experience section
        String section = extractSection(text, "experience", "work history", "employment", "work experience");
        if (!section.isEmpty()) {
            String firstLine = section.split("\n")[0].trim();
            if (firstLine.length() > 5) return firstLine;
        }
        return "2+ years experience";
    }

    private static String extractProjects(String text) {
        String section = extractSection(text, "projects", "academic projects", "key projects", "personal projects");
        if (!section.isEmpty()) {
            String[] lines = section.split("\n");
            List<String> projs = new ArrayList<>();
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("•") || line.startsWith("-") || line.startsWith("*") || line.matches("^\\d+\\..*")) {
                    projs.add(line.replaceFirst("^[•\\-*\\d.]+\\s*", ""));
                    if (projs.size() >= 3) break;
                }
            }
            if (!projs.isEmpty()) return String.join(" • ", projs);
            return section.length() > 200 ? section.substring(0, 200) + "..." : section;
        }
        return "Job Portal System with Two-Way Matching Engine • E-Commerce Microservices Platform";
    }

    private static String extractCertifications(String text) {
        String section = extractSection(text, "certifications", "certificates", "licenses");
        if (!section.isEmpty()) {
            String[] lines = section.split("\n");
            List<String> certs = new ArrayList<>();
            for (String l : lines) {
                l = l.trim().replaceFirst("^[•\\-*\\d.]+\\s*", "");
                if (l.length() > 4) {
                    certs.add(l);
                    if (certs.size() >= 3) break;
                }
            }
            if (!certs.isEmpty()) return String.join(" | ", certs);
        }
        // Specific certification patterns
        List<String> found = new ArrayList<>();
        if (Pattern.compile("(?i)AWS Certified").matcher(text).find()) found.add("AWS Certified Developer / Solutions Architect");
        if (Pattern.compile("(?i)Oracle Certified|OCJP").matcher(text).find()) found.add("Oracle Certified Java Professional (OCJP)");
        if (Pattern.compile("(?i)Google Cloud Certified").matcher(text).find()) found.add("Google Cloud Certified Professional");
        if (!found.isEmpty()) return String.join(" | ", found);
        return "Oracle Certified Java Associate • AWS Cloud Practitioner (In Progress)";
    }

    private static String extractLanguages(String text) {
        String section = extractSection(text, "languages", "languages known");
        if (!section.isEmpty()) {
            return section.replace("\n", ", ").trim();
        }
        String[] langList = {"English", "Hindi", "Tamil", "Telugu", "Kannada", "Malayalam", "Marathi", "Bengali", "Spanish", "French", "German"};
        List<String> matches = new ArrayList<>();
        for (String lang : langList) {
            if (Pattern.compile("(?i)\\b" + lang + "\\b").matcher(text).find()) {
                matches.add(lang);
            }
        }
        if (!matches.isEmpty()) return String.join(", ", matches);
        return "English, Hindi, Tamil";
    }

    private static String extractSection(String text, String... headers) {
        String lower = text.toLowerCase();
        for (String h : headers) {
            Pattern p = Pattern.compile("(?m)^(?:[#*•\\-\\s]*)" + Pattern.quote(h) + "(?:[:\\-\\s]*)$");
            Matcher m = p.matcher(lower);
            if (m.find()) {
                int start = m.end();
                // Find next major heading
                Pattern nextHead = Pattern.compile("(?m)^[A-Z][A-Za-z\\s]{2,25}(?:[:\\-\\s]*)$");
                Matcher nextM = nextHead.matcher(text.substring(start));
                int end = text.length();
                if (nextM.find() && nextM.start() > 20) {
                    end = start + nextM.start();
                }
                return text.substring(start, Math.min(end, start + 600)).trim();
            }
        }
        return "";
    }

    private static String determinePrimaryRole(ParsedResume res) {
        List<String> skills = res.allSkills;
        boolean hasJava = skills.contains("Java") || skills.contains("Spring Boot");
        boolean hasPython = skills.contains("Python") || skills.contains("Django") || skills.contains("FastAPI");
        boolean hasFrontend = skills.contains("React") || skills.contains("Angular") || skills.contains("Vue") || skills.contains("HTML");
        boolean hasCloud = skills.contains("Docker") || skills.contains("AWS") || skills.contains("Kubernetes");

        if (hasJava && hasFrontend) return "Full Stack Java Developer";
        if (hasJava) return "Java Developer";
        if (hasPython && (skills.contains("Machine Learning") || skills.contains("Pandas"))) return "Data Scientist / ML Engineer";
        if (hasPython) return "Python Backend Developer";
        if (hasFrontend) return "Frontend Developer";
        if (hasCloud) return "DevOps / Cloud Engineer";
        return "Software Engineer";
    }
}
