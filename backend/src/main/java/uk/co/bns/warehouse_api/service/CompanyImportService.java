package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.co.bns.warehouse_api.dto.CompanyImportPreview;
import uk.co.bns.warehouse_api.dto.CompanyImportResult;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.entity.Contact;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.CompanyRepository;
import uk.co.bns.warehouse_api.repository.ContactRepository;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * Bulk create/update of Companies and their Contacts from the two OrderWise
 * export spreadsheets (customer list + customer contact details), matched by
 * Account number / CustomerCode. Mirrors StockImportService's preview/commit
 * pattern - buildPlan() is shared by both so preview can never promise
 * something commit doesn't actually do.
 *
 * Never excludes "Do not use"/"On hold" companies (explicit decision - those
 * flags are imported and just become filters on the Companies page).
 *
 * Header matching deliberately normalizes via trim().toLowerCase() only, NOT
 * stripping spaces - the contacts sheet has two similarly-named columns,
 * "Main contact" (ambiguous, meaning unconfirmed from sample data - left
 * unmapped on purpose) and "MainContact" (the real per-contact main-contact
 * flag, immediately before ActiveContact). Stripping spaces would collide
 * them into the same key.
 */
@Service
@RequiredArgsConstructor
public class CompanyImportService {

    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;

    private record CompanyRow(String accountNumber, String name, boolean onHold, boolean doNotUse,
                               boolean gaps, boolean gdms, BigDecimal creditLimit, String eoriNumber,
                               String vatNumber) {}

    private record ContactRow(String customerCode, String name, String email, String phone,
                               String position, boolean mainContact, boolean active) {}

    private static class ImportPlan {
        List<CompanyRow> companyRows = new ArrayList<>();
        List<ContactRow> contactRows = new ArrayList<>();
        Map<String, Company> companiesByAccountNumber = new LinkedHashMap<>();
        List<String> unmatchedContactCodes = new ArrayList<>();
        List<String> edgeCaseNotes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int companiesToCreate = 0;
        int companiesToUpdate = 0;
        int contactsToCreate = 0;
        int contactsToUpdate = 0;
    }

    public CompanyImportPreview preview(MultipartFile companiesFile, MultipartFile contactsFile) {
        ImportPlan plan = buildPlan(companiesFile, contactsFile);
        return new CompanyImportPreview(
                plan.companyRows.size(), plan.contactRows.size(),
                plan.companiesToCreate, plan.companiesToUpdate,
                plan.contactsToCreate, plan.contactsToUpdate,
                plan.unmatchedContactCodes, plan.edgeCaseNotes, plan.errors);
    }

    @Transactional
    public CompanyImportResult commit(MultipartFile companiesFile, MultipartFile contactsFile) {
        ImportPlan plan = buildPlan(companiesFile, contactsFile);
        if (!plan.errors.isEmpty()) {
            return new CompanyImportResult(false, 0, 0, 0, 0, 0, plan.errors);
        }

        int companiesCreated = 0;
        int companiesUpdated = 0;
        Map<String, Company> byAccountNumber = new LinkedHashMap<>();
        for (Company existing : companyRepository.findAll()) {
            if (existing.getAccountNumber() != null) {
                byAccountNumber.put(existing.getAccountNumber().toUpperCase(), existing);
            }
        }

        for (CompanyRow row : plan.companyRows) {
            String key = row.accountNumber().toUpperCase();
            Company company = byAccountNumber.get(key);
            boolean isNew = company == null;
            if (isNew) {
                company = new Company();
                company.setAccountNumber(row.accountNumber());
            }
            company.setName(row.name());
            company.setOnHold(row.onHold());
            company.setDoNotUse(row.doNotUse());
            company.setGaps(row.gaps());
            company.setGdms(row.gdms());
            if (row.creditLimit() != null) {
                company.setCreditLimit(row.creditLimit());
            }
            if (row.eoriNumber() != null) {
                company.setEoriNumber(row.eoriNumber());
            }
            if (row.vatNumber() != null) {
                company.setVatNumber(row.vatNumber());
            }
            company = companyRepository.save(company);
            byAccountNumber.put(key, company);
            if (isNew) {
                companiesCreated++;
            } else {
                companiesUpdated++;
            }
        }

        int contactsCreated = 0;
        int contactsUpdated = 0;
        int contactsSkipped = 0;
        for (ContactRow row : plan.contactRows) {
            Company company = byAccountNumber.get(row.customerCode().toUpperCase());
            if (company == null) {
                contactsSkipped++;
                continue;
            }
            Contact contact = row.email() != null && !row.email().isBlank()
                    ? contactRepository.findFirstByCompany_IdAndEmailIgnoreCase(company.getId(), row.email()).orElse(null)
                    : null;
            boolean isNew = contact == null;
            if (isNew) {
                contact = new Contact();
                contact.setCompany(company);
            }
            contact.setName(row.name());
            contact.setEmail(row.email());
            contact.setPhone(row.phone());
            contact.setPosition(row.position());
            contact.setMainContact(row.mainContact());
            contact.setActive(row.active());
            contactRepository.save(contact);
            if (isNew) {
                contactsCreated++;
            } else {
                contactsUpdated++;
            }
        }

        return new CompanyImportResult(true, companiesCreated, companiesUpdated,
                contactsCreated, contactsUpdated, contactsSkipped, List.of());
    }

