package com.rplhub.service;

import com.rplhub.model.Issue;
import com.rplhub.model.QualificationContent;
import com.rplhub.model.QualificationContent.CompetencyContent;
import com.rplhub.model.RawRow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Groups rows by qualification code and validates each group. A qualification with any ERROR is rejected as a
 * whole; nothing is guessed for missing official criteria. Warnings do not block a draft.
 */
@Component
class RowValidator {

    record Accepted(String code, QualificationContent content) {}
    record Result(List<Accepted> accepted, List<Issue> issues, int rejected) {}

    private static final Set<String> TRUE = Set.of("true", "yes", "y", "1");
    private static final Set<String> FALSE = Set.of("false", "no", "n", "0");

    Result validate(List<RawRow> rows) {
        List<Issue> issues = new ArrayList<>();
        Map<String, List<RawRow>> groups = new LinkedHashMap<>();
        for (RawRow r : rows) {
            String code = r.get("qualification_code");
            if (code.isEmpty()) issues.add(Issue.error(r.rowNumber(), null, "qualification_code", "Required value is missing"));
            else groups.computeIfAbsent(code, k -> new ArrayList<>()).add(r);
        }
        List<Accepted> accepted = new ArrayList<>();
        int rejected = 0;
        for (var e : groups.entrySet()) {
            List<Issue> own = new ArrayList<>();
            QualificationContent content = build(e.getKey(), e.getValue(), own);
            issues.addAll(own);
            if (own.stream().anyMatch(i -> i.severity().equals("ERROR"))) rejected++;
            else accepted.add(new Accepted(e.getKey(), content));
        }
        warnSameTitleInFile(accepted, issues);
        return new Result(accepted, issues, rejected);
    }

    private QualificationContent build(String code, List<RawRow> rows, List<Issue> out) {
        RawRow first = rows.get(0);
        for (String col : List.of("title", "trade")) {
            if (first.get(col).isEmpty()) out.add(Issue.error(first.rowNumber(), code, col, "Required value is missing"));
        }
        for (String col : List.of("title", "trade", "nsqf_level", "source")) {
            for (RawRow r : rows) {
                if (!r.get(col).equals(first.get(col)))
                    out.add(Issue.error(r.rowNumber(), code, col, "Conflicts with row " + first.rowNumber() + ": '" + r.get(col) + "' vs '" + first.get(col) + "'"));
            }
        }
        Integer level = null;
        if (first.get("nsqf_level").isEmpty()) {
            out.add(Issue.warning(first.rowNumber(), code, "nsqf_level", "NSQF level not provided"));
        } else {
            level = parseInt(first.get("nsqf_level"));
            if (level == null || level < 1 || level > 10)
                out.add(Issue.error(first.rowNumber(), code, "nsqf_level", "Must be a whole number from 1 to 10"));
        }

        List<CompetencyContent> comps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RawRow r : rows) {
            String cc = r.get("competency_code");
            if (cc.isEmpty()) out.add(Issue.error(r.rowNumber(), code, "competency_code", "Required value is missing"));
            else if (!seen.add(cc)) out.add(Issue.error(r.rowNumber(), code, "competency_code", "Duplicate competency '" + cc + "'"));
            if (r.get("competency_title").isEmpty()) out.add(Issue.error(r.rowNumber(), code, "competency_title", "Required value is missing"));

            Integer max = parseInt(r.get("max_score"));
            if (max == null || max <= 0) out.add(Issue.error(r.rowNumber(), code, "max_score", "Must be a positive whole number"));

            boolean mandatory = true;
            String m = r.get("mandatory").toLowerCase(Locale.ROOT);
            if (m.isEmpty()) out.add(Issue.warning(r.rowNumber(), code, "mandatory", "Not provided; treated as mandatory until reviewed"));
            else if (TRUE.contains(m)) mandatory = true;
            else if (FALSE.contains(m)) mandatory = false;
            else out.add(Issue.error(r.rowNumber(), code, "mandatory", "Must be true/false (or yes/no)"));

            if (r.get("rubric").isEmpty()) out.add(Issue.warning(r.rowNumber(), code, "rubric", "No scoring rubric provided"));
            List<String> evidence = r.get("evidence_requirements").isEmpty() ? List.of()
                : Arrays.stream(r.get("evidence_requirements").split("\\" + FileParser.EVIDENCE_SEPARATOR))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (evidence.isEmpty()) out.add(Issue.warning(r.rowNumber(), code, "evidence_requirements", "No evidence requirements provided"));

            comps.add(new CompetencyContent(cc, r.get("competency_title"), mandatory, max == null ? 0 : max,
                r.get("rubric").isEmpty() ? null : r.get("rubric"), evidence));
        }
        String source = first.get("source");
        return new QualificationContent(first.get("title"), first.get("trade"), level, source, comps);
    }

    private void warnSameTitleInFile(List<Accepted> accepted, List<Issue> issues) {
        Map<String, String> byTitle = new LinkedHashMap<>();
        for (Accepted a : accepted) {
            String key = (a.content().title() + "|" + a.content().trade()).toLowerCase(Locale.ROOT);
            String other = byTitle.putIfAbsent(key, a.code());
            if (other != null)
                issues.add(Issue.warning(null, a.code(), "title", "Possible duplicate of '" + other + "' in this file (same title and trade)"));
        }
    }

    private static Integer parseInt(String s) {
        try { return Integer.valueOf(s); } catch (NumberFormatException e) { return null; }
    }
}
