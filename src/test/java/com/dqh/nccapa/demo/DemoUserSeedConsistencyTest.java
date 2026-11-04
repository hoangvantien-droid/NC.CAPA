package com.dqh.nccapa.demo;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bắt lỗi lệch tên giữa tài khoản demo ({@link CurrentUserService}) và user/role được seed thật.
 * Guard backend tra vai trò qua app_user_role, nên mọi tài khoản demo (trừ admin) phải có dòng
 * gán đúng vai trò trong migration V14 (đọc tĩnh từ classpath; profile test tắt DemoSeeder).
 */
class DemoUserSeedConsistencyTest {

    @Test
    void moiTaiKhoanDemoTruAdminDuocSeedDungVaiTro() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V14__seed_roles_backfill.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        for (DemoUser u : new CurrentUserService().danhSach()) {
            if (u.vaiTro() == DemoRole.ADMIN) continue;
            String regex = "(?s)'" + u.vaiTro().name() + "'\\s+FROM\\s+app_user\\s+WHERE\\s+username\\s*=\\s*'"
                    + u.username() + "'";
            assertThat(java.util.regex.Pattern.compile(regex).matcher(sql).find())
                    .as("V14 phải gán vai trò %s cho user demo %s", u.vaiTro(), u.username())
                    .isTrue();
        }
    }
}
