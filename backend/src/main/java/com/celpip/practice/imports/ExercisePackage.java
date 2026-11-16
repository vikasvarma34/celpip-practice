package com.celpip.practice.imports;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "exercise_package")
public class ExercisePackage {
    @Id @Column(columnDefinition = "varchar") String id;
    String module;
    int schemaVersion;
    Instant importedAt;
    String storageKey;
    String fingerprint;
    String promptFingerprint;
    @Lob String canonicalJson;
    @Lob String assetManifest;
    protected ExercisePackage() {}
}
