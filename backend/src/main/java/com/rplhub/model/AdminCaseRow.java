package com.rplhub.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A submitted case with its workflow state (pending / failed / completed). */
public record AdminCaseRow(@JsonProperty("case") CaseRow caseRow, WorkflowStatus workflow) {}
