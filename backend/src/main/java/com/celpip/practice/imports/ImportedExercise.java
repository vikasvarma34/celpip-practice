package com.celpip.practice.imports;

import jakarta.persistence.*;

@Entity
@Table(name = "imported_exercise")
public class ImportedExercise {
    @Id @Column(columnDefinition = "varchar") String id;
    @Column(columnDefinition = "varchar") String packageId;
    int taskNumber;
    int exerciseOrder;
    String fingerprint;
    String promptFingerprint;
    protected ImportedExercise() {}
}
