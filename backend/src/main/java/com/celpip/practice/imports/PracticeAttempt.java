package com.celpip.practice.imports;
import jakarta.persistence.*;
@Entity
public class PracticeAttempt {
    @Id String id;
    String sessionId;
    @Column(columnDefinition="varchar") String exerciseId;
    int taskNumber;
    int sequenceNumber;
    String status;
    String stage;
    String choice;
    boolean automaticChoice;
    long selectionMs;
    long preparationMs;
    long durationMs;
    @Lob String draftJson;
    @Lob String responseJson;
    @Lob String resultJson;
    String submissionHash;
    String audioPath;
    String audioHash;
    long audioBytes;
    java.time.Instant createdAt;
    java.time.Instant completedAt;
    protected PracticeAttempt() {}
}
