package com.dqh.nccapa.pr;

import com.dqh.nccapa.common.enums.LoaiThayDoi;
import com.dqh.nccapa.common.enums.PrStatus;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.pr.dto.PrActionRequest;
import com.dqh.nccapa.pr.dto.TaoPrRequest;
import com.dqh.nccapa.pr.entity.Pr;
import com.dqh.nccapa.pr.service.PrService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Đợt d — PR: máy trạng thái nhiều cấp + ma trận định tuyến. */
class PrFlowTest extends MySqlTestBase {

    @Autowired PrService prService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private Pr taoPr(LoaiThayDoi loai, String maHang, String maLoi) {
        TaoPrRequest r = new TaoPrRequest();
        r.loaiThayDoi = loai; r.maHang = maHang; r.maLoiId = maLoi; r.actor = "qa";
        return prService.tao(r);
    }

    private void toPending(Long id) {
        PrActionRequest a = new PrActionRequest(); a.actor = "qa";
        prService.toAnalyzing(id, a);
        prService.submit(id, a);
    }

    private PrActionRequest approveAs(String vaiTro, String actor) {
        PrActionRequest a = new PrActionRequest(); a.actor = actor; a.vaiTro = vaiTro;
        return a;
    }

    @Test // AC: Bao bì/nhãn 3 cấp R&D -> QA TĐ -> Pháp chế, đúng thứ tự
    void baoBiNhan_3Cap_dungThuTu() {
        Pr pr = taoPr(LoaiThayDoi.BAO_BI_NHAN, "SP001", "FG-01");
        Long id = pr.getId();
        toPending(id);

        prService.approve(id, approveAs("R_AND_D", "rd1"));
        assertThat(prService.lay(id).getTrangThai()).isEqualTo(PrStatus.PendingApproval); // còn 2 cấp
        prService.approve(id, approveAs("QA_TAP_DOAN", "qatd"));
        assertThat(prService.lay(id).getTrangThai()).isEqualTo(PrStatus.PendingApproval); // còn 1 cấp
        prService.approve(id, approveAs("PHAP_CHE", "pc"));
        assertThat(prService.lay(id).getTrangThai()).isEqualTo(PrStatus.Approved);        // cấp cuối -> Approved

        PrActionRequest a = new PrActionRequest(); a.actor = "qa";
        prService.implement(id, a);
        prService.close(id, a);
        assertThat(prService.lay(id).getTrangThai()).isEqualTo(PrStatus.Closed);
    }

    @Test // Sai vai trò ở một cấp -> chặn
    void saiVaiTro_biChan() {
        Pr pr = taoPr(LoaiThayDoi.CONG_THUC, "SP001", "PRC-02"); // R&D -> QA TĐ
        Long id = pr.getId();
        toPending(id);
        // cấp 0 cần R_AND_D, nhưng duyệt bằng QA_TAP_DOAN
        assertThatThrownBy(() -> prService.approve(id, approveAs("QA_TAP_DOAN", "x")))
                .isInstanceOf(BusinessException.class);
    }

    @Test // Từ chối -> Rejected -> resubmit (toAnalyzing) lại được
    void tuChoi_roiResubmit() {
        Pr pr = taoPr(LoaiThayDoi.QUY_CACH, "SP001", "FG-03"); // 1 cấp QA TĐ
        Long id = pr.getId();
        toPending(id);
        PrActionRequest rej = new PrActionRequest(); rej.actor = "qatd"; rej.vaiTro = "QA_TAP_DOAN"; rej.lyDo = "thiếu dữ liệu";
        prService.reject(id, rej);
        assertThat(prService.lay(id).getTrangThai()).isEqualTo(PrStatus.Rejected);

        PrActionRequest a = new PrActionRequest(); a.actor = "qa";
        prService.toAnalyzing(id, a);
        assertThat(prService.lay(id).getTrangThai()).isEqualTo(PrStatus.Analyzing);
    }

    @Test // Chống trùng: PR thứ 2 cùng (mã hàng+mã lỗi) khi PR đầu đang mở -> chặn
    void chongTrungPr_cungVanDe() {
        taoPr(LoaiThayDoi.QUY_CACH, "SP001", "FG-03");
        assertThatThrownBy(() -> taoPr(LoaiThayDoi.QUY_CACH, "SP001", "FG-03"))
                .isInstanceOf(BusinessException.class);
    }

    @Test // Sau khi PR đầu đóng, tạo PR mới cùng vấn đề -> được
    void dongRoi_taoMoiDuoc() {
        Pr p1 = taoPr(LoaiThayDoi.QUY_CACH, "SP001", "FG-04");
        Long id = p1.getId();
        toPending(id);
        prService.approve(id, approveAs("QA_TAP_DOAN", "qatd"));
        PrActionRequest a = new PrActionRequest(); a.actor = "qa";
        prService.implement(id, a); prService.close(id, a);
        // PR đã Closed -> tạo mới cùng vấn đề OK
        Pr p2 = taoPr(LoaiThayDoi.QUY_CACH, "SP001", "FG-04");
        assertThat(p2.getId()).isNotNull();
    }
}
