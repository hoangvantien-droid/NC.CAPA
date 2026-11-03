package com.dqh.nccapa.demo;

import com.dqh.nccapa.common.error.ApiResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * Chuyển tài khoản giả lập phục vụ demo (KHÔNG login thật, KHÔNG Spring Security).
 *
 * <p>{@code POST /demo/switch-user} đặt user hiện tại vào session rồi redirect về trang trước
 * (Referer) hoặc "/". {@code GET /demo/users} trả JSON danh sách tài khoản demo.
 */
@Controller
public class DemoUserController {

    private final CurrentUserService currentUser;

    public DemoUserController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @PostMapping("/demo/switch-user")
    public String switchUser(@RequestParam("username") String username,
                             @RequestHeader(value = "Referer", required = false) String referer,
                             HttpSession session) {
        currentUser.doiUser(session, username);
        String dest = StringUtils.hasText(referer) ? referer : "/";
        return "redirect:" + dest;
    }

    @GetMapping("/demo/users")
    @ResponseBody
    public ApiResponse<List<DemoUser>> users() {
        return ApiResponse.ok(currentUser.danhSach());
    }
}
