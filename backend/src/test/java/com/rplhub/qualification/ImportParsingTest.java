package com.rplhub.qualification;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ImportParsingTest {

    private final FileParser parser = new FileParser(new ObjectMapper());
    private final RowValidator validator = new RowValidator();

    private RowValidator.Result run(String file) throws Exception {
        Path p = Path.of("../samples", file);
        return validator.validate(parser.parse(file, Files.readAllBytes(p)));
    }

    @Test
    void csvAcceptsValidRejectsInvalidAndFlagsDuplicates() throws Exception {
        var r = run("qualifications.csv");
        assertEquals(3, r.accepted().size());
        assertEquals(2, r.rejected());
        assertTrue(r.issues().stream().anyMatch(i -> "WELD-L4-001".equals(i.qualificationCode()) && i.message().contains("Duplicate competency")));
        assertTrue(r.issues().stream().anyMatch(i -> "CARP-L2-001".equals(i.qualificationCode()) && "nsqf_level".equals(i.field())));
        assertTrue(r.issues().stream().anyMatch(i -> "CARP-L2-001".equals(i.qualificationCode()) && "max_score".equals(i.field())));
        assertTrue(r.issues().stream().anyMatch(i -> i.message().startsWith("Possible duplicate")));
        assertTrue(r.issues().stream().anyMatch(i -> "PLUMB-L3-001".equals(i.qualificationCode()) && "rubric".equals(i.field())));
    }

    @Test
    void jsonMapsToSameStructure() throws Exception {
        var r = run("qualifications.json");
        assertEquals(1, r.accepted().size());
        var c = r.accepted().get(0).content();
        assertEquals(2, c.competencies().size());
        assertEquals(2, c.competencies().get(0).evidenceRequirements().size());
        assertFalse(c.competencies().get(1).mandatory());
    }

    @Test
    void identicalContentHashesEqualAndChangesDiffer() throws Exception {
        var a = run("qualifications.csv").accepted().get(0).content();
        var b = run("qualifications.csv").accepted().get(0).content();
        assertEquals(a.contentHash(), b.contentHash());
        var changed = new QualificationContent(a.title() + "x", a.trade(), a.nsqfLevel(), a.source(), a.competencies());
        assertNotEquals(a.contentHash(), changed.contentHash());
    }

    @Test
    void missingRequiredColumnIsFormatError() {
        var ex = assertThrows(FileParser.ImportFormatException.class,
            () -> parser.parse("x.csv", "qualification_code,title\nA,B\n".getBytes()));
        assertTrue(ex.getMessage().contains("trade"));
    }
}
