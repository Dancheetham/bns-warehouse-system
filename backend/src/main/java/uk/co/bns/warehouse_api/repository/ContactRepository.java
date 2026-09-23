package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.Contact;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByCompany_IdOrderByNameAsc(Long companyId);
    List<Contact> findAllByOrderByNameAsc();

    // Case-insensitive exact match, used by the bulk importer to find an
    // existing contact at a company to update rather than duplicate.
    Optional<Contact> findFirstByCompany_IdAndEmailIgnoreCase(Long companyId, String email);
}
