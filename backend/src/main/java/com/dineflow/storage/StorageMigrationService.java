package com.dineflow.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Controlled local-to-cloud object migration utility.
 * Copies objects from local storage to the configured target {@link BinaryObjectStorage},
 * re-downloads and verifies SHA-256 checksums, and NEVER deletes source files unless
 * verification succeeds AND deleteVerifiedSource is explicitly requested.
 */
@Service
public class StorageMigrationService {

    private static final Logger log = LoggerFactory.getLogger(StorageMigrationService.class);

    public record MigrationSummary(
            int scannedFiles,
            int migratedFiles,
            int skippedAlreadyPresent,
            int verifiedCount,
            int failedCount,
            List<String> errors
    ) {}

    public MigrationSummary migrateDirectoryToTarget(Path sourceRoot,
                                                     BinaryObjectStorage targetStorage,
                                                     boolean deleteVerifiedSource) {
        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        if (!Files.exists(normalizedRoot) || !Files.isDirectory(normalizedRoot)) {
            return new MigrationSummary(0, 0, 0, 0, 0, List.of("Source root does not exist: " + normalizedRoot));
        }

        int scanned = 0;
        int migrated = 0;
        int skipped = 0;
        int verified = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(normalizedRoot)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                scanned++;
                String relativeKey = normalizedRoot.relativize(file).toString().replace("\\", "/");
                try {
                    byte[] localBytes = Files.readAllBytes(file);
                    String localChecksum = ObjectStorageService.computeSha256(localBytes);

                    if (targetStorage.exists(relativeKey)) {
                        Optional<byte[]> existing = targetStorage.getObject(relativeKey);
                        if (existing.isPresent() && localChecksum.equals(ObjectStorageService.computeSha256(existing.get()))) {
                            skipped++;
                            verified++;
                            continue;
                        }
                    }

                    targetStorage.putObject(relativeKey, localBytes, inferContentType(relativeKey));
                    migrated++;

                    // Mandatory post-upload checksum verification
                    Optional<byte[]> remoteBytes = targetStorage.getObject(relativeKey);
                    if (remoteBytes.isEmpty() || !localChecksum.equals(ObjectStorageService.computeSha256(remoteBytes.get()))) {
                        failed++;
                        errors.add("Checksum verification failed after upload for key: " + relativeKey);
                        continue;
                    }
                    verified++;

                    if (deleteVerifiedSource) {
                        Files.deleteIfExists(file);
                    }
                } catch (Exception e) {
                    failed++;
                    errors.add("Failed migrating key=" + relativeKey + ": " + e.getMessage());
                    log.error("Migration error for key={}: {}", relativeKey, e.getMessage());
                }
            }
        } catch (IOException e) {
            failed++;
            errors.add("Failed traversing source directory: " + e.getMessage());
        }

        return new MigrationSummary(scanned, migrated, skipped, verified, failed, List.copyOf(errors));
    }

    private String inferContentType(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
