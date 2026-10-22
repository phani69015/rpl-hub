package com.rplhub.repository;

import com.rplhub.model.AppUser;
import com.rplhub.model.AssessorView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> find(UUID id) {
        return jdbc.sql("select id, phone, role, can_publish, can_decide from app_user where id = ?")
            .param(id).query(AppUser.class).optional();
    }

    /** Seeds (or promotes) the bootstrap administrator. */
    public void saveAdmin(String phone) {
        jdbc.sql("""
                insert into app_user(id, phone, role, can_publish, can_decide) values (?,?, 'ADMIN', true, true)
                on conflict (phone) do update set role = 'ADMIN', can_publish = true, can_decide = true""")
            .params(UUID.randomUUID(), phone).update();
    }

    /** Signing in with an unknown phone number creates a WORKER. */
    public AppUser findOrCreateWorker(String phone) {
        jdbc.sql("insert into app_user(id, phone, role) values (?,?, 'WORKER') on conflict (phone) do nothing")
            .params(UUID.randomUUID(), phone).update();
        return jdbc.sql("select id, phone, role, can_publish, can_decide from app_user where phone = ?")
            .param(phone).query(AppUser.class).single();
    }

    public List<AssessorView> assessors() {
        return jdbc.sql("select id, phone, can_decide from app_user where role = 'ASSESSOR' order by phone")
            .query((rs, i) -> new AssessorView(UUID.fromString(rs.getString("id")), rs.getString("phone"), rs.getBoolean("can_decide"), trades(UUID.fromString(rs.getString("id")))))
            .list();
    }

    /** Assessors authorised for a trade (case-insensitive). */
    public List<AssessorView> eligibleAssessors(String trade) {
        return assessors().stream()
            .filter(a -> a.trades().stream().anyMatch(t -> t.equalsIgnoreCase(trade.trim())))
            .toList();
    }

    public boolean isEligible(UUID assessorId, String trade) {
        return eligibleAssessors(trade).stream().anyMatch(a -> a.id().equals(assessorId));
    }

    /** Creates or updates an assessor. Refuses to demote an administrator. */
    public AssessorView saveAssessor(String phone, List<String> trades, boolean canDecide) {
        UUID id = jdbc.sql("""
                insert into app_user(id, phone, role, can_decide) values (?,?, 'ASSESSOR', ?)
                on conflict (phone) do update set can_decide = excluded.can_decide
                  where app_user.role in ('ASSESSOR','WORKER')
                returning id""")
            .params(UUID.randomUUID(), phone, canDecide).query(UUID.class).optional()
            .orElseThrow(() -> new IllegalStateException("Cannot change an administrator into an assessor"));
        jdbc.sql("update app_user set role = 'ASSESSOR' where id = ?").param(id).update();
        jdbc.sql("delete from assessor_trade where user_id = ?").param(id).update();
        for (String t : trades) {
            jdbc.sql("insert into assessor_trade(user_id, trade) values (?,?) on conflict do nothing").params(id, t.trim()).update();
        }
        return new AssessorView(id, phone, canDecide, trades(id));
    }

    private List<String> trades(UUID id) {
        return jdbc.sql("select trade from assessor_trade where user_id = ? order by trade").param(id).query(String.class).list();
    }
}
