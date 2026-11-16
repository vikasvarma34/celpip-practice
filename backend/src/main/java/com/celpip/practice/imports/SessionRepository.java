package com.celpip.practice.imports;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SessionRepository extends JpaRepository<PracticeSession,String> {
    List<PracticeSession> findAllByOrderByCreatedAtDesc();
}
