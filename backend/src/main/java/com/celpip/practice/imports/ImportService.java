package com.celpip.practice.imports;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@Service
public class ImportService {
    private final LocalStorage storage;
    private final UploadReader uploads;
    private final ContractValidator validator;
    private final PackageRepository packages;
    private final ExerciseRepository exercises;
    private final TransactionTemplate transaction;
    public record Receipt(String packageId, String module, int schemaVersion, List<Integer> taskNumbers, int exerciseCount, String status) {}
    ImportService(LocalStorage storage, UploadReader uploads, ContractValidator validator, PackageRepository packages,
                  ExerciseRepository exercises, PlatformTransactionManager manager) {
        this.storage = storage; this.uploads = uploads; this.validator = validator; this.packages = packages;
        this.exercises = exercises; transaction = new TransactionTemplate(manager);
    }
    @PostConstruct
    void recover() throws IOException {
        // Only this process can own the data root. Uncommitted files are never served.
        try (var paths = Files.list(storage.root.resolve("staging"))) {
            for (Path p : paths.toList()) {
                if (p.getFileName().toString().startsWith("media-")) continue;
                Path journal = p.resolve("install-key");
                if (Files.isSymbolicLink(p) || Files.isSymbolicLink(journal)) throw new IOException("Unsafe import recovery journal.");
                if (Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)) {
                    String key = Files.readString(journal);
                    if (!key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IOException("Invalid import recovery journal.");
                    if (!packages.existsByStorageKey(key)) LocalStorage.deleteTree(storage.images(key));
                }
                LocalStorage.deleteTree(p);
            }
        }
        try (var paths = Files.list(storage.root.resolve("images"))) {
            for (Path p : paths.toList()) {
                if (Files.isSymbolicLink(p)) throw new IOException("Unsafe managed image directory.");
                if (!packages.existsByStorageKey(p.getFileName().toString())) throw new IOException("Unrecognized image directory preserved. Restore the complete database or inspect storage before restarting.");
            }
        }
        for (ExercisePackage p : packages.findAll()) {
            var manifest = ContractValidator.JSON.readTree(p.assetManifest);
            for (String name : manifest.propertyNames()) {
                Path file = storage.images(p.storageKey).resolve(name.substring("images/".length()));
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toRealPath().startsWith(storage.images(p.storageKey).toRealPath())
                    || !UploadReader.sha(Files.readAllBytes(file)).equals(manifest.get(name).stringValue()))
                    throw new IOException("Stored image integrity check failed. Restore the complete data backup.");
            }
        }
    }
    public synchronized Receipt importUnit(MultipartFile file, String json, List<MultipartFile> assets, List<String> paths, String expectedModule) {
        Path stage = null, installed = null;
        boolean committed = false;
        try {
            stage = storage.stage();
            UploadReader.Upload upload = uploads.read(stage, file, json, assets, paths);
            var unit = validator.validate(Files.readAllBytes(upload.json()));
            if (expectedModule != null) ImportProblem.require(expectedModule.equals(unit.module()), "expectedModule", "Package module does not match the selected module.");
            Map<String, String> hashes = uploads.verifyImages(unit, upload);
            List<String> full = unit.exercises().stream().map(e -> Fingerprints.exercise(unit.module(), e, hashes, false)).toList();
            List<String> prompts = unit.exercises().stream().map(e -> Fingerprints.exercise(unit.module(), e, hashes, true)).toList();
            String fingerprint = Fingerprints.unit(full), promptFingerprint = Fingerprints.unit(prompts);
            var sameId = packages.findById(unit.packageId());
            if (sameId.isPresent() && !sameId.get().fingerprint.equals(fingerprint)) throw conflict("PACKAGE_ID_CONFLICT", "Package ID already belongs to different content.");
            for (int i = 0; i < unit.exercises().size(); i++) {
                var existing = exercises.findById(unit.exercises().get(i).get("exerciseId").stringValue());
                if (existing.isPresent() && !existing.get().fingerprint.equals(full.get(i))) throw conflict("EXERCISE_ID_CONFLICT", "Exercise ID already belongs to different content.");
            }
            var duplicate = packages.findByFingerprint(fingerprint);
            if (duplicate.isPresent()) return receipt(duplicate.get(), "EXISTING");
            if (packages.existsByPromptFingerprint(promptFingerprint)) throw conflict("PROMPT_DUPLICATE", "This prompt or passage already exists with different questions or answers; existing content is preserved.");
            for (int i = 0; i < full.size(); i++) {
                if (exercises.existsById(unit.exercises().get(i).get("exerciseId").stringValue()) || exercises.existsByFingerprint(full.get(i)) || exercises.existsByPromptFingerprint(prompts.get(i)))
                    throw conflict("EXERCISE_DUPLICATE", "An exercise or its prompt already belongs to an existing unit.");
            }
            ExercisePackage entity = new ExercisePackage();
            entity.id = unit.packageId(); entity.module = unit.module(); entity.schemaVersion = 1; entity.importedAt = Instant.now();
            entity.storageKey = UUID.randomUUID().toString(); entity.fingerprint = fingerprint; entity.promptFingerprint = promptFingerprint;
            entity.canonicalJson = Fingerprints.canonical(unit.document()); entity.assetManifest = ContractValidator.JSON.writeValueAsString(hashes);
            if (!hashes.isEmpty()) {
                Files.writeString(stage.resolve("install-key"), entity.storageKey, StandardOpenOption.CREATE_NEW);
                installed = storage.images(entity.storageKey);
                try { Files.move(stage.resolve("images"), installed, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ex) { Files.move(stage.resolve("images"), installed); }
            }
            transaction.executeWithoutResult(status -> {
                packages.saveAndFlush(entity);
                for (int i = 0; i < unit.exercises().size(); i++) {
                    var e = unit.exercises().get(i); ImportedExercise row = new ImportedExercise();
                    row.id = e.get("exerciseId").stringValue(); row.packageId = entity.id; row.taskNumber = e.get("taskNumber").intValue();
                    row.exerciseOrder = i; row.fingerprint = full.get(i); row.promptFingerprint = prompts.get(i); exercises.save(row);
                }
                exercises.flush();
            });
            committed = true;
            return receipt(entity, "IMPORTED");
        } catch (ImportProblem ex) { throw ex; }
        catch (Exception ex) { throw new ImportProblem(503, "IMPORT_SAVE_FAILED", "upload", "Import could not be saved. Check local storage and retry."); }
        finally {
            try {
                if (!committed && installed != null && !packages.existsByStorageKey(installed.getFileName().toString())) LocalStorage.deleteTree(installed);
                if (stage != null) LocalStorage.deleteTree(stage);
            } catch (IOException ex) { throw new ImportProblem(503, "IMPORT_CLEANUP_FAILED", "upload", "Storage cleanup failed; restart the application before retrying."); }
        }
    }
    public Receipt ready(String id) {
        return receipt(packages.findById(id).orElseThrow(() -> new ImportProblem(404, "PACKAGE_NOT_FOUND", "packageId", "Package was not found.")), "READY");
    }
    private Receipt receipt(ExercisePackage p, String status) {
        List<Integer> tasks = exercises.findByPackageIdOrderByExerciseOrder(p.id).stream().map(e -> e.taskNumber).toList();
        return new Receipt(p.id, p.module, p.schemaVersion, tasks, tasks.size(), status);
    }
    private static ImportProblem conflict(String code, String message) { return new ImportProblem(409, code, "package", message); }
}
