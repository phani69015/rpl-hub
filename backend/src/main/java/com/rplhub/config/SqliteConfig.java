package com.rplhub.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * SQLite wiring. Transactions start with BEGIN IMMEDIATE so a read-then-write sequence takes the write lock up front
 * (this replaces row locks); WAL lets readers run alongside the single writer.
 */
@Configuration
public class SqliteConfig {

    @Bean(destroyMethod = "close")
    DataSource dataSource(@Value("${app.db.path}") String path) throws IOException {
        Path file = Path.of(path).toAbsolutePath();
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        var config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + file + "?foreign_keys=on&journal_mode=WAL&busy_timeout=15000&transaction_mode=IMMEDIATE");
        config.setMaximumPoolSize(8);
        return new HikariDataSource(config);
    }

    /** Timestamps are stored as ISO text; teach the JdbcClient row mapper to read them back (UUIDs convert out of the box). */
    static {
        ((ConverterRegistry) DefaultConversionService.getSharedInstance())
            .addConverter(String.class, OffsetDateTime.class, OffsetDateTime::parse);
    }
}
