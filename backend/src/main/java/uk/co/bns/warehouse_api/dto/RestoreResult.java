package uk.co.bns.warehouse_api.dto;

import java.util.List;

/**
 * What came back from restoring a backup - not just "ok", but enough detail
 * to know what actually happened: which backup it was (from the manifest
 * inside the zip), and which connection secrets that backup carries that
 * were deliberately NOT applied automatically (a running container can't
 * rewrite its own environment variables - see BackupService), so the person
 * restoring knows to check their .env by hand if those matter here.
 */
public record RestoreResult(
        boolean success,
        String backedUpAt,
        String backedUpFromDatabase,
        List<String> secretKeysInBackup
) {}
