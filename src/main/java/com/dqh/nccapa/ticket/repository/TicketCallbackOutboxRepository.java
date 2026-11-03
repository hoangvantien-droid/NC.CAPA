package com.dqh.nccapa.ticket.repository;

import com.dqh.nccapa.ticket.entity.TicketCallbackOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TicketCallbackOutboxRepository extends JpaRepository<TicketCallbackOutbox, Long> {
    Optional<TicketCallbackOutbox> findByTicketIdAndCallbackTypeAndEventId(String ticketId, String callbackType, String eventId);
    List<TicketCallbackOutbox> findByTrangThaiOrderByIdAsc(String trangThai);
    List<TicketCallbackOutbox> findByTicketIdOrderByIdAsc(String ticketId);
}
