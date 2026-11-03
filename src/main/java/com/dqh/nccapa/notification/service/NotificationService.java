package com.dqh.nccapa.notification.service;

import com.dqh.nccapa.config.AppProperties;
import com.dqh.nccapa.notification.entity.Notification;
import com.dqh.nccapa.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Hệ thống thông báo (outbox) cho NC/CAPA. App mẫu KHÔNG có SMTP thật:
 * - quetVaSinh(): quét dữ liệu nguồn và sinh thông báo PENDING (chống trùng trong ngày).
 * - flush(): đánh dấu PENDING -> SENT và log mô phỏng gửi email (cấm SMTP sau).
 * Giống pattern ticket_callback_outbox nhưng đơn giản hơn (không gọi HTTP).
 *
 * Quét dữ liệu nguồn bằng JdbcTemplate so với thời gian của DB (CURDATE()/NOW()),
 * để test có thể đẩy mốc bằng SQL (DATE_SUB(...)) mà không lệ thuộc wall-clock của JVM.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String ACTION_QUA_HAN = "ACTION_QUA_HAN";
    private static final String MOC_SAP_DEN = "MOC_SAP_DEN";
    private static final String MOC_QUA_HAN = "MOC_QUA_HAN";
    private static final String DT_ACTION = "ACTION";
    private static final String DT_CAPA = "CAPA";

    private final NotificationRepository repo;
    private final JdbcTemplate jdbc;
    private final AppProperties appProps;

    public NotificationService(NotificationRepository repo, JdbcTemplate jdbc, AppProperties appProps) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.appProps = appProps;
    }

    /**
     * Quét và sinh thông báo:
     *  (a) capa_action trạng thái Open có deadline < hôm nay -> ACTION_QUA_HAN (gửi người_phụ_trách).
     *  (b) CAPA 'Monitoring' có han_kiem_chung_tiep_theo trong [hôm nay, hôm nay + ngưỡng] -> MOC_SAP_DEN.
     *  (c) CAPA 'Monitoring' có han_kiem_chung_tiep_theo < now -> MOC_QUA_HAN.
     * Chống trùng: không tạo lại cùng loại/đối tượng đã sinh trong NGÀY hiện tại.
     * @return số thông báo đã sinh trong lần quét này.
     */
    @Transactional
    public int quetVaSinh() {
        int sinh = 0;
        int nguong = appProps.getNotification().getMocCanhBaoTruocNgay();

        // (a) Action Open quá hạn.
        List<Map<String, Object>> actions = jdbc.queryForList(
                "SELECT id, capa_id, nguoi_phu_trach, deadline " +
                "FROM capa_action WHERE trang_thai = 'Open' AND deadline < CURDATE()");
        for (Map<String, Object> r : actions) {
            String actionId = String.valueOf(r.get("id"));
            String nguoiNhan = (String) r.get("nguoi_phu_trach");
            String tieuDe = "Action CAPA quá hạn (#" + actionId + ")";
            String noiDung = "Hành động CAPA #" + actionId + " (CAPA #" + r.get("capa_id") +
                    ") đã quá hạn (deadline " + r.get("deadline") + ") nhưng vẫn ở trạng thái Open.";
            if (sinhNeuChuaTrung(ACTION_QUA_HAN, DT_ACTION, actionId, nguoiNhan, tieuDe, noiDung)) sinh++;
        }

        // (b)/(c) CAPA Monitoring theo han_kiem_chung_tiep_theo.
        List<Map<String, Object>> capas = jdbc.queryForList(
                "SELECT c.id AS capa_id, c.nguoi_dieu_tra, v.han_kiem_chung_tiep_theo, " +
                "       (v.han_kiem_chung_tiep_theo < NOW()) AS qua_han, " +
                "       (v.han_kiem_chung_tiep_theo >= NOW() AND " +
                "        v.han_kiem_chung_tiep_theo <= DATE_ADD(NOW(), INTERVAL ? DAY)) AS sap_den " +
                "FROM capa c JOIN capa_verification v ON v.capa_id = c.id " +
                "WHERE c.trang_thai = 'Monitoring' AND v.han_kiem_chung_tiep_theo IS NOT NULL",
                nguong);
        for (Map<String, Object> r : capas) {
            String capaId = String.valueOf(r.get("capa_id"));
            String nguoiNhan = (String) r.get("nguoi_dieu_tra");
            Object han = r.get("han_kiem_chung_tiep_theo");
            boolean quaHan = toBool(r.get("qua_han"));
            boolean sapDen = toBool(r.get("sap_den"));
            if (quaHan) {
                String tieuDe = "Mốc kiểm chứng CAPA quá hạn (CAPA #" + capaId + ")";
                String noiDung = "CAPA #" + capaId + " đang Monitoring đã QUÁ hạn kiểm chứng tiếp theo (" + han + ").";
                if (sinhNeuChuaTrung(MOC_QUA_HAN, DT_CAPA, capaId, nguoiNhan, tieuDe, noiDung)) sinh++;
            } else if (sapDen) {
                String tieuDe = "Mốc kiểm chứng CAPA sắp đến hạn (CAPA #" + capaId + ")";
                String noiDung = "CAPA #" + capaId + " đang Monitoring có mốc kiểm chứng tiếp theo sắp đến hạn (" +
                        han + ", trong vòng " + nguong + " ngày).";
                if (sinhNeuChuaTrung(MOC_SAP_DEN, DT_CAPA, capaId, nguoiNhan, tieuDe, noiDung)) sinh++;
            }
        }
        return sinh;
    }

    /**
     * Đánh dấu các thông báo PENDING -> SENT và log mô phỏng gửi email.
     * Để chỗ cắm SMTP sau (hiện chỉ log dòng "[EMAIL] gửi tới ...").
     * @return số thông báo đã "gửi" trong lần flush này.
     */
    @Transactional
    public int flush() {
        List<Notification> pending = repo.findByTrangThaiOrderByIdAsc("PENDING");
        for (Notification n : pending) {
            log.info("[EMAIL] gửi tới {}: {}", n.getNguoiNhan(), n.getTieuDe());
            n.danhDauGui();
            repo.save(n);
        }
        return pending.size();
    }

    /** 50 thông báo gần nhất (để demo/kiểm). */
    @Transactional(readOnly = true)
    public List<Notification> ganDay() {
        return repo.findTop50ByOrderByIdDesc();
    }

    /** Sinh thông báo nếu trong ngày hiện tại chưa có thông báo cùng loại/đối tượng. */
    private boolean sinhNeuChuaTrung(String loai, String doiTuongLoai, String doiTuongId,
                                     String nguoiNhan, String tieuDe, String noiDung) {
        LocalDateTime dauNgay = LocalDate.now().atStartOfDay();
        LocalDateTime cuoiNgay = dauNgay.plusDays(1);
        if (repo.existsByLoaiAndDoiTuongLoaiAndDoiTuongIdAndCreatedAtBetween(
                loai, doiTuongLoai, doiTuongId, dauNgay, cuoiNgay)) {
            return false;
        }
        repo.save(new Notification(loai, doiTuongLoai, doiTuongId, nguoiNhan, tieuDe, noiDung));
        return true;
    }

    private static boolean toBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return "1".equals(v.toString()) || "true".equalsIgnoreCase(v.toString());
    }
}
