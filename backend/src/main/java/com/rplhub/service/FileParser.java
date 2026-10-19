package com.rplhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rplhub.exception.ImportFormatException;
import com.rplhub.model.RawRow;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Maps CSV, Excel (.xlsx, first sheet) and JSON into flat rows with the common column names.
 * Structural problems (unreadable file, missing required columns) throw ImportFormatException.
 */
@Component
class FileParser {

    static final List<String> COLUMNS = List.of(
        "qualification_code", "title", "trade", "nsqf_level", "source",
        "competency_code", "competency_title", "mandatory", "max_score", "rubric", "evidence_requirements");
    static final Set<String> REQUIRED_COLUMNS = Set.of(
        "qualification_code", "title", "trade", "competency_code", "competency_title", "max_score");
    static final String EVIDENCE_SEPARATOR = "|";

    private final ObjectMapper mapper;

    FileParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String sourceType(String filename) {
        String f = filename.toLowerCase(Locale.ROOT);
        if (f.endsWith(".csv")) return "CSV";
        if (f.endsWith(".xlsx")) return "EXCEL";
        if (f.endsWith(".json")) return "JSON";
        throw new ImportFormatException("Unsupported file type; use .csv, .xlsx or .json");
    }

    List<RawRow> parse(String filename, byte[] content) {
        try {
            List<RawRow> rows = switch (sourceType(filename)) {
                case "CSV" -> csv(content);
                case "EXCEL" -> excel(content);
                default -> json(content);
            };
            if (rows.isEmpty()) throw new ImportFormatException("File contains no data rows");
            return rows;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ImportFormatException f) throw f;
            throw new ImportFormatException("Could not read file: " + e.getMessage());
        }
    }

    private List<RawRow> csv(byte[] content) throws IOException {
        // strip a UTF-8 BOM (common in Excel exports)
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        var format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true).setTrim(true).build();
        try (CSVParser p = format.parse(new java.io.StringReader(text))) {
            List<String> headers = p.getHeaderNames().stream().map(FileParser::normalise).toList();
            requireColumns(headers);
            List<RawRow> rows = new ArrayList<>();
            for (CSVRecord r : p) {
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < headers.size() && i < r.size(); i++) values.put(headers.get(i), r.get(i));
                rows.add(new RawRow((int) r.getRecordNumber() + 1, values)); // +1: header is row 1
            }
            return rows;
        }
    }

    private List<RawRow> excel(byte[] content) throws IOException {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheetAt(0);
            var fmt = new DataFormatter();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw new ImportFormatException("Sheet is empty");
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < header.getLastCellNum(); c++) headers.add(normalise(fmt.formatCellValue(header.getCell(c))));
            requireColumns(headers);
            List<RawRow> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> values = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < headers.size(); c++) {
                    String v = fmt.formatCellValue(row.getCell(c)).trim();
                    any |= !v.isEmpty();
                    values.put(headers.get(c), v);
                }
                if (any) rows.add(new RawRow(r + 1, values));
            }
            return rows;
        }
    }

    /** JSON: array of qualifications, each with a "competencies" array. Flattened to the same columns. */
    private List<RawRow> json(byte[] content) throws IOException {
        JsonNode root = mapper.readTree(content);
        if (root == null || !root.isArray()) throw new ImportFormatException("JSON must be an array of qualifications");
        List<RawRow> rows = new ArrayList<>();
        int n = 0;
        for (JsonNode q : root) {
            JsonNode comps = q.path("competencies");
            if (!comps.isArray() || comps.isEmpty()) {
                n++;
                rows.add(new RawRow(n, header(q, null)));
                continue;
            }
            for (JsonNode c : comps) {
                n++;
                rows.add(new RawRow(n, header(q, c)));
            }
        }
        return rows;
    }

    private Map<String, String> header(JsonNode q, JsonNode c) {
        Map<String, String> v = new LinkedHashMap<>();
        for (String col : List.of("qualification_code", "title", "trade", "nsqf_level", "source")) v.put(col, text(q.get(col)));
        if (c != null) {
            for (String col : List.of("competency_code", "competency_title", "mandatory", "max_score", "rubric"))
                v.put(col, text(c.get(col)));
            JsonNode ev = c.path("evidence_requirements");
            if (ev.isArray()) {
                List<String> items = new ArrayList<>();
                ev.forEach(e -> items.add(e.asText().trim()));
                v.put("evidence_requirements", String.join(EVIDENCE_SEPARATOR, items));
            } else {
                v.put("evidence_requirements", text(ev));
            }
        }
        return v;
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() ? "" : n.asText().trim();
    }

    private static String normalise(String h) {
        return h == null ? "" : h.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static void requireColumns(List<String> headers) {
        var missing = REQUIRED_COLUMNS.stream().filter(c -> !headers.contains(c)).sorted().toList();
        if (!missing.isEmpty()) throw new ImportFormatException("Missing required columns: " + String.join(", ", missing));
    }
}
