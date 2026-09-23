package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.TicketEntry;

import java.util.List;

public interface TicketEntryRepository extends JpaRepository<TicketEntry, Long> {
    List<TicketEntry> findByTicket_IdOrderByCreatedAtAsc(Long ticketId);
}
