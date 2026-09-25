package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.InvoiceLine;

import java.util.List;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {
    // Used by InvoiceService's RMA credit-note auto-apply, to find which
    // invoice(s) already cover a given order (e.g. an RMA's replacement
    // order) so a credit note can be applied straight to them.
    List<InvoiceLine> findByOrder_Id(Long orderId);
}
