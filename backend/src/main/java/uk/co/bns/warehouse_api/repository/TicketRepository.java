package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uk.co.bns.warehouse_api.entity.Ticket;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByTicketNumber(String ticketNumber);
    List<Ticket> findByCompany_IdOrderByCreatedAtDesc(Long companyId);
    List<Ticket> findByOrder_IdOrderByCreatedAtDesc(Long orderId);
    List<Ticket> findAllByOrderByCreatedAtDesc();

    // Allocates the next ticket number atomically from the ticket_number_seq
    // sequence (see the V43 migration) - safe under two tickets being opened
    // at the same moment, unlike a count()+1 style check.
    @Query(value = "SELECT nextval('ticket_number_seq')", nativeQuery = true)
    long nextTicketNumberValue();
}
