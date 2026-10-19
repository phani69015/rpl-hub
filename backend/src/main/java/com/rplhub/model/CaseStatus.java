package com.rplhub.model;

/** Lifecycle of an RPL case; stored as text in rpl_case.status. */
public final class CaseStatus {

    public static final String DRAFT = "DRAFT";
    public static final String NEW = "NEW";
    public static final String AWAITING_QUALIFICATION = "AWAITING_QUALIFICATION";
    public static final String AWAITING_ASSESSOR = "AWAITING_ASSESSOR";
    public static final String IN_ASSESSMENT = "IN_ASSESSMENT";
    public static final String EVIDENCE_REQUESTED = "EVIDENCE_REQUESTED";
    public static final String REPORT_PENDING = "REPORT_PENDING";
    public static final String AWAITING_DECISION = "AWAITING_DECISION";
    public static final String DECIDED = "DECIDED";

    private CaseStatus() {}
}
