package com.rplhub.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Append-only. Call inside the caller's transaction so audit and change commit together. */
@Service
public class AuditService {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    AuditService(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void record(UUID actor, String action, String entityType, Object entityId, Map<String, ?> detail) {
        String json;
        try {
            json = mapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
        jdbc.sql("insert into audit_event(id, actor_id, action, entity_type, entity_id, detail) values (?,?,?,?,?,cast(? as jsonb))")
            .params(UUID.randomUUID(), actor, action, entityType, String.valueOf(entityId), json)
            .update();
    }
}
