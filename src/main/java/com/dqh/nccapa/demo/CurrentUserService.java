package com.dqh.nccapa.demo;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Giả lập "người dùng hiện tại" phục vụ demo SoD/phân quyền mà KHÔNG cần đăng nhập.
 *
 * <p>Danh sách tài khoản là HẰNG SỐ trong code (không có bảng DB). User hiện tại lưu trong
 * {@link HttpSession} theo username; nếu session chưa chọn thì mặc định là user đầu tiên.
 */
@Service
public class CurrentUserService {

    /** Khóa lưu username đang chọn trong session. */
    public static final String SESSION_KEY = "demoCurrentUsername";

    /** Danh sách tài khoản demo trải đủ các vai (người báo cáo, SX, QA nhà máy, QA tập đoàn, admin). */
    private static final List<DemoUser> USERS = List.of(
            new DemoUser("baocao", "Nguyễn Văn Báo Cáo", DemoRole.NGUOI_BAO_CAO),
            new DemoUser("sxmgp", "Trần Sản Xuất", DemoRole.BO_PHAN_SX),
            new DemoUser("qamgp", "Lê QA Nhà Máy", DemoRole.QA_NHA_MAY),
            new DemoUser("qatd", "Phạm QA Tập Đoàn", DemoRole.QA_TAP_DOAN),
            new DemoUser("admin", "Quản Trị Hệ Thống", DemoRole.ADMIN));

    /** Danh sách bất biến các tài khoản demo. */
    public List<DemoUser> danhSach() {
        return USERS;
    }

    /** Tìm user theo username (nếu có). */
    public Optional<DemoUser> timTheoUsername(String username) {
        if (username == null) return Optional.empty();
        return USERS.stream().filter(u -> u.username().equals(username)).findFirst();
    }

    /** User hiện tại theo session; mặc định user đầu tiên nếu chưa chọn hoặc giá trị không hợp lệ. */
    public DemoUser hienTai(HttpSession session) {
        Object v = session.getAttribute(SESSION_KEY);
        if (v instanceof String username) {
            return timTheoUsername(username).orElse(USERS.get(0));
        }
        return USERS.get(0);
    }

    /**
     * Đổi user hiện tại trong session.
     *
     * @return {@code true} nếu username hợp lệ và đã đổi; {@code false} nếu username không thuộc danh sách.
     */
    public boolean doiUser(HttpSession session, String username) {
        Optional<DemoUser> u = timTheoUsername(username);
        if (u.isEmpty()) return false;
        session.setAttribute(SESSION_KEY, u.get().username());
        return true;
    }
}