    private ImportPlan buildPlan(MultipartFile companiesFile, MultipartFile contactsFile) {
        ImportPlan plan = new ImportPlan();

        try {
            plan.companyRows = readCompanyRows(companiesFile);
        } catch (IOException e) {
            plan.errors.add("Could not read the companies spreadsheet: " + e.getMessage());
        } catch (ValidationException e) {
            plan.errors.add(e.getMessage());
        }
        try {
            plan.contactRows = readContactRows(contactsFile);
        } catch (IOException e) {
            plan.errors.add("Could not read the contacts spreadsheet: " + e.getMessage());
        } catch (ValidationException e) {
            plan.errors.add(e.getMessage());
        }
        if (!plan.errors.isEmpty()) {
            return plan;
        }

        Set<String> knownAccountNumbers = new HashSet<>();
        for (Company existing : companyRepository.findAll()) {
            if (existing.getAccountNumber() != null) {
                knownAccountNumbers.add(existing.getAccountNumber().toUpperCase());
            }
        }
        Set<String> seenInFile = new HashSet<>();
        for (CompanyRow row : plan.companyRows) {
            String key = row.accountNumber().toUpperCase();
            seenInFile.add(key);
            if (knownAccountNumbers.contains(key)) {
                plan.companiesToUpdate++;
            } else {
                plan.companiesToCreate++;
            }
        }

        Set<String> matchableCodes = new HashSet<>(knownAccountNumbers);
        matchableCodes.addAll(seenInFile);

        Set<String> unmatched = new LinkedHashSet<>();
        for (ContactRow row : plan.contactRows) {
            String key = row.customerCode().toUpperCase();
            if (!matchableCodes.contains(key)) {
                unmatched.add(row.customerCode());
                continue;
            }
            // Preview can't know per-row create-vs-update against existing
            // contacts without hitting the DB per row - approximated here as
            // "to create" since most companies have no contacts yet; commit's
            // actual create/update counts are authoritative.
            plan.contactsToCreate++;
        }
        plan.unmatchedContactCodes = new ArrayList<>(unmatched);

        return plan;
    }

    private static final Map<String, String> COMPANY_HEADER_MAP = Map.ofEntries(
            Map.entry("account number", "accountnumber"),
            Map.entry("statement name", "name"),
            Map.entry("on hold", "onhold"),
            Map.entry("do not use this account", "donotuse"),
            Map.entry("gaps", "gaps"),
            Map.entry("gdms", "gdms"),
            Map.entry("credit limit", "creditlimit"),
            Map.entry("eori number", "eorinumber"),
            Map.entry("vat number", "vatnumber")
    );

