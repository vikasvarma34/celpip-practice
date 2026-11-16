package com.celpip.practice.imports;
import jakarta.persistence.*;
@Entity
public class PracticeSession {
    @Id String id;
    @Column(columnDefinition="varchar") String packageId;
    String mode;
    String status;
    String currentAttemptId;
    String reason;
    java.time.Instant createdAt;
    protected PracticeSession() {}
}
