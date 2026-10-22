package com.rplhub.repository;

import com.rplhub.model.Profile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileRepository {

    private final JdbcClient jdbc;

    ProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Profile> find(UUID userId) {
        return jdbc.sql("select full_name, primary_trade, years_experience, skills from worker_profile where user_id = ?")
            .param(userId).query(Profile.class).optional();
    }

    public void save(UUID userId, Profile p) {
        jdbc.sql("""
                insert into worker_profile(user_id, full_name, primary_trade, years_experience, skills) values (?,?,?,?,?)
                on conflict (user_id) do update set full_name = excluded.full_name, primary_trade = excluded.primary_trade,
                  years_experience = excluded.years_experience, skills = excluded.skills, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')""")
            .params(userId, p.fullName().trim(), p.primaryTrade(), p.yearsExperience(), p.skills()).update();
    }
}
