package com.dqh.nccapa.demo;

import com.dqh.nccapa.common.security.RoleChecker;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Bơm "người dùng hiện tại" + danh sách tài khoản demo + cờ quyền UI vào model của MỌI view Thymeleaf,
 * để fragment chuyển tài khoản (header chung), sidebar và gating nút hoạt động trên mọi trang mà không
 * phải sửa từng controller. Chỉ áp cho các controller trả view (@Controller), không đụng REST.
 */
@ControllerAdvice(annotations = Controller.class)
public class DemoUserModelAdvice {

    private final CurrentUserService currentUser;
    private final RoleChecker roleChecker;

    public DemoUserModelAdvice(CurrentUserService currentUser, RoleChecker roleChecker) {
        this.currentUser = currentUser;
        this.roleChecker = roleChecker;
    }

    @ModelAttribute("currentUser")
    public DemoUser currentUser(HttpSession session) {
        return currentUser.hienTai(session);
    }

    @ModelAttribute("demoUsers")
    public List<DemoUser> demoUsers() {
        return currentUser.danhSach();
    }

    /** Cờ quyền UI suy từ DemoRole user hiện tại — dùng cho th:if trong sidebar/nút. */
    @ModelAttribute("quyen")
    public QuyenUi quyen(HttpSession session) {
        return QuyenUi.tu(currentUser.hienTai(session).vaiTro());
    }

    /**
     * Nhà máy của user hiện tại (từ app_user.nha_may qua RoleChecker) — hiển huy hiệu quyền.
     * {@code null} nghĩa là toàn tập đoàn (không gắn nhà máy cụ thể).
     */
    @ModelAttribute("nhaMayUser")
    public String nhaMayUser(HttpSession session) {
        return roleChecker.nhaMayCua(currentUser.hienTai(session).username());
    }
}
