package com.dqh.nccapa.demo;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Bơm "người dùng hiện tại" + danh sách tài khoản demo vào model của MỌI view Thymeleaf,
 * để fragment chuyển tài khoản (header chung) hiển thị được trên mọi trang mà không phải
 * sửa từng controller. Chỉ áp cho các controller trả view (@Controller), không đụng REST.
 */
@ControllerAdvice(annotations = Controller.class)
public class DemoUserModelAdvice {

    private final CurrentUserService currentUser;

    public DemoUserModelAdvice(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @ModelAttribute("currentUser")
    public DemoUser currentUser(HttpSession session) {
        return currentUser.hienTai(session);
    }

    @ModelAttribute("demoUsers")
    public List<DemoUser> demoUsers() {
        return currentUser.danhSach();
    }
}
