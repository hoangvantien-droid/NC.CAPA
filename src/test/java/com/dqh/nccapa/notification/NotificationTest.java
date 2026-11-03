package com.dqh.nccapa.notification;

import com.dqh.nccapa.notification.entity.Notification;
import com.dqh.nccapa.notification.repository.NotificationRepository;
import com.dqh.nccapa.notification.service.NotificationService;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hệ thống thông báo outbox: quetVaSinh sinh ACTION_QUA_HAN / MOC_QUA_HAN,
 * flush đổi PENDING->SENT, chống trùng trong ngày.
 * Mốc thời gian chèn cố định tương đối so với thời gian DB (DATE_SUB/DATE_ADD),
 * KHÔNG lệ thuộc wall-clock. Scheduler tắt ở profile test; gọi service trực tiếp.
 */
class NotificationTest extends MySqlTestBase {

    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notificationRepo;
    @Autowired JdbcTemplate jdbc;

    /** Tạo CAPA Monitoring + verification với mốc kiểm chứng đẩy theo số ngày (âm = quá khứ). */
    private Long taoCapaMonitoring(int hanOffsetNgay, String nguoiDieuTra) {
        jdbc.update("INSERT INTO capa(trang_thai, dem_gop_nlo, is_8d, co_yeu_cau_phap_ly, handover_done, " +
                "nguoi_dieu_tra, created_at, created_by) " +
                "VALUES ('Monitoring', 0, 0, 0, 0, ?, NOW(), 'qa')", nguoiDieuTra);
        Long capaId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO capa_verification(capa_id, so_lan_doi_han, han_kiem_chung_tiep_theo) " +
                "VALUES (?, 0, DATE_ADD(NOW(), INTERVAL ? DAY))", capaId, hanOffsetNgay);
        return capaId;
    }

    /** Tạo action Open với deadline đẩy theo số ngày (âm = quá khứ). */
    private Long taoAction(Long capaId, String nguoiPhuTrach, int deadlineOffsetNgay) {
        jdbc.update("INSERT INTO capa_action(capa_id, loai, mo_ta, nguoi_phu_trach, deadline, trang_thai, loai_hanh_dong) " +
                "VALUES (?, 'NGAN_TAI_DIEN', 'x', ?, DATE_ADD(CURDATE(), INTERVAL ? DAY), 'Open', 'CORRECTIVE')",
                capaId, nguoiPhuTrach, deadlineOffsetNgay);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void quetVaSinh_sinhMocQuaHan_vaActionQuaHan() {
        Long capaId = taoCapaMonitoring(-1, "qa01");   // mốc quá hạn 1 ngày
        taoAction(capaId, "sx01", -2);                  // action Open quá hạn 2 ngày

        int sinh = notificationService.quetVaSinh();
        assertThat(sinh).isEqualTo(2);

        List<Notification> all = notificationRepo.findAll();
        assertThat(all).extracting(Notification::getLoai)
                .containsExactlyInAnyOrder("MOC_QUA_HAN", "ACTION_QUA_HAN");
        assertThat(all).allMatch(n -> "PENDING".equals(n.getTrangThai()));
    }

    @Test
    void quetVaSinh_sinhMocSapDen_trongNguong() {
        taoCapaMonitoring(2, "qa02");   // sắp đến hạn trong 2 ngày (<= ngưỡng mặc định 3)

        int sinh = notificationService.quetVaSinh();
        assertThat(sinh).isEqualTo(1);
        assertThat(notificationRepo.findAll()).extracting(Notification::getLoai)
                .containsExactly("MOC_SAP_DEN");
    }

    @Test
    void mocNgoaiNguong_khongSinh() {
        taoCapaMonitoring(30, "qa03");  // còn 30 ngày, ngoài ngưỡng 3 -> không sinh

        assertThat(notificationService.quetVaSinh()).isEqualTo(0);
        assertThat(notificationRepo.count()).isZero();
    }

    @Test
    void flush_doiPendingThanhSent() {
        Long capaId = taoCapaMonitoring(-1, "qa04");
        taoAction(capaId, "sx04", -1);
        notificationService.quetVaSinh();

        int daGui = notificationService.flush();
        assertThat(daGui).isEqualTo(2);
        assertThat(notificationRepo.findAll()).allMatch(n -> "SENT".equals(n.getTrangThai()));
        assertThat(notificationRepo.findAll()).allMatch(n -> n.getSentAt() != null);
    }

    @Test
    void chongTrung_quetHaiLan_khongTaoTrung() {
        Long capaId = taoCapaMonitoring(-1, "qa05");
        taoAction(capaId, "sx05", -1);

        assertThat(notificationService.quetVaSinh()).isEqualTo(2);
        // Quét lần 2 trong cùng ngày: không tạo thông báo trùng cho cùng đối tượng.
        assertThat(notificationService.quetVaSinh()).isEqualTo(0);
        assertThat(notificationRepo.count()).isEqualTo(2);
    }
}
