package com.rplhub.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Append-only. Call `record` inside the caller's transaction so audit and change commit together. */
@Repository
public class AuditRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    AuditRepository(JdbcClient jdbc, ObjectMapper mapper) {
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
        jdbc.sql("insert into audit_event(id, actor_id, action, entity_type, entity_id, detail) values (?,?,?,?,?,?)")
            .params(UUID.randomUUID(), actor, action, entityType, String.valueOf(entityId), json)
            .update();
    }

    public List<Map<String, Object>> recent() {
        return jdbc.sql("""
                select a.created_at, a.action, u.phone as actor, a.entity_type, a.entity_id, a.detail
                from audit_event a left join app_user u on u.id = a.actor_id order by a.created_at desc limit 200""")
            .query().listOfRows();
    }

    public List<Map<String, Object>> forEntity(UUID entityId) {
        return jdbc.sql("""
                select a.created_at, a.action, u.phone as actor, a.detail from audit_event a
                left join app_user u on u.id = a.actor_id where a.entity_id = ? order by a.created_at, a.id""")
            .param(entityId.toString()).query().listOfRows();
    }
}
