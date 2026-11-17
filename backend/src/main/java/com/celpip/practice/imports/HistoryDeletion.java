package com.celpip.practice.imports;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import static com.celpip.practice.imports.PracticeService.state;
@Service
public class HistoryDeletion {
 final PracticeService practice;final PracticeMedia media;final AudioConversion conversion;
 HistoryDeletion(PracticeService practice,PracticeMedia media,AudioConversion conversion){this.practice=practice;this.media=media;this.conversion=conversion;}
 public JsonNode delete(String id)throws Exception{
  synchronized(practice){synchronized(conversion){
   var session=practice.session(id);
   state(!session.status.equals("IN_PROGRESS"));
   var attempts=practice.attempts.findBySessionIdOrderByCreatedAt(id);
   Set<Path> files=new LinkedHashSet<>();
   for(var attempt:attempts){
    var status=conversion.statuses.get(attempt.id);
    if(status!=null&&Set.of("QUEUED","CONVERTING").contains(status.status()))throw new ImportProblem(409,"CONVERSION_ACTIVE","sessionId","Wait for audio conversion to finish before deleting this session.");
    if(attempt.audioPath!=null){files.add(media.safe(attempt.audioPath,false));Path m4a=conversion.target(attempt.id);files.add(m4a);}
   }
   practice.tx.executeWithoutResult(tx->{practice.attempts.deleteAll(attempts);practice.attempts.flush();practice.sessions.delete(session);practice.sessions.flush();});
   boolean cleanupPending=false;
   for(Path file:files)try{Files.deleteIfExists(file);}catch(java.io.IOException failure){cleanupPending=true;}
   for(var attempt:attempts)conversion.statuses.remove(attempt.id);
   return ContractValidator.JSON.createObjectNode().put("deleted",true).put("cleanupPending",cleanupPending);
  }}
 }
}
