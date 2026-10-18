


AI-Assisted RPL Assessment Platform
1. Project Overview
The AI-Assisted Recognition of Prior Learning (RPL) Assessment Platform helps workers get their existing skills formally assessed, even if they learned those skills through work experience rather than formal training.
The platform supports multiple trades through configurable qualification data and assessment criteria. AI helps match workers to relevant qualifications and summarize assessment results. Human assessors remain responsible for evaluating practical skills and making decisions according to applicable rules.
The platform also uses durable execution to manage long-running assessment workflows. If a service restarts or an assessor takes time to respond, the workflow can resume from its saved progress rather than starting over.
2. Problem Statement
Many workers have practical trade skills but lack formal credentials. Current RPL assessments can involve manual evaluation, which may be slow, difficult to scale, and inconsistent.
The platform aims to make RPL assessments easier to manage, more consistent, and more accessible, including for users with limited internet connectivity.
3. Project Objectives
* Help workers declare their experience and skills.
* Match workers to relevant, approved qualification packs.
* Provide standardized assessment checklists and scoring rubrics.
* Help assessors review evidence and produce competency reports.
* Maintain a clear record of assessment progress and decisions.
* Support qualification data from official public sources and administrator-uploaded files.
* Preserve workflow progress during delays and recoverable service failures.
* Support offline form and evidence capture with later synchronization.
4. User Roles
Worker / Candidate
* Sign in using a phone number and OTP.
* Create and update a profile.
* Enter trade, experience, skills, and supporting details.
* View suggested qualification matches and assessment status.
* Submit requested evidence and view permitted results.
Administrator
* Import qualification data from supported sources and files.
* Review import errors and correct qualification drafts.
* Review and confirm AI-suggested qualification matches.
* Approve and publish qualification versions, if authorized.
* Assign eligible assessors to assessment cases.
* Monitor workflow status and view audit records.
Assessor
* View assessments assigned to them.
* Review worker declarations and evidence.
* Evaluate competencies using the relevant checklist and scoring rubric.
* Record scores, observations, and evidence.
* Request additional evidence and submit an assessment recommendation.
* Record a final decision only if authorized by the applicable rules.
5. Main Features and Requirements
5.1 Worker Registration and Self-Declaration
* Workers register or sign in using phone number and OTP.
* Workers submit their trade, experience, skills, and supporting information.
* The system validates required fields and saves progress.
* Each submitted case receives a unique identifier.
5.2 Qualification Catalogue for Multiple Trades
* The platform is designed to support all eligible trades for which suitable qualification data is available.
* Qualification packs, competencies, evidence requirements, and scoring rubrics are configurable data rather than hard-coded trade-specific logic.
* Each qualification has an identifier, title, trade or sector, NSQF level where applicable, source, version, and status.
* Each assessment references the exact qualification version used.
5.3 Qualification Data Import
Administrators can add qualification data through:
* Public official sources, where access and reuse are permitted.
* Administrator-uploaded CSV, Excel, and JSON files.
The system must:
* Validate file formats and required fields.
* Map source fields to a common internal structure.
* Identify missing fields, invalid values, broken references, and possible duplicates.
* Show errors and warnings for affected records.
* Keep imported qualifications in draft status until reviewed and approved.
* Record source information, import results, and approval history.
* Avoid creating duplicate records when the same data is imported again.
The system must not invent missing official qualification criteria. Only approved and published qualification versions can be used for matching and assessment.
5.4 AI-Assisted Qualification Matching
* Compare a worker's declared trade and skills with approved qualification packs.
* Suggest relevant matches and provide a brief explanation.
* Flag unclear or low-confidence matches for review.
* Require an administrator to confirm the selected qualification before assessment.
* Record the suggestion and the administrator's selection.
AI recommendations are advisory and do not replace human review.
5.5 Assessor Assignment
* Recommend eligible assessors based on relevant trade or qualification authorization.
* Allow an administrator to review and confirm the assignment.
* Record the assignment against the assessment case.
* Keep the case pending if no eligible assessor is available.
5.6 Practical Assessment
* Provide qualification-specific checklists and scoring rubrics.
* Display assessment criteria and required evidence for each competency.
* Allow assessors to record scores, comments, and evidence.
* Validate score ranges and flag missing mandatory information.
* Keep automated image or video evaluation optional for the initial version.
5.7 Competency Report and Decision
* Generate a report showing competency-wise scores, evidence, missing requirements, and skill gaps.
* Distinguish AI-generated summaries from assessor-entered findings.
* Identify AI output as a recommendation, not an official certification decision.
* Allow an authorized human decision-maker to record the outcome.
* Maintain traceable records of decisions, corrections, and overrides.
* Keep official certificate issuance outside the initial scope unless an authorized integration is available.
5.8 Durable Execution
Durable execution coordinates long-running workflows such as qualification processing, assessor assignment, evidence collection, and assessment review.
The system must:
* Save workflow progress.
* Resume from persisted progress after a workflow worker or backend service restarts.
* Wait for human actions without restarting completed steps.
* Retry temporary failures safely.
* Prevent duplicate assessment cases or final decisions when requests are repeated.
* Show pending, failed, and completed workflow states.
* Allow authorized users to inspect failures and retry or resume where appropriate.
Durable execution does not replace database persistence, access control, or offline synchronization. Database writes and external calls must be designed to handle retries safely.
5.9 Offline Support
* Allow supported forms and evidence to be saved locally while offline.
* Show whether data is saved locally, waiting to sync, or synchronized.
* Synchronize data when connectivity returns.
* Prevent duplicate submissions during repeated synchronization.
* Detect conflicting updates and handle them safely.
The initial version focuses on offline form and evidence capture, not making every feature fully available offline.
5.10 Audit and Security
* Enforce role-based access control.
* Allow workers to access only their permitted records.
* Restrict assessors to their assigned cases.
* Require appropriate permissions for administrative actions.
* Record qualification approvals, assessment submissions, score changes, and final decisions with the responsible user and timestamp.
* Protect personal information and uploaded evidence from unauthorized access.
6. Main Assessment Workflow
1. Worker signs in and submits a self-declaration.
2. The system suggests relevant approved qualifications.
3. An administrator reviews and confirms the selected qualification.
4. The system creates the assessment case and recommends an eligible assessor.
5. The administrator confirms the assessor assignment.
6. The assessor conducts the practical assessment and records scores and evidence.
7. The system generates a competency report and recommendation.
8. The authorized decision-maker records the assessment outcome.
9. The worker views the permitted status and results.
If more evidence is required, the assessment returns to the appropriate evidence or assessment step while preserving previous progress and history.
7. Qualification Import Workflow
1. The administrator uploads a file or initiates an import from a supported official source.
2. The system parses and validates the data.
3. The system reports accepted records, invalid records, warnings, and possible duplicates.
4. Valid records are saved as drafts.
5. The administrator reviews and corrects the drafts.
6. An authorized administrator approves and publishes the qualification version.
7. The approved version becomes available to matching and assessment processes.
8. Business Rules
* Only approved and published qualification versions may be used for matching and assessment.
* Qualification data must be reviewed before publication.
* Each assessment must reference the qualification version used to evaluate the worker.
* AI matching and generated summaries are advisory.
* Only authorized human personnel may record final assessment decisions.
* Incomplete mandatory assessment criteria must be flagged before finalization.
* Assessment decisions and score changes must remain traceable.
* Retrying a workflow must not create duplicate business records.
* Qualification changes must not silently alter an assessment already in progress.
* Assessment and certificate-issuance rules must follow the applicable RPL scheme and authorized body's requirements.
9. Non-Functional Requirements
* Reliability: Saved progress should survive recoverable service failures.
* Security: Enforce role-based access and protect personal data.
* Usability: Provide clear, mobile-friendly screens and understandable messages.
* Performance: Common operations should respond promptly under the expected demo workload.
* Auditability: Important actions and decisions must be traceable.
* Maintainability: New trades and qualification packs should be added through configuration wherever possible.
* Offline capability: Supported forms and evidence should be saved locally and synchronized safely.
* Data integrity: Validation, versioning, and duplicate prevention must protect assessment records.
10. Suggested Technology Stack
This is a proposed stack and can be changed based on team experience.
* Frontend: React Progressive Web App (PWA)
* Backend: Java with springbok 
* Database: PostgreSQL
* AI matching: Anthropic client 
* Durable execution: Temporal java sdk
* Offline capture: PWA local storage or IndexedDB with synchronization logic
* Reports: HTML/PDF report generation - hbs files for templates

