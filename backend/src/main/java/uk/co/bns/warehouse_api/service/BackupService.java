package uk.co.bns.warehouse_api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.co.bns.warehouse_api.dto.RestoreResult;
import uk.co.bns.warehouse_api.exception.ValidationException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Whole-system backup and restore, built for exactly one job: take everything
 * needed to stand this system up again - identical data AND identical
 * connection secrets - on a different machine, in one file.
 *
 * A plain Postgres dump alone isn't quite that. Almost everything editable
 * from Settings (DPD credentials, printer names, the Shopify access token,
 * despatch/RMA/customisation options...) lives in the app_settings table, so
 * a database dump already covers it - see SettingsService/AppSetting. But a
 * handful of genuinely secret values are deliberately kept OUT of the
 * database and only ever exist as environment variables passed in from
 * docker-compose.yml/.env (Postgres's own credentials, the SMTP account, the
 * Shopify app's Client ID/Secret) - see .env.example for the full list. Those
 * never touch the database, so a database dump alone would silently leave
 * them out of a "full" backup. This service bundles both into one zip:
 * dump.sql (from pg_dump) + secrets.env (this process's actual current
 * values for those env-only variables, in .env format - ready to drop
 * straight in as .env on a new machine) + manifest.json (when/what it was).
 *
 * Restoring the secrets back automatically isn't possible from inside a
 * request handler: a running container's environment variables are fixed at
 * container start (Docker passes them in from .env at "docker compose up"
 * time) and Java can't rewrite its own process's env for a future restart.
 * So restore() only ever touches the database - see restoreZip()'s Javadoc
 * for the two ways secrets.env actually gets used.
 */
@Service
@Slf4j
public class BackupService {

    @Value("${DB_HOST:localhost}")
    private String dbHost;
    @Value("${DB_PORT:5432}")
    private String dbPort;
    @Value("${DB_NAME:bnswarehouse}")
    private String dbName;
    @Value("${DB_USER:bnsadmin}")
    private String dbUser;
    @Value("${DB_PASSWORD:bnsadmin}")
    private String dbPassword;

    @Value("${SMTP_HOST:}")
    private String smtpHost;
    @Value("${SMTP_PORT:587}")
    private String smtpPort;
    @Value("${SMTP_USERNAME:}")
    private String smtpUsername;
    @Value("${SMTP_PASSWORD:}")
    private String smtpPassword;
    @Value("${MAIL_FROM_ADDRESS:}")
    private String mailFromAddress;
    @Value("${ALLOW_TEST_DATA_RESET:false}")
    private String allowTestDataReset;
    @Value("${SHOPIFY_SHOP_DOMAIN:}")
    private String shopifyShopDomain;
    @Value("${SHOPIFY_CLIENT_ID:}")
    private String shopifyClientId;
    @Value("${SHOPIFY_CLIENT_SECRET:}")
    private String shopifyClientSecret;

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    // --------------------------------------------------------------------
    // Backup
    // --------------------------------------------------------------------

    public byte[] createBackupZip() {
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("bns-backup-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a temp folder for the backup", e);
        }
        try {
            Path dumpFile = tempDir.resolve("dump.sql");
            runPgTool("pg_dump",
                    "--no-owner", "--no-privileges", "--clean", "--if-exists",
                    "--file=" + dumpFile);

            String secretsEnv = buildSecretsEnv();
            String manifest = buildManifest();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                addZipEntry(zip, "dump.sql", Files.readAllBytes(dumpFile));
                addZipEntry(zip, "secrets.env", secretsEnv.getBytes(StandardCharsets.UTF_8));
                addZipEntry(zip, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build the backup zip", e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private String buildSecretsEnv() {
        // Exactly the .env.example format, with this process's real current
        // values - deliberately not the DB_* names the API's own Spring
        // config uses internally, but the POSTGRES_*/SMTP_*/SHOPIFY_* names
        // docker-compose.yml and .env actually expect, so this file can be
        // renamed straight to .env on a new machine with nothing to translate.
        StringBuilder sb = new StringBuilder();
        sb.append("# Written by BNS Warehouse System's Backup & Restore feature (Settings) -\n")
          .append("# the real connection secrets this system was running with at backup time.\n")
          .append("# Rename this file to .env in a fresh copy of the project to bring a new\n")
          .append("# machine up with the exact same settings. See docs/BNS_Warehouse_Setup_Guide.pdf.\n\n")
          .append("POSTGRES_DB=").append(dbName).append('\n')
          .append("POSTGRES_USER=").append(dbUser).append('\n')
          .append("POSTGRES_PASSWORD=").append(dbPassword).append("\n\n")
          .append("SMTP_HOST=").append(smtpHost).append('\n')
          .append("SMTP_PORT=").append(smtpPort).append('\n')
          .append("SMTP_USERNAME=").append(smtpUsername).append('\n')
          .append("SMTP_PASSWORD=").append(smtpPassword).append('\n')
          .append("MAIL_FROM_ADDRESS=").append(mailFromAddress).append("\n\n")
          .append("ALLOW_TEST_DATA_RESET=").append(allowTestDataReset).append("\n\n")
          .append("SHOPIFY_SHOP_DOMAIN=").append(shopifyShopDomain).append('\n')
          .append("SHOPIFY_CLIENT_ID=").append(shopifyClientId).append('\n')
          .append("SHOPIFY_CLIENT_SECRET=").append(shopifyClientSecret).append('\n');
        return sb.toString();
    }

    private String buildManifest() {
        String now = OffsetDateTime.now().format(TIMESTAMP);
        // Hand-built, not a JSON library dependency - this is three fixed fields.
        return "{\n"
                + "  \"backedUpAt\": \"" + now + "\",\n"
                + "  \"database\": \"" + dbName + "\",\n"
                + "  \"host\": \"" + dbHost + "\"\n"
                + "}\n";
    }

    // --------------------------------------------------------------------
    // Restore
    // --------------------------------------------------------------------

    /**
     * Restores dump.sql from the uploaded backup zip into THIS system's
     * current database - replacing all current data, since the dump was
     * produced with --clean --if-exists (every object is dropped and
     * recreated). This is how an already-running system gets its data
     * cloned from a backup (e.g. pulling prod data onto a test box).
     *
     * secrets.env inside the zip is read back only to report which secret
     * keys it contains (never their values, and never applied) - this
     * process cannot rewrite its own environment variables. There are two
     * real ways secrets.env gets used:
     *   1. Bringing up a BRAND NEW machine: restore.sh (repo root) reads
     *      secrets.env and writes it out as that machine's .env BEFORE
     *      "docker compose up" ever runs, which is the only point at which
     *      Docker actually reads .env and injects it into the containers.
     *   2. An already-running system that needs those secrets restored too:
     *      copy the values out of secrets.env into this machine's own .env
     *      by hand, then "docker compose up --build" to restart with them.
     */
    public RestoreResult restoreZip(MultipartFile file) {
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("bns-restore-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a temp folder for the restore", e);
        }
        try {
            Path dumpFile = null;
            String manifestJson = null;
            List<String> secretKeys = new ArrayList<>();

            try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    byte[] content = zip.readAllBytes();
                    switch (entry.getName()) {
                        case "dump.sql" -> {
                            dumpFile = tempDir.resolve("dump.sql");
                            Files.write(dumpFile, content);
                        }
                        case "manifest.json" -> manifestJson = new String(content, StandardCharsets.UTF_8);
                        case "secrets.env" -> secretKeys.addAll(extractKeys(new String(content, StandardCharsets.UTF_8)));
                        default -> { /* ignore anything else in the zip */ }
                    }
                }
            }

            if (dumpFile == null) {
                throw new ValidationException("That doesn't look like a BNS Warehouse System backup - no dump.sql found inside it.");
            }

            runPgTool("psql", "--file=" + dumpFile);

            String backedUpAt = extractManifestValue(manifestJson, "backedUpAt");
            String database = extractManifestValue(manifestJson, "database");
            return new RestoreResult(true, backedUpAt, database, secretKeys);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded backup file", e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    // --------------------------------------------------------------------
    // Shared helpers
    // --------------------------------------------------------------------

    private void runPgTool(String tool, String... extraArgs) {
        List<String> command = new ArrayList<>(List.of(
                tool,
                "--host=" + dbHost,
                "--port=" + dbPort,
                "--username=" + dbUser,
                "--dbname=" + dbName
        ));
        command.addAll(List.of(extraArgs));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("PGPASSWORD", dbPassword);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            boolean finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(tool + " timed out after 2 minutes");
            }
            if (process.exitValue() != 0) {
                log.error("{} failed (exit {}): {}", tool, process.exitValue(), output);
                throw new IllegalStateException(tool + " failed - " + firstMeaningfulLine(output));
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    tool + " isn't available in this container. This needs the print of the backend image built "
                            + "from the current Dockerfile (adds postgresql-client) - rebuild with "
                            + "'docker compose up --build' and try again.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(tool + " was interrupted", e);
        }
    }

    private static String firstMeaningfulLine(String output) {
        return Arrays.stream(output.split("\n"))
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .findFirst()
                .orElse("no output");
    }

    private static List<String> extractKeys(String envContent) {
        List<String> keys = new ArrayList<>();
        for (String line : envContent.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) continue;
            String key = trimmed.substring(0, trimmed.indexOf('='));
            String value = trimmed.substring(trimmed.indexOf('=') + 1);
            if (!value.isBlank()) keys.add(key); // only report keys that actually had a value set
        }
        return keys;
    }

    private static String extractManifestValue(String manifestJson, String key) {
        if (manifestJson == null) return null;
        var matcher = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(manifestJson);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void addZipEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of a temp dir - not worth failing the request over
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup of a temp dir - not worth failing the request over
        }
    }
}
