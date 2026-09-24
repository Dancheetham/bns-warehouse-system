package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.Invoice;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    boolean existsByInvoiceNumber(Integer invoiceNumber);
    Optional<Invoice> findByInvoiceNumber(Integer invoiceNumber);
    List<Invoice> findByCompany_IdOrderByCreatedAtDesc(Long companyId);
}
