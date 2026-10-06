package com.cognologix.fpa.bankrecon;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
class LearnedMappingVectorStore {

    private final JdbcTemplate jdbcTemplate;

    LearnedMappingVectorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void saveEmbedding(UUID mappingId, float[] embedding) {
        jdbcTemplate.update(
                "UPDATE learned_mapping SET narration_embedding = CAST(? AS vector) WHERE id = ?",
                toLiteral(embedding), mappingId);
    }

    List<SimilarMapping> similaritySearch(float[] embedding, String voucherType, int topK) {
        if (embedding == null || embedding.length == 0) {
            return List.of();
        }
        String sql = """
                SELECT id, normalised_narration, ledger_name, voucher_type
                FROM learned_mapping
                WHERE voucher_type = ?
                  AND narration_embedding IS NOT NULL
                ORDER BY narration_embedding <=> CAST(? AS vector)
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new SimilarMapping(
                        rs.getObject("id", UUID.class),
                        rs.getString("normalised_narration"),
                        rs.getString("ledger_name"),
                        rs.getString("voucher_type")),
                voucherType, toLiteral(embedding), topK);
    }

    static String toLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    record SimilarMapping(UUID id, String normalisedNarration, String ledgerName, String voucherType) {}
}
