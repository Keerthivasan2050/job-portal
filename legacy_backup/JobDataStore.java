import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JobDataStore - Complete Data Access Layer
 * ==========================================
 * Handles all database operations for the Job Portal System.
 * Automatically falls back to in-memory storage if MySQL is unavailable.
 *
 * Supports:
 *  - Job CRUD (Create, Read, Update, Delete)
 *  - Application management (save, update status, query)
 *  - User management
 *  - Recruiter management
 *  - Admin statistics
 */
public class JobDataStore {

    // ============================================================
    // DATA MODELS
    // ============================================================

    /**
     * Represents a single job posting.
     */
    public static class JobRecord {
        public int jobId;
        public String jobTitle;
        public String companyName;
        public String description;
        public String skillsRequired;
        public String qualification;
        public String experienceRequired;
        public String salary;
        public String location;
        public String jobType;
        public String status;
        public int recruiterUserId;

        public JobRecord(int jobId, String jobTitle, String companyName, String description,
                         String skillsRequired, String qualification, String experienceRequired,
                         String salary, String location, String jobType) {
            this.jobId = jobId;
            this.jobTitle = jobTitle;
            this.companyName = companyName;
            this.description = description;
            this.skillsRequired = skillsRequired;
            this.qualification = qualification;
            this.experienceRequired = experienceRequired;
            this.salary = salary;
            this.location = location;
            this.jobType = jobType;
            this.status = "OPEN";
            this.recruiterUserId = -1;
        }
    }

    /**
     * Represents a submitted application (in-memory fallback).
     */
    public static class ApplicationRecord {
        public int applicationId;
        public int jobId;
        public int userId;
        public String fullName;
        public String email;
        public String phone;
        public String coverLetter;
        public String status;
        public String appliedAt;

        public ApplicationRecord(int applicationId, int jobId, int userId, String fullName,
                                 String email, String phone, String coverLetter) {
            this.applicationId = applicationId;
            this.jobId = jobId;
            this.userId = userId;
            this.fullName = fullName;
            this.email = email;
            this.phone = phone;
            this.coverLetter = coverLetter;
            this.status = "APPLIED";
            this.appliedAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
        }
    }

    public enum ApplicationResult {
        SAVED, DUPLICATE, JOB_UNAVAILABLE, ERROR
    }

    /**
     * Represents an uploaded and parsed resume.
     */
    public static class ResumeRecord {
        public int resumeId;
        public int userId;
        public String fileName;
        public String fileType;
        public String rawText;
        public String parsedName;
        public String parsedEmail;
        public String parsedPhone;
        public String parsedEducation;
        public String parsedExperience;
        public String parsedProjects;
        public String parsedCertifications;
        public String parsedLanguages;
        public String primaryRole;
        public String uploadedAt;

        public ResumeRecord() {}

        public ResumeRecord(int resumeId, int userId, String fileName, String fileType, String rawText,
                            String parsedName, String parsedEmail, String parsedPhone,
                            String parsedEducation, String parsedExperience, String parsedProjects,
                            String parsedCertifications, String parsedLanguages, String primaryRole) {
            this.resumeId = resumeId;
            this.userId = userId;
            this.fileName = fileName;
            this.fileType = fileType;
            this.rawText = rawText;
            this.parsedName = parsedName;
            this.parsedEmail = parsedEmail;
            this.parsedPhone = parsedPhone;
            this.parsedEducation = parsedEducation;
            this.parsedExperience = parsedExperience;
            this.parsedProjects = parsedProjects;
            this.parsedCertifications = parsedCertifications;
            this.parsedLanguages = parsedLanguages;
            this.primaryRole = primaryRole != null ? primaryRole : "Software Engineer";
            this.uploadedAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
        }
    }

    /**
     * Categorized skill extracted from resume.
     */
    public static class ResumeSkillRecord {
        public int id;
        public int resumeId;
        public int userId;
        public String skillName;
        public String category;
        public String proficiency;

        public ResumeSkillRecord(int id, int resumeId, int userId, String skillName, String category, String proficiency) {
            this.id = id;
            this.resumeId = resumeId;
            this.userId = userId;
            this.skillName = skillName;
            this.category = category;
            this.proficiency = proficiency;
        }
    }

    /**
     * Smart notification / job alert record.
     */
    public static class NotificationRecord {
        public int notificationId;
        public int userId;
        public String title;
        public String message;
        public String type; // JOB_ALERT, INVITATION, APPLICATION_STATUS, INTERVIEW
        public String link;
        public boolean isRead;
        public String createdAt;

        public NotificationRecord(int notificationId, int userId, String title, String message, String type, String link) {
            this.notificationId = notificationId;
            this.userId = userId;
            this.title = title;
            this.message = message;
            this.type = type;
            this.link = link;
            this.isRead = false;
            this.createdAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
        }
    }

    /**
     * Scheduled interview record.
     */
    public static class InterviewRecord {
        public int interviewId;
        public int applicationId;
        public int jobId;
        public int userId;
        public int recruiterUserId;
        public String interviewDate;
        public String interviewType;
        public String meetingLink;
        public String notes;
        public String status; // SCHEDULED, COMPLETED, CANCELLED
        public String createdAt;
        public String jobTitle = "";
        public String companyName = "";
        public String candidateName = "";

        public InterviewRecord(int interviewId, int applicationId, int jobId, int userId, int recruiterUserId,
                               String interviewDate, String interviewType, String meetingLink, String notes) {
            this.interviewId = interviewId;
            this.applicationId = applicationId;
            this.jobId = jobId;
            this.userId = userId;
            this.recruiterUserId = recruiterUserId;
            this.interviewDate = interviewDate;
            this.interviewType = interviewType;
            this.meetingLink = meetingLink;
            this.notes = notes;
            this.status = "SCHEDULED";
            this.createdAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
        }
    }

    /**
     * Job quality / scam report record.
     */
    public static class JobReportRecord {
        public int reportId;
        public int jobId;
        public int userId;
        public String reporterEmail;
        public String reason;
        public String details;
        public String status; // PENDING, REVIEWED, RESOLVED, DISMISSED
        public String createdAt;
        public String jobTitle = "";
        public String companyName = "";

        public JobReportRecord(int reportId, int jobId, int userId, String reporterEmail, String reason, String details) {
            this.reportId = reportId;
            this.jobId = jobId;
            this.userId = userId;
            this.reporterEmail = reporterEmail;
            this.reason = reason;
            this.details = details;
            this.status = "PENDING";
            this.createdAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
        }
    }

    /**
     * Recruiter job invitation to candidate.
     */
    public static class JobInvitationRecord {
        public int invitationId;
        public int jobId;
        public int recruiterUserId;
        public int seekerUserId;
        public int matchScore;
        public String message;
        public String status; // PENDING, ACCEPTED, DECLINED
        public String createdAt;
        public String jobTitle = "";
        public String companyName = "";

        public JobInvitationRecord(int invitationId, int jobId, int recruiterUserId, int seekerUserId, int matchScore, String message) {
            this.invitationId = invitationId;
            this.jobId = jobId;
            this.recruiterUserId = recruiterUserId;
            this.seekerUserId = seekerUserId;
            this.matchScore = matchScore;
            this.message = message;
            this.status = "PENDING";
            this.createdAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
        }
    }

    // ============================================================
    // IN-MEMORY STORES (fallback when MySQL is not available)
    // ============================================================

    private static final List<JobRecord> SAMPLE_JOBS = new CopyOnWriteArrayList<>();
    private static final List<ApplicationRecord> IN_MEMORY_APPLICATIONS = new CopyOnWriteArrayList<>();
    private static final Map<Integer, Map<String, String>> IN_MEMORY_USERS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Integer, Map<String, String>> IN_MEMORY_COMPANIES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<String> IN_MEMORY_SAVED_JOBS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<Integer, ResumeRecord> IN_MEMORY_RESUMES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Integer, List<ResumeSkillRecord>> IN_MEMORY_RESUME_SKILLS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<NotificationRecord> IN_MEMORY_NOTIFICATIONS = new CopyOnWriteArrayList<>();
    private static final List<InterviewRecord> IN_MEMORY_INTERVIEWS = new CopyOnWriteArrayList<>();
    private static final List<JobReportRecord> IN_MEMORY_REPORTS = new CopyOnWriteArrayList<>();
    private static final List<JobInvitationRecord> IN_MEMORY_INVITATIONS = new CopyOnWriteArrayList<>();

    private static final AtomicInteger APP_ID_COUNTER = new AtomicInteger(1);
    private static final AtomicInteger JOB_ID_COUNTER = new AtomicInteger(51);
    private static final AtomicInteger RESUME_ID_COUNTER = new AtomicInteger(1);
    private static final AtomicInteger NOTIF_ID_COUNTER = new AtomicInteger(1);
    private static final AtomicInteger INTERVIEW_ID_COUNTER = new AtomicInteger(1);
    private static final AtomicInteger REPORT_ID_COUNTER = new AtomicInteger(1);
    private static final AtomicInteger INVITATION_ID_COUNTER = new AtomicInteger(1);

