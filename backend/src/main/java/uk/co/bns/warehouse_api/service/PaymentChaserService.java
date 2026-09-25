package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.entity.Invoice;
import uk.co.bns.warehouse_api.enums.InvoiceType;
import uk.co.bns.warehouse_api.repository.CompanyRepository;
import uk.co.bns.warehouse_api.repository.InvoiceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The daily job behind Payment Tracking: chaser emails (once each per
 * invoice - a 5-day-out warning and an on-the-day-it's-due-overdue notice,
 * both editable under Settings > Invoicing) and, per company, putting an
 * overdue account on hold and taking it back off once it's clear.
 *
 * Auto-hold is edge-triggered, not level-triggered: it only fires for
 * invoices that haven't caused a hold before (Invoice.autoHoldTriggered).
 * Once a company's been auto-held, every invoice that was overdue at that
 * moment is marked as having triggered it - so if staff take the company
 * back off hold by hand (which also clears Company.autoHeld - see
 * CompanyService.apply), this job won't silently put them straight back on
 * hold tomorrow for the same invoices. A genuinely new invoice going
 * overdue later can still trigger a fresh hold, though.
 */
@Service
@RequiredArgsConstructor
public class PaymentChaserService {

    private static final Logger log = LoggerFactory.getLogger(PaymentChaserService.class);

    private final InvoiceRepository invoiceRepository;
    private final CompanyRepository companyRepository;
    private final SettingsService settingsService;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void runDaily() {
        List<Invoice> outstanding = invoiceRepository.findAll().stream()
                .filter(i -> i.getInvoiceType() == InvoiceType.INVOICE)
                .filter(i -> i.getGrandTotal().subtract(i.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0)
                .toList();

        int warningDays = Integer.parseInt(settingsService.get("payment_terms_warning_days", "5"));
        LocalDate today = LocalDate.now();

        for (Invoice invoice : outstanding) {
            sendChasersIfDue(invoice, today, warningDays);
        }

        Map<Long, List<Invoice>> overdueByCompany = new java.util.HashMap<>();
        for (Invoice invoice : outstanding) {
            int termsDays = termsDaysFor(invoice.getCompany());
            int daysSince = (int) ChronoUnit.DAYS.between(invoice.getGenerationDate(), today);
            if (daysSince >= termsDays) {
                overdueByCompany.computeIfAbsent(invoice.getCompany().getId(), k -> new ArrayList<>()).add(invoice);
            }
        }

        for (Company company : companyRepository.findAll()) {
            if (!company.isAutoHoldOnOverdue()) continue;
            List<Invoice> overdue = overdueByCompany.getOrDefault(company.getId(), List.of());
            boolean hasFreshOverdue = overdue.stream().anyMatch(i -> !i.isAutoHoldTriggered());

            if (hasFreshOverdue && !company.isOnHold()) {
                company.setOnHold(true);
                company.setAutoHeld(true);
                overdue.forEach(i -> i.setAutoHoldTriggered(true));
                companyRepository.save(company);
                log.info("Auto-held {} - overdue invoice(s): {}", company.getName(),
                        overdue.stream().map(i -> String.valueOf(i.getInvoiceNumber())).toList());
            } else if (overdue.isEmpty() && company.isOnHold() && company.isAutoHeld()) {
                company.setOnHold(false);
                company.setAutoHeld(false);
                companyRepository.save(company);
                log.info("Auto-unheld {} - no overdue invoices remain", company.getName());
            }
        }
    }

    private void sendChasersIfDue(Invoice invoice, LocalDate today, int warningDays) {
        Company company = invoice.getCompany();
        String to = company.getInvoiceEmail();
        int termsDays = termsDaysFor(company);
        int daysSince = (int) ChronoUnit.DAYS.between(invoice.getGenerationDate(), today);
        boolean changed = false;

        if (invoice.getChaserWarningSentAt() == null
                && daysSince >= termsDays - warningDays && daysSince < termsDays) {
            if (to != null && !to.isBlank()) {
                sendChaser(invoice, to, "chaser_warning_subject", "chaser_warning_body", termsDays - daysSince, 0);
            } else {
                log.info("Skipped payment-warning chaser for invoice {} - no invoice email set for {}",
                        invoice.getInvoiceNumber(), company.getName());
            }
            invoice.setChaserWarningSentAt(LocalDateTime.now());
            changed = true;
        }

        if (invoice.getChaserOverdueSentAt() == null && daysSince >= termsDays) {
            if (to != null && !to.isBlank()) {
                sendChaser(invoice, to, "chaser_overdue_subject", "chaser_overdue_body", 0, daysSince - termsDays);
            } else {
                log.info("Skipped overdue chaser for invoice {} - no invoice email set for {}",
                        invoice.getInvoiceNumber(), company.getName());
            }
            invoice.setChaserOverdueSentAt(LocalDateTime.now());
            changed = true;
        }

        if (changed) invoiceRepository.save(invoice);
    }

    private void sendChaser(Invoice invoice, String to, String subjectKey, String bodyKey, int daysRemaining, int daysOverdue) {
        String subject = fill(settingsService.get(subjectKey, subjectKey), invoice, daysRemaining, daysOverdue);
        String body = fill(settingsService.get(bodyKey, bodyKey), invoice, daysRemaining, daysOverdue);
        EmailService.SendResult result = emailService.send(to, subject, body);
        if (!result.sent()) {
            log.warn("Failed to send chaser for invoice {}: {}", invoice.getInvoiceNumber(), result.reason());
        }
    }

    private String fill(String template, Invoice invoice, int daysRemaining, int daysOverdue) {
        return template
                .replace("{invoiceNumber}", String.valueOf(invoice.getInvoiceNumber()))
                .replace("{companyName}", invoice.getCompany().getName())
                .replace("{amount}", "£" + invoice.getGrandTotal().subtract(invoice.getPaidAmount()).setScale(2, java.math.RoundingMode.HALF_UP))
                .replace("{invoiceDate}", invoice.getGenerationDate().toString())
                .replace("{daysRemaining}", String.valueOf(daysRemaining))
                .replace("{daysOverdue}", String.valueOf(daysOverdue));
    }

    private int termsDaysFor(Company company) {
        return company.getPaymentTermsDays() != null
                ? company.getPaymentTermsDays()
                : Integer.parseInt(settingsService.get("payment_terms_days", "30"));
    }
}
