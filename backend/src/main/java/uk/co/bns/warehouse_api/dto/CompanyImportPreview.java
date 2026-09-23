package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record CompanyImportPreview(
        int totalCompanyRows,
        int totalContactRows,
        int companiesToCreate,
        int companiesToUpdate,
        int contactsToCreate,
        int contactsToUpdate,
        // Contact rows whose CustomerCode doesn't match any company's Account
        // number, either in the companies file or already in the system -
        // these contacts are skipped, not guessed at.
        List<String> unmatchedContactCodes,
        List<String> edgeCaseNotes,
        List<String> errors
) {}
