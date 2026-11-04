package com.dqh.nccapa.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dqh.nccapa.common.enums.ActionCategory;
import com.dqh.nccapa.common.enums.ActionStatus;
import com.dqh.nccapa.common.enums.ActionType;
import com.dqh.nccapa.common.enums.CapaStatus;
import com.dqh.nccapa.common.enums.LoaiThayDoi;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.enums.NhomNguyenNhan6M;
import com.dqh.nccapa.common.enums.PrStatus;
import com.dqh.nccapa.common.enums.RcaMethod;
import org.junit.jupiter.api.Test;

/** Mọi giá trị enum chính đều có nhãn Việt (khác mã gốc); giá trị lạ fallback về mã; null hiện "—". */
class NhanHienThiTest {

    private final NhanHienThi nhan = NhanHienThi.INSTANCE;

    @Test
    void moiGiaTriEnumDeuCoNhan() {
        for (NcStatus s : NcStatus.values()) {
            assertThat(nhan.nc(s)).as("NcStatus " + s).isNotEqualTo(s.name());
        }
        for (CapaStatus s : CapaStatus.values()) {
            assertThat(nhan.capa(s)).as("CapaStatus " + s).isNotEqualTo(s.name());
        }
        for (PrStatus s : PrStatus.values()) {
            assertThat(nhan.pr(s)).as("PrStatus " + s).isNotEqualTo(s.name());
        }
        for (ActionStatus s : ActionStatus.values()) {
            assertThat(nhan.actionStatus(s)).as("ActionStatus " + s).isNotEqualTo(s.name());
        }
        for (ActionType t : ActionType.values()) {
            assertThat(nhan.loaiAction(t)).as("ActionType " + t).isNotEqualTo(t.name());
        }
        for (ActionCategory c : ActionCategory.values()) {
            assertThat(nhan.loaiHanhDong(c)).as("ActionCategory " + c).isNotEqualTo(c.name());
        }
        for (RcaMethod m : RcaMethod.values()) {
            assertThat(nhan.rca(m)).as("RcaMethod " + m).isNotEqualTo(m.name());
        }
        for (NhomNguyenNhan6M m : NhomNguyenNhan6M.values()) {
            assertThat(nhan.nhom6m(m)).as("6M " + m).isNotEqualTo(m.name());
        }
        for (NcSource s : NcSource.values()) {
            // MES giữ nguyên là tên riêng; các nguồn còn lại phải có nhãn.
            if (s != NcSource.MES) {
                assertThat(nhan.nguon(s)).as("NcSource " + s).isNotEqualTo(s.name());
            }
        }
        for (LoaiThayDoi l : LoaiThayDoi.values()) {
            assertThat(nhan.loaiThayDoi(l)).as("LoaiThayDoi " + l).isNotEqualTo(l.name());
            for (String vaiTro : l.chuoiDuyet()) {
                assertThat(nhan.vaiTro(vaiTro)).as("Vai trò " + vaiTro).isNotEqualTo(vaiTro);
            }
        }
        for (String ma : new String[] {"THANH_PHAM", "TRONG_SX", "NVL", "MOI_TRUONG", "THIET_BI_DO", "KHIEU_NAI"}) {
            assertThat(nhan.loaiNc(ma)).isNotEqualTo(ma);
        }
        for (String ma : new String[] {"REWORK", "RETURN_NCC", "HA_CAP", "TAI_CHE", "TIEU_HUY"}) {
            assertThat(nhan.disposition(ma)).isNotEqualTo(ma);
        }
        assertThat(nhan.ketQuaDuyet("APPROVED")).isEqualTo("Đã duyệt");
        assertThat(nhan.ketQuaDuyet("REJECTED")).isEqualTo("Từ chối");
        assertThat(nhan.nc(NcStatus.PendingApproval)).isEqualTo("Chờ duyệt");
    }

    @Test
    void giaTriLaFallbackVeMaVaNullHienGachNgang() {
        assertThat(nhan.nc("TrangThaiLa")).isEqualTo("TrangThaiLa");
        assertThat(nhan.loaiNc("LOAI_LA")).isEqualTo("LOAI_LA");
        assertThat(nhan.vaiTro("ROLE_LA")).isEqualTo("ROLE_LA");
        assertThat(nhan.nc(null)).isEqualTo("—");
    }
}
