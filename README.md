# 🔍 Job Portal System — Complete Full System

A complete, full-featured Job Portal web application built with **Pure Java + JDBC + MySQL** (Spring Boot architecture style), running at `http://localhost:8080`.

## 🚀 How to Run

```bash
# Option 1: Using run.bat (Windows)
run.bat

# Option 2: Manual compile & run
javac -cp "lib/*" -d bin src/*.java
java -cp "bin;lib/*" Main
```

Then open: **http://localhost:8080**

---

## 🔐 Login Credentials

| Role       | Email                    | Password   | Security Password |
|------------|--------------------------|------------|-------------------|
| **Admin**  | admin@jobportal.com      | admin123   | `admin`           |
| **Recruiter** | Register as Recruiter | your choice | `recruiter`    |
| **Job Seeker** | Register as Job Seeker | your choice | (none)        |

---

## 📋 Complete Feature List

### 🏠 Home Page (`/`)
- Hero section with job search
- Category browsing (Java, Technologies, Finance, Data Science, etc.)
- Location-wise quick links
- "How It Works" section

### 🔍 Job Search (`/jobs`)
- Search by keyword (job title, company, skill)
- **Location-wise search** (Bangalore, Chennai, Mumbai, Hyderabad, etc.)
- 50 pre-loaded sample jobs (India-focused tech roles)
- Quick location filter chips
- Job count display

### 📝 Apply for Jobs (`/apply?job_id=X`)
- View complete job details
- Apply with name, email, phone, cover letter
- Pre-filled if logged in as Job Seeker

### 👤 User Registration (`/register`)
- Job Seeker registration (no security password needed)
- Recruiter registration (security password: `recruiter` + company details)
- Admin registration (security password: `admin`)

### 🔑 Login (`/login`)
- Role-based login → auto-redirect to correct dashboard
- Session management (cookie-based)

---

## 🎯 Job Seeker Dashboard (`/seeker/dashboard`)
- Welcome with name
- Application statistics (Total / Applied / Shortlisted / Rejected)
- Recent applications table with status badges
- Quick action links (Search Jobs, Location Search, My Applications)

### My Applications (`/seeker/applications`)
- Full list of all applications with status tracking
- Status badges: Applied, Shortlisted, Rejected, Accepted

### My Profile (`/seeker/profile`)
- View account details

---

## 🏢 Recruiter Dashboard (`/recruiter/dashboard`)
- Stats: Jobs Posted, Total Applications, Active Jobs
- Recent job postings table
- Company name badge

### Post New Job (`/recruiter/jobs/add`)
- Job Title, Description, Skills Required
- Qualification, Experience, Salary
- Location, Job Type (Full-time/Part-time/Contract/Internship)

### My Jobs (`/recruiter/jobs`)
- Full list of all posted jobs
- Application count per job
- Edit / Delete actions

### View Applications (`/recruiter/applications`)
- All candidate applications with filter by job
- Candidate name, email, cover letter preview
- **Shortlist** / **Reject** candidates with one click
- Status badges (Applied → Shortlisted → Accepted/Rejected)

---

## ⚙️ Admin Dashboard (`/admin/dashboard`)
- System-wide statistics (Users, Recruiters, Jobs, Applications)
- Module cards for quick navigation

### Manage Users (`/admin/users`)
- All registered users with role badges
- Delete any user (except current admin)

### Manage Recruiters (`/admin/recruiters`)
- All recruiters with company info and job count

### Manage Jobs (`/admin/jobs`)
- All job postings across all recruiters
- View details / Delete any job

---

## 🗄️ Database Schema

```sql
users        → user_id, full_name, email, password, phone, user_type
companies    → company_id, company_name, location, recruiter_user_id
jobs         → job_id, company_id, recruiter_user_id, job_title, ...
applications → application_id, job_id, user_id, full_name, email, ...
```

**Run schema:** `mysql -u root -p < database/schema.sql`

---

## 🛠️ Technology Stack

| Layer       | Technology                    |
|-------------|-------------------------------|
| Language    | Java 21                       |
| Web Server  | Java `com.sun.net.httpserver` |
| ORM/DB      | JDBC + MySQL (Hibernate-style) |
| Database    | MySQL 8.x                     |
| Session     | Cookie-based in-memory sessions |
| Frontend    | HTML5, CSS3, Vanilla JS       |
| Build       | Maven                         |
| IDE         | NetBeans                      |

---

## 🌐 URL Routes

| URL | Description |
|-----|-------------|
| `/` | Home page |
| `/jobs` | Job search + location search |
| `/jobs?keyword=Java` | Search by keyword |
| `/jobs?location=Bangalore` | Location-wise search |
| `/apply?job_id=1` | Job details + apply form |
| `/register` | User registration |
| `/login` | Login |
| `/logout` | Logout |
| `/seeker/dashboard` | Job seeker dashboard |
| `/seeker/applications` | My applications |
| `/seeker/profile` | My profile |
| `/recruiter/dashboard` | Recruiter dashboard |
| `/recruiter/jobs` | My job listings |
| `/recruiter/jobs/add` | Post new job |
| `/recruiter/jobs/edit?job_id=X` | Edit job |
| `/recruiter/jobs/delete?job_id=X` | Delete job |
| `/recruiter/applications` | View all applications |
| `/admin/dashboard` | Admin dashboard |
| `/admin/users` | Manage users |
| `/admin/recruiters` | Manage recruiters |
| `/admin/jobs` | Manage all jobs |
| `/api/chat/message` | AI Resume Coach chat & STAR bullet rewriter API |
| `/api/chat/evaluate` | Resume upload evaluation & ATS scoring API |
| `/api/chat/profile` | Active user resume retrieval API |
| `/api/chat/sample` | Pre-configured demonstration profile loader |

---

## 🤖 SmartHire AI Resume Coach Chatbot

Available across all pages on the portal via the floating 🤖 avatar in the bottom-right corner and navbar link.

### Features
1. **Multi-Domain Pre-Trained Intelligence (40+ Roles)**:
   - Full Stack, Java Backend, React Frontend, DevOps & Cloud, AI/ML & Data Science, Cybersecurity, Mobile, QA/SDET, Big Data, and more.
2. **Comprehensive Resume Evaluation**:
   - ATS Compatibility Score (0-100) with pass/fail/warning diagnostics
   - Overall Resume Strength Score (0-100)
   - Quantified metrics counter and action verb density scanner
   - Passive voice detector ("worked on", "responsible for" flags)
   - Section-by-section health audit (Header, Summary, Skills, Experience, Certifications)
3. **"Make it More Advanced" — STAR & Google X-Y-Z Bullet Rewriter**:
   - Transforms weak bullets into executive STAR bullets with metrics, scale, and active verbs.
   - 1-click **Copy to Clipboard** button.
4. **Tailored Executive Summary Generator**:
   - Generates 3 styles of professional summaries (Impact-Driven, Tech-Driven, Leadership-Driven).
5. **Interactive Mock Interview Simulator**:
   - 5 tailored technical & architectural questions with model answers matching candidate tech stack.
6. **Live Job Matching & Salary Benchmarks**:
   - Matches candidate skills against live database vacancies with 1-click apply links.
7. **Drag-and-Drop & One-Click Sample Resumes**:
   - Direct PDF, DOCX, and TXT upload, or test with Keerthivasan / Priya Patel profiles.
