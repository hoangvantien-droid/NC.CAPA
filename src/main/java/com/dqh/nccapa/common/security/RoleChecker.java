package com.dqh.nccapa.common.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Kiểm vai trò THẬT của actor theo dữ liệu {@code app_user_role} (KHÔNG tin vai trò do client tự khai).
 * Dùng chung cho các điểm duyệt cần SoD theo role (NC approve, PR approve, CAPA dời hạn mốc).
 *
 * <p>Chỉ phụ thuộc {@link JdbcTemplate} — KHÔNG import service nghiệp vụ nào để tránh phụ thuộc vòng.
 * Tra {@code app_user_role} JOIN {@code app_user} theo username (khớp cách đã dùng ở CapaService).
 */
@Component
public class RoleChecker {

    private final JdbcTemplate jdbc;

    public RoleChecker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** True nếu {@code username} thực sự được gán vai trò {@code roleMa} trong app_user_role. */
    public boolean coRole(String username, String roleMa) {
        if (username == null || username.isBlank() || roleMa == null || roleMa.isBlank()) return false;
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user_role ur JOIN app_user u ON u.id = ur.user_id " +
                "WHERE u.username = ? AND ur.role_ma = ?",
                Integer.class, username, roleMa);
        return n != null && n > 0;
    }

    /** True nếu {@code username} có vai trò QA_TAP_DOAN. */
    public boolean laQaTapDoan(String username) {
        return coRole(username, "QA_TAP_DOAN");
    }

    /** True nếu {@code username} thuộc nhóm QA (QA_NHA_MAY HOẶC QA_TAP_DOAN). */
    public boolean laQa(String username) {
        return coRole(username, "QA_NHA_MAY") || coRole(username, "QA_TAP_DOAN");
    }

    /**
     * Nhà máy gắn với {@code username} theo app_user.nha_may.
     * Trả {@code null} nếu nha_may NULL trong DB (user toàn tập đoàn, ví dụ QA tập đoàn/admin)
     * HOẶC user không tồn tại trong app_user. {@code null} nghĩa là thấy/thao tác mọi nhà máy.
     */
    public String nhaMayCua(String username) {
        if (username == null || username.isBlank()) return null;
        java.util.List<String> rows = jdbc.queryForList(
                "SELECT nha_may FROM app_user WHERE username = ?",
                String.class, username);
        if (rows.isEmpty()) return null;
        return rows.get(0);
    }
}
