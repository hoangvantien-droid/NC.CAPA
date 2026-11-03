package com.dqh.nccapa.notification.repository;

import com.dqh.nccapa.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByTrangThaiOrderByIdAsc(String trangThai);

    List<Notification> findTop50ByOrderByIdDesc();

    /**
     * Chống trùng: đã có thông báo CÙNG loại + đối tượng được tạo trong khoảng
     * [đầu ngày, cuối ngày) chưa. Dùng khoảng thời gian thay vì DATE() để tận dụng index.
     */
    boolean existsByLoaiAndDoiTuongLoaiAndDoiTuongIdAndCreatedAtBetween(
            String loai, String doiTuongLoai, String doiTuongId,
            LocalDateTime from, LocalDateTime to);
}
