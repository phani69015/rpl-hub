package com.rplhub.model;

import org.springframework.core.io.Resource;

public record EvidenceDownload(Resource file, String filename, String contentType) {}
