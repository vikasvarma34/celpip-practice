package com.celpip.practice.imports;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PackageRepository extends JpaRepository<ExercisePackage, String> {
    Optional<ExercisePackage> findByFingerprint(String fingerprint);
    boolean existsByPromptFingerprint(String fingerprint);
    boolean existsByStorageKey(String key);
}
