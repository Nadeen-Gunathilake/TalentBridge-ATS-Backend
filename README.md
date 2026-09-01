# TalentBridge ATS

A backend REST API for a single-company Applicant Tracking System — built to replace the scattered mess of job boards, inboxes, and spreadsheets that most hiring teams still run on. Candidates get one portal to browse roles and apply; recruiters get one dashboard to review, rate, and move applicants through a real hiring pipeline, with internal data that never leaks to the outside.

**Production:**[View Live Website](https://talentbridge-ats.up.railway.app)

## Highlights

- **Role-aware data boundaries.** Candidates and recruiters never see the same shape of data — ratings and internal notes are structurally excluded from anything a candidate can receive, not filtered out at runtime.
- **A hiring pipeline with real rules.** Status changes are validated against an explicit transition graph (`APPLIED → UNDER_REVIEW → SHORTLISTED → INTERVIEW → OFFER → HIRED`, with `REJECTED`/`WITHDRAWN` exits) — illegal jumps are rejected outright, not silently accepted.
- **Ownership enforced on every read.** No record is returned to a candidate without first confirming they own it — closing the most common vulnerability class in systems like this (insecure direct object reference).
- **Stateless JWT authentication** with role-based authorization at both the endpoint and method level.
- **Dynamic, filterable search** on jobs and applications — work mode, employment type, location, keyword, status, sorting, and pagination, backed by JPA Specifications rather than a pile of hand-written queries.

## Roles

| Role | Access | Capabilities |
|---|---|---|
| **Candidate** | Self-registration | Browse open roles, apply once per job (with resume + cover note), track and withdraw their own applications |
| **Recruiter** | Seeded, not self-registered | Post and manage jobs, review every applicant, rate and annotate them, drive the hiring pipeline, download resumes |

Recruiter accounts are deliberately never opened to public registration — the first account is seeded on startup, and only an authenticated recruiter can create another. This mirrors how access is actually provisioned inside a real company: HR doesn't let anyone sign up as staff.

## Architecture

- **Layered design** — controller → service → repository, with DTOs at every boundary. Entities never cross into an HTTP response.
- **Two response models per resource where it matters.** `ApplicationResponseDTO` (candidate-facing) and `ApplicationRecruiterViewDTO` (recruiter-facing) are separate classes, not one object with fields conditionally hidden — a rating has nowhere to travel through on the candidate side, so it can't leak by a missed `if`.
- **Centralized exception handling** — a single `@RestControllerAdvice` maps domain exceptions (not-found, ownership violations, illegal pipeline moves, validation failures) to consistent, correctly-coded HTTP responses instead of leaking stack traces.
- **JOINED table inheritance** for `User` → `Candidate` / `Recruiter`, keeping shared identity fields normalized while letting each role carry its own attributes.
- **Pipeline and lifecycle rules centralized** in dedicated validator classes (`ApplicationPipeline`), rather than scattered as inline conditionals across services — one place to read, audit, and extend the rules.

## Tech stack

Java 21 · Spring Boot 4.1 · Spring Security (JWT, stateless) · Spring Data JPA (Hibernate) · MySQL 8 · Bean Validation · JJWT · Lombok

## Running it

**Prerequisites:** Java 21, Maven, a running MySQL 8 instance.

1. Set the required environment variables — nothing sensitive is hardcoded:

    DB_USERNAME=root
    DB_PASSWORD=your-mysql-password
    JWT_SECRET=a-random-string-at-least-32-characters-long

2. From the project root:

   mvn spring-boot:run

The database is created automatically on first connection; Hibernate manages the schema from there.
3. The API is live at `http://localhost:8080`. A recruiter account is seeded automatically on first startup (see [Configuration](#configuration)).

Import the endpoint reference below into Postman (or any REST client) to exercise the API.

## Configuration

Default seed credentials for the first recruiter account (override via environment variables before first run if needed):

app.admin.name=Company HR
app.admin.email=hr@talentbridge.com
app.admin.password=Hr@123
app.admin.recruiter-type=RECRUITER

The seeder checks by email and only runs once, so it's safe across restarts.

## API reference

All endpoints are prefixed `/api` except auth. JWT required unless marked public.

### Auth

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/auth/register` | Public | Register as a candidate |
| POST | `/auth/login` | Public | Log in, receive a JWT |

### Jobs

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/jobs` | Public | Browse — filter by `workMode`, `employmentType`, `location`, `keyword`; sort and paginate. Candidates always see `OPEN` only |
| GET | `/api/jobs/{id}` | Public | View a single job (closed jobs hidden from non-recruiters) |
| POST | `/api/jobs` | Recruiter | Create a job posting |
| PUT | `/api/jobs/{id}` | Recruiter | Update a job's content |
| PUT | `/api/jobs/status/{id}` | Recruiter | Move through `DRAFT → OPEN → CLOSED` (validated) |
| DELETE | `/api/jobs/{id}` | Recruiter | Delete a job |

### Applications — candidate side

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/applications` | Candidate | List your own applications |
| GET | `/api/applications/{id}` | Candidate (owner only) | View one of your own applications |
| POST | `/api/applications/jobs/{jobId}` | Candidate | Apply to an open job — multipart form: optional `coverNote`, optional `resume` (PDF, ≤5MB) |
| PUT | `/api/applications/{id}/withdraw` | Candidate (owner only) | Withdraw — only from an active stage |

### Applications — recruiter side

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/applications/jobs/{jobId}` | Recruiter | Applications for a job — filter by `status`, sort by rating/date, paginate |
| GET | `/api/applications/recruiter/{id}` | Recruiter | Full detail on one application, including candidate identity and resume availability |
| PUT | `/api/applications/{id}/status` | Recruiter | Advance the pipeline (validated transitions only) |
| PUT | `/api/applications/{id}/rating` | Recruiter | Set a 1–5 rating |
| POST | `/api/applications/{id}/notes` | Recruiter | Add a timestamped internal note (never shown to candidates) |
| GET | `/api/applications/{id}/notes` | Recruiter | List internal notes |
| GET | `/api/applications/{id}/resume` | Recruiter | Download the candidate's uploaded resume |

### Accounts

| Method | Path | Access | Description |
|---|---|---|---|
| GET / PUT / DELETE | `/api/candidates/{id}` | Candidate (owner only) | Manage your own profile |
| GET / PUT / DELETE | `/api/recruiters/{id}` | Recruiter | Manage recruiter accounts |
| POST | `/api/recruiters` | Recruiter | Create another recruiter account |

## The hiring pipeline

APPLIED → UNDER_REVIEW → SHORTLISTED → INTERVIEW → OFFER → HIRED

- **REJECTED** — a recruiter may reject from any active stage.
- **WITHDRAWN** — only the owning candidate may withdraw, only from an active stage.
- Any other move — skipping a stage, going backward, a recruiter attempting to withdraw on a candidate's behalf — is rejected with `409 Conflict`.

## Roadmap

This is the backend, built first and deliberately. The two roles already map to two clear frontends: a public careers site for candidates and a recruiter dashboard — and since the API's response shapes already encode exactly what each screen should show, that phase is largely presentation on top of what's here.