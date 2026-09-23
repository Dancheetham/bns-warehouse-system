package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.PublicTicketRequest;
import uk.co.bns.warehouse_api.dto.TicketEntryRequest;
import uk.co.bns.warehouse_api.dto.TicketEntryView;
import uk.co.bns.warehouse_api.dto.TicketRequest;
import uk.co.bns.warehouse_api.dto.TicketSummaryView;
import uk.co.bns.warehouse_api.dto.TicketView;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.entity.Contact;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.Ticket;
import uk.co.bns.warehouse_api.entity.TicketEntry;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.CompanyRepository;
import uk.co.bns.warehouse_api.repository.ContactRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.TicketEntryRepository;
import uk.co.bns.warehouse_api.repository.TicketRepository;

import java.util.List;

/**
 * Support tickets for inbound calls/emails - optionally linked to a Company
 * and/or a specific Order, with a dated, user-attributed timeline of notes
 * (TicketEntry) rather than one free-text field, so a ticket added to over
 * several calls stays readable. See the V43 migration for why the ticket
 * number comes from a real DB sequence rather than Order's
 * count()+1/existsBy retry loop.
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    private static final String TICKET_NUMBER_PREFIX = "TKT-";
    private static final String DEFAULT_PUBLIC_AUTHOR = "Call Transcript";

    private final TicketRepository ticketRepository;
    private final TicketEntryRepository ticketEntryRepository;
    private final CompanyRepository companyRepository;
    private final OrderRepository orderRepository;
    private final ContactRepository contactRepository;

    public List<Ticket> findAll() {
        return ticketRepository.findAllByOrderByCreatedAtDesc();
    }

    public Ticket findById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket " + id + " not found"));
    }

    public Ticket findByTicketNumber(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new NotFoundException("Ticket " + ticketNumber + " not found"));
    }

    public List<Ticket> findByCompany(Long companyId) {
        return ticketRepository.findByCompany_IdOrderByCreatedAtDesc(companyId);
    }

    public List<Ticket> findByOrder(Long orderId) {
        return ticketRepository.findByOrder_IdOrderByCreatedAtDesc(orderId);
    }

    public List<Ticket> findByContact(Long contactId) {
        return ticketRepository.findByContact_IdOrderByCreatedAtDesc(contactId);
    }

    @Transactional
    public Ticket create(TicketRequest request) {
        Ticket ticket = new Ticket();
        ticket.setTicketNumber(nextTicketNumber());
        apply(ticket, request);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket update(Long id, TicketRequest request) {
        Ticket ticket = findById(id);
        apply(ticket, request);
        return ticketRepository.save(ticket);
    }

    private void apply(Ticket ticket, TicketRequest request) {
        ticket.setTitle(request.title());
        ticket.setCallerName(request.callerName());
        ticket.setPhone(request.phone());
        ticket.setEmail(request.email());
        if (request.status() != null) {
            ticket.setStatus(request.status());
        }
        ticket.setTalkTimeMinutes(request.talkTimeMinutes() != null ? request.talkTimeMinutes() : ticket.getTalkTimeMinutes());

        if (request.contactId() != null) {
            Contact contact = contactRepository.findById(request.contactId())
                    .orElseThrow(() -> new NotFoundException("Contact " + request.contactId() + " not found"));
            ticket.setContact(contact);
        } else {
            ticket.setContact(null);
        }

        Long effectiveCompanyId = request.companyId();
        if (effectiveCompanyId == null && request.contactId() != null && ticket.getContact() != null) {
            // A ticket linked to a contact defaults to that contact's own
            // company when no company was explicitly chosen - mirrors the
            // order->company default below.
            effectiveCompanyId = ticket.getContact().getCompany().getId();
        }

        if (request.orderId() != null) {
            Order order = orderRepository.findById(request.orderId())
                    .orElseThrow(() -> new NotFoundException("Order " + request.orderId() + " not found"));
            ticket.setOrder(order);
            // A ticket about a specific order defaults to that order's own
            // company when no company was explicitly chosen, rather than
            // leaving the ticket unlinked from the account it's actually about.
            if (effectiveCompanyId == null && order.getCompany() != null) {
                effectiveCompanyId = order.getCompany().getId();
            }
        } else {
            ticket.setOrder(null);
        }

        if (effectiveCompanyId != null) {
            Company company = companyRepository.findById(effectiveCompanyId)
                    .orElseThrow(() -> new NotFoundException("Company " + effectiveCompanyId + " not found"));
            ticket.setCompany(company);
        } else {
            ticket.setCompany(null);
        }
    }

    @Transactional
    public Ticket createFromPublicApi(PublicTicketRequest request) {
        Ticket ticket = new Ticket();
        ticket.setTicketNumber(nextTicketNumber());
        ticket.setTitle(request.title());
        ticket.setCallerName(request.callerName());
        ticket.setPhone(request.phone());
        ticket.setEmail(request.email());
        ticket.setTalkTimeMinutes(request.talkTimeMinutes() != null ? request.talkTimeMinutes() : 0);

        if (request.orderNumber() != null && !request.orderNumber().isBlank()) {
            Order order = orderRepository.findByOrderNumber(request.orderNumber())
                    .orElseThrow(() -> new ValidationException("No order found with number " + request.orderNumber()));
            ticket.setOrder(order);
            if (order.getCompany() != null) {
                ticket.setCompany(order.getCompany());
            }
        }
        if (request.companyId() != null) {
            Company company = companyRepository.findById(request.companyId())
                    .orElseThrow(() -> new NotFoundException("Company " + request.companyId() + " not found"));
            ticket.setCompany(company);
        }
        ticket = ticketRepository.save(ticket);

        if (request.note() != null && !request.note().isBlank()) {
            addEntry(ticket, request.note(),
                    request.author() != null && !request.author().isBlank() ? request.author() : DEFAULT_PUBLIC_AUTHOR);
        }
        return ticket;
    }

    @Transactional
    public TicketEntry addEntry(Long ticketId, TicketEntryRequest request, String defaultAuthor) {
        Ticket ticket = findById(ticketId);
        String author = request.author() != null && !request.author().isBlank() ? request.author() : defaultAuthor;
        return addEntry(ticket, request.note(), author);
    }

    @Transactional
    public TicketEntry addEntryByTicketNumber(String ticketNumber, String note, String author) {
        Ticket ticket = findByTicketNumber(ticketNumber);
        return addEntry(ticket, note, author != null && !author.isBlank() ? author : DEFAULT_PUBLIC_AUTHOR);
    }

    private TicketEntry addEntry(Ticket ticket, String note, String author) {
        TicketEntry entry = new TicketEntry();
        entry.setTicket(ticket);
        entry.setNote(note);
        entry.setAuthor(author);
        return ticketEntryRepository.save(entry);
    }

    public List<TicketEntry> entriesFor(Long ticketId) {
        return ticketEntryRepository.findByTicket_IdOrderByCreatedAtAsc(ticketId);
    }

    private String nextTicketNumber() {
        return TICKET_NUMBER_PREFIX + ticketRepository.nextTicketNumberValue();
    }

    public TicketSummaryView toSummaryView(Ticket ticket) {
        return new TicketSummaryView(
                ticket.getId(), ticket.getTicketNumber(), ticket.getTitle(), ticket.getCallerName(),
                ticket.getPhone(), ticket.getEmail(),
                ticket.getCompany() != null ? ticket.getCompany().getId() : null,
                ticket.getCompany() != null ? ticket.getCompany().getName() : null,
                ticket.getOrder() != null ? ticket.getOrder().getId() : null,
                ticket.getOrder() != null ? ticket.getOrder().getOrderNumber() : null,
                ticket.getContact() != null ? ticket.getContact().getId() : null,
                ticket.getContact() != null ? ticket.getContact().getName() : null,
                ticket.getStatus(), ticket.getTalkTimeMinutes(), ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    public TicketView toView(Ticket ticket) {
        List<TicketEntryView> entries = entriesFor(ticket.getId()).stream()
                .map(e -> new TicketEntryView(e.getId(), e.getAuthor(), e.getNote(), e.getCreatedAt()))
                .toList();
        return new TicketView(
                ticket.getId(), ticket.getTicketNumber(), ticket.getTitle(), ticket.getCallerName(),
                ticket.getPhone(), ticket.getEmail(),
                ticket.getCompany() != null ? ticket.getCompany().getId() : null,
                ticket.getCompany() != null ? ticket.getCompany().getName() : null,
                ticket.getOrder() != null ? ticket.getOrder().getId() : null,
                ticket.getOrder() != null ? ticket.getOrder().getOrderNumber() : null,
                ticket.getContact() != null ? ticket.getContact().getId() : null,
                ticket.getContact() != null ? ticket.getContact().getName() : null,
                ticket.getStatus(), ticket.getTalkTimeMinutes(), ticket.getCreatedAt(), ticket.getUpdatedAt(), entries);
    }
}
