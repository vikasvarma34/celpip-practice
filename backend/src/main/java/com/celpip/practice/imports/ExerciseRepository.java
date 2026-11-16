package com.celpip.practice.imports;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ExerciseRepository extends JpaRepository<ImportedExercise, String> {
    List<ImportedExercise> findByPackageIdOrderByExerciseOrder(String packageId);
    boolean existsByFingerprint(String fingerprint);
    boolean existsByPromptFingerprint(String fingerprint);
}
