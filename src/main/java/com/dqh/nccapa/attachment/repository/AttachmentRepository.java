package com.dqh.nccapa.attachment.repository;

import com.dqh.nccapa.attachment.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByDoiTuongAndDoiTuongId(String doiTuong, String doiTuongId);
}
