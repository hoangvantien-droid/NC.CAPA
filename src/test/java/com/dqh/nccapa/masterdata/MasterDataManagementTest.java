package com.dqh.nccapa.masterdata;

import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.demo.CurrentUserService;
import com.dqh.nccapa.masterdata.dto.MasterDataRequest;
import com.dqh.nccapa.masterdata.service.MasterDataLoai;
import com.dqh.nccapa.masterdata.service.MasterDataService;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Màn hình + API quản lý master data: CRUD, ngừng dùng, quyền ADMIN ở server, render dữ liệu thật. */
@AutoConfigureMockMvc
class MasterDataManagementTest extends MySqlTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired CurrentUserService currentUser;
    @Autowired MasterDataService service;
    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc); // nha_may MGP, nhóm lỗi FG/PRC/REC/CMP/ENV, các mã lỗi
        jdbc.update("INSERT IGNORE INTO ma_hang(ma, ten, nha_may) VALUES ('SP001','San pham A','MGP')");
        jdbc.update("INSERT IGNORE INTO ncc(ma, ten, co_giay_cn) VALUES ('NCC01','Nha cung cap 1',1)");
    }

    private MockHttpSession as(String username) {
        MockHttpSession s = new MockHttpSession();
        currentUser.doiUser(s, username);
        return s;
    }

    private ResultActions api(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b,
                              String user, String json) throws Exception {
        b = b.session(as(user));
        if (json != null) b = b.contentType(MediaType.APPLICATION_JSON).content(json);
        return mockMvc.perform(b);
    }

    private static MasterDataRequest req(String ma, String ten) {
        MasterDataRequest r = new MasterDataRequest();
        r.ma = ma; r.ten = ten;
        return r;
    }

    private Integer dem(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---- Thêm / sửa / trùng mã ----

    @Test
    void admin_themCaNamDanhMuc_vaSua() throws Exception {
        api(post("/api/v1/masterdata/nha-may"), "admin", "{\"ma\":\"DNA\",\"ten\":\"Nha may Dong Nai\"}")
                .andExpect(status().isOk());
        api(post("/api/v1/masterdata/ma-hang"), "admin", "{\"ma\":\"SP900\",\"ten\":\"San pham 900\",\"nhaMay\":\"DNA\"}")
                .andExpect(status().isOk());
        api(post("/api/v1/masterdata/nhom-loi"), "admin", "{\"ma\":\"XYZ\",\"ten\":\"Nhom XYZ\",\"theoNguon\":\"FG\"}")
                .andExpect(status().isOk());
        api(post("/api/v1/masterdata/ncc"), "admin",
                "{\"ma\":\"NCC09\",\"ten\":\"NCC 9\",\"coGiayCn\":true,\"cnHetHanNgay\":\"2030-12-31\"}")
                .andExpect(status().isOk());
        api(post("/api/v1/masterdata/ma-loi"), "admin",
                "{\"ma\":\"XYZ-01\",\"ten\":\"Loi XYZ\",\"nhomLoiId\":\"XYZ\",\"mucNghiemTrongDefault\":\"NANG\","
                        + "\"moiNguyDefault\":\"VAT_LY\",\"batBuocKhoanhVung\":true,"
                        + "\"nhomNKiemChung\":\"SX_TP_NVL\",\"hieuLucTu\":\"2026-01-01\"}")
                .andExpect(status().isOk());

        assertThat(dem("SELECT COUNT(*) FROM nha_may WHERE ma='DNA' AND active=1")).isEqualTo(1);
        assertThat(dem("SELECT COUNT(*) FROM ma_hang WHERE ma='SP900' AND nha_may='DNA'")).isEqualTo(1);
        assertThat(dem("SELECT COUNT(*) FROM ncc WHERE ma='NCC09' AND co_giay_cn=1")).isEqualTo(1);
        assertThat(dem("SELECT COUNT(*) FROM ma_loi WHERE ma_loi_id='XYZ-01' AND bat_buoc_khoanh_vung=1 "
                + "AND nguoi_so_huu='QA_TAP_DOAN' AND hieu_luc_den IS NULL")).isEqualTo(1);

        // sửa thuộc tính (mã lấy từ URL, không đổi)
        api(put("/api/v1/masterdata/ma-loi/XYZ-01"), "admin",
                "{\"ten\":\"Loi XYZ doi ten\",\"mucNghiemTrongDefault\":\"NGHIEM_TRONG\"}")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT ten_ma_loi FROM ma_loi WHERE ma_loi_id='XYZ-01'", String.class))
                .isEqualTo("Loi XYZ doi ten");
        assertThat(jdbc.queryForObject("SELECT muc_nghiem_trong_default FROM ma_loi WHERE ma_loi_id='XYZ-01'",
                String.class)).isEqualTo("NGHIEM_TRONG");
        assertThat(dem("SELECT COUNT(*) FROM ma_loi WHERE ma_loi_id='XYZ-01' AND nhom_n_kiem_chung='SX_TP_NVL'"))
                .isEqualTo(1);

        api(put("/api/v1/masterdata/ncc/NCC09"), "admin", "{\"ten\":\"NCC 9 moi\",\"coGiayCn\":false}")
                .andExpect(status().isOk());
        assertThat(dem("SELECT COUNT(*) FROM ncc WHERE ma='NCC09' AND ten='NCC 9 moi' AND co_giay_cn=0")).isEqualTo(1);

        api(put("/api/v1/masterdata/nha-may/KHONGCO"), "admin", "{\"ten\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test
    void trungMa_biTuChoi_vaThieuTruong() throws Exception {
        api(post("/api/v1/masterdata/nha-may"), "admin", "{\"ma\":\"MGP\",\"ten\":\"Trung\"}")
                .andExpect(status().isUnprocessableEntity());
        api(post("/api/v1/masterdata/ma-loi"), "admin",
                "{\"ma\":\"FG-01\",\"ten\":\"Trung\",\"nhomLoiId\":\"FG\",\"mucNghiemTrongDefault\":\"NHE\","
                        + "\"moiNguyDefault\":\"KHONG\",\"nhomNKiemChung\":\"SX_TP_NVL\",\"hieuLucTu\":\"2026-01-01\"}")
                .andExpect(status().isUnprocessableEntity());
        assertThatThrownBy(() -> service.tao(MasterDataLoai.NHA_MAY, req("MGP", "Trung")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đã tồn tại");
        assertThatThrownBy(() -> service.tao(MasterDataLoai.NHA_MAY, req("", "Ten")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.tao(MasterDataLoai.NHA_MAY, req("ABC", " ")))
                .isInstanceOf(BusinessException.class);
        // mã lỗi phải thuộc nhóm lỗi đang hoạt động
        jdbc.update("UPDATE nhom_loi SET active = 0 WHERE nhom_loi_id = 'ENV'");
        MasterDataRequest r = req("ENV-99", "Loi moi");
        r.nhomLoiId = "ENV"; r.mucNghiemTrongDefault = "NHE"; r.moiNguyDefault = "KHONG";
        r.nhomNKiemChung = "SX_TP_NVL"; r.hieuLucTu = "2026-01-01";
        assertThatThrownBy(() -> service.tao(MasterDataLoai.MA_LOI, r)).isInstanceOf(BusinessException.class);
        assertThat(dem("SELECT COUNT(*) FROM ma_loi WHERE ma_loi_id='ENV-99'")).isZero();
    }

    // ---- Ngừng dùng ----

    @Test
    void ngungDung_maLoiDatHieuLucDen_loaiKhacDatCo() throws Exception {
        api(post("/api/v1/masterdata/ma-loi/FG-01/ngung-dung"), "admin", null).andExpect(status().isOk());
        assertThat(dem("SELECT COUNT(*) FROM ma_loi WHERE ma_loi_id='FG-01' AND hieu_luc_den IS NOT NULL"))
                .isEqualTo(1);
        api(post("/api/v1/masterdata/nha-may/MGP/ngung-dung"), "admin", null).andExpect(status().isOk());
        api(post("/api/v1/masterdata/ma-hang/SP001/ngung-dung"), "admin", null).andExpect(status().isOk());
        api(post("/api/v1/masterdata/nhom-loi/ENV/ngung-dung"), "admin", null).andExpect(status().isOk());
        api(post("/api/v1/masterdata/ncc/NCC01/ngung-dung"), "admin", null).andExpect(status().isOk());
        assertThat(dem("SELECT COUNT(*) FROM nha_may WHERE ma='MGP' AND active=0")).isEqualTo(1);
        assertThat(dem("SELECT COUNT(*) FROM ma_hang WHERE ma='SP001' AND active=0")).isEqualTo(1);
        assertThat(dem("SELECT COUNT(*) FROM nhom_loi WHERE nhom_loi_id='ENV' AND active=0")).isEqualTo(1);
        assertThat(dem("SELECT COUNT(*) FROM ncc WHERE ma='NCC01' AND active=0")).isEqualTo(1);
        // dùng lại
        api(post("/api/v1/masterdata/ma-loi/FG-01/kich-hoat"), "admin", null).andExpect(status().isOk());
        assertThat(dem("SELECT COUNT(*) FROM ma_loi WHERE ma_loi_id='FG-01' AND hieu_luc_den IS NULL")).isEqualTo(1);
    }

    private TaoNcRequest ncReq(String maLoi, String maHang) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "THANH_PHAM"; r.nguonPhatSinh = NcSource.THU_CONG; r.nhaMay = "MGP"; r.nguoiBaoCao = "baocao";
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = maLoi; ct.maHang = maHang;
        r.chiTieu = List.of(ct);
        return r;
    }

    @Test
    void danhMucNgungDung_khongTaoNcMoi_nhungNcCuVanDoc() throws Exception {
        Nc cu = ncService.taoNc(ncReq("FG-01", "SP001"));

        api(post("/api/v1/masterdata/ma-loi/FG-01/ngung-dung"), "admin", null).andExpect(status().isOk());
        assertThatThrownBy(() -> ncService.taoNc(ncReq("FG-01", "SP001")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ngừng dùng");

        // nhóm lỗi ngừng dùng -> mọi mã lỗi trong nhóm không dùng được
        api(post("/api/v1/masterdata/nhom-loi/PRC/ngung-dung"), "admin", null).andExpect(status().isOk());
        assertThatThrownBy(() -> ncService.taoNc(ncReq("PRC-02", null))).isInstanceOf(BusinessException.class);

        // mã hàng ngừng dùng
        api(post("/api/v1/masterdata/ma-hang/SP001/ngung-dung"), "admin", null).andExpect(status().isOk());
        assertThatThrownBy(() -> ncService.taoNc(ncReq("REC-06", "SP001"))).isInstanceOf(BusinessException.class);

        // NC cũ vẫn đọc + hiển thị bình thường
        assertThat(ncService.layNc(cu.getSoHieu()).getMaLoiChinh()).isEqualTo("FG-01");
        mockMvc.perform(get("/nc/" + cu.getSoHieu()).session(as("admin"))).andExpect(status().isOk());

        // mã lỗi khác còn dùng vẫn tạo được
        assertThat(ncService.taoNc(ncReq("REC-06", null)).getSoHieu()).isNotBlank();
        // dùng lại thì tạo được
        api(post("/api/v1/masterdata/ma-loi/FG-01/kich-hoat"), "admin", null).andExpect(status().isOk());
        api(post("/api/v1/masterdata/ma-hang/SP001/kich-hoat"), "admin", null).andExpect(status().isOk());
        assertThat(ncService.taoNc(ncReq("FG-01", "SP001")).getSoHieu()).isNotBlank();
    }

    @Test
    void doiTuongNccVaMaHangNgungDung_khongTaoNcMoi() throws Exception {
        TaoNcRequest.DoiTuongItem ncc = new TaoNcRequest.DoiTuongItem();
        ncc.loaiDoiTuong = "NCC"; ncc.giaTri = "NCC01";
        TaoNcRequest ok = ncReq("FG-01", null);
        ok.doiTuong = List.of(ncc);
        assertThat(ncService.taoNc(ok).getSoHieu()).isNotBlank();

        api(post("/api/v1/masterdata/ncc/NCC01/ngung-dung"), "admin", null).andExpect(status().isOk());
        TaoNcRequest bi = ncReq("REC-06", null);
        bi.doiTuong = List.of(ncc);
        assertThatThrownBy(() -> ncService.taoNc(bi)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngừng dùng");

        TaoNcRequest.DoiTuongItem mh = new TaoNcRequest.DoiTuongItem();
        mh.loaiDoiTuong = "MA_HANG"; mh.giaTri = "SP001";
        api(post("/api/v1/masterdata/ma-hang/SP001/ngung-dung"), "admin", null).andExpect(status().isOk());
        TaoNcRequest bi2 = ncReq("REC-06", null);
        bi2.doiTuong = List.of(mh);
        assertThatThrownBy(() -> ncService.taoNc(bi2)).isInstanceOf(BusinessException.class);
    }

    @Test
    void suaConKhiChaNgungDung_khongDoiCha_vaFormGiuChaHienTai() throws Exception {
        jdbc.update("UPDATE nhom_loi SET active = 0 WHERE nhom_loi_id = 'FG'");
        // form sửa (gửi lại nhóm hiện tại, đang ngừng dùng) + đổi tên -> cha giữ nguyên
        api(put("/api/v1/masterdata/ma-loi/FG-01"), "admin", "{\"ten\":\"Doi ten\",\"nhomLoiId\":\"FG\"}")
                .andExpect(status().isOk());
        assertThat(dem("SELECT COUNT(*) FROM ma_loi WHERE ma_loi_id='FG-01' AND nhom_loi_id='FG' "
                + "AND ten_ma_loi='Doi ten'")).isEqualTo(1);
        // trang render có cha ngừng dùng trong lựa chọn (kèm nhãn) để dropdown sửa giữ đúng cha
        // Nhãn nằm trong JS inline: Thymeleaf escape Unicode (ừ -> \u1EEB, ù -> \u00F9) nên so dạng đã escape.
        assertThat(html("/quan-tri/master-data/ma-loi")).contains("(ng\\u1EEBng d\\u00F9ng)");
        // nhưng không chuyển sang một cha đang ngừng dùng khác
        jdbc.update("UPDATE nhom_loi SET active = 0 WHERE nhom_loi_id = 'PRC'");
        api(put("/api/v1/masterdata/ma-loi/REC-06"), "admin", "{\"nhomLoiId\":\"PRC\"}")
                .andExpect(status().isUnprocessableEntity());
        // nhà máy ngừng dùng: sửa mã hàng giữ nguyên nhà máy
        jdbc.update("UPDATE nha_may SET active = 0 WHERE ma = 'MGP'");
        api(put("/api/v1/masterdata/ma-hang/SP001"), "admin", "{\"ten\":\"Doi ten SP\",\"nhaMay\":\"MGP\"}")
                .andExpect(status().isOk());
        assertThat(dem("SELECT COUNT(*) FROM ma_hang WHERE ma='SP001' AND nha_may='MGP' AND ten='Doi ten SP'"))
                .isEqualTo(1);
    }

    // ---- Quyền: chặn ở server ----

    @Test
    void vaiTroKhongPhaiAdmin_biChanCaViewLanEndpointGhi() throws Exception {
        for (String u : List.of("baocao", "sxmgp", "qamgp", "qatd")) {
            mockMvc.perform(get("/quan-tri/master-data").session(as(u))).andExpect(status().is3xxRedirection());
            for (MasterDataLoai l : MasterDataLoai.values()) {
                mockMvc.perform(get("/quan-tri/master-data/" + l.getSlug()).session(as(u)))
                        .andExpect(status().is3xxRedirection());
            }
            api(get("/api/v1/masterdata/nha-may"), u, null).andExpect(status().isForbidden());
            api(post("/api/v1/masterdata/nha-may"), u, "{\"ma\":\"HACK\",\"ten\":\"x\"}")
                    .andExpect(status().isForbidden());
            api(put("/api/v1/masterdata/nha-may/MGP"), u, "{\"ten\":\"hack\"}").andExpect(status().isForbidden());
            api(post("/api/v1/masterdata/ma-loi/FG-01/ngung-dung"), u, null).andExpect(status().isForbidden());
            api(post("/api/v1/masterdata/ma-loi/FG-01/kich-hoat"), u, null).andExpect(status().isForbidden());
        }
        assertThat(dem("SELECT COUNT(*) FROM nha_may WHERE ma='HACK'")).isZero();
        assertThat(dem("SELECT COUNT(*) FROM nha_may WHERE ma='MGP' AND ten='Nha may MGP'")).isEqualTo(1);
        assertThat(dem("SELECT COUNT(*) FROM ma_loi WHERE ma_loi_id='FG-01' AND hieu_luc_den IS NULL")).isEqualTo(1);
    }

    @Test
    void menuMasterData_chiHienVoiAdmin() throws Exception {
        String admin = mockMvc.perform(get("/quan-tri").session(as("admin"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(admin).contains("/quan-tri/master-data");
        String qa = mockMvc.perform(get("/").session(as("qatd"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(qa).doesNotContain("/quan-tri/master-data");
    }

    // ---- Render với dữ liệu thật ----

    @Test
    void admin_xemCacTrang_coNoiDungBanGhi() throws Exception {
        String nm = html("/quan-tri/master-data/nha-may");
        assertThat(nm).contains("MGP").contains("Nha may MGP");
        String mh = html("/quan-tri/master-data/ma-hang");
        assertThat(mh).contains("SP001").contains("San pham A");
        String nl = html("/quan-tri/master-data/nhom-loi");
        assertThat(nl).contains("PRC").contains("Trong SX");
        String ml = html("/quan-tri/master-data/ma-loi");
        assertThat(ml).contains("FG-03").contains("Vi sinh thanh pham OOS").contains("sev-NGHIEM_TRONG");
        String ncc = html("/quan-tri/master-data/ncc");
        assertThat(ncc).contains("NCC01").contains("Nha cung cap 1");
        // tìm kiếm theo mã / tên
        String q = mockMvc.perform(get("/quan-tri/master-data/ma-loi").param("q", "Giay CN").session(as("admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(q).contains("REC-06").doesNotContain("FG-03");
        // sau khi ngừng dùng vẫn liệt kê (kèm nút Dùng lại)
        service.ngungDung(MasterDataLoai.MA_LOI, "FG-03");
        assertThat(html("/quan-tri/master-data/ma-loi")).contains("FG-03").contains("data-act=\"resume\"");
        // slug lạ -> 404
        api(get("/api/v1/masterdata/khong-co"), "admin", null).andExpect(status().isNotFound());
    }

    private String html(String url) throws Exception {
        return mockMvc.perform(get(url).session(as("admin"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
