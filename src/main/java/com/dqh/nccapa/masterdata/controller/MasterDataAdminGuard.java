package com.dqh.nccapa.masterdata.controller;

import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.common.error.ErrorCode;
import com.dqh.nccapa.demo.CurrentUserService;
import com.dqh.nccapa.demo.DemoRole;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/** Chặn ở SERVER: chỉ user hiện tại (theo session) có vai trò ADMIN mới vào/ghi master data. */
@Component
public class MasterDataAdminGuard {

    private final CurrentUserService currentUser;

    public MasterDataAdminGuard(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    public boolean laAdmin(HttpSession session) {
        return currentUser.hienTai(session).vaiTro() == DemoRole.ADMIN;
    }

    /** @throws BusinessException ROLE_DENIED (HTTP 403) nếu không phải ADMIN. */
    public void yeuCauAdmin(HttpSession session) {
        if (!laAdmin(session)) {
            throw new BusinessException(ErrorCode.ROLE_DENIED, "Chỉ quản trị viên (ADMIN) được quản lý master data");
        }
    }
}
