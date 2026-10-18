package com.rplhub.report;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.rplhub.cases.CaseView;
import com.rplhub.qualification.QualificationRepository.CompetencyView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Builds the competency report from case data and renders it from the Handlebars template (HTML, or PDF). */
@Service
public class ReportService {

    private final Template template;
    private final int gapPercent;

    ReportService(@Value("${app.report.gap-threshold-percent}") int gapPercent) throws IOException {
        this.template = new Handlebars(new ClassPathTemplateLoader("/templates", ".hbs")).compile("report");
        this.gapPercent = gapPercent;
    }

    Map<String, Object> model(CaseView c) {
        var q = c.qualification();
        var scores = c.scores().stream().collect(Collectors.toMap(CaseView.ScoreView::competencyId, Function.identity()));
        Map<UUID, List<CaseView.EvidenceView>> evidenceBy = c.evidence().stream().filter(e -> e.competencyId() != null)
            .collect(Collectors.groupingBy(CaseView.EvidenceView::competencyId));

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> gaps = new ArrayList<>(), missing = new ArrayList<>();
        int total = 0, max = 0;
        for (CompetencyView comp : q.competencies()) {
            var s = scores.get(comp.id());
            var ev = evidenceBy.getOrDefault(comp.id(), List.of());
            boolean scored = s != null;
            int percent = scored ? Math.round(100f * s.score() / comp.maxScore()) : 0;
            max += comp.maxScore();
            if (scored) total += s.score();
            if (!scored) missing.add(comp.code() + " " + comp.title() + ": not scored" + (comp.mandatory() ? " (mandatory)" : ""));
            else if (percent < gapPercent) gaps.add(comp.code() + " " + comp.title() + ": " + s.score() + "/" + comp.maxScore());
            if (ev.isEmpty() && !comp.evidenceRequirements().isEmpty()) missing.add(comp.code() + " " + comp.title() + ": no evidence attached");

            var row = new LinkedHashMap<String, Object>();
            row.put("code", comp.code()); row.put("title", comp.title()); row.put("mandatory", comp.mandatory());
            row.put("scored", scored); row.put("score", scored ? s.score() : null); row.put("max", comp.maxScore()); row.put("percent", percent);
            row.put("comment", scored ? s.comment() : null); row.put("rubric", comp.rubric());
            row.put("required", comp.evidenceRequirements());
            row.put("evidence", ev.stream().map(e -> e.filename() + (e.note() == null ? "" : " – " + e.note())).toList());
            row.put("gap", scored && percent < gapPercent);
            rows.add(row);
        }
        var m = new LinkedHashMap<String, Object>();
        m.put("caseNumber", c.caseNumber()); m.put("generatedAt", OffsetDateTime.now().toString());
        m.put("workerName", c.workerName()); m.put("workerPhone", c.workerPhone());
        m.put("trade", c.trade()); m.put("experienceYears", c.experienceYears()); m.put("skills", c.skills());
        var qm = q.summary();
        m.put("qualification", Map.of("code", qm.code(), "title", qm.title(), "version", qm.version(), "trade", qm.trade(),
            "nsqf", qm.nsqfLevel() == null ? "n/a" : qm.nsqfLevel().toString(), "source", qm.source()));
        m.put("rows", rows); m.put("gaps", gaps); m.put("missing", missing);
        m.put("total", total); m.put("max", max); m.put("gapPercent", gapPercent);
        var sub = c.submission();
        m.put("submission", sub == null ? null : Map.of("recommendation", sub.recommendation(), "notes", nz(sub.notes())));
        var dec = c.decision();
        m.put("decision", dec == null ? null : Map.of("outcome", dec.outcome(), "rationale", nz(dec.rationale()),
            "decidedBy", dec.decidedBy(), "decidedAt", dec.decidedAt().toString()));
        m.put("aiSummary", c.submission() == null ? null : c.submission().aiSummary());
        return m;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public String html(CaseView c) {
        try {
            return template.apply(model(c));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public byte[] pdf(CaseView c) {
        try (var out = new ByteArrayOutputStream()) {
            var b = new PdfRendererBuilder();
            b.withHtmlContent(html(c), null);
            b.toStream(out);
            b.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }

    /** Compact text for the AI summary prompt. */
    String plainText(CaseView c) {
        var m = model(c);
        var sb = new StringBuilder();
        sb.append("Qualification: ").append(((Map<?, ?>) m.get("qualification")).get("title")).append("\n");
        for (Object r : (List<?>) m.get("rows")) {
            var row = (Map<?, ?>) r;
            sb.append("- ").append(row.get("title")).append(row.get("mandatory").equals(true) ? " (mandatory)" : "")
              .append(": ").append(row.get("scored").equals(true) ? row.get("score") + "/" + row.get("max") : "not scored")
              .append("; evidence files: ").append(((List<?>) row.get("evidence")).size())
              .append(row.get("comment") == null ? "" : "; assessor comment: " + row.get("comment")).append("\n");
        }
        if (c.submission() != null) sb.append("Assessor recommendation: ").append(c.submission().recommendation()).append("\n")
            .append("Assessor notes: ").append(c.submission().notes() == null ? "" : c.submission().notes()).append("\n");
        return sb.toString();
    }
}
