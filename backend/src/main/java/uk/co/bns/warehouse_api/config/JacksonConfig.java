package uk.co.bns.warehouse_api.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Every timestamp in this app (BugReport.createdAt/occurredAt, Order's DPD
 * timestamps, etc.) is a plain LocalDateTime written with LocalDateTime.now()
 * - and because the backend container's JVM runs with the default (UTC)
 * timezone, that wall-clock value genuinely IS a UTC instant, it just
 * carries no timezone marker of its own once serialized.
 *
 * Jackson's default LocalDateTime serialization reflects that lack of a
 * marker literally - it writes a plain "2026-09-22T12:27:00" with no 'Z' or
 * offset. The browser's `new Date(...)` then does exactly what the JS spec
 * says for a date-time string with no offset: treats it as *local* time,
 * not UTC - so the UTC-to-BST/GMT conversion never happens. The practical
 * effect confirmed by a live report: every timestamp shown in the UI (Bug
 * Reports, order history, etc.) reads a full hour behind reality during
 * British Summer Time (and would be correct only in winter, by accident).
 *
 * Serializing with an explicit 'Z' suffix tells the browser these are real
 * UTC instants, so frontend/src/utils/format.ts's toLocaleString() calls
 * convert them to the viewer's actual local time correctly, in BST or GMT.
 * Only the serializer is overridden (via modulesToInstall, which adds to -
 * rather than replaces - Spring Boot's own auto-configured Jackson modules),
 * so parsing LocalDateTime from request bodies elsewhere is unaffected.
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcLocalDateTimeCustomizer() {
        return builder -> builder.modulesToInstall(new SimpleModule()
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(UTC_FORMAT)));
    }
}
