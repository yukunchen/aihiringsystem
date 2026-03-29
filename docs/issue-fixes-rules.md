# Issue Fix Rules

This file defines stricter rules for GitHub issue-driven bug fixing.

It is especially important for:
- UI / data integration bugs
- DB initialization issues
- environment-specific regressions
- bugs where code changes alone do not prove the visible result is fixed

---

## 1. Core Principle

Do not confuse:
- suspected cause
with
- confirmed root cause

The reporter may suggest a likely cause.
Claude must verify the real DB -> API -> UI chain when applicable.

Do not claim success without evidence.

---

## 2. Scope

These rules apply when Claude is triggered by:
- GitHub issue
- GitHub issue comment
- GitHub PR linked to an issue
- automated issue autofix workflow
- issue-specific staging verification

---

## 3. Status Values

Use exactly one of the following top-level statuses in issue comments or PR descriptions:

- ANALYZING
- NEEDS CLARIFICATION
- READY FOR REVIEW
- BLOCKED
- NOT VERIFIED

Do not use "fixed" as the top-level status unless all required verification evidence is already present.

---

## 4. Required Response Format

Every issue-driven fix response must include the following sections when applicable:

## Status
One of:
- ANALYZING
- NEEDS CLARIFICATION
- READY FOR REVIEW
- BLOCKED
- NOT VERIFIED

## Issue
- issue number or link

## Understanding
- concise restatement of the problem
- target page / endpoint / workflow affected

## Root Cause
- confirmed root cause
- if still unconfirmed, say so explicitly

## Changes Made
- changed files
- changed behavior
- migration / seed / API / frontend / test updates

## Verification
Broken down by:
- DB verification
- API verification
- UI verification
- reproduction verification
- commands/tests run

## Residual Risks
- what remains unverified
- environment gaps
- follow-up risks

## Recommended Next State
One of:
- waiting-review
- qa-needed
- blocked

---

## 5. Required Evidence By Bug Type

### A. UI / Data Integration Bug

Examples:
- dropdown shows no data
- page loads but content is missing
- list is empty unexpectedly
- form cannot submit due to missing reference data
- visible data mismatch

Required evidence:
- target URL
- target environment
- account / role used
- DB row count if lookup/reference data is involved
- exact API endpoint tested
- response sample
- screenshot after fix or browser automation evidence
- confirmation that original reproduction steps no longer fail

### B. DB Initialization Bug

Examples:
- required seed data missing
- lookup/reference tables empty
- app cannot work from a clean DB

Required evidence:
- migration result
- seed result
- row count
- sample records
- environment where verified
- whether reset + migrate + seed path was tested

### C. API Bug

Examples:
- endpoint returns wrong fields
- endpoint returns empty unexpectedly
- contract mismatch between backend and frontend

Required evidence:
- exact endpoint tested
- request parameters
- response status
- response body sample
- payload count or relevant field proof
- confirmation that frontend-consumed fields are correct

### D. Auth / Permission Bug

Examples:
- page blank because role cannot fetch required data
- admin can see data locally but not in target env
- endpoint returns empty due to auth scope

Required evidence:
- exact account / role used
- auth flow tested
- response behavior by role
- target environment verified
- whether the issue is data absence or permission filtering

---

## 6. Verification Rules

### 6.1 Database Verification

When relevant, provide:
- relevant table name
- row count
- sample rows
- whether data came from migration, seed, manual insertion, or existing state

Do not simply say:
- seed added
- DB initialized

without proof.

### 6.2 API Verification

When relevant, provide:
- exact endpoint path
- method
- response status
- response count / non-empty result when expected
- sample payload

Do not treat HTTP 200 alone as success.

### 6.3 UI Verification

For user-visible issues, provide:
- target URL
- account / role used
- what was visible before
- what is visible now
- screenshot or browser automation artifact

If UI was not verified, say so explicitly.

### 6.4 Reproduction Verification

Always re-run the original reproduction flow when possible.

State explicitly whether:
- the original problem still reproduces
- the original problem no longer reproduces
- reproduction could not be completed

---

## 7. Environment Verification

Always state the environment actually verified:
- local
- staging
- target VPS / target environment
- other

If the reported issue was on a specific environment and Claude only verified locally, it must explicitly say:
- local only
- target environment not verified

Do not imply target-environment success from local-only verification.

---

## 8. Closing Rules

Claude must not directly close:
- UI / data integration bugs
- environment-specific bugs
- DB initialization bugs
- issues not verified in the target environment

These should move to:
- waiting-review
- qa-needed
- blocked

A human should decide final closure.

---

## 9. Blocking Conditions

Claude must respond with BLOCKED or NOT VERIFIED if any of the following is true:
- target environment cannot be verified
- screenshot / browser evidence is required but missing
- DB/API/UI chain was not fully checked for a UI/data bug
- root cause is still only a hypothesis
- fix was applied but original reproduction was not re-run
- the issue depends on deployment / seed / config changes not yet applied in the target environment

---

## 10. Recommended Workflow

Recommended issue-driven workflow:

1. Read issue
2. Classify bug type
3. Confirm target environment
4. Verify reproduction
5. Verify DB/API/UI chain as applicable
6. Make minimal fix
7. Add regression coverage when feasible
8. Re-run verification
9. Submit PR or response with evidence
10. Move issue to waiting-review / qa-needed instead of closing directly

---

## 11. Minimal Response Templates

### 11.1 Analyze Template

```md
Status: ANALYZING

Issue:
- #<id>

Understanding:
- ...

Target environment:
- ...

Initial hypotheses:
1. ...
2. ...

Planned verification:
1. DB
2. API
3. UI
4. Reproduction

I will not claim a fix until the required evidence is collected.
```
