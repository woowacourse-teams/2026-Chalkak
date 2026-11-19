package com.chalkak.backend.jev.infrastructure.persistence;

import com.chalkak.backend.jev.domain.JevDisposition;
import com.chalkak.backend.jev.domain.JevEvaluation;
import com.chalkak.backend.jev.domain.JevGoldLabel;
import com.chalkak.backend.jev.repository.JevMetricsRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class JevMetricsRepositoryImpl implements JevMetricsRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Override
    public List<Sample> findSamples(
            Instant from,
            Instant to,
            Boolean preview,
            int limit
    ) {
        var parameters = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to))
                .addValue(
                        "preview",
                        preview,
                        java.sql.Types.BOOLEAN
                ).addValue("limit", limit);
        // 호출 원문/프롬프트는 로딩하지 않고 평가에 필요한 작은 값만 조회한다.
        return jdbc.query(
                """
                        SELECT id, post_id, topic_id, preview, disposition,
                            evaluation::text AS evaluation, gold_label::text AS gold_label,
                            post_id::text || ':' || topic_id::text || ':' || topic_title || ':' ||
                                CASE WHEN preview THEN md5(preview_draft::text)
                                ELSE COALESCE(classification_prompt_id::text, '') || ':' ||
                                     COALESCE(photo_prompt_id::text, '') END AS repeat_key
                        FROM jev_moderation_runs
                        WHERE created_at >= :from AND created_at < :to
                          AND (:preview IS NULL OR preview = :preview)
                        ORDER BY created_at, id LIMIT :limit
                        """,
                parameters,
                (row, index) -> {
                    return new Sample(
                            row.getObject("id", UUID.class),
                            row.getObject("post_id", UUID.class),
                            row.getObject("topic_id", UUID.class),
                            row.getBoolean("preview"),
                            row.getString("disposition") == null
                                    ? null
                                    : JevDisposition.valueOf(row.getString("disposition")),
                            read(row.getString("evaluation"), JevEvaluation.class),
                            read(row.getString("gold_label"), JevGoldLabel.class),
                            row.getString("repeat_key")
                    );
                }
        );
    }

    @Override
    public List<Call> findCalls(List<UUID> runIds) {
        if (runIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                """
                        WITH calls AS (
                            SELECT jsonb_array_elements(phase->'calls') AS value
                            FROM jev_moderation_runs WHERE id IN (:ids)
                            UNION ALL
                            SELECT jsonb_array_elements(category.phase->'calls') AS value
                            FROM jev_topic_categories category WHERE category.id IN (
                                SELECT DISTINCT classification_id FROM jev_moderation_runs WHERE id IN (:ids)
                            )
                        )
                        SELECT DISTINCT ON (value->>'callId')
                            (value->>'callId')::uuid AS id, value->>'errorCode' AS error,
                            (value->>'httpStatus')::integer AS http_status,
                            (value->>'latencyMs')::bigint AS latency,
                            (value->>'inputTokens')::bigint AS input_tokens,
                            (value->>'outputTokens')::bigint AS output_tokens
                        FROM calls
                        ORDER BY value->>'callId'
                        """,
                new MapSqlParameterSource("ids", runIds),
                (row, index) -> {
                    return new Call(
                            row.getObject("id", UUID.class),
                            row.getString("error"),
                            row.getObject("http_status", Integer.class),
                            row.getObject("latency", Long.class),
                            row.getObject("input_tokens", Long.class),
                            row.getObject("output_tokens", Long.class)
                    );
                }
        );
    }

    private <T> T read(String json, Class<T> type) {
        return json == null ? null : mapper.readValue(json, type);
    }
}