    private List<CompanyRow> readCompanyRows(MultipartFile file) throws IOException {
        List<CompanyRow> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return rows;

            Map<Integer, String> columnMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell).trim().toLowerCase();
                String mapped = COMPANY_HEADER_MAP.get(header);
                if (mapped != null) {
                    columnMap.put(cell.getColumnIndex(), mapped);
                }
            }
            if (!columnMap.containsValue("accountnumber") || !columnMap.containsValue("name")) {
                throw new ValidationException(
                        "Expected columns 'Account number' and 'Statement name' were not found in the companies spreadsheet");
            }

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, String> rowData = new HashMap<>();
                for (Map.Entry<Integer, String> col : columnMap.entrySet()) {
                    Cell cell = row.getCell(col.getKey());
                    rowData.put(col.getValue(), cell == null ? "" : formatter.formatCellValue(cell).trim());
                }

                String accountNumber = rowData.getOrDefault("accountnumber", "");
                // Skips the export's "Grand Summaries"/count-sum rows, which
                // carry no account number.
                if (accountNumber.isBlank()) continue;

                String name = rowData.getOrDefault("name", "");
                if (name.isBlank()) name = accountNumber;

                rows.add(new CompanyRow(
                        accountNumber,
                        name,
                        parseBoolean(rowData.get("onhold")),
                        parseBoolean(rowData.get("donotuse")),
                        parseBoolean(rowData.get("gaps")),
                        parseBoolean(rowData.get("gdms")),
                        parseDecimal(rowData.get("creditlimit")),
                        blankToNull(rowData.get("eorinumber")),
                        blankToNull(rowData.get("vatnumber"))
                ));
            }
        }
        return rows;
    }

    private static final Map<String, String> CONTACT_HEADER_MAP = Map.ofEntries(
            Map.entry("customercode", "customercode"),
            Map.entry("customercontact", "name"),
            Map.entry("contactemail", "email"),
            Map.entry("contacttelephone", "phone"),
            Map.entry("contactposition", "position"),
            Map.entry("maincontact", "maincontact"),
            Map.entry("activecontact", "activecontact")
            // "main contact" (with a space) deliberately left unmapped - see
            // class javadoc.
    );

    private List<ContactRow> readContactRows(MultipartFile file) throws IOException {
        List<ContactRow> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return rows;

            Map<Integer, String> columnMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell).trim().toLowerCase();
                String mapped = CONTACT_HEADER_MAP.get(header);
                if (mapped != null) {
                    columnMap.put(cell.getColumnIndex(), mapped);
                }
            }
            if (!columnMap.containsValue("customercode") || !columnMap.containsValue("name")) {
                throw new ValidationException(
                        "Expected columns 'CustomerCode' and 'CustomerContact' were not found in the contacts spreadsheet");
            }

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, String> rowData = new HashMap<>();
                for (Map.Entry<Integer, String> col : columnMap.entrySet()) {
                    Cell cell = row.getCell(col.getKey());
                    rowData.put(col.getValue(), cell == null ? "" : formatter.formatCellValue(cell).trim());
                }

                String customerCode = rowData.getOrDefault("customercode", "");
                String name = rowData.getOrDefault("name", "");
                if (customerCode.isBlank() || name.isBlank()) continue;

                // ActiveContact seen blank for some rows in the real export -
                // treated as active (blank/unset shouldn't silently hide a
                // contact), only an explicit False deactivates.
                String activeRaw = rowData.get("activecontact");
                boolean active = activeRaw == null || activeRaw.isBlank() || parseBoolean(activeRaw);

                rows.add(new ContactRow(
                        customerCode,
                        name,
                        blankToNull(rowData.get("email")),
                        blankToNull(rowData.get("phone")),
                        blankToNull(rowData.get("position")),
                        parseBoolean(rowData.get("maincontact")),
                        active
                ));
            }
        }
        return rows;
    }

    private boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) return false;
        String v = value.trim().toLowerCase();
        return v.equals("true") || v.equals("yes") || v.equals("1");
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value;
    }
}
