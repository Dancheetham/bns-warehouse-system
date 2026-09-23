package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.ContactRequest;
import uk.co.bns.warehouse_api.dto.ContactView;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.entity.Contact;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.CompanyRepository;
import uk.co.bns.warehouse_api.repository.ContactRepository;

import java.util.List;

/**
 * People at a Company, kept permanently (not just an import artifact) so
 * they're searchable/linkable when opening a support ticket. Deleting a
 * contact never fails on a linked ticket - the FK is ON DELETE SET NULL
 * (see the V44 migration), so old tickets just lose that specific link and
 * keep their own caller name/phone/email as free text.
 */
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final CompanyRepository companyRepository;

    public List<Contact> findAll() {
        return contactRepository.findAllByOrderByNameAsc();
    }

    public Contact findById(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Contact " + id + " not found"));
    }

    public List<Contact> findByCompany(Long companyId) {
        return contactRepository.findByCompany_IdOrderByNameAsc(companyId);
    }

    @Transactional
    public Contact create(ContactRequest request) {
        Contact contact = new Contact();
        apply(contact, request);
        return contactRepository.save(contact);
    }

    @Transactional
    public Contact update(Long id, ContactRequest request) {
        Contact contact = findById(id);
        apply(contact, request);
        return contactRepository.save(contact);
    }

    @Transactional
    public void delete(Long id) {
        Contact contact = findById(id);
        contactRepository.delete(contact);
    }

    private void apply(Contact contact, ContactRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new NotFoundException("Company " + request.companyId() + " not found"));
        contact.setCompany(company);
        contact.setName(request.name());
        contact.setEmail(request.email());
        contact.setPhone(request.phone());
        contact.setPosition(request.position());
        contact.setMainContact(request.mainContact() != null ? request.mainContact() : contact.isMainContact());
        contact.setActive(request.active() != null ? request.active() : contact.isActive());
    }

    public ContactView toView(Contact contact) {
        return new ContactView(
                contact.getId(),
                contact.getCompany().getId(),
                contact.getCompany().getName(),
                contact.getName(),
                contact.getEmail(),
                contact.getPhone(),
                contact.getPosition(),
                contact.isMainContact(),
                contact.isActive()
        );
    }
}
