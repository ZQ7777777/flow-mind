package com.flowmind.business.message;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Repository
public class BusinessUserMessageRepository {

    private static final DateTimeFormatter DB_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<BusinessUserMessageEntity> ROW_MAPPER =
            new RowMapper<BusinessUserMessageEntity>() {
                @Override
                public BusinessUserMessageEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
                    BusinessUserMessageEntity entity = new BusinessUserMessageEntity();
                    entity.setId(rs.getString("id"));
                    entity.setSourceMessageId(rs.getString("source_message_id"));
                    entity.setRecipientUserId(rs.getString("recipient_user_id"));
                    entity.setMessageType(rs.getString("message_type"));
                    entity.setTitle(rs.getString("title"));
                    entity.setContent(rs.getString("content"));
                    entity.setSeverity(rs.getString("severity"));
                    entity.setPayloadJson(rs.getString("payload_json"));
                    entity.setReadStatus(rs.getString("read_status"));
                    entity.setCreatedAt(toLocalDateTime(rs.getString("created_at")));
                    entity.setReadAt(toLocalDateTime(rs.getString("read_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    public BusinessUserMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertIgnore(BusinessUserMessageEntity entity) {
        return jdbcTemplate.update("INSERT OR IGNORE INTO business_user_message "
                        + "(id, source_message_id, recipient_user_id, message_type, title, content, severity, "
                        + "payload_json, read_status, created_at, read_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'UNREAD'), COALESCE(?, datetime('now')), ?)",
                entity.getId(), entity.getSourceMessageId(), entity.getRecipientUserId(), entity.getMessageType(),
                entity.getTitle(), entity.getContent(), entity.getSeverity(), entity.getPayloadJson(),
                entity.getReadStatus(), toDbString(entity.getCreatedAt()), toDbString(entity.getReadAt()));
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM business_user_message", Long.class);
        return count == null ? 0L : count.longValue();
    }

    public long countUnread(String recipientUserId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM business_user_message "
                        + "WHERE recipient_user_id = ? AND read_status = 'UNREAD'",
                Long.class, recipientUserId);
        return count == null ? 0L : count.longValue();
    }

    public List<BusinessUserMessageEntity> query(String recipientUserId, String readStatus, String messageType,
                                                 int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM business_user_message WHERE recipient_user_id = ? ");
        java.util.List<Object> params = new java.util.ArrayList<Object>();
        params.add(recipientUserId);
        if (!isBlank(readStatus)) {
            sql.append("AND read_status = ? ");
            params.add(readStatus);
        }
        if (!isBlank(messageType)) {
            sql.append("AND message_type = ? ");
            params.add(messageType);
        }
        sql.append("ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(Math.max(1, limit)));
        params.add(Integer.valueOf(Math.max(0, offset)));
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public int markRead(String messageId, String recipientUserId, LocalDateTime readAt) {
        return jdbcTemplate.update("UPDATE business_user_message SET read_status = 'READ', read_at = ? "
                        + "WHERE id = ? AND recipient_user_id = ?",
                toDbString(readAt), messageId, recipientUserId);
    }

    public int markAllRead(String recipientUserId, LocalDateTime readAt) {
        return jdbcTemplate.update("UPDATE business_user_message SET read_status = 'READ', read_at = ? "
                        + "WHERE recipient_user_id = ? AND read_status = 'UNREAD'",
                toDbString(readAt), recipientUserId);
    }

    private static String toDbString(LocalDateTime value) {
        return value == null ? null : value.format(DB_TIME);
    }

    private static LocalDateTime toLocalDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() >= 19) {
            normalized = normalized.substring(0, 19);
        }
        return LocalDateTime.parse(normalized, DB_TIME);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}