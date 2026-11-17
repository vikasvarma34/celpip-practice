package com.celpip.practice.imports;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AttemptRepository extends JpaRepository<PracticeAttempt,String> {
    String HISTORY_SELECT="select new com.celpip.practice.imports.HistoryRow(s.id,s.packageId,s.status,s.createdAt,a.id,p.module,a.taskNumber,a.sequenceNumber,s.mode,a.status,a.stage,a.createdAt,a.durationMs,a.preparationMs,a.selectionMs,a.choice,a.automaticChoice,case when p.module='READING' and a.status='COMPLETED' then a.resultJson else null end) from PracticeAttempt a join PracticeSession s on a.sessionId=s.id join ExercisePackage p on s.packageId=p.id ";
    @org.springframework.data.jpa.repository.Query(HISTORY_SELECT+"where a.createdAt >= :from and a.createdAt < :until order by a.createdAt desc")
    List<HistoryRow> historyDay(@org.springframework.data.repository.query.Param("from") java.time.Instant from,@org.springframework.data.repository.query.Param("until") java.time.Instant until);
    @org.springframework.data.jpa.repository.Query(HISTORY_SELECT+"where s.id=:sessionId order by a.createdAt asc")
    List<HistoryRow> historySession(@org.springframework.data.repository.query.Param("sessionId") String sessionId);
    List<PracticeAttempt> findBySessionIdOrderByCreatedAt(String id);
    @org.springframework.data.jpa.repository.Query("select coalesce(max(a.sequenceNumber),0) from PracticeAttempt a where a.exerciseId=:exerciseId")
    long highestAttemptNumber(@org.springframework.data.repository.query.Param("exerciseId") String exerciseId);
    long countByExerciseId(String id);
}
