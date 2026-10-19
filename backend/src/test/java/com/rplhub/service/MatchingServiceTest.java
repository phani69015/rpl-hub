package com.rplhub.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rplhub.model.CaseRow;
import com.rplhub.model.CompetencyView;
import com.rplhub.model.VersionDetail;
import com.rplhub.model.VersionSummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchingServiceTest {

    private final MatchingService service = new MatchingService(null, new ObjectMapper());
    private final UUID elec = UUID.randomUUID(), plumb = UUID.randomUUID();

    private VersionDetail q(UUID id, String title, String trade, String... comps) {
        return new VersionDetail(new VersionSummary(id, "C-" + id.toString().substring(0, 4), 1, title, trade, 4, "src", "PUBLISHED", null, null),
            java.util.Arrays.stream(comps).map(c -> new CompetencyView(UUID.randomUUID(), c, c, true, 10, null, List.of())).toList());
    }

    @Test
    void aiOutputIsRestrictedToPublishedCandidatesAndClamped() {
        var candidates = List.of(q(elec, "Electrician", "Electrical", "wiring"));
        String model = "Here you go: [{\"id\":\"" + elec + "\",\"confidence\":1.7,\"explanation\":\"fits\"},"
            + "{\"id\":\"" + UUID.randomUUID() + "\",\"confidence\":0.9,\"explanation\":\"invented\"},"
            + "{\"id\":\"" + elec + "\",\"confidence\":0.2,\"explanation\":\"dup\"}]";
        var out = service.parse(model, candidates);
        assertEquals(1, out.size());
        assertEquals(1.0, out.get(0).confidence());
        assertFalse(out.get(0).flagged());
    }

    @Test
    void lowConfidenceIsFlagged() {
        var out = service.parse("[{\"id\":\"" + elec + "\",\"confidence\":0.3,\"explanation\":\"weak\"}]", List.of(q(elec, "E", "Electrical")));
        assertTrue(out.get(0).flagged());
    }

    @Test
    void unparseableOutputThrowsSoActivityRetries() {
        assertThrows(IllegalStateException.class, () -> service.parse("sorry, cannot", List.of(q(elec, "E", "Electrical"))));
    }

    private static CaseRow declaration(String trade, String skills) {
        return new CaseRow(UUID.randomUUID(), "RPL-1", UUID.randomUUID(), trade, 5, skills, null, "NEW", 1, null, null, null, null, null);
    }

    @Test
    void keywordFallbackPrefersMatchingTrade() {
        var out = service.viaKeywords(declaration("Electrical", "wiring and testing"),
            List.of(q(plumb, "Plumber", "Plumbing", "pipework"), q(elec, "Electrician", "Electrical", "wiring", "testing")));
        assertEquals(elec, out.get(0).versionId());
        assertEquals("KEYWORD", out.get(0).source());
    }
}
