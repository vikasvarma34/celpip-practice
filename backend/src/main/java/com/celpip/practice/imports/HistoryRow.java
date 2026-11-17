package com.celpip.practice.imports;
import java.time.Instant;
/** Query projection excludes question snapshots, draft/response text and media paths. */
public record HistoryRow(String sessionId,String packageId,String sessionStatus,Instant sessionCreatedAt,
 String attemptId,String module,int taskNumber,int attemptNumber,String mode,String status,String stage,
 Instant createdAt,long durationMs,long preparationMs,long selectionMs,String choice,boolean automaticChoice,String readingResult) {}
