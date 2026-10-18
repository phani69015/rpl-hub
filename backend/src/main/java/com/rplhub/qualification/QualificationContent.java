package com.rplhub.qualification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** The versioned content of a qualification (everything except its stable code). Also the draft-edit payload. */
record QualificationContent(
    @NotBlank String title,
    @NotBlank String trade,
    @Min(1) @Max(10) Integer nsqfLevel,
    @NotBlank String source,
    @NotEmpty List<@Valid CompetencyContent> competencies) {

    record CompetencyContent(
        @NotBlank String code,
        @NotBlank String title,
        boolean mandatory,
        @Positive int maxScore,
        String rubric,
        List<@NotBlank String> evidenceRequirements) {}

    /** Hash of the assessable content. Source is excluded so a re-import from another file is still a duplicate. */
    String contentHash() {
        var sb = new StringBuilder();
        field(sb, title); field(sb, trade); field(sb, String.valueOf(nsqfLevel));
        competencies.stream().sorted(Comparator.comparing(CompetencyContent::code)).forEach(c -> {
            field(sb, c.code()); field(sb, c.title()); field(sb, String.valueOf(c.mandatory()));
            field(sb, String.valueOf(c.maxScore())); field(sb, c.rubric() == null ? "" : c.rubric());
            for (String e : c.evidenceRequirements() == null ? List.<String>of() : c.evidenceRequirements()) field(sb, e);
            sb.append('\u001e');
        });
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void field(StringBuilder sb, String s) {
        sb.append(s.trim()).append('\u001f');
    }
}
