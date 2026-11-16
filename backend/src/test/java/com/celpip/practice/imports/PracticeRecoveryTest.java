package com.celpip.practice.imports;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static com.celpip.practice.imports.TestSupport.*;
import static com.celpip.practice.imports.ContractValidator.JSON;
import static com.celpip.practice.imports.PracticeService.parse;
import static org.junit.jupiter.api.Assertions.*;

class PracticeRecoveryTest {
 @TempDir Path temporary;
 @Test void restartPreservesDraftCompletedAudioAndPairStatus()throws Exception{
  String writing,first,pairId;Path root=temporary.toRealPath();
  try(var app=ImportIntegrationTest.open(root)){
   var p=app.getBean(PracticeService.class);var m=app.getBean(PracticeMedia.class);importFixture(app.getBean(ImportService.class),"writing-1");importFixture(app.getBean(ImportService.class),"speaking-3-4");
   var s=p.start(parse("{\"packageId\":\"SYNTHETIC-WRITING-1\",\"mode\":\"TIMED\"}"));writing=s.get("attemptId").stringValue();p.draft(writing,parse("{\"text\":\"  Original draft\\r\\ntext  \"}"));
   var pair=p.start(parse("{\"packageId\":\"SYNTHETIC-SPEAKING-3-4\",\"mode\":\"PRACTICE\"}"));first=pair.get("attemptId").stringValue();pairId=pair.get("sessionId").stringValue();
   p.advance(pairId,JSON.createObjectNode().put("attemptId",first).put("action","START_RECORDING").putNull("choice").put("timeout",false).put("elapsedMs",10));m.submit(first,"{\"durationMs\":20}",PracticeTest.audio());
   Path stage=Files.createDirectory(root.resolve("staging/media-crash"));String orphan="recordings/orphan.webm";Files.write(root.resolve(orphan),PracticeTest.webm());Files.writeString(stage.resolve("journal.json"),JSON.writeValueAsString(Map.of("attemptId",writing,"path",orphan)));
  }
  try(var app=ImportIntegrationTest.open(root)){
   var p=app.getBean(PracticeService.class);var m=app.getBean(PracticeMedia.class);assertEquals("INTERRUPTED",p.review(writing).get("status").stringValue());assertEquals("  Original draft\r\ntext  ",p.review(writing).at("/draft/text").stringValue());
   assertEquals("COMPLETED",p.review(first).get("status").stringValue());assertArrayEquals(PracticeTest.webm(),Files.readAllBytes(m.audio(first)));assertEquals("INTERRUPTED",p.view(pairId).get("status").stringValue());assertEquals(4,p.view(pairId).get("taskNumber").intValue());assertFalse(Files.exists(root.resolve("recordings/orphan.webm")));
   assertThrows(ImportProblem.class,()->app.getBean(EvaluationService.class).context(first,true));
  }
 }
}
