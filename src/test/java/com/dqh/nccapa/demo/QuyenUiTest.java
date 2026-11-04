package com.dqh.nccapa.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test cờ quyền UI suy từ {@link DemoRole} — thuần JUnit, KHÔNG chạm DB, KHÔNG Spring context.
 */
class QuyenUiTest {

    @Test
    void nguoiBaoCao_chiCoCoNguoiBaoCao() {
        QuyenUi q = QuyenUi.tu(DemoRole.NGUOI_BAO_CAO);
        assertThat(q.laNguoiBaoCao()).isTrue();
        assertThat(q.laSx()).isFalse();
        assertThat(q.laQaNhaMay()).isFalse();
        assertThat(q.laQaTapDoan()).isFalse();
        assertThat(q.laAdmin()).isFalse();
        assertThat(q.laQa()).isFalse();
    }

    @Test
    void boPhanSx_chiCoSx() {
        QuyenUi q = QuyenUi.tu(DemoRole.BO_PHAN_SX);
        assertThat(q.laSx()).isTrue();
        assertThat(q.laQa()).isFalse();
        assertThat(q.laAdmin()).isFalse();
    }

    @Test
    void qaNhaMay_laQaNhungKhongPhaiTapDoan() {
        QuyenUi q = QuyenUi.tu(DemoRole.QA_NHA_MAY);
        assertThat(q.laQaNhaMay()).isTrue();
        assertThat(q.laQaTapDoan()).isFalse();
        assertThat(q.laQa()).isTrue();
        assertThat(q.laAdmin()).isFalse();
    }

    @Test
    void qaTapDoan_laQaVaTapDoanNhungKhongPhaiAdmin() {
        QuyenUi q = QuyenUi.tu(DemoRole.QA_TAP_DOAN);
        assertThat(q.laQaTapDoan()).isTrue();
        assertThat(q.laQa()).isTrue();
        assertThat(q.laAdmin()).isFalse();
    }

    @Test
    void admin_chiCoAdminVaKhongThuocNhomQa() {
        QuyenUi q = QuyenUi.tu(DemoRole.ADMIN);
        assertThat(q.laAdmin()).isTrue();
        assertThat(q.laQa()).isFalse();
        assertThat(q.laQaTapDoan()).isFalse();
        assertThat(q.laQaNhaMay()).isFalse();
        assertThat(q.laNguoiBaoCao()).isFalse();
        assertThat(q.laSx()).isFalse();
    }
}
