-- ============================================================
-- SmartHire — Resume Intelligence & Job Matching Portal
-- Complete Database Schema (MySQL + JDBC)
-- ============================================================

CREATE DATABASE IF NOT EXISTS jobportal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jobportal;

-- ============================================================
-- TABLE 1: Companies
-- ============================================================
CREATE TABLE IF NOT EXISTS companies (
    company_id        INT AUTO_INCREMENT PRIMARY KEY,
    company_name      VARCHAR(150) NOT NULL,
    industry          VARCHAR(100),
    location          VARCHAR(100),
    website           VARCHAR(255),
    company_desc      TEXT,
    recruiter_user_id INT DEFAULT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- TABLE 2: Users
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(100) NOT NULL UNIQUE,
    password      VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    skills        TEXT,
    qualification VARCHAR(200),
    experience    VARCHAR(100),
    user_type     ENUM('JOB_SEEKER','RECRUITER','ADMIN') DEFAULT 'JOB_SEEKER',
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- TABLE 3: Jobs
-- ============================================================
CREATE TABLE IF NOT EXISTS jobs (
    job_id              INT AUTO_INCREMENT PRIMARY KEY,
    company_id          INT NOT NULL,
    recruiter_user_id   INT DEFAULT NULL,
    job_title           VARCHAR(150) NOT NULL,
    description         TEXT NOT NULL,
    skills_required     TEXT,
    qualification       VARCHAR(200),
    experience_required VARCHAR(80),
    salary              VARCHAR(100),
    location            VARCHAR(100),
    job_type            VARCHAR(50) DEFAULT 'Full-time',
    status              ENUM('OPEN','CLOSED') DEFAULT 'OPEN',
    posted_date         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(company_id) ON DELETE CASCADE
);

-- ============================================================
-- TABLE 4: Applications
-- ============================================================
CREATE TABLE IF NOT EXISTS applications (
    application_id     INT AUTO_INCREMENT PRIMARY KEY,
    job_id             INT NOT NULL,
    user_id            INT DEFAULT NULL,
    full_name          VARCHAR(100),
    email              VARCHAR(100),
    phone              VARCHAR(30),
    cover_letter       TEXT,
    application_status ENUM('APPLIED','SHORTLISTED','REJECTED','ACCEPTED','WITHDRAWN') DEFAULT 'APPLIED',
    applied_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_applications_job_user (job_id, user_id),
    FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- ============================================================
-- TABLE 5: Saved Jobs
-- ============================================================
CREATE TABLE IF NOT EXISTS saved_jobs (
    user_id  INT NOT NULL,
    job_id   INT NOT NULL,
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, job_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE
);

-- ============================================================
-- TABLE 6: Resumes (Resume Analyzer & Intelligence)
-- ============================================================
CREATE TABLE IF NOT EXISTS resumes (
    resume_id             INT AUTO_INCREMENT PRIMARY KEY,
    user_id               INT NOT NULL,
    file_name             VARCHAR(255) NOT NULL,
    file_type             VARCHAR(50) DEFAULT 'PDF',
    raw_text              LONGTEXT,
    parsed_name           VARCHAR(150),
    parsed_email          VARCHAR(150),
    parsed_phone          VARCHAR(50),
    parsed_education      TEXT,
    parsed_experience     TEXT,
    parsed_projects       TEXT,
    parsed_certifications TEXT,
    parsed_languages      TEXT,
    primary_role          VARCHAR(100) DEFAULT 'Software Engineer',
    uploaded_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- ============================================================
-- TABLE 7: Resume Skills (Categorized Taxonomy)
-- ============================================================
CREATE TABLE IF NOT EXISTS resume_skills (
    skill_id    INT AUTO_INCREMENT PRIMARY KEY,
    resume_id   INT NOT NULL,
    user_id     INT NOT NULL,
    skill_name  VARCHAR(100) NOT NULL,
    category    VARCHAR(50) NOT NULL,
    proficiency VARCHAR(50) DEFAULT 'Intermediate',
    FOREIGN KEY (resume_id) REFERENCES resumes(resume_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- ============================================================
-- TABLE 8: Smart Notifications & Alerts
-- ============================================================
CREATE TABLE IF NOT EXISTS notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    title           VARCHAR(200) NOT NULL,
    message         TEXT NOT NULL,
    type            VARCHAR(50) DEFAULT 'JOB_ALERT',
    link            VARCHAR(255) DEFAULT '/seeker/matching-jobs',
    is_read         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- ============================================================
-- TABLE 9: Interviews (Recruiter-Seeker Scheduling)
-- ============================================================
CREATE TABLE IF NOT EXISTS interviews (
    interview_id      INT AUTO_INCREMENT PRIMARY KEY,
    application_id    INT NOT NULL,
    job_id            INT NOT NULL,
    user_id           INT NOT NULL,
    recruiter_user_id INT NOT NULL,
    interview_date    VARCHAR(100) NOT NULL,
    interview_type    VARCHAR(50) DEFAULT 'Online Video',
    meeting_link      VARCHAR(255),
    notes             TEXT,
    status            ENUM('SCHEDULED','COMPLETED','CANCELLED') DEFAULT 'SCHEDULED',
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- ============================================================
-- TABLE 10: Job Reports (Trust & Safety / Scam Reporting)
-- ============================================================
CREATE TABLE IF NOT EXISTS job_reports (
    report_id      INT AUTO_INCREMENT PRIMARY KEY,
    job_id         INT NOT NULL,
    user_id        INT DEFAULT NULL,
    reporter_email VARCHAR(150),
    reason         VARCHAR(100) NOT NULL,
    details        TEXT,
    status         ENUM('PENDING','REVIEWED','RESOLVED','DISMISSED') DEFAULT 'PENDING',
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE
);

-- ============================================================
-- TABLE 11: Job Invitations (Recruiter Candidate Discovery)
-- ============================================================
CREATE TABLE IF NOT EXISTS job_invitations (
    invitation_id     INT AUTO_INCREMENT PRIMARY KEY,
    job_id            INT NOT NULL,
    recruiter_user_id INT NOT NULL,
    seeker_user_id    INT NOT NULL,
    match_score       INT DEFAULT 0,
    message           TEXT,
    status            ENUM('PENDING','ACCEPTED','DECLINED') DEFAULT 'PENDING',
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE,
    FOREIGN KEY (seeker_user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- ============================================================
-- DEFAULT SEED DATA
-- ============================================================

-- Admin User
INSERT IGNORE INTO users (full_name, email, password, phone, user_type)
VALUES ('Admin User', 'admin@jobportal.com', 'admin123', '9999999999', 'ADMIN');

-- Recruiter User
INSERT IGNORE INTO users (full_name, email, password, phone, user_type)
VALUES ('ABC Recruiter', 'recruit@abc.com', 'recruit123', '9876543210', 'RECRUITER');

-- Showcase Candidate: Keerthivasan
INSERT IGNORE INTO users (full_name, email, password, phone, user_type, skills, qualification, experience)
VALUES ('Keerthivasan', 'keerthi@example.com', 'keerthi123', '9876501234', 'JOB_SEEKER',
        'Java, Spring Boot, MySQL, REST API, Git, Docker, Hibernate, Microservices, HTML, CSS, JavaScript',
        'B.E. Computer Science and Engineering', '3 years');
