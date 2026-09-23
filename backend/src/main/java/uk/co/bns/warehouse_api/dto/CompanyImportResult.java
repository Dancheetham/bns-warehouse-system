package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record CompanyImportResult(
        boolean success,
        int companiesCreated,
        int companiesUpdated,
        int contactsCreated,
        int contactsUpdated,
        int contactsSkipped,
        List<String> errors
) {}
