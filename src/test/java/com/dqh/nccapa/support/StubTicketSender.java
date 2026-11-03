package com.dqh.nccapa.support;

import com.dqh.nccapa.ticket.service.TicketSender;

import java.util.ArrayList;
import java.util.List;

/** Stub TicketSender cho test: điều khiển thành công/thất bại + ghi lại payload đã gửi. */
public class StubTicketSender implements TicketSender {
    public volatile boolean failNext = false;        // nếu true → send trả false
    public final List<String> sent = new ArrayList<>();

    @Override
    public boolean send(String url, String payloadJson) {
        if (failNext) return false;
        sent.add(payloadJson);
        return true;
    }
}
