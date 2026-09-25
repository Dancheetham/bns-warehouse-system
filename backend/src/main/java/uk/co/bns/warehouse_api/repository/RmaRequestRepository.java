package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.RmaRequest;
import uk.co.bns.warehouse_api.enums.RmaStatus;

import java.util.List;
import java.util.Optional;

public interface RmaRequestRepository extends JpaRepository<RmaRequest, Long> {
    Optional<RmaRequest> findByPublicReference(String publicReference);
    List<RmaRequest> findByStatusOrderBySubmittedAtAsc(RmaStatus status);
    // Generate Invoices uses this to find, for a credit note it's just
    // raised, whether it came from an RMA with a replacement order to
    // auto-apply the credit to - see InvoiceService.
    Optional<RmaRequest> findByCreditOrder_Id(Long orderId);
}
