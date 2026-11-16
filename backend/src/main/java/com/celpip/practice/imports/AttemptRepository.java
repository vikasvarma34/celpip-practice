package com.celpip.practice.imports;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AttemptRepository extends JpaRepository<PracticeAttempt,String> {
    List<PracticeAttempt> findBySessionIdOrderByCreatedAt(String id);
    long countByExerciseId(String id);
}
