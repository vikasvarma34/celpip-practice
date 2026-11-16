package com.celpip.practice.imports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static com.celpip.practice.imports.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @TempDir Path temporary;
    @Test void unjournaledImagesArePreservedIfDatabaseHasNoMatchingRecord() throws Exception {
        Path root = temporary.toRealPath();
        try (var app = ImportIntegrationTest.open(root)) { assertNotNull(app.getBean(ImportService.class)); }
        Path unknown = Files.createDirectory(root.resolve("images").resolve(UUID.randomUUID().toString()));
        Files.writeString(unknown.resolve("keep.txt"), "Do not discard unrecognized data.");
        assertThrows(Exception.class, () -> { try (var app = ImportIntegrationTest.open(root)) { fail("Startup should refuse unknown image data."); } });
        assertEquals("Do not discard unrecognized data.", Files.readString(unknown.resolve("keep.txt")));
    }
    @Test void restartPreservesCanonicalDataAndImagesAndReconcilesCrashFiles() throws Exception {
        Path root=temporary.toRealPath(); String canonical,storageKey;
        try(var app=ImportIntegrationTest.open(root)) {
            var service=app.getBean(ImportService.class); importFixture(service,"speaking-3-4");
            var pkg=app.getBean(PackageRepository.class).findById("SYNTHETIC-SPEAKING-3-4").orElseThrow(); canonical=pkg.canonicalJson; storageKey=pkg.storageKey;
            assertThrows(Exception.class,()->new LocalStorage(root.toString()));
        }
        Path orphan=root.resolve("images").resolve(UUID.randomUUID().toString()); Files.createDirectory(orphan); Files.writeString(orphan.resolve("partial.txt"),"synthetic orphan");
        Path journal = Files.createDirectory(root.resolve("staging/partial-import"));
        Files.writeString(journal.resolve("install-key"), orphan.getFileName().toString());
        Files.writeString(root.resolve("staging/partial-upload"),"synthetic interrupted upload");
        Path committedJournal = Files.createDirectory(root.resolve("staging/committed-import"));
        Files.writeString(committedJournal.resolve("install-key"), storageKey);
        try(var app=ImportIntegrationTest.open(root)) {
            var pkg=app.getBean(PackageRepository.class).findById("SYNTHETIC-SPEAKING-3-4").orElseThrow(); assertEquals(canonical,pkg.canonicalJson);
            assertEquals(2,app.getBean(ImportService.class).ready(pkg.id).exerciseCount());
            assertArrayEquals(Files.readAllBytes(FIXTURES.resolve("images/asset-0.png")),Files.readAllBytes(root.resolve("images").resolve(storageKey).resolve("asset-0.png")));
            assertFalse(Files.exists(orphan)); assertEquals(0,ImportIntegrationTest.count(root.resolve("staging")));
            assertEquals("EXISTING",importFixture(app.getBean(ImportService.class),"speaking-3-4").status());
        }
    }
}
