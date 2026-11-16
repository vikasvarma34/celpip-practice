package com.celpip.practice.imports;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public record ImportLimits(
    @Value("${celpip.import.compressed-bytes:31457280}") long compressedBytes,
    @Value("${celpip.import.extracted-bytes:62914560}") long extractedBytes,
    @Value("${celpip.import.json-bytes:2097152}") long jsonBytes,
    @Value("${celpip.import.image-bytes:15728640}") long imageBytes,
    @Value("${celpip.import.max-pixels:25000000}") long maxPixels,
    @Value("${celpip.import.max-entries:16}") int maxEntries,
    @Value("${celpip.import.max-ratio:100}") long maxRatio) {
    public ImportLimits {
        if (compressedBytes <= 0 || extractedBytes <= 0 || jsonBytes <= 0 || imageBytes <= 0 || maxPixels <= 0 || maxEntries < 1 || maxRatio < 1) throw new IllegalArgumentException("Import limits must be positive.");
    }
}
