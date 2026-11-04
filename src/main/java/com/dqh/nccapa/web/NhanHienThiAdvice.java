package com.dqh.nccapa.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Bơm bảng nhãn tiếng Việt {@link NhanHienThi} vào model của mọi view Thymeleaf (tên {@code nhan}),
 * tương tự DemoUserModelAdvice. Chỉ áp cho @Controller trả view, không đụng REST.
 */
@ControllerAdvice(annotations = Controller.class)
public class NhanHienThiAdvice {

    @ModelAttribute("nhan")
    public NhanHienThi nhan() {
        return NhanHienThi.INSTANCE;
    }
}