    static {
        // =====================================================================
        // 50 REALISTIC SAMPLE JOBS - Technology & India-focused
        // =====================================================================

        // JAVA / SPRING BOOT
        SAMPLE_JOBS.add(new JobRecord(1, "Senior Java Developer", "TechCorp Solutions",
            "Design and build scalable microservices using Java 21 and Spring Boot. Work with Hibernate/JPA and MySQL.",
            "Java, Spring Boot, Hibernate, MySQL, Microservices", "B.E./B.Tech Computer Science", "5+ years",
            "₹15-22 LPA", "Bangalore", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(2, "Java Spring Boot Developer", "Infosys Ltd",
            "Develop RESTful APIs and backend services using Spring Boot and Hibernate ORM with MySQL database.",
            "Java, Spring Boot, Hibernate, REST API, MySQL", "B.E./B.Tech", "2-4 years",
            "₹8-14 LPA", "Pune", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(3, "Java Backend Engineer", "Wipro Technologies",
            "Build enterprise backend systems using Spring Framework, JPA, and microservices architecture.",
            "Java, Spring MVC, JPA, Hibernate, Docker", "B.E./B.Tech", "3-6 years",
            "₹10-18 LPA", "Hyderabad", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(4, "Junior Java Developer", "StartupHub India",
            "Entry-level Java developer role working with Spring Boot and MySQL. Good for freshers with project experience.",
            "Java, Spring Boot, MySQL, HTML/CSS", "B.E./B.Tech/BCA", "0-2 years",
            "₹4-6 LPA", "Chennai", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(5, "Full Stack Java Developer", "Cognizant",
            "Full stack development using Java Spring Boot backend and React/Angular frontend. CI/CD and cloud deployment.",
            "Java, Spring Boot, React, Hibernate, MySQL, AWS", "B.E./B.Tech", "3-5 years",
            "₹12-20 LPA", "Bangalore", "Full-time"));

        // FRONTEND / FULL STACK
        SAMPLE_JOBS.add(new JobRecord(6, "Frontend React Developer", "PixelCraft Studio",
            "Build modern, accessible web application UIs using React, TypeScript and Tailwind CSS.",
            "React, TypeScript, Redux, Tailwind CSS, Next.js", "B.S. or equivalent", "3+ years",
            "₹10-16 LPA", "Mumbai", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(7, "Full Stack Developer (MERN)", "CloudScale Inc",
            "Develop end-to-end features using MongoDB, Express, React and Node.js (MERN stack).",
            "Node.js, React, MongoDB, REST APIs, Docker", "B.E./B.Tech or IT", "4+ years",
            "₹12-18 LPA", "Bangalore", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(8, "Angular Developer", "HCL Technologies",
            "Develop enterprise-grade Angular applications with TypeScript, RxJS and REST API integration.",
            "Angular, TypeScript, RxJS, HTML5, CSS3", "B.E./B.Tech", "2-4 years",
            "₹8-13 LPA", "Noida", "Full-time"));

        // PYTHON / DATA
        SAMPLE_JOBS.add(new JobRecord(9, "Python Backend Developer", "DataSphere Systems",
            "Build high-performance APIs and data processing pipelines using Python, FastAPI and Django.",
            "Python, FastAPI, Django, Redis, PostgreSQL", "B.E. Software Engineering", "4+ years",
            "₹14-22 LPA", "Bangalore", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(10, "Senior Data Scientist", "AnalyticsHub",
            "Extract insights from large datasets and build ML models for production.",
            "Python, R, SQL, Machine Learning, Tableau", "M.S. Data Science or Statistics", "5+ years",
            "₹18-28 LPA", "Hyderabad", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(11, "Data Engineer", "BigData Insights",
            "Design scalable ETL pipelines and manage data warehouse solutions.",
            "Apache Spark, Snowflake, Airflow, SQL, Python", "B.E. CS or Data Engineering", "4+ years",
            "₹15-22 LPA", "Bangalore", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(12, "Machine Learning Engineer", "AI India Labs",
            "Train and deploy ML/DL models for production AI products.",
            "Python, TensorFlow, PyTorch, Scikit-learn, MLOps", "M.Tech or Ph.D CS/AI", "3+ years",
            "₹20-35 LPA", "Bangalore", "Full-time"));

        // DEVOPS / CLOUD
        SAMPLE_JOBS.add(new JobRecord(13, "DevOps Engineer", "CloudOps India",
            "Automate CI/CD pipelines, manage Kubernetes clusters and cloud infrastructure.",
            "Kubernetes, Docker, AWS, Jenkins, Terraform", "B.E./B.Tech CS", "4+ years",
            "₹14-22 LPA", "Bangalore", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(14, "Cloud Infrastructure Engineer", "Skyline Solutions",
            "Manage AWS/Azure cloud infrastructure and optimize multi-region deployments.",
            "AWS, Azure, GCP, Ansible, Python", "B.E./B.Tech CS", "4+ years",
            "₹15-25 LPA", "Mumbai", "Full-time"));

        // MOBILE
        SAMPLE_JOBS.add(new JobRecord(15, "Android Developer", "AppVibe Digital",
            "Build Android applications using Kotlin and Jetpack Compose.",
            "Kotlin, Android SDK, Jetpack Compose, Firebase, REST API", "B.E./B.Tech", "2-4 years",
            "₹8-14 LPA", "Pune", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(16, "Flutter Developer", "MobileFirst India",
            "Develop cross-platform mobile apps using Flutter and Dart for iOS and Android.",
            "Flutter, Dart, Firebase, REST APIs, State Management", "B.E./B.Tech", "2-3 years",
            "₹7-13 LPA", "Chennai", "Full-time"));

        // DATABASE
        SAMPLE_JOBS.add(new JobRecord(17, "MySQL Database Administrator", "DataVault Corp",
            "Manage MySQL databases, optimize queries, and ensure 99.99% database availability.",
            "MySQL, PostgreSQL, Query Optimization, Backup & Recovery", "B.E./B.Tech IT", "4+ years",
            "₹10-16 LPA", "Hyderabad", "Full-time"));

        // CYBERSECURITY
        SAMPLE_JOBS.add(new JobRecord(18, "Cybersecurity Specialist", "ShieldSec Networks",
            "Conduct vulnerability assessments and implement zero-trust security architecture.",
            "Network Security, Penetration Testing, SIEM, CISSP", "B.E./B.Tech Cybersecurity/CS", "4+ years",
            "₹15-25 LPA", "Delhi", "Full-time"));

        // QA / TESTING
        SAMPLE_JOBS.add(new JobRecord(19, "QA Automation Engineer", "TestPro Solutions",
            "Build automated test suites using Selenium, Java, and Cypress.",
            "Selenium, Java, Cypress, JUnit, Postman, CI/CD", "B.E./B.Tech CS", "3+ years",
            "₹8-14 LPA", "Coimbatore", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(20, "Manual QA Tester", "QualityFirst Tech",
            "Perform functional, regression, and UAT testing for web and mobile applications.",
            "Test Planning, Bug Reporting, Agile/Scrum, Jira", "B.E./B.Tech or BCA", "1-3 years",
            "₹4-8 LPA", "Chennai", "Full-time"));

        // DESIGN / UI
        SAMPLE_JOBS.add(new JobRecord(21, "UI/UX Designer", "CreativeFlow UI",
            "Design user experiences, wireframes and high-fidelity prototypes for web and mobile.",
            "Figma, User Research, Wireframing, Prototyping, Adobe XD", "B.Des or BCA", "3+ years",
            "₹8-14 LPA", "Bangalore", "Full-time"));

        // PRODUCT / MANAGEMENT
        SAMPLE_JOBS.add(new JobRecord(22, "Product Manager", "InnovateTech",
            "Drive product vision from discovery to launch across cross-functional teams.",
            "Product Strategy, Agile/Scrum, Roadmap Planning, Jira", "Bachelor's or MBA", "5+ years",
            "₹20-35 LPA", "Bangalore", "Full-time"));

        // HR / RECRUITMENT
        SAMPLE_JOBS.add(new JobRecord(23, "Technical Recruiter", "TalentAcquire Inc",
            "Source, screen and hire top-tier software engineers and tech leads.",
            "Sourcing, Candidate Screening, LinkedIn Recruiting, ATS", "B.A./B.Sc", "3+ years",
            "₹6-10 LPA", "Pune", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(24, "HR Operations Specialist", "Workforce Dynamics",
            "Manage employee onboarding, HR processes and maintain HRIS database.",
            "Onboarding, HRIS, Employee Relations, Compliance", "B.A. in HR or Management", "2+ years",
            "₹5-8 LPA", "Mumbai", "Full-time"));

        // FINANCE
        SAMPLE_JOBS.add(new JobRecord(25, "Financial Analyst", "Global Finance Partners",
            "Perform financial modelling, variance analysis and revenue forecasting.",
            "Financial Modeling, Forecasting, Excel, Valuation", "B.Sc/B.Com Finance or Economics", "3+ years",
            "₹8-14 LPA", "Mumbai", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(26, "Chartered Accountant", "Summit Accounting Group",
            "Manage corporate tax compliance, internal audit and general ledger reconciliation.",
            "Tax Audit, GAAP, Financial Statements, Tally, Quickbooks", "CA (Chartered Accountant)", "3+ years",
            "₹10-18 LPA", "Delhi", "Full-time"));

        // SALES & MARKETING
        SAMPLE_JOBS.add(new JobRecord(27, "Digital Marketing Manager", "OmniMedia Group",
            "Plan and execute multi-channel digital advertising and organic SEO growth campaigns.",
            "Google Ads, SEO, Google Analytics, Content Marketing, SEM", "B.A. Marketing", "3+ years",
            "₹8-14 LPA", "Bangalore", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(28, "Business Development Executive", "GrowthB2B India",
            "Qualify leads, execute outbound prospecting and schedule product demos.",
            "Cold Outreach, Prospecting, LinkedIn Sales Navigator, CRM", "B.A./B.Com", "1-3 years",
            "₹5-9 LPA", "Chennai", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(29, "Sales Manager (IT Products)", "CloudForce Systems",
            "Drive enterprise software sales to Fortune 500 clients in India.",
            "B2B Sales, Salesforce, Contract Negotiation, Pipeline Management", "B.E./MBA", "5+ years",
            "₹15-28 LPA", "Hyderabad", "Full-time"));

        // EMBEDDED / HARDWARE
        SAMPLE_JOBS.add(new JobRecord(30, "Embedded Systems Engineer", "RoboDynamics",
            "Develop real-time firmware for autonomous robotics and industrial automation.",
            "C, C++, RTOS, ARM Microcontrollers, CAN Bus", "B.E. Electronics/Computer Engineering", "3+ years",
            "₹8-14 LPA", "Bangalore", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(31, "IoT Developer", "SmartTech India",
            "Design and develop IoT solutions integrating hardware sensors and cloud backends.",
            "C, Python, MQTT, AWS IoT, Raspberry Pi, Arduino", "B.E. ECE/CS", "2-4 years",
            "₹6-12 LPA", "Coimbatore", "Full-time"));

        // HEALTHCARE / BIOTECH
        SAMPLE_JOBS.add(new JobRecord(32, "Healthcare IT Analyst", "CareFirst Systems",
            "Streamline EHR data integration and hospital clinical workflow systems.",
            "Epic EHR, HL7, FHIR, Medical Data Standards, SQL", "M.S. Health Informatics or CS", "4+ years",
            "₹12-20 LPA", "Hyderabad", "Full-time"));

        // CONTENT / WRITING
        SAMPLE_JOBS.add(new JobRecord(33, "Technical Writer", "DocuTech Press",
            "Author comprehensive API documentation, SDK tutorials and developer portals.",
            "Markdown, OpenAPI/Swagger, Git, Technical Communication", "B.A. English or CS", "2-4 years",
            "₹6-10 LPA", "Pune", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(34, "Content Strategist", "ContentFirst Agency",
            "Develop content strategies, manage blogs and social media presence for B2B tech brands.",
            "SEO Writing, Content Strategy, WordPress, Social Media", "B.A. Communications/Marketing", "2+ years",
            "₹5-9 LPA", "Bangalore", "Full-time"));

        // OPERATIONS / PROJECT MANAGEMENT
        SAMPLE_JOBS.add(new JobRecord(35, "Project Manager (IT)", "BuildRight Tech",
            "Manage software delivery projects using Agile/Scrum. PMP certified preferred.",
            "PMP, Jira, Agile/Scrum, Stakeholder Management, Risk Management", "B.E./MBA", "5+ years",
            "₹14-22 LPA", "Mumbai", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(36, "Scrum Master", "Agile Systems India",
            "Facilitate scrum ceremonies, remove impediments and coach teams on agile practices.",
            "Scrum, Kanban, Jira, Confluence, SAFe", "B.E./B.Tech", "4+ years",
            "₹12-20 LPA", "Bangalore", "Full-time"));

        // FRESHERS / ENTRY LEVEL
        SAMPLE_JOBS.add(new JobRecord(37, "Software Engineer Trainee", "TCS (Tata Consultancy)",
            "Fresher opportunity for BCA/B.Tech graduates. Training in Java, Spring Boot, and databases.",
            "Java, OOP, HTML/CSS, SQL (Basic)", "B.E./B.Tech/BCA (2024-2025 batch)", "Fresher (0-1 year)",
            "₹3.5-4.5 LPA", "Chennai", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(38, "Graduate Engineer Trainee", "HCL Technologies",
            "GET program for fresh graduates. Learn enterprise software development with mentorship.",
            "Java (Basic), SQL, Problem Solving, Communication", "B.E./B.Tech any branch", "Fresher",
            "₹4-5 LPA", "Noida", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(39, "Web Development Intern", "Startup India Hub",
            "6-month internship in full-stack web development. Learn React, Node.js, and MySQL.",
            "HTML, CSS, JavaScript (Basic), Willingness to learn", "B.E./B.Tech/BCA (pursuing)", "Internship",
            "₹15,000-25,000/month", "Coimbatore", "Internship"));
        SAMPLE_JOBS.add(new JobRecord(40, "Java Developer (Fresher)", "Accenture India",
            "Fresher Java developer role. Training provided in Spring Boot, Hibernate and cloud.",
            "Java, Spring Boot (Basic), MySQL (Basic), OOP Concepts", "B.E./B.Tech/MCA", "0-1 year",
            "₹4-6 LPA", "Bangalore", "Full-time"));

        // ADDITIONAL LOCATIONS
        SAMPLE_JOBS.add(new JobRecord(41, "Software Engineer", "Tech Mahindra",
            "Backend software engineering role using Java technologies and cloud platforms.",
            "Java, Spring Boot, Hibernate, AWS/Azure", "B.E./B.Tech", "2-5 years",
            "₹8-15 LPA", "Pune", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(42, "Python Developer", "Zoho Corporation",
            "Develop internal tools and web services using Python and Django/Flask.",
            "Python, Django, Flask, PostgreSQL, REST API", "B.E./B.Tech or MCA", "2-4 years",
            "₹8-14 LPA", "Chennai", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(43, "React Native Developer", "Freshworks",
            "Build cross-platform mobile apps using React Native for global markets.",
            "React Native, JavaScript, iOS, Android, Redux", "B.E./B.Tech", "2-4 years",
            "₹10-18 LPA", "Chennai", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(44, "Software Developer", "L&T Technology Services",
            "Develop software solutions for engineering and manufacturing domain clients.",
            "Java or Python, SQL, SDLC, Requirements Analysis", "B.E./B.Tech", "2-5 years",
            "₹7-13 LPA", "Mumbai", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(45, "Backend Developer", "MakeMyTrip",
            "Build highly scalable travel booking backends using Java microservices.",
            "Java, Spring Boot, Kafka, Redis, MySQL", "B.E./B.Tech CS", "3-6 years",
            "₹18-28 LPA", "Gurgaon", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(46, "Data Analyst", "Amazon India",
            "Analyze business data and build dashboards to drive strategic decisions.",
            "SQL, Python, Excel, Tableau, PowerBI, Statistics", "B.E./B.Tech or B.Sc Statistics", "2-4 years",
            "₹12-20 LPA", "Hyderabad", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(47, "Platform Engineer", "Flipkart",
            "Design and maintain platform infrastructure for high-scale e-commerce systems.",
            "Java, Go, Kubernetes, Kafka, Redis, Distributed Systems", "B.E./B.Tech CS", "4+ years",
            "₹25-40 LPA", "Bangalore", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(48, "Java Hibernate Developer", "Oracle India",
            "Develop enterprise Java applications using Hibernate ORM with Oracle and MySQL databases.",
            "Java, Hibernate, JPA, Oracle DB, MySQL, Spring Framework", "B.E./B.Tech or MCA", "3-5 years",
            "₹12-20 LPA", "Hyderabad", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(49, "Site Reliability Engineer", "Google India",
            "Ensure 99.999% availability of critical infrastructure through automation.",
            "Go, Python, Kubernetes, Distributed Systems, Monitoring", "B.E./B.Tech CS", "4+ years",
            "₹35-60 LPA", "Bangalore", "Full-time"));
        SAMPLE_JOBS.add(new JobRecord(50, "Software Architect", "Persistent Systems",
            "Design system architecture for large-scale enterprise applications.",
            "Java, Spring Boot, Microservices, System Design, Cloud", "B.E./B.Tech + M.Tech", "8+ years",
            "₹30-50 LPA", "Pune", "Full-time"));

        // Assign sample recruiter jobs to recruiter user 2
        for (JobRecord j : SAMPLE_JOBS) {
            if (j.jobId == 1 || j.jobId == 2 || j.jobId == 5) {
                j.recruiterUserId = 2;
            }
        }

        // Seed in-memory default accounts
        Map<String, String> adminUser = new ConcurrentHashMap<>();
        adminUser.put("userId", "1");
        adminUser.put("fullName", "Admin User");
        adminUser.put("email", "admin@jobportal.com");
        adminUser.put("password", "admin123");
        adminUser.put("phone", "9999999999");
        adminUser.put("userType", "ADMIN");
        adminUser.put("isActive", "true");
        adminUser.put("createdAt", "2026-01-01");
        IN_MEMORY_USERS.put(1, adminUser);

        Map<String, String> recruiterUser = new ConcurrentHashMap<>();
        recruiterUser.put("userId", "2");
        recruiterUser.put("fullName", "ABC Recruiter");
        recruiterUser.put("email", "recruit@abc.com");
        recruiterUser.put("password", "recruit123");
        recruiterUser.put("phone", "9876543210");
        recruiterUser.put("userType", "RECRUITER");
        recruiterUser.put("companyName", "TechCorp Solutions");
        recruiterUser.put("isActive", "true");
        recruiterUser.put("createdAt", "2026-01-02");
        IN_MEMORY_USERS.put(2, recruiterUser);

        Map<String, String> recCompany = new ConcurrentHashMap<>();
        recCompany.put("company_name", "TechCorp Solutions");
        recCompany.put("industry", "Information Technology");
        recCompany.put("location", "Bangalore");
        recCompany.put("website", "https://techcorp.example.com");
        recCompany.put("company_desc", "Enterprise cloud & microservices software development.");
        IN_MEMORY_COMPANIES.put(2, recCompany);

        Map<String, String> seekerUser = new ConcurrentHashMap<>();
        seekerUser.put("userId", "3");
        seekerUser.put("fullName", "Rahul Sharma");
        seekerUser.put("email", "seeker@example.com");
        seekerUser.put("password", "seeker123");
        seekerUser.put("phone", "9123456780");
        seekerUser.put("userType", "JOB_SEEKER");
        seekerUser.put("skills", "Java 21, Spring Boot, MySQL, REST APIs, Microservices");
        seekerUser.put("qualification", "B.Tech Computer Science");
        seekerUser.put("experience", "3 years");
        seekerUser.put("isActive", "true");
        seekerUser.put("createdAt", "2026-01-03");
        IN_MEMORY_USERS.put(3, seekerUser);

        IN_MEMORY_APPLICATIONS.add(new ApplicationRecord(
            APP_ID_COUNTER.getAndIncrement(),
            1, 3, "Rahul Sharma", "seeker@example.com", "9123456780",
            "Experienced Java & Spring Boot engineer eager to contribute to high-performance microservices."
        ));

        // Candidate 4: Keerthivasan (Core Showcase Candidate)
        Map<String, String> keerthiUser = new ConcurrentHashMap<>();
        keerthiUser.put("userId", "4");
        keerthiUser.put("fullName", "Keerthivasan");
        keerthiUser.put("email", "keerthi@example.com");
        keerthiUser.put("password", "keerthi123");
        keerthiUser.put("phone", "9876501234");
        keerthiUser.put("userType", "JOB_SEEKER");
        keerthiUser.put("skills", "Java, Spring Boot, MySQL, REST API, Git, Docker, Hibernate, Microservices, HTML, CSS, JavaScript");
        keerthiUser.put("qualification", "B.E. Computer Science and Engineering");
        keerthiUser.put("experience", "3 years");
        keerthiUser.put("isActive", "true");
        keerthiUser.put("createdAt", "2026-01-05");
        IN_MEMORY_USERS.put(4, keerthiUser);

        // Resume for Keerthivasan
        ResumeRecord kr = new ResumeRecord(
            RESUME_ID_COUNTER.getAndIncrement(), 4, "Keerthivasan_Resume.pdf", "PDF",
            "Keerthivasan\nFull Stack Java & Spring Boot Engineer\nBangalore, India | keerthi@example.com\nEducation: B.E. Computer Science and Engineering (CGPA 8.8)\nSkills: Java, Spring Boot, MySQL, REST API, Git, Docker, Hibernate, Microservices, HTML, CSS, JavaScript\nExperience: 3 years backend engineering building cloud microservices\nProjects: Job Portal System with Two-Way Matching Engine, High Throughput Payment Gateway\nCertifications: Oracle Certified Java Associate",
            "Keerthivasan", "keerthi@example.com", "9876501234",
            "B.E. Computer Science and Engineering", "3 years",
            "Job Portal System with Two-Way Matching Engine • E-Commerce Microservices",
            "Oracle Certified Java Associate • AWS Cloud Practitioner (In Progress)",
            "English, Tamil, Hindi", "Java Developer"
        );
        IN_MEMORY_RESUMES.put(4, kr);

        List<ResumeSkillRecord> kSkills = new CopyOnWriteArrayList<>();
        kSkills.add(new ResumeSkillRecord(1, kr.resumeId, 4, "Java", "Programming", "Advanced"));
        kSkills.add(new ResumeSkillRecord(2, kr.resumeId, 4, "MySQL", "Database", "Advanced"));
        kSkills.add(new ResumeSkillRecord(3, kr.resumeId, 4, "HTML", "Web", "Intermediate"));
        kSkills.add(new ResumeSkillRecord(4, kr.resumeId, 4, "CSS", "Web", "Intermediate"));
        kSkills.add(new ResumeSkillRecord(5, kr.resumeId, 4, "JavaScript", "Web", "Intermediate"));
        kSkills.add(new ResumeSkillRecord(6, kr.resumeId, 4, "Spring Boot", "Framework", "Advanced"));
        kSkills.add(new ResumeSkillRecord(7, kr.resumeId, 4, "Hibernate", "Framework", "Intermediate"));
        kSkills.add(new ResumeSkillRecord(8, kr.resumeId, 4, "Git", "Tools & Cloud", "Advanced"));
        kSkills.add(new ResumeSkillRecord(9, kr.resumeId, 4, "Docker", "Tools & Cloud", "Intermediate"));
        kSkills.add(new ResumeSkillRecord(10, kr.resumeId, 4, "REST API", "Architecture & Concepts", "Advanced"));
        kSkills.add(new ResumeSkillRecord(11, kr.resumeId, 4, "Microservices", "Architecture & Concepts", "Intermediate"));
        IN_MEMORY_RESUME_SKILLS.put(4, kSkills);

        // Resume for Rahul Sharma (User 3)
        ResumeRecord rr = new ResumeRecord(
            RESUME_ID_COUNTER.getAndIncrement(), 3, "Rahul_Sharma_Resume.pdf", "PDF",
            "Rahul Sharma\nJava Developer\nPune | seeker@example.com\nEducation: B.Tech Computer Science\nSkills: Java, Spring Boot, MySQL, REST API, Microservices\nExperience: 3 years",
            "Rahul Sharma", "seeker@example.com", "9123456780",
            "B.Tech Computer Science", "3 years",
            "Microservices Architecture • Payment Integration",
            "Java Standard Edition Certified", "English, Hindi", "Java Developer"
        );
        IN_MEMORY_RESUMES.put(3, rr);

        List<ResumeSkillRecord> rSkills = new CopyOnWriteArrayList<>();
        rSkills.add(new ResumeSkillRecord(12, rr.resumeId, 3, "Java", "Programming", "Advanced"));
        rSkills.add(new ResumeSkillRecord(13, rr.resumeId, 3, "MySQL", "Database", "Intermediate"));
        rSkills.add(new ResumeSkillRecord(14, rr.resumeId, 3, "Spring Boot", "Framework", "Advanced"));
        rSkills.add(new ResumeSkillRecord(15, rr.resumeId, 3, "REST API", "Architecture & Concepts", "Intermediate"));
        rSkills.add(new ResumeSkillRecord(16, rr.resumeId, 3, "Microservices", "Architecture & Concepts", "Intermediate"));
        IN_MEMORY_RESUME_SKILLS.put(3, rSkills);

        // Candidate 5: Priya Patel (Frontend Specialist)
        Map<String, String> priyaUser = new ConcurrentHashMap<>();
        priyaUser.put("userId", "5");
        priyaUser.put("fullName", "Priya Patel");
        priyaUser.put("email", "priya@example.com");
        priyaUser.put("password", "priya123");
        priyaUser.put("phone", "9823456789");
        priyaUser.put("userType", "JOB_SEEKER");
        priyaUser.put("skills", "React, TypeScript, Redux, Tailwind CSS, Next.js, HTML5, CSS3, JavaScript, Git");
        priyaUser.put("qualification", "B.Tech Information Technology");
        priyaUser.put("experience", "3 years");
        priyaUser.put("isActive", "true");
        priyaUser.put("createdAt", "2026-01-08");
        IN_MEMORY_USERS.put(5, priyaUser);

        ResumeRecord pr = new ResumeRecord(
            RESUME_ID_COUNTER.getAndIncrement(), 5, "Priya_Patel_Frontend_Resume.pdf", "PDF",
            "Priya Patel\nSenior Frontend Engineer\nBangalore | priya@example.com\nSkills: React, TypeScript, Redux, Tailwind CSS, Next.js, Git\nEducation: B.Tech IT\nExperience: 3 years building responsive enterprise web applications",
            "Priya Patel", "priya@example.com", "9823456789",
            "B.Tech Information Technology", "3 years",
            "Design System Components • SaaS Analytics Dashboard",
            "Meta Certified Frontend Developer", "English, Hindi, Gujarati", "Frontend Developer"
        );
        IN_MEMORY_RESUMES.put(5, pr);

        List<ResumeSkillRecord> pSkills = new CopyOnWriteArrayList<>();
        pSkills.add(new ResumeSkillRecord(17, pr.resumeId, 5, "React", "Web", "Advanced"));
        pSkills.add(new ResumeSkillRecord(18, pr.resumeId, 5, "TypeScript", "Programming", "Advanced"));
        pSkills.add(new ResumeSkillRecord(19, pr.resumeId, 5, "Redux", "Web", "Advanced"));
        pSkills.add(new ResumeSkillRecord(20, pr.resumeId, 5, "Tailwind CSS", "Web", "Intermediate"));
        pSkills.add(new ResumeSkillRecord(21, pr.resumeId, 5, "Next.js", "Web", "Intermediate"));
        pSkills.add(new ResumeSkillRecord(22, pr.resumeId, 5, "Git", "Tools & Cloud", "Intermediate"));
        IN_MEMORY_RESUME_SKILLS.put(5, pSkills);

        // Seed sample notifications for Keerthivasan
        IN_MEMORY_NOTIFICATIONS.add(new NotificationRecord(
            NOTIF_ID_COUNTER.getAndIncrement(), 4,
            "🔥 94% Job Match Found!",
            "Senior Java Developer at TechCorp Solutions matches 94% of your resume skills.",
            "JOB_ALERT", "/seeker/matching-jobs"
        ));
        IN_MEMORY_NOTIFICATIONS.add(new NotificationRecord(
            NOTIF_ID_COUNTER.getAndIncrement(), 4,
            "📩 Recruiter Invitation Received",
            "TechCorp Solutions discovered your profile and invited you to apply for Java Spring Boot Developer!",
            "INVITATION", "/seeker/matching-jobs"
        ));

        // Seed sample interview for Keerthivasan
        InterviewRecord sampInt = new InterviewRecord(
            INTERVIEW_ID_COUNTER.getAndIncrement(), 1, 1, 4, 2,
            "2026-10-05 14:30", "Technical Video Round (Google Meet)",
            "https://meet.google.com/abc-defg-hij",
            "System Design & Spring Boot Architecture Discussion with Lead Architect"
        );
        sampInt.jobTitle = "Senior Java Developer";
        sampInt.companyName = "TechCorp Solutions";
        sampInt.candidateName = "Keerthivasan";
        IN_MEMORY_INTERVIEWS.add(sampInt);

        // Seed sample job report
        JobReportRecord sampRep = new JobReportRecord(
            REPORT_ID_COUNTER.getAndIncrement(), 7, 4, "keerthi@example.com",
            "Incorrect information", "Listing states remote but recruiter asked for mandatory daily on-site presence."
        );
        sampRep.jobTitle = "Full Stack Developer (MERN)";
        sampRep.companyName = "CloudScale Inc";
        IN_MEMORY_REPORTS.add(sampRep);
    }

    // ============================================================
    // DATABASE INITIALIZATION
    // ============================================================

    /**
     * Initialize MySQL database tables and seed sample data if MySQL is available.
     */
    public static void initializeDatabaseIfAvailable() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) return;

            try (Statement st = conn.createStatement()) {
                // Companies table
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS companies (
                        company_id INT AUTO_INCREMENT PRIMARY KEY,
                        company_name VARCHAR(150) NOT NULL,
                        industry VARCHAR(100),
                        location VARCHAR(100),
                        website VARCHAR(255),
                        company_desc TEXT,
                        recruiter_user_id INT DEFAULT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Users table
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        user_id INT AUTO_INCREMENT PRIMARY KEY,
                        full_name VARCHAR(100) NOT NULL,
                        email VARCHAR(100) NOT NULL UNIQUE,
                        password VARCHAR(255) NOT NULL,
                        phone VARCHAR(20),
                        skills TEXT,
                        qualification VARCHAR(200),
                        experience VARCHAR(100),
                        user_type ENUM('JOB_SEEKER','RECRUITER','ADMIN') DEFAULT 'JOB_SEEKER',
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Jobs table with recruiter_user_id
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS jobs (
                        job_id INT AUTO_INCREMENT PRIMARY KEY,
                        company_id INT NOT NULL,
                        recruiter_user_id INT DEFAULT NULL,
                        job_title VARCHAR(150) NOT NULL,
                        description TEXT NOT NULL,
                        skills_required TEXT,
                        qualification VARCHAR(200),
                        experience_required VARCHAR(80),
                        salary VARCHAR(100),
                        location VARCHAR(100),
                        job_type VARCHAR(50) DEFAULT 'Full-time',
                        status ENUM('OPEN','CLOSED') DEFAULT 'OPEN',
                        posted_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (company_id) REFERENCES companies(company_id) ON DELETE CASCADE
                    )
                """);

                // Applications table with guest details and one application per user/job
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS applications (
                        application_id INT AUTO_INCREMENT PRIMARY KEY,
                        job_id INT NOT NULL,
                        user_id INT DEFAULT NULL,
                        full_name VARCHAR(100),
                        email VARCHAR(100),
                        phone VARCHAR(30),
                        cover_letter TEXT,
                        application_status ENUM('APPLIED','SHORTLISTED','REJECTED','ACCEPTED','WITHDRAWN') DEFAULT 'APPLIED',
                        applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uq_applications_job_user (job_id, user_id),
                        FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                """);

                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS saved_jobs (
                        user_id INT NOT NULL,
                        job_id INT NOT NULL,
                        saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (user_id, job_id),
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                        FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE
                    )
                """);

                // Resumes table
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS resumes (
                        resume_id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        file_name VARCHAR(255) NOT NULL,
                        file_type VARCHAR(50) DEFAULT 'PDF',
                        raw_text LONGTEXT,
                        parsed_name VARCHAR(150),
                        parsed_email VARCHAR(150),
                        parsed_phone VARCHAR(50),
                        parsed_education TEXT,
                        parsed_experience TEXT,
                        parsed_projects TEXT,
                        parsed_certifications TEXT,
                        parsed_languages TEXT,
                        primary_role VARCHAR(100) DEFAULT 'Software Engineer',
                        uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                """);

                // Resume skills table
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS resume_skills (
                        skill_id INT AUTO_INCREMENT PRIMARY KEY,
                        resume_id INT NOT NULL,
                        user_id INT NOT NULL,
                        skill_name VARCHAR(100) NOT NULL,
                        category VARCHAR(50) NOT NULL,
                        proficiency VARCHAR(50) DEFAULT 'Intermediate',
                        FOREIGN KEY (resume_id) REFERENCES resumes(resume_id) ON DELETE CASCADE,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                """);

                // Notifications table
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        notification_id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        message TEXT NOT NULL,
                        type VARCHAR(50) DEFAULT 'JOB_ALERT',
                        link VARCHAR(255) DEFAULT '/seeker/matching-jobs',
                        is_read BOOLEAN DEFAULT FALSE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                """);

                // Interviews table
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS interviews (
                        interview_id INT AUTO_INCREMENT PRIMARY KEY,
                        application_id INT NOT NULL,
                        job_id INT NOT NULL,
                        user_id INT NOT NULL,
                        recruiter_user_id INT NOT NULL,
                        interview_date VARCHAR(100) NOT NULL,
                        interview_type VARCHAR(50) DEFAULT 'Online Video',
                        meeting_link VARCHAR(255),
                        notes TEXT,
                        status ENUM('SCHEDULED','COMPLETED','CANCELLED') DEFAULT 'SCHEDULED',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                """);

                // Job reports table
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS job_reports (
                        report_id INT AUTO_INCREMENT PRIMARY KEY,
                        job_id INT NOT NULL,
                        user_id INT DEFAULT NULL,
                        reporter_email VARCHAR(150),
                        reason VARCHAR(100) NOT NULL,
                        details TEXT,
                        status ENUM('PENDING','REVIEWED','RESOLVED','DISMISSED') DEFAULT 'PENDING',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE
                    )
                """);

                // Job invitations table
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS job_invitations (
                        invitation_id INT AUTO_INCREMENT PRIMARY KEY,
                        job_id INT NOT NULL,
                        recruiter_user_id INT NOT NULL,
                        seeker_user_id INT NOT NULL,
                        match_score INT DEFAULT 0,
                        message TEXT,
                        status ENUM('PENDING','ACCEPTED','DECLINED') DEFAULT 'PENDING',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE,
                        FOREIGN KEY (seeker_user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                """);
            }

            // Idempotent migrations for installations created by older versions.
            try (Statement st = conn.createStatement()) {
                try { st.executeUpdate("ALTER TABLE companies MODIFY user_id INT DEFAULT NULL"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE jobs MODIFY job_type VARCHAR(50) DEFAULT 'Full-time'"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE jobs MODIFY status VARCHAR(50) DEFAULT 'OPEN'"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes MODIFY file_path VARCHAR(500) DEFAULT NULL"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes ADD COLUMN raw_text LONGTEXT"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes ADD COLUMN file_type VARCHAR(50) DEFAULT 'PDF'"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes ADD COLUMN parsed_name VARCHAR(150)"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes ADD COLUMN parsed_email VARCHAR(150)"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes ADD COLUMN parsed_phone VARCHAR(50)"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes ADD COLUMN parsed_education TEXT"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes ADD COLUMN parsed_experience TEXT"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes ADD COLUMN parsed_projects TEXT"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes ADD COLUMN parsed_certifications TEXT"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes ADD COLUMN parsed_languages TEXT"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE resumes ADD COLUMN primary_role VARCHAR(100) DEFAULT 'Software Engineer'"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE notifications ADD COLUMN title VARCHAR(200) NOT NULL DEFAULT 'Alert'"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE notifications ADD COLUMN type VARCHAR(50) DEFAULT 'JOB_ALERT'"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE notifications ADD COLUMN link VARCHAR(255) DEFAULT '/seeker/matching-jobs'"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE notifications MODIFY message TEXT"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE interviews ADD COLUMN job_id INT DEFAULT NULL"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE interviews ADD COLUMN user_id INT DEFAULT NULL"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE interviews ADD COLUMN recruiter_user_id INT DEFAULT NULL"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE interviews ADD COLUMN interview_type VARCHAR(50) DEFAULT 'Online Video'"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE interviews ADD COLUMN notes TEXT"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE interviews MODIFY interview_date VARCHAR(100)"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE interviews MODIFY interview_time TIME DEFAULT NULL"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE interviews MODIFY interview_mode VARCHAR(50) DEFAULT 'ONLINE'"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE jobs ADD COLUMN recruiter_user_id INT DEFAULT NULL"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE companies ADD COLUMN recruiter_user_id INT DEFAULT NULL"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE companies ADD COLUMN industry VARCHAR(100)"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE companies ADD COLUMN location VARCHAR(100)"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE companies ADD COLUMN website VARCHAR(255)"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE companies ADD COLUMN company_desc TEXT"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE users ADD COLUMN skills TEXT"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE users ADD COLUMN qualification VARCHAR(200)"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE users ADD COLUMN experience VARCHAR(100)"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE users ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE applications ADD COLUMN full_name VARCHAR(100)"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE applications ADD COLUMN email VARCHAR(100)"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE applications ADD COLUMN phone VARCHAR(30)"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE applications MODIFY application_status ENUM('APPLIED','SHORTLISTED','REJECTED','ACCEPTED','WITHDRAWN') DEFAULT 'APPLIED'"); }
                catch (Exception ignored) {}
                try { st.executeUpdate("CREATE UNIQUE INDEX uq_applications_job_user ON applications(job_id, user_id)"); }
                catch (Exception ignored) {}
            }

            // Seed default admin user
            try (PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM users WHERE user_type='ADMIN' LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO users (full_name,email,password,phone,user_type) VALUES (?,?,?,?,?)")) {
                        ins.setString(1, "Admin User");
                        ins.setString(2, "admin@jobportal.com");
                        ins.setString(3, "admin123");
                        ins.setString(4, "9999999999");
                        ins.setString(5, "ADMIN");
                        ins.executeUpdate();
                    }
                }
            }

            // Seed default recruiter user
            int recruiterId = -1;
            try (PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM users WHERE email='recruit@abc.com' LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    recruiterId = rs.getInt("user_id");
                } else {
                    try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO users (full_name,email,password,phone,user_type) VALUES (?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                        ins.setString(1, "ABC Recruiter");
                        ins.setString(2, "recruit@abc.com");
                        ins.setString(3, "recruit123");
                        ins.setString(4, "9876543210");
                        ins.setString(5, "RECRUITER");
                        ins.executeUpdate();
                        ResultSet keys = ins.getGeneratedKeys();
                        if (keys.next()) recruiterId = keys.getInt(1);
                    }
                }
            }

            if (recruiterId > 0) {
                // Ensure company profile for recruiter
                try (PreparedStatement cps = conn.prepareStatement(
                    "SELECT company_id FROM companies WHERE recruiter_user_id=? LIMIT 1")) {
                    cps.setInt(1, recruiterId);
                    ResultSet crs = cps.executeQuery();
                    if (!crs.next()) {
                        try (PreparedStatement ins = conn.prepareStatement("""
                            INSERT INTO companies (company_name, industry, location, website, company_desc, recruiter_user_id)
                            VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                            ins.setString(1, "TechCorp Solutions");
                            ins.setString(2, "Information Technology");
                            ins.setString(3, "Bangalore");
                            ins.setString(4, "https://techcorp.example.com");
                            ins.setString(5, "Enterprise cloud & microservices software development.");
                            ins.setInt(6, recruiterId);
                            ins.executeUpdate();
                        }
                    }
                }
                // Associate sample jobs 1, 2, 5 with recruiter
                try (PreparedStatement jps = conn.prepareStatement(
                    "UPDATE jobs SET recruiter_user_id=? WHERE job_id IN (1, 2, 5)")) {
                    jps.setInt(1, recruiterId);
                    jps.executeUpdate();
                }
            }

            // Seed default job seeker user
            int seekerId = -1;
            try (PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM users WHERE email='seeker@example.com' LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    seekerId = rs.getInt("user_id");
                } else {
                    try (PreparedStatement ins = conn.prepareStatement("""
                        INSERT INTO users (full_name,email,password,phone,user_type,skills,qualification,experience)
                        VALUES (?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                        ins.setString(1, "Rahul Sharma");
                        ins.setString(2, "seeker@example.com");
                        ins.setString(3, "seeker123");
                        ins.setString(4, "9123456780");
                        ins.setString(5, "JOB_SEEKER");
                        ins.setString(6, "Java 21, Spring Boot, MySQL, REST APIs, Microservices");
                        ins.setString(7, "B.Tech Computer Science");
                        ins.setString(8, "3 years");
                        ins.executeUpdate();
                        ResultSet keys = ins.getGeneratedKeys();
                        if (keys.next()) seekerId = keys.getInt(1);
                    }
                }
            }

            // Seed sample application if none exist
            if (seekerId > 0) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM applications WHERE user_id=?")) {
                    ps.setInt(1, seekerId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next() && rs.getInt(1) == 0) {
                        try (PreparedStatement ins = conn.prepareStatement("""
                            INSERT INTO applications (job_id, user_id, full_name, email, phone, cover_letter, application_status)
                            VALUES (1, ?, 'Rahul Sharma', 'seeker@example.com', '9123456780',
                                    'Experienced Java & Spring Boot engineer eager to contribute to high-performance microservices.', 'APPLIED')
                        """)) {
                            ins.setInt(1, seekerId);
                            ins.executeUpdate();
                        }
                    }
                }
            }

            // Seed Keerthivasan user in MySQL if not present
            int keerthiId = -1;
            try (PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM users WHERE email='keerthi@example.com' LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    keerthiId = rs.getInt("user_id");
                } else {
                    try (PreparedStatement ins = conn.prepareStatement("""
                        INSERT INTO users (full_name,email,password,phone,user_type,skills,qualification,experience)
                        VALUES (?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                        ins.setString(1, "Keerthivasan");
                        ins.setString(2, "keerthi@example.com");
                        ins.setString(3, "keerthi123");
                        ins.setString(4, "9876501234");
                        ins.setString(5, "JOB_SEEKER");
                        ins.setString(6, "Java, Spring Boot, MySQL, REST API, Git, Docker, Hibernate, Microservices, HTML, CSS, JavaScript");
                        ins.setString(7, "B.E. Computer Science and Engineering");
                        ins.setString(8, "3 years");
                        ins.executeUpdate();
                        ResultSet keys = ins.getGeneratedKeys();
                        if (keys.next()) keerthiId = keys.getInt(1);
                    }
                }
            }

            // Seed Keerthivasan resume in MySQL if not present
            if (keerthiId > 0) {
                int resumeId = -1;
                try (PreparedStatement ps = conn.prepareStatement("SELECT resume_id FROM resumes WHERE user_id=? LIMIT 1")) {
                    ps.setInt(1, keerthiId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        resumeId = rs.getInt("resume_id");
                    } else {
                        try (PreparedStatement ins = conn.prepareStatement("""
                            INSERT INTO resumes (user_id, file_name, file_type, raw_text, parsed_name, parsed_email, parsed_phone,
                                parsed_education, parsed_experience, parsed_projects, parsed_certifications, parsed_languages, primary_role)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                            ins.setInt(1, keerthiId);
                            ins.setString(2, "Keerthivasan_Resume.pdf");
                            ins.setString(3, "PDF");
                            ins.setString(4, "Keerthivasan Resume. Java, Spring Boot, MySQL, REST API, Microservices, Git, Docker, Hibernate.");
                            ins.setString(5, "Keerthivasan");
                            ins.setString(6, "keerthi@example.com");
                            ins.setString(7, "9876501234");
                            ins.setString(8, "B.E. Computer Science and Engineering");
                            ins.setString(9, "3 years");
                            ins.setString(10, "Job Portal System with Two-Way Matching Engine • E-Commerce Microservices");
                            ins.setString(11, "Oracle Certified Java Associate");
                            ins.setString(12, "English, Tamil, Hindi");
                            ins.setString(13, "Java Developer");
                            ins.executeUpdate();
                            ResultSet keys = ins.getGeneratedKeys();
                            if (keys.next()) resumeId = keys.getInt(1);
                        }
                    }
                }

                // Seed resume skills
                if (resumeId > 0) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM resume_skills WHERE resume_id=?")) {
                        ps.setInt(1, resumeId);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next() && rs.getInt(1) == 0) {
                            String[][] skillsData = {
                                {"Java", "Programming", "Advanced"},
                                {"MySQL", "Database", "Advanced"},
                                {"HTML", "Web", "Intermediate"},
                                {"CSS", "Web", "Intermediate"},
                                {"JavaScript", "Web", "Intermediate"},
                                {"Spring Boot", "Framework", "Advanced"},
                                {"Hibernate", "Framework", "Intermediate"},
                                {"Git", "Tools & Cloud", "Advanced"},
                                {"Docker", "Tools & Cloud", "Intermediate"},
                                {"REST API", "Architecture & Concepts", "Advanced"},
                                {"Microservices", "Architecture & Concepts", "Intermediate"}
                            };
                            for (String[] sk : skillsData) {
                                try (PreparedStatement ins = conn.prepareStatement("""
                                    INSERT INTO resume_skills (resume_id, user_id, skill_name, category, proficiency)
                                    VALUES (?, ?, ?, ?, ?)
                                """)) {
                                    ins.setInt(1, resumeId);
                                    ins.setInt(2, keerthiId);
                                    ins.setString(3, sk[0]);
                                    ins.setString(4, sk[1]);
                                    ins.setString(5, sk[2]);
                                    ins.executeUpdate();
                                }
                            }
                        }
                    }
                }
            }

            // Seed 50 jobs if empty
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM jobs")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    for (JobRecord job : SAMPLE_JOBS) {
                        int companyId = getOrCreateCompany(conn, job.companyName, job.location);
                        String insertJobSql = """
                            INSERT INTO jobs (job_id, company_id, job_title, description, skills_required,
                                qualification, experience_required, salary, location, job_type, status)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')
                        """;
                        try (PreparedStatement jps = conn.prepareStatement(insertJobSql)) {
                            jps.setInt(1, job.jobId);
                            jps.setInt(2, companyId);
                            jps.setString(3, job.jobTitle);
                            jps.setString(4, job.description);
                            jps.setString(5, job.skillsRequired);
                            jps.setString(6, job.qualification);
                            jps.setString(7, job.experienceRequired);
                            jps.setString(8, job.salary);
                            jps.setString(9, job.location);
                            jps.setString(10, job.jobType);
                            jps.executeUpdate();
                        }
                    }
                    System.out.println("✅ Seeded 50 sample jobs into MySQL.");
                }
            }

            System.out.println("✅ MySQL database initialized successfully.");

        } catch (Exception e) {
            System.out.println("⚠️  MySQL not available. Using in-memory store. (" + e.getMessage() + ")");
        }
    }

    private static int getOrCreateCompany(Connection conn, String companyName, String location) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT company_id FROM companies WHERE company_name = ?")) {
            ps.setString(1, companyName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("company_id");
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO companies (company_name, location) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, companyName);
            ps.setString(2, location);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return 1;
    }

    // ============================================================
    // JOB QUERIES
    // ============================================================

    /**
     * Search jobs by keyword and/or location.
     */
    public static List<JobRecord> getJobs(String keyword, String location) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            StringBuilder sql = new StringBuilder("""
                SELECT j.job_id, j.job_title, j.description, j.skills_required,
                       j.qualification, j.experience_required, j.salary, j.location,
                       j.job_type, j.status, j.recruiter_user_id, c.company_name
                FROM jobs j
                INNER JOIN companies c ON j.company_id = c.company_id
                WHERE j.status = 'OPEN'
            """);
            List<String> params = new ArrayList<>();

            if (keyword != null && !keyword.trim().isEmpty()) {
                sql.append(" AND (LOWER(j.job_title) LIKE ? OR LOWER(j.description) LIKE ? OR LOWER(j.skills_required) LIKE ? OR LOWER(c.company_name) LIKE ?)");
                String kw = "%" + keyword.trim().toLowerCase() + "%";
                params.add(kw); params.add(kw); params.add(kw); params.add(kw);
            }
            if (location != null && !location.trim().isEmpty()) {
                sql.append(" AND LOWER(j.location) LIKE ?");
                params.add("%" + location.trim().toLowerCase() + "%");
            }
            sql.append(" ORDER BY j.job_id DESC");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) ps.setString(i + 1, params.get(i));
                ResultSet rs = ps.executeQuery();
                List<JobRecord> result = new ArrayList<>();
                while (rs.next()) result.add(mapJobRecord(rs));
                return result;
            }
        } catch (Exception ignored) {}

        String kw = (keyword != null) ? keyword.trim().toLowerCase() : "";
        String loc = (location != null) ? location.trim().toLowerCase() : "";
        List<JobRecord> filtered = new ArrayList<>();
        for (JobRecord job : SAMPLE_JOBS) {
            boolean matchesKw = kw.isEmpty() || job.jobTitle.toLowerCase().contains(kw)
                || job.companyName.toLowerCase().contains(kw)
                || job.description.toLowerCase().contains(kw)
                || job.skillsRequired.toLowerCase().contains(kw);
            boolean matchesLoc = loc.isEmpty() || job.location.toLowerCase().contains(loc);
            if ("OPEN".equals(job.status) && matchesKw && matchesLoc) filtered.add(job);
        }
        return filtered;
    }

    public static List<JobRecord> getAllJobs() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT j.job_id, j.job_title, j.description, j.skills_required,
                        j.qualification, j.experience_required, j.salary, j.location,
                        j.job_type, j.status, j.recruiter_user_id, c.company_name
                 FROM jobs j INNER JOIN companies c ON j.company_id=c.company_id
                 ORDER BY j.job_id DESC
             """)) {
            List<JobRecord> result = new ArrayList<>();
            while (rs.next()) result.add(mapJobRecord(rs));
            return result;
        } catch (Exception ignored) {}
        return new ArrayList<>(SAMPLE_JOBS);
    }

    public static JobRecord getJobByIdForRecruiter(int jobId, int recruiterUserId) {
        JobRecord job = getJobById(jobId);
        return job != null && job.recruiterUserId == recruiterUserId ? job : null;
    }

    /**
     * Get single job by ID.
     */
    public static JobRecord getJobById(int jobId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT j.job_id, j.job_title, j.description, j.skills_required,
                       j.qualification, j.experience_required, j.salary, j.location,
                       j.job_type, j.status, j.recruiter_user_id, c.company_name
                FROM jobs j
                INNER JOIN companies c ON j.company_id = c.company_id
                WHERE j.job_id = ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jobId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return mapJobRecord(rs);
            }
        } catch (Exception ignored) {}
        for (JobRecord job : SAMPLE_JOBS) {
            if (job.jobId == jobId) return job;
        }
        return null;
    }

    /**
     * Get all jobs posted by a specific recruiter.
     */
    public static List<JobRecord> getJobsByRecruiter(int recruiterUserId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT j.job_id, j.job_title, j.description, j.skills_required,
                       j.qualification, j.experience_required, j.salary, j.location,
                       j.job_type, j.status, j.recruiter_user_id, c.company_name
                FROM jobs j
                INNER JOIN companies c ON j.company_id = c.company_id
                WHERE j.recruiter_user_id = ?
                ORDER BY j.job_id DESC
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, recruiterUserId);
                ResultSet rs = ps.executeQuery();
                List<JobRecord> result = new ArrayList<>();
                while (rs.next()) result.add(mapJobRecord(rs));
                if (!result.isEmpty()) return result;
            }
        } catch (Exception ignored) {}
        List<JobRecord> fallback = new ArrayList<>();
        for (JobRecord j : SAMPLE_JOBS) {
            if (j.recruiterUserId == recruiterUserId || (recruiterUserId == 2 && (j.jobId == 1 || j.jobId == 2 || j.jobId == 5))) {
                fallback.add(j);
            }
        }
        return fallback;
    }

    /**
     * Count applications for a given job.
     */
    public static int getApplicationCountForJob(int jobId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM applications WHERE job_id=?")) {
            ps.setInt(1, jobId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception ignored) {}
        int count = 0;
        for (ApplicationRecord a : IN_MEMORY_APPLICATIONS) {
            if (a.jobId == jobId) count++;
        }
        return count;
    }

    private static JobRecord mapJobRecord(ResultSet rs) throws SQLException {
        JobRecord jr = new JobRecord(
            rs.getInt("job_id"),
            rs.getString("job_title"),
            rs.getString("company_name"),
            rs.getString("description"),
            rs.getString("skills_required"),
            rs.getString("qualification"),
            rs.getString("experience_required"),
            rs.getString("salary"),
            rs.getString("location"),
            rs.getString("job_type")
        );
        jr.status = rs.getString("status");
        try { jr.recruiterUserId = rs.getInt("recruiter_user_id"); } catch (Exception ignored) {}
        return jr;
    }

    // ============================================================
    // JOB CRUD (Recruiter)
    // ============================================================

    /**
     * Add a new job posted by a recruiter.
     */
    public static boolean addJob(int recruiterUserId, String companyName, String jobTitle,
                                  String description, String skillsRequired, String qualification,
                                  String experience, String salary, String location, String jobType) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get or create company
            int companyId = getOrCreateCompanyForRecruiter(conn, companyName, location, recruiterUserId);

            String sql = """
                INSERT INTO jobs (company_id, recruiter_user_id, job_title, description, skills_required,
                    qualification, experience_required, salary, location, job_type, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, companyId);
                ps.setInt(2, recruiterUserId);
                ps.setString(3, jobTitle);
                ps.setString(4, description);
                ps.setString(5, skillsRequired);
                ps.setString(6, qualification);
                ps.setString(7, experience);
                ps.setString(8, salary);
                ps.setString(9, location);
                ps.setString(10, jobType);
                ps.executeUpdate();
                int newJobId = -1;
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) newJobId = keys.getInt(1);
                JobRecord newJob = new JobRecord(newJobId, jobTitle, companyName, description,
                    skillsRequired, qualification, experience, salary, location, jobType);
                newJob.recruiterUserId = recruiterUserId;
                triggerSmartJobAlerts(newJob);
                return true;
            }
        } catch (Exception e) {
            System.out.println("addJob error: " + e.getMessage());
            // In-memory fallback
            int newId = JOB_ID_COUNTER.getAndIncrement();
            JobRecord jr = new JobRecord(newId, jobTitle, companyName.isEmpty() ? "My Company" : companyName,
                description, skillsRequired, qualification, experience, salary, location, jobType);
            jr.recruiterUserId = recruiterUserId;
            SAMPLE_JOBS.add(jr);
            triggerSmartJobAlerts(jr);
            return true;
        }
    }

    /**
     * Update an existing job.
     */
    public static boolean updateJob(int jobId, int recruiterUserId, String jobTitle, String description,
                                     String skillsRequired, String qualification, String experience,
                                     String salary, String location, String jobType, String status) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                UPDATE jobs SET job_title=?, description=?, skills_required=?, qualification=?,
                    experience_required=?, salary=?, location=?, job_type=?, status=?
                WHERE job_id=? AND recruiter_user_id=?
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, jobTitle);
                ps.setString(2, description);
                ps.setString(3, skillsRequired);
                ps.setString(4, qualification);
                ps.setString(5, experience);
                ps.setString(6, salary);
                ps.setString(7, location);
                ps.setString(8, jobType);
                ps.setString(9, status);
                ps.setInt(10, jobId);
                ps.setInt(11, recruiterUserId);
                return ps.executeUpdate() == 1;
            }
        } catch (Exception e) {
            for (JobRecord job : SAMPLE_JOBS) {
                if (job.jobId == jobId && job.recruiterUserId == recruiterUserId) {
                    job.jobTitle = jobTitle; job.description = description;
                    job.skillsRequired = skillsRequired; job.qualification = qualification;
                    job.experienceRequired = experience; job.salary = salary;
                    job.location = location; job.jobType = jobType; job.status = status;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Delete a job owned by the recruiter.
     */
    public static boolean deleteJob(int jobId, int recruiterUserId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM jobs WHERE job_id=? AND recruiter_user_id=?")) {
            ps.setInt(1, jobId);
            ps.setInt(2, recruiterUserId);
            return ps.executeUpdate() == 1;
        } catch (Exception ignored) {}
        boolean removed = SAMPLE_JOBS.removeIf(j -> j.jobId == jobId && j.recruiterUserId == recruiterUserId);
        if (removed) {
            IN_MEMORY_APPLICATIONS.removeIf(a -> a.jobId == jobId);
            IN_MEMORY_SAVED_JOBS.removeIf(key -> key.endsWith(":" + jobId));
        }
        return removed;
    }

    /**
     * Delete a job (admin delete - any job).
     */
    public static boolean deleteJobAdmin(int jobId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM applications WHERE job_id=?")) {
                ps.setInt(1, jobId); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM jobs WHERE job_id=?")) {
                ps.setInt(1, jobId); ps.executeUpdate();
            }
            return true;
        } catch (Exception ignored) {}
        SAMPLE_JOBS.removeIf(j -> j.jobId == jobId);
        IN_MEMORY_APPLICATIONS.removeIf(a -> a.jobId == jobId);
        return true;
    }

    /**
     * Recruiter toggles status of their job (OPEN / CLOSED).
     */
    public static boolean setJobStatusRecruiter(int jobId, int recruiterUserId, String status) {
        if (!Set.of("OPEN", "CLOSED").contains(status)) return false;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE jobs SET status=? WHERE job_id=? AND recruiter_user_id=?")) {
            ps.setString(1, status); ps.setInt(2, jobId); ps.setInt(3, recruiterUserId);
            if (ps.executeUpdate() == 1) return true;
        } catch (Exception ignored) {}
        JobRecord job = getJobById(jobId);
        if (job != null && (job.recruiterUserId == recruiterUserId || recruiterUserId == 2)) {
            job.status = status;
            return true;
        }
        return false;
    }

    private static int getOrCreateCompanyForRecruiter(Connection conn, String companyName, String location, int recruiterUserId) throws SQLException {
        if (companyName == null || companyName.isEmpty()) companyName = "My Company";
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT company_id FROM companies WHERE recruiter_user_id=? LIMIT 1")) {
            ps.setInt(1, recruiterUserId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("company_id");
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT company_id FROM companies WHERE company_name=? LIMIT 1")) {
            ps.setString(1, companyName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("company_id");
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO companies (company_name, location, recruiter_user_id) VALUES (?,?,?)",
            Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, companyName);
            ps.setString(2, location);
            ps.setInt(3, recruiterUserId);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return 1;
    }

    // ============================================================
    // APPLICATION MANAGEMENT
    // ============================================================

    /**
     * Save a new application with full details.
     */
    public static ApplicationResult saveApplicationFull(int jobId, int userId, String fullName,
                                                         String email, String phone, String coverLetter) {
        JobRecord job = getJobById(jobId);
        if (job == null || !"OPEN".equals(job.status)) return ApplicationResult.JOB_UNAVAILABLE;

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (userId == -1) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM users WHERE email=?")) {
                    ps.setString(1, email);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) userId = rs.getInt("user_id");
                }
                if (userId == -1) {
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users (full_name,email,password,phone,user_type) VALUES (?,?,'guest123',?,'JOB_SEEKER')",
                        Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, fullName); ps.setString(2, email); ps.setString(3, phone);
                        ps.executeUpdate();
                        ResultSet keys = ps.getGeneratedKeys();
                        if (keys.next()) userId = keys.getInt(1);
                    }
                }
            }
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM applications WHERE job_id=? AND user_id=?")) {
                check.setInt(1, jobId); check.setInt(2, userId);
                if (check.executeQuery().next()) return ApplicationResult.DUPLICATE;
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO applications (job_id,user_id,full_name,email,phone,cover_letter,application_status)
                    SELECT ?,?,?,?,?,?,'APPLIED' FROM jobs WHERE job_id=? AND status='OPEN'
                """)) {
                ps.setInt(1, jobId); ps.setInt(2, userId); ps.setString(3, fullName);
                ps.setString(4, email); ps.setString(5, phone); ps.setString(6, coverLetter);
                ps.setInt(7, jobId);
                return ps.executeUpdate() == 1 ? ApplicationResult.SAVED : ApplicationResult.JOB_UNAVAILABLE;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            return ApplicationResult.DUPLICATE;
        } catch (Exception ignored) {}

        for (ApplicationRecord app : IN_MEMORY_APPLICATIONS) {
            if (app.jobId == jobId && (app.userId == userId || app.email.equalsIgnoreCase(email))) {
                return ApplicationResult.DUPLICATE;
            }
        }
        int appId = APP_ID_COUNTER.getAndIncrement();
        IN_MEMORY_APPLICATIONS.add(new ApplicationRecord(appId, jobId, userId, fullName, email, phone, coverLetter));
        return ApplicationResult.SAVED;
    }

    public static boolean saveApplication(int jobId, String fullName, String email, String phone, String coverLetter) {
        return saveApplicationFull(jobId, -1, fullName, email, phone, coverLetter) == ApplicationResult.SAVED;
    }

    public static boolean withdrawApplication(int applicationId, int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE applications SET application_status='WITHDRAWN' WHERE application_id=? AND user_id=? AND application_status IN ('APPLIED', 'SHORTLISTED')")) {
            ps.setInt(1, applicationId); ps.setInt(2, userId);
            if (ps.executeUpdate() == 1) return true;
        } catch (Exception ignored) {}
        for (ApplicationRecord app : IN_MEMORY_APPLICATIONS) {
            if (app.applicationId == applicationId && app.userId == userId && ("APPLIED".equals(app.status) || "SHORTLISTED".equals(app.status))) {
                app.status = "WITHDRAWN";
                return true;
            }
        }
        return false;
    }

    /**
     * Get applications by a job seeker (by userId or email).
     */
    public static List<Map<String, String>> getApplicationsByUser(int userId, String email) {
        List<Map<String, String>> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT a.application_id, a.job_id, a.application_status, a.applied_at,
                       j.job_title, j.location, c.company_name
                FROM applications a
                INNER JOIN jobs j ON a.job_id = j.job_id
                INNER JOIN companies c ON j.company_id = c.company_id
                WHERE a.user_id = ? OR a.email = ?
                ORDER BY a.applied_at DESC
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setString(2, email);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("applicationId", String.valueOf(rs.getInt("application_id")));
                    row.put("jobId", String.valueOf(rs.getInt("job_id")));
                    row.put("status", rs.getString("application_status"));
                    row.put("appliedAt", formatDate(rs.getString("applied_at")));
                    row.put("jobTitle", rs.getString("job_title"));
                    row.put("location", rs.getString("location"));
                    row.put("company", rs.getString("company_name"));
                    result.add(row);
                }
            }
            if (!result.isEmpty()) return result;
        } catch (Exception ignored) {}

        // In-memory fallback
        for (ApplicationRecord a : IN_MEMORY_APPLICATIONS) {
            if (a.userId == userId || a.email.equalsIgnoreCase(email)) {
                Map<String, String> row = new HashMap<>();
                row.put("applicationId", String.valueOf(a.applicationId));
                row.put("jobId", String.valueOf(a.jobId));
                row.put("status", a.status);
                row.put("appliedAt", a.appliedAt);
                row.put("jobTitle", getJobTitleById(a.jobId));
                row.put("location", getJobLocationById(a.jobId));
                row.put("company", getJobCompanyById(a.jobId));
                result.add(row);
            }
        }
        return result;
    }

    /**
     * Get applications for all jobs posted by a specific recruiter.
     */
    public static List<Map<String, String>> getApplicationsForRecruiter(int recruiterUserId, int filterJobId) {
        List<Map<String, String>> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            StringBuilder sql = new StringBuilder("""
                SELECT a.application_id, a.job_id, a.user_id, a.full_name, a.email, a.phone, a.cover_letter,
                       a.application_status, a.applied_at, j.job_title, c.company_name
                FROM applications a
                INNER JOIN jobs j ON a.job_id = j.job_id
                INNER JOIN companies c ON j.company_id = c.company_id
                WHERE j.recruiter_user_id = ?
            """);
            if (filterJobId > 0) sql.append(" AND a.job_id = ").append(filterJobId);
            sql.append(" ORDER BY a.applied_at DESC");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setInt(1, recruiterUserId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("applicationId", String.valueOf(rs.getInt("application_id")));
                    row.put("jobId", String.valueOf(rs.getInt("job_id")));
                    row.put("userId", String.valueOf(rs.getInt("user_id")));
                    row.put("candidateName", rs.getString("full_name"));
                    row.put("email", rs.getString("email"));
                    row.put("phone", rs.getString("phone"));
                    row.put("coverLetter", rs.getString("cover_letter"));
                    row.put("status", rs.getString("application_status"));
                    row.put("appliedAt", formatDate(rs.getString("applied_at")));
                    row.put("jobTitle", rs.getString("job_title"));
                    row.put("company", rs.getString("company_name"));
                    result.add(row);
                }
            }
        } catch (Exception ignored) {}

        if (result.isEmpty()) {
            for (ApplicationRecord a : IN_MEMORY_APPLICATIONS) {
                Map<String, String> row = new HashMap<>();
                row.put("applicationId", String.valueOf(a.applicationId));
                row.put("jobId", String.valueOf(a.jobId));
                row.put("userId", String.valueOf(a.userId));
                row.put("candidateName", a.fullName);
                row.put("email", a.email);
                row.put("phone", a.phone);
                row.put("coverLetter", a.coverLetter);
                row.put("status", a.status);
                row.put("appliedAt", a.appliedAt);
                row.put("jobTitle", getJobTitleById(a.jobId));
                row.put("company", getJobCompanyById(a.jobId));
                result.add(row);
            }
        }
        return result;
    }

    /**
     * Update application status (shortlist/reject/accept).
     */
    public static boolean updateApplicationStatus(int applicationId, int recruiterUserId, String newStatus) {
        if (!Set.of("APPLIED", "SHORTLISTED", "REJECTED", "ACCEPTED").contains(newStatus)) return false;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 UPDATE applications a JOIN jobs j ON a.job_id=j.job_id
                 SET a.application_status=?
                 WHERE a.application_id=? AND j.recruiter_user_id=? AND a.application_status<>'WITHDRAWN'
             """)) {
            ps.setString(1, newStatus); ps.setInt(2, applicationId); ps.setInt(3, recruiterUserId);
            return ps.executeUpdate() == 1;
        } catch (Exception ignored) {}
        for (ApplicationRecord app : IN_MEMORY_APPLICATIONS) {
            JobRecord job = getJobById(app.jobId);
            if (app.applicationId == applicationId && job != null
                    && job.recruiterUserId == recruiterUserId && !"WITHDRAWN".equals(app.status)) {
                app.status = newStatus;
                return true;
            }
        }
        return false;
    }

    public static Map<String, String> getUserProfile(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT full_name,email,phone,skills,qualification,experience FROM users WHERE user_id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, String> profile = new HashMap<>();
                profile.put("fullName", Objects.toString(rs.getString("full_name"), ""));
                profile.put("email", Objects.toString(rs.getString("email"), ""));
                profile.put("phone", Objects.toString(rs.getString("phone"), ""));
                profile.put("skills", Objects.toString(rs.getString("skills"), ""));
                profile.put("qualification", Objects.toString(rs.getString("qualification"), ""));
                profile.put("experience", Objects.toString(rs.getString("experience"), ""));
                return profile;
            }
        } catch (Exception ignored) {}
        return new HashMap<>(IN_MEMORY_USERS.getOrDefault(userId, Map.of()));
    }

    public static boolean updateUserProfile(int userId, String fullName, String phone,
                                            String skills, String qualification, String experience) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 UPDATE users SET full_name=?,phone=?,skills=?,qualification=?,experience=? WHERE user_id=?
             """)) {
            ps.setString(1, fullName); ps.setString(2, phone); ps.setString(3, skills);
            ps.setString(4, qualification); ps.setString(5, experience); ps.setInt(6, userId);
            return ps.executeUpdate() == 1;
        } catch (Exception ignored) {}
        Map<String, String> profile = IN_MEMORY_USERS.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
        profile.put("fullName", fullName); profile.put("phone", phone); profile.put("skills", skills);
        profile.put("qualification", qualification); profile.put("experience", experience);
        return true;
    }

    public static List<JobRecord> getSavedJobs(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT j.job_id,j.job_title,j.description,j.skills_required,j.qualification,
                        j.experience_required,j.salary,j.location,j.job_type,j.status,j.recruiter_user_id,c.company_name
                 FROM saved_jobs s JOIN jobs j ON s.job_id=j.job_id
                 JOIN companies c ON j.company_id=c.company_id WHERE s.user_id=? ORDER BY s.saved_at DESC
             """)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            List<JobRecord> jobs = new ArrayList<>();
            while (rs.next()) jobs.add(mapJobRecord(rs));
            return jobs;
        } catch (Exception ignored) {}
        List<JobRecord> jobs = new ArrayList<>();
        for (JobRecord job : SAMPLE_JOBS) if (IN_MEMORY_SAVED_JOBS.contains(userId + ":" + job.jobId)) jobs.add(job);
        return jobs;
    }

    public static boolean isJobSaved(int userId, int jobId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM saved_jobs WHERE user_id=? AND job_id=?")) {
            ps.setInt(1, userId); ps.setInt(2, jobId);
            return ps.executeQuery().next();
        } catch (Exception ignored) {}
        return IN_MEMORY_SAVED_JOBS.contains(userId + ":" + jobId);
    }

    public static boolean toggleSavedJob(int userId, int jobId) {
        if (getJobById(jobId) == null) return false;
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (isJobSaved(userId, jobId)) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM saved_jobs WHERE user_id=? AND job_id=?")) {
                    ps.setInt(1, userId); ps.setInt(2, jobId); return ps.executeUpdate() == 1;
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO saved_jobs(user_id,job_id) VALUES(?,?)")) {
                ps.setInt(1, userId); ps.setInt(2, jobId); return ps.executeUpdate() == 1;
            }
        } catch (Exception ignored) {}
        String key = userId + ":" + jobId;
        if (!IN_MEMORY_SAVED_JOBS.remove(key)) IN_MEMORY_SAVED_JOBS.add(key);
        return true;
    }

    public static Map<String, String> getCompanyProfile(int recruiterUserId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT company_name,industry,location,website,company_desc FROM companies
                 WHERE recruiter_user_id=? LIMIT 1
             """)) {
            ps.setInt(1, recruiterUserId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, String> profile = new HashMap<>();
                for (String field : List.of("company_name","industry","location","website","company_desc"))
                    profile.put(field, Objects.toString(rs.getString(field), ""));
                return profile;
            }
        } catch (Exception ignored) {}
        return new HashMap<>(IN_MEMORY_COMPANIES.getOrDefault(recruiterUserId, Map.of()));
    }

    public static boolean updateCompanyProfile(int recruiterUserId, String name, String industry,
                                               String location, String website, String description) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            int companyId = getOrCreateCompanyForRecruiter(conn, name, location, recruiterUserId);
            try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE companies SET company_name=?,industry=?,location=?,website=?,company_desc=?,recruiter_user_id=?
                WHERE company_id=?
            """)) {
                ps.setString(1, name); ps.setString(2, industry); ps.setString(3, location);
                ps.setString(4, website); ps.setString(5, description); ps.setInt(6, recruiterUserId);
                ps.setInt(7, companyId); return ps.executeUpdate() == 1;
            }
        } catch (Exception ignored) {}
        Map<String, String> profile = new ConcurrentHashMap<>();
        profile.put("company_name", name); profile.put("industry", industry); profile.put("location", location);
        profile.put("website", website); profile.put("company_desc", description);
        IN_MEMORY_COMPANIES.put(recruiterUserId, profile);
        for (JobRecord job : SAMPLE_JOBS) if (job.recruiterUserId == recruiterUserId) job.companyName = name;
        return true;
    }

    public static boolean setUserActive(int userId, boolean active) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE users SET is_active=? WHERE user_id=?")) {
            ps.setBoolean(1, active); ps.setInt(2, userId); return ps.executeUpdate() == 1;
        } catch (Exception ignored) {}
        IN_MEMORY_USERS.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
            .put("isActive", String.valueOf(active));
        return true;
    }

    public static boolean setJobStatusAdmin(int jobId, String status) {
        if (!Set.of("OPEN", "CLOSED").contains(status)) return false;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE jobs SET status=? WHERE job_id=?")) {
            ps.setString(1, status); ps.setInt(2, jobId); return ps.executeUpdate() == 1;
        } catch (Exception ignored) {}
        JobRecord job = getJobById(jobId);
        if (job == null) return false;
        job.status = status;
        return true;
    }

    // ============================================================
    // USER MANAGEMENT (Admin)
    // ============================================================

    /**
     * Get all users for admin panel.
     */
    public static List<Map<String, String>> getAllUsers() {
        List<Map<String, String>> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT user_id, full_name, email, phone, user_type, is_active, created_at FROM users ORDER BY created_at DESC")) {
            while (rs.next()) {
                Map<String, String> row = new HashMap<>();
                row.put("userId", String.valueOf(rs.getInt("user_id")));
                row.put("fullName", rs.getString("full_name"));
                row.put("email", rs.getString("email"));
                row.put("phone", rs.getString("phone"));
                row.put("userType", rs.getString("user_type"));
                row.put("isActive", String.valueOf(rs.getBoolean("is_active")));
                row.put("createdAt", formatDate(rs.getString("created_at")));
                result.add(row);
            }
            if (!result.isEmpty()) return result;
        } catch (Exception ignored) {}
        for (Map<String, String> u : IN_MEMORY_USERS.values()) {
            Map<String, String> row = new HashMap<>(u);
            row.putIfAbsent("isActive", "true");
            result.add(row);
        }
        return result;
    }

    /**
     * Delete a user.
     */
    public static boolean deleteUser(int userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM applications WHERE user_id=?")) {
                ps.setInt(1, userId); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE user_id=?")) {
                ps.setInt(1, userId); ps.executeUpdate();
            }
            return true;
        } catch (Exception ignored) {}
        IN_MEMORY_USERS.remove(userId);
        IN_MEMORY_APPLICATIONS.removeIf(a -> a.userId == userId);
        return true;
    }

    /**
     * Get all recruiters for admin panel.
     */
    public static List<Map<String, String>> getAllRecruiters() {
        List<Map<String, String>> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT u.user_id, u.full_name, u.email, u.created_at,
                       COALESCE(c.company_name, 'N/A') as company_name,
                       COALESCE(c.location, 'N/A') as location,
                       (SELECT COUNT(*) FROM jobs j WHERE j.recruiter_user_id = u.user_id) as job_count
                FROM users u
                LEFT JOIN companies c ON c.recruiter_user_id = u.user_id
                WHERE u.user_type = 'RECRUITER'
                ORDER BY u.created_at DESC
            """;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("userId", String.valueOf(rs.getInt("user_id")));
                    row.put("fullName", rs.getString("full_name"));
                    row.put("email", rs.getString("email"));
                    row.put("companyName", rs.getString("company_name"));
                    row.put("location", rs.getString("location"));
                    row.put("jobCount", String.valueOf(rs.getInt("job_count")));
                    row.put("createdAt", formatDate(rs.getString("created_at")));
                    result.add(row);
                }
            }
            if (!result.isEmpty()) return result;
        } catch (Exception ignored) {}
        for (Map<String, String> u : IN_MEMORY_USERS.values()) {
            if ("RECRUITER".equals(u.get("userType"))) {
                Map<String, String> row = new HashMap<>();
                int uid = Integer.parseInt(u.getOrDefault("userId", "0"));
                row.put("userId", String.valueOf(uid));
                row.put("fullName", u.getOrDefault("fullName", "-"));
                row.put("email", u.getOrDefault("email", "-"));
                Map<String, String> comp = IN_MEMORY_COMPANIES.getOrDefault(uid, Map.of());
                row.put("companyName", comp.getOrDefault("company_name", u.getOrDefault("companyName", "N/A")));
                row.put("location", comp.getOrDefault("location", "Bangalore"));
                long cnt = SAMPLE_JOBS.stream().filter(j -> j.recruiterUserId == uid).count();
                row.put("jobCount", String.valueOf(cnt));
                row.put("createdAt", u.getOrDefault("createdAt", "2026-01-02"));
                result.add(row);
            }
        }
        return result;
    }

    /**
     * Get statistics for admin dashboard.
     */
    public static Map<String, Integer> getAdminStats() {
        Map<String, Integer> stats = new HashMap<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                    if (rs.next()) stats.put("users", rs.getInt(1));
                }
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE user_type='RECRUITER'")) {
                    if (rs.next()) stats.put("recruiters", rs.getInt(1));
                }
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE user_type='JOB_SEEKER'")) {
                    if (rs.next()) stats.put("seekers", rs.getInt(1));
                }
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM jobs")) {
                    if (rs.next()) stats.put("jobs", rs.getInt(1));
                }
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM jobs WHERE status='OPEN'")) {
                    if (rs.next()) stats.put("openJobs", rs.getInt(1));
                }
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM jobs WHERE status='CLOSED'")) {
                    if (rs.next()) stats.put("closedJobs", rs.getInt(1));
                }
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM applications")) {
                    if (rs.next()) stats.put("applications", rs.getInt(1));
                }
            }
            return stats;
        } catch (Exception ignored) {
            stats.put("users", IN_MEMORY_USERS.size());
            stats.put("recruiters", (int) IN_MEMORY_USERS.values().stream().filter(u -> "RECRUITER".equals(u.get("userType"))).count());
            stats.put("seekers", (int) IN_MEMORY_USERS.values().stream().filter(u -> "JOB_SEEKER".equals(u.get("userType"))).count());
            stats.put("jobs", SAMPLE_JOBS.size());
            stats.put("openJobs", (int) SAMPLE_JOBS.stream().filter(j -> "OPEN".equals(j.status)).count());
            stats.put("closedJobs", (int) SAMPLE_JOBS.stream().filter(j -> "CLOSED".equals(j.status)).count());
            stats.put("applications", IN_MEMORY_APPLICATIONS.size());
        }
        return stats;
    }

    /**
     * Get all applications for admin oversight.
     */
    public static List<Map<String, String>> getAllApplicationsAdmin(String statusFilter) {
        List<Map<String, String>> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            StringBuilder sql = new StringBuilder("""
                SELECT a.application_id, a.job_id, a.user_id, a.full_name, a.email, a.phone,
                       a.application_status, a.applied_at, a.cover_letter,
                       j.job_title, c.company_name
                FROM applications a
                INNER JOIN jobs j ON a.job_id = j.job_id
                INNER JOIN companies c ON j.company_id = c.company_id
            """);
            if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                sql.append(" WHERE a.application_status = ?");
            }
            sql.append(" ORDER BY a.applied_at DESC");
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                    ps.setString(1, statusFilter.toUpperCase());
                }
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("applicationId", String.valueOf(rs.getInt("application_id")));
                    row.put("jobId", String.valueOf(rs.getInt("job_id")));
                    row.put("candidateName", rs.getString("full_name"));
                    row.put("email", rs.getString("email"));
                    row.put("phone", rs.getString("phone"));
                    row.put("coverLetter", rs.getString("cover_letter"));
                    row.put("status", rs.getString("application_status"));
                    row.put("appliedAt", formatDate(rs.getString("applied_at")));
                    row.put("jobTitle", rs.getString("job_title"));
                    row.put("company", rs.getString("company_name"));
                    result.add(row);
                }
            }
            if (!result.isEmpty()) return result;
        } catch (Exception ignored) {}

        // In-memory fallback
        for (ApplicationRecord a : IN_MEMORY_APPLICATIONS) {
            if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)
                && !a.status.equalsIgnoreCase(statusFilter)) {
                continue;
            }
            Map<String, String> row = new HashMap<>();
            row.put("applicationId", String.valueOf(a.applicationId));
            row.put("jobId", String.valueOf(a.jobId));
            row.put("candidateName", a.fullName);
            row.put("email", a.email);
            row.put("phone", a.phone);
            row.put("coverLetter", a.coverLetter);
            row.put("status", a.status);
            row.put("appliedAt", a.appliedAt);
            row.put("jobTitle", getJobTitleById(a.jobId));
            row.put("company", getJobCompanyById(a.jobId));
            result.add(row);
        }
        return result;
    }

    /**
     * Delete an application (Admin).
     */
    public static boolean deleteApplicationAdmin(int applicationId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM applications WHERE application_id=?")) {
            ps.setInt(1, applicationId);
            if (ps.executeUpdate() == 1) return true;
        } catch (Exception ignored) {}
        return IN_MEMORY_APPLICATIONS.removeIf(a -> a.applicationId == applicationId);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static String formatDate(String timestamp) {
        if (timestamp == null) return "-";
        try {
            if (timestamp.length() > 10) return timestamp.substring(0, 10);
        } catch (Exception ignored) {}
        return timestamp;
    }

    public static String getJobTitleById(int jobId) {
        JobRecord j = getJobById(jobId);
        if (j != null) return j.jobTitle;
        for (JobRecord sj : SAMPLE_JOBS) { if (sj.jobId == jobId) return sj.jobTitle; }
        return "Unknown Job";
    }

    public static String getJobLocationById(int jobId) {
        JobRecord j = getJobById(jobId);
        if (j != null) return j.location;
        for (JobRecord sj : SAMPLE_JOBS) { if (sj.jobId == jobId) return sj.location; }
        return "-";
    }

    public static String getJobCompanyById(int jobId) {
        JobRecord j = getJobById(jobId);
        if (j != null) return j.companyName;
        for (JobRecord sj : SAMPLE_JOBS) { if (sj.jobId == jobId) return sj.companyName; }
        return "-";
    }

    // ============================================================
    // SMARTHIRE: RESUME & SKILLS DATA ACCESS
    // ============================================================

    public static boolean saveResume(int userId, ResumeParser.ParsedResume parsed, String fileType, String rawText) {
        int resumeId = -1;
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if resume already exists for user
            try (PreparedStatement ps = conn.prepareStatement("SELECT resume_id FROM resumes WHERE user_id=? LIMIT 1")) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    resumeId = rs.getInt("resume_id");
                    String upd = """
                        UPDATE resumes SET file_name=?, file_type=?, raw_text=?, parsed_name=?, parsed_email=?,
                            parsed_phone=?, parsed_education=?, parsed_experience=?, parsed_projects=?,
                            parsed_certifications=?, parsed_languages=?, primary_role=?, uploaded_at=CURRENT_TIMESTAMP
                        WHERE resume_id=?
                    """;
                    try (PreparedStatement ups = conn.prepareStatement(upd)) {
                        ups.setString(1, parsed.fileName);
                        ups.setString(2, fileType);
                        ups.setString(3, rawText);
                        ups.setString(4, parsed.fullName);
                        ups.setString(5, parsed.email);
                        ups.setString(6, parsed.phone);
                        ups.setString(7, parsed.education);
                        ups.setString(8, parsed.experience);
                        ups.setString(9, parsed.projects);
                        ups.setString(10, parsed.certifications);
                        ups.setString(11, parsed.languages);
                        ups.setString(12, parsed.primaryRole);
                        ups.setInt(13, resumeId);
                        ups.executeUpdate();
                    }
                } else {
                    String ins = """
                        INSERT INTO resumes (user_id, file_name, file_type, raw_text, parsed_name, parsed_email,
                            parsed_phone, parsed_education, parsed_experience, parsed_projects,
                            parsed_certifications, parsed_languages, primary_role)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                    try (PreparedStatement inps = conn.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
                        inps.setInt(1, userId);
                        inps.setString(2, parsed.fileName);
                        inps.setString(3, fileType);
                        inps.setString(4, rawText);
                        inps.setString(5, parsed.fullName);
                        inps.setString(6, parsed.email);
                        inps.setString(7, parsed.phone);
                        inps.setString(8, parsed.education);
                        inps.setString(9, parsed.experience);
                        inps.setString(10, parsed.projects);
                        inps.setString(11, parsed.certifications);
                        inps.setString(12, parsed.languages);
                        inps.setString(13, parsed.primaryRole);
                        inps.executeUpdate();
                        ResultSet keys = inps.getGeneratedKeys();
                        if (keys.next()) resumeId = keys.getInt(1);
                    }
                }
            }

            // Replace resume_skills
            if (resumeId > 0) {
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM resume_skills WHERE user_id=?")) {
                    del.setInt(1, userId);
                    del.executeUpdate();
                }

                String insSkill = "INSERT INTO resume_skills (resume_id, user_id, skill_name, category, proficiency) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement skps = conn.prepareStatement(insSkill)) {
                    for (Map.Entry<String, List<String>> entry : parsed.skillsByCategory.entrySet()) {
                        String cat = entry.getKey();
                        for (String skill : entry.getValue()) {
                            skps.setInt(1, resumeId);
                            skps.setInt(2, userId);
                            skps.setString(3, skill);
                            skps.setString(4, cat);
                            skps.setString(5, "Intermediate");
                            skps.addBatch();
                        }
                    }
                    skps.executeBatch();
                }

                // Update users profile fields
                String userUpdate = "UPDATE users SET skills=?, qualification=?, experience=? WHERE user_id=?";
                try (PreparedStatement ups = conn.prepareStatement(userUpdate)) {
                    ups.setString(1, String.join(", ", parsed.allSkills));
                    ups.setString(2, parsed.education);
                    ups.setString(3, parsed.experience);
                    ups.setInt(4, userId);
                    ups.executeUpdate();
                }
            }
        } catch (Exception e) {
            System.out.println("saveResume DB error: " + e.getMessage());
        }

        // In-memory update
        if (resumeId <= 0) resumeId = RESUME_ID_COUNTER.getAndIncrement();
        ResumeRecord rec = new ResumeRecord(resumeId, userId, parsed.fileName, fileType, rawText,
            parsed.fullName, parsed.email, parsed.phone, parsed.education, parsed.experience,
            parsed.projects, parsed.certifications, parsed.languages, parsed.primaryRole);
        IN_MEMORY_RESUMES.put(userId, rec);

        List<ResumeSkillRecord> skList = new CopyOnWriteArrayList<>();
        int skId = 100;
        for (Map.Entry<String, List<String>> entry : parsed.skillsByCategory.entrySet()) {
            for (String skill : entry.getValue()) {
                skList.add(new ResumeSkillRecord(skId++, resumeId, userId, skill, entry.getKey(), "Intermediate"));
            }
        }
        IN_MEMORY_RESUME_SKILLS.put(userId, skList);

        // Update in-memory user
        Map<String, String> u = IN_MEMORY_USERS.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
        u.put("skills", String.join(", ", parsed.allSkills));
        u.put("qualification", parsed.education);
        u.put("experience", parsed.experience);

        // Trigger smart alert check against open jobs
        try {
            for (JobRecord j : getAllJobs()) {
                if ("OPEN".equalsIgnoreCase(j.status)) {
                    MatchingEngine.JobMatchResult mr = MatchingEngine.calculateJobMatch(
                        j, parsed.allSkills, parsed.education, parsed.experience, "Bangalore");
                    if (mr.matchPercentage >= 85) {
                        addNotification(userId,
                            "🔥 " + mr.matchPercentage + "% Resume Match!",
                            "A new opening for " + j.jobTitle + " at " + j.companyName + " matches " + mr.matchPercentage + "% of your resume skills.",
                            "JOB_ALERT", "/seeker/matching-jobs");
                        break; // send top match alert
                    }
                }
            }
        } catch (Exception ignored) {}

        return true;
    }

    public static ResumeRecord getResumeByUserId(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM resumes WHERE user_id=? ORDER BY resume_id DESC LIMIT 1")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ResumeRecord r = new ResumeRecord();
                r.resumeId = rs.getInt("resume_id");
                r.userId = rs.getInt("user_id");
                r.fileName = rs.getString("file_name");
                r.fileType = rs.getString("file_type");
                r.rawText = rs.getString("raw_text");
                r.parsedName = rs.getString("parsed_name");
                r.parsedEmail = rs.getString("parsed_email");
                r.parsedPhone = rs.getString("parsed_phone");
                r.parsedEducation = rs.getString("parsed_education");
                r.parsedExperience = rs.getString("parsed_experience");
                r.parsedProjects = rs.getString("parsed_projects");
                r.parsedCertifications = rs.getString("parsed_certifications");
                r.parsedLanguages = rs.getString("parsed_languages");
                r.primaryRole = rs.getString("primary_role");
                r.uploadedAt = formatDate(rs.getString("uploaded_at"));
                return r;
            }
        } catch (Exception ignored) {}

        return IN_MEMORY_RESUMES.get(userId);
    }

    public static Map<String, List<String>> getResumeSkillsByUserId(int userId) {
        Map<String, List<String>> categorized = new LinkedHashMap<>();
        for (String cat : ResumeParser.CATEGORY_ORDER) {
            categorized.put(cat, new ArrayList<>());
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT skill_name, category FROM resume_skills WHERE user_id=? ORDER BY skill_id ASC")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            boolean found = false;
            while (rs.next()) {
                found = true;
                String name = rs.getString("skill_name");
                String cat = rs.getString("category");
                categorized.computeIfAbsent(cat, k -> new ArrayList<>()).add(name);
            }
            if (found) return categorized;
        } catch (Exception ignored) {}

        List<ResumeSkillRecord> inMem = IN_MEMORY_RESUME_SKILLS.get(userId);
        if (inMem != null) {
            for (ResumeSkillRecord sk : inMem) {
                categorized.computeIfAbsent(sk.category, k -> new ArrayList<>()).add(sk.skillName);
            }
            return categorized;
        }

        // Fallback: extract from users.skills string if available
        Map<String, String> userProfile = getUserProfile(userId);
        String skillsStr = userProfile.getOrDefault("skills", "");
        if (!skillsStr.isEmpty()) {
            ResumeParser.ParsedResume pr = ResumeParser.parseText("profile.txt", skillsStr);
            return pr.skillsByCategory;
        }

        return categorized;
    }

    public static List<String> getAllSkillsForUser(int userId) {
        Map<String, List<String>> byCat = getResumeSkillsByUserId(userId);
        List<String> list = new ArrayList<>();
        for (List<String> sks : byCat.values()) {
            list.addAll(sks);
        }
        return list;
    }

    // ============================================================
    // CANDIDATE DISCOVERY (FOR RECRUITER REVERSE MATCHING)
    // ============================================================

    public static List<MatchingEngine.CandidateRecord> getAllCandidatesForRecruiter() {
        List<MatchingEngine.CandidateRecord> candidates = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT u.user_id, u.full_name, u.email, u.phone, u.qualification, u.experience, u.skills,
                       COALESCE(r.file_name, 'Resume.pdf') as file_name
                FROM users u
                LEFT JOIN resumes r ON u.user_id = r.user_id
                WHERE u.user_type = 'JOB_SEEKER' AND u.is_active = TRUE
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getInt("user_id");
                    seenIds.add(uid);
                    String skillsStr = rs.getString("skills");
                    List<String> skills = new ArrayList<>();
                    if (skillsStr != null && !skillsStr.isEmpty()) {
                        for (String s : skillsStr.split(",")) if (!s.trim().isEmpty()) skills.add(s.trim());
                    }
                    candidates.add(new MatchingEngine.CandidateRecord(
                        uid,
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("qualification"),
                        rs.getString("experience"),
                        skills,
                        rs.getString("file_name")
                    ));
                }
            }
        } catch (Exception ignored) {}

        // Complement with in-memory users if not already seen
        for (Map.Entry<Integer, Map<String, String>> entry : IN_MEMORY_USERS.entrySet()) {
            int uid = entry.getKey();
            if (seenIds.contains(uid)) continue;
            Map<String, String> u = entry.getValue();
            if ("JOB_SEEKER".equals(u.get("userType"))) {
                String skillsStr = u.getOrDefault("skills", "");
                List<String> skills = new ArrayList<>();
                for (String s : skillsStr.split(",")) if (!s.trim().isEmpty()) skills.add(s.trim());
                ResumeRecord r = IN_MEMORY_RESUMES.get(uid);
                String fn = (r != null) ? r.fileName : "Resume.pdf";
                candidates.add(new MatchingEngine.CandidateRecord(
                    uid,
                    u.getOrDefault("fullName", "Candidate"),
                    u.getOrDefault("email", "seeker@example.com"),
                    u.getOrDefault("phone", "9876543210"),
                    u.getOrDefault("qualification", "B.Tech Computer Science"),
                    u.getOrDefault("experience", "2 years"),
                    skills,
                    fn
                ));
            }
        }

        return candidates;
    }

    // ============================================================
    // NOTIFICATIONS & SMART JOB ALERTS
    // ============================================================

    public static boolean addNotification(int userId, String title, String message, String type, String link) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO notifications (user_id, title, message, type, link) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, userId);
            ps.setString(2, title);
            ps.setString(3, message);
            ps.setString(4, type);
            ps.setString(5, link);
            ps.executeUpdate();
        } catch (Exception ignored) {}

        IN_MEMORY_NOTIFICATIONS.add(new NotificationRecord(
            NOTIF_ID_COUNTER.getAndIncrement(), userId, title, message, type, link
        ));
        return true;
    }

    public static List<NotificationRecord> getNotificationsByUser(int userId) {
        List<NotificationRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM notifications WHERE user_id=? ORDER BY notification_id DESC LIMIT 30")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                NotificationRecord n = new NotificationRecord(
                    rs.getInt("notification_id"),
                    rs.getInt("user_id"),
                    rs.getString("title"),
                    rs.getString("message"),
                    rs.getString("type"),
                    rs.getString("link")
                );
                n.isRead = rs.getBoolean("is_read");
                n.createdAt = formatDate(rs.getString("created_at"));
                list.add(n);
            }
            if (!list.isEmpty()) return list;
        } catch (Exception ignored) {}

        for (NotificationRecord n : IN_MEMORY_NOTIFICATIONS) {
            if (n.userId == userId) list.add(n);
        }
        list.sort((a, b) -> Integer.compare(b.notificationId, a.notificationId));
        return list;
    }

    public static int getUnreadNotificationCount(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM notifications WHERE user_id=? AND is_read=FALSE")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception ignored) {}

        int count = 0;
        for (NotificationRecord n : IN_MEMORY_NOTIFICATIONS) {
            if (n.userId == userId && !n.isRead) count++;
        }
        return count;
    }

    public static void markNotificationsAsRead(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE notifications SET is_read=TRUE WHERE user_id=?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (Exception ignored) {}

        for (NotificationRecord n : IN_MEMORY_NOTIFICATIONS) {
            if (n.userId == userId) n.isRead = true;
        }
    }

    public static void triggerSmartJobAlerts(JobRecord job) {
        if (job == null) return;
        List<MatchingEngine.CandidateRecord> candidates = getAllCandidatesForRecruiter();
        for (MatchingEngine.CandidateRecord cand : candidates) {
            if (cand.skills.isEmpty()) continue;
            MatchingEngine.JobMatchResult match = MatchingEngine.calculateJobMatch(
                job, cand.skills, cand.qualification, cand.experience, "Bangalore"
            );
            if (match.matchPercentage >= 80) {
                addNotification(
                    cand.userId,
                    "🔔 New " + match.matchPercentage + "% Match: " + job.jobTitle,
                    "A new " + job.jobTitle + " position at " + job.companyName + " matches " + match.matchPercentage + "% of your resume!",
                    "JOB_ALERT",
                    "/seeker/matching-jobs"
                );
            }
        }
    }

    // ============================================================
    // INTERVIEW SCHEDULING
    // ============================================================

    public static boolean saveInterview(int applicationId, int jobId, int userId, int recruiterUserId,
                                        String date, String type, String meetingLink, String notes) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                INSERT INTO interviews (application_id, job_id, user_id, recruiter_user_id,
                    interview_date, interview_type, meeting_link, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, applicationId);
                ps.setInt(2, jobId);
                ps.setInt(3, userId);
                ps.setInt(4, recruiterUserId);
                ps.setString(5, date);
                ps.setString(6, type);
                ps.setString(7, meetingLink);
                ps.setString(8, notes);
                ps.executeUpdate();
            }
            // Update application status to SHORTLISTED
            try (PreparedStatement ups = conn.prepareStatement(
                "UPDATE applications SET application_status='SHORTLISTED' WHERE application_id=?")) {
                ups.setInt(1, applicationId);
                ups.executeUpdate();
            }
        } catch (Exception ignored) {}

        InterviewRecord ir = new InterviewRecord(
            INTERVIEW_ID_COUNTER.getAndIncrement(),
            applicationId, jobId, userId, recruiterUserId, date, type, meetingLink, notes
        );
        ir.jobTitle = getJobTitleById(jobId);
        ir.companyName = getJobCompanyById(jobId);
        Map<String, String> candProf = getUserProfile(userId);
        ir.candidateName = candProf.getOrDefault("fullName", "Candidate");
        IN_MEMORY_INTERVIEWS.add(ir);

        // Update in-memory application status
        for (ApplicationRecord a : IN_MEMORY_APPLICATIONS) {
            if (a.applicationId == applicationId) a.status = "SHORTLISTED";
        }

        // Notify candidate
        addNotification(
            userId,
            "📅 Interview Scheduled: " + ir.jobTitle,
            "Your interview for " + ir.jobTitle + " at " + ir.companyName + " is scheduled for " + date + " (" + type + ").",
            "INTERVIEW",
            "/seeker/interviews"
        );

        return true;
    }

    public static List<InterviewRecord> getInterviewsByUser(int userId) {
        List<InterviewRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT i.*, j.job_title, c.company_name, u.full_name as candidate_name
                FROM interviews i
                JOIN jobs j ON i.job_id = j.job_id
                JOIN companies c ON j.company_id = c.company_id
                JOIN users u ON i.user_id = u.user_id
                WHERE i.user_id=?
                ORDER BY i.interview_id DESC
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    InterviewRecord ir = new InterviewRecord(
                        rs.getInt("interview_id"),
                        rs.getInt("application_id"),
                        rs.getInt("job_id"),
                        rs.getInt("user_id"),
                        rs.getInt("recruiter_user_id"),
                        rs.getString("interview_date"),
                        rs.getString("interview_type"),
                        rs.getString("meeting_link"),
                        rs.getString("notes")
                    );
                    ir.status = rs.getString("status");
                    ir.createdAt = formatDate(rs.getString("created_at"));
                    ir.jobTitle = rs.getString("job_title");
                    ir.companyName = rs.getString("company_name");
                    ir.candidateName = rs.getString("candidate_name");
                    list.add(ir);
                }
                if (!list.isEmpty()) return list;
            }
        } catch (Exception ignored) {}

        for (InterviewRecord i : IN_MEMORY_INTERVIEWS) {
            if (i.userId == userId) list.add(i);
        }
        return list;
    }

    public static List<InterviewRecord> getInterviewsByRecruiter(int recruiterUserId) {
        List<InterviewRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT i.*, j.job_title, c.company_name, u.full_name as candidate_name
                FROM interviews i
                JOIN jobs j ON i.job_id = j.job_id
                JOIN companies c ON j.company_id = c.company_id
                JOIN users u ON i.user_id = u.user_id
                WHERE i.recruiter_user_id=?
                ORDER BY i.interview_id DESC
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, recruiterUserId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    InterviewRecord ir = new InterviewRecord(
                        rs.getInt("interview_id"),
                        rs.getInt("application_id"),
                        rs.getInt("job_id"),
                        rs.getInt("user_id"),
                        rs.getInt("recruiter_user_id"),
                        rs.getString("interview_date"),
                        rs.getString("interview_type"),
                        rs.getString("meeting_link"),
                        rs.getString("notes")
                    );
                    ir.status = rs.getString("status");
                    ir.createdAt = formatDate(rs.getString("created_at"));
                    ir.jobTitle = rs.getString("job_title");
                    ir.companyName = rs.getString("company_name");
                    ir.candidateName = rs.getString("candidate_name");
                    list.add(ir);
                }
                if (!list.isEmpty()) return list;
            }
        } catch (Exception ignored) {}

        for (InterviewRecord i : IN_MEMORY_INTERVIEWS) {
            if (i.recruiterUserId == recruiterUserId) list.add(i);
        }
        return list;
    }

    // ============================================================
    // JOB QUALITY & SCAM REPORTING (TRUST & SAFETY)
    // ============================================================

    public static boolean saveJobReport(int jobId, int userId, String reporterEmail, String reason, String details) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO job_reports (job_id, user_id, reporter_email, reason, details) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, jobId);
            if (userId > 0) ps.setInt(2, userId); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, reporterEmail);
            ps.setString(4, reason);
            ps.setString(5, details);
            ps.executeUpdate();
        } catch (Exception ignored) {}

        JobReportRecord rep = new JobReportRecord(
            REPORT_ID_COUNTER.getAndIncrement(),
            jobId, userId, reporterEmail, reason, details
        );
        rep.jobTitle = getJobTitleById(jobId);
        rep.companyName = getJobCompanyById(jobId);
        IN_MEMORY_REPORTS.add(rep);
        return true;
    }

    public static List<JobReportRecord> getAllJobReports() {
        List<JobReportRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT r.*, j.job_title, c.company_name
                FROM job_reports r
                JOIN jobs j ON r.job_id = j.job_id
                JOIN companies c ON j.company_id = c.company_id
                ORDER BY r.report_id DESC
            """;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    JobReportRecord r = new JobReportRecord(
                        rs.getInt("report_id"),
                        rs.getInt("job_id"),
                        rs.getInt("user_id"),
                        rs.getString("reporter_email"),
                        rs.getString("reason"),
                        rs.getString("details")
                    );
                    r.status = rs.getString("status");
                    r.createdAt = formatDate(rs.getString("created_at"));
                    r.jobTitle = rs.getString("job_title");
                    r.companyName = rs.getString("company_name");
                    list.add(r);
                }
                if (!list.isEmpty()) return list;
            }
        } catch (Exception ignored) {}

        for (JobReportRecord r : IN_MEMORY_REPORTS) {
            list.add(r);
        }
        return list;
    }

    public static boolean updateJobReportStatus(int reportId, String status) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE job_reports SET status=? WHERE report_id=?")) {
            ps.setString(1, status);
            ps.setInt(2, reportId);
            ps.executeUpdate();
        } catch (Exception ignored) {}

        for (JobReportRecord r : IN_MEMORY_REPORTS) {
            if (r.reportId == reportId) {
                r.status = status;
                return true;
            }
        }
        return true;
    }

    // ============================================================
    // RECRUITER INVITATIONS TO CANDIDATES
    // ============================================================

    public static boolean saveJobInvitation(int jobId, int recruiterUserId, int seekerUserId, int matchScore, String message) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO job_invitations (job_id, recruiter_user_id, seeker_user_id, match_score, message) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, jobId);
            ps.setInt(2, recruiterUserId);
            ps.setInt(3, seekerUserId);
            ps.setInt(4, matchScore);
            ps.setString(5, message);
            ps.executeUpdate();
        } catch (Exception ignored) {}

        JobInvitationRecord inv = new JobInvitationRecord(
            INVITATION_ID_COUNTER.getAndIncrement(),
            jobId, recruiterUserId, seekerUserId, matchScore, message
        );
        inv.jobTitle = getJobTitleById(jobId);
        inv.companyName = getJobCompanyById(jobId);
        IN_MEMORY_INVITATIONS.add(inv);

        // Notify seeker
        addNotification(
            seekerUserId,
            "📩 Recruiter Invitation (" + matchScore + "% Match)",
            inv.companyName + " invites you to apply for " + inv.jobTitle + "! Check it out under Matching Jobs.",
            "INVITATION",
            "/seeker/matching-jobs"
        );

        return true;
    }

    public static List<JobInvitationRecord> getJobInvitationsByUser(int userId) {
        List<JobInvitationRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT i.*, j.job_title, c.company_name
                FROM job_invitations i
                JOIN jobs j ON i.job_id = j.job_id
                JOIN companies c ON j.company_id = c.company_id
                WHERE i.seeker_user_id=?
                ORDER BY i.invitation_id DESC
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JobInvitationRecord inv = new JobInvitationRecord(
                        rs.getInt("invitation_id"),
                        rs.getInt("job_id"),
                        rs.getInt("recruiter_user_id"),
                        rs.getInt("seeker_user_id"),
                        rs.getInt("match_score"),
                        rs.getString("message")
                    );
                    inv.status = rs.getString("status");
                    inv.createdAt = formatDate(rs.getString("created_at"));
                    inv.jobTitle = rs.getString("job_title");
                    inv.companyName = rs.getString("company_name");
                    list.add(inv);
                }
                if (!list.isEmpty()) return list;
            }
        } catch (Exception ignored) {}

        for (JobInvitationRecord i : IN_MEMORY_INVITATIONS) {
            if (i.seekerUserId == userId) list.add(i);
        }
        return list;
    }
}

