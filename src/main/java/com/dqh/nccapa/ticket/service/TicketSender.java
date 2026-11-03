package com.dqh.nccapa.ticket.service;

/** Gửi callback tới hệ ticket. Impl mặc định dùng HTTP; test có thể thay bằng stub. */
public interface TicketSender {
    /** @return true nếu gửi thành công (2xx). */
    boolean send(String url, String payloadJson);
}
