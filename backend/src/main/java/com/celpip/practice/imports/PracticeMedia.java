package com.celpip.practice.imports;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.io.*;
import static com.celpip.practice.imports.PracticeService.*;
import static com.celpip.practice.imports.ContractValidator.JSON;

@Service
public class PracticeMedia {
    final PracticeService practice; final LocalStorage storage; final long maxBytes;
    PracticeMedia(PracticeService practice,LocalStorage storage,ImportService imports,@Value("${celpip.audio.max-bytes:52428800}")long maxBytes){this.practice=practice;this.storage=storage;this.maxBytes=maxBytes;}
    @PostConstruct void recover() throws IOException {
        try(var paths=Files.list(storage.root.resolve("staging"))){for(Path stage:paths.toList())if(stage.getFileName().toString().startsWith("media-")){
            Path journal=stage.resolve("journal.json");if(Files.isRegularFile(journal,LinkOption.NOFOLLOW_LINKS)){var j=parse(Files.readString(journal));String path=j.get("path").stringValue();var a=practice.attempts.findById(j.get("attemptId").stringValue());if(a.isEmpty()||!path.equals(a.get().audioPath))Files.deleteIfExists(safe(path,false));}LocalStorage.deleteTree(stage);
        }}
        for(var a:practice.attempts.findAll())if(a.audioPath!=null){Path file=safe(a.audioPath,true);if(!UploadReader.sha(Files.readAllBytes(file)).equals(a.audioHash))throw new IOException("Recording integrity check failed.");}
    }
    Path safe(String relative,boolean exists) throws IOException {
        Path p=storage.root.resolve(relative).normalize();if(!p.startsWith(storage.root)||p.equals(storage.root))throw new IOException("Unmanaged path.");
        for(Path q=p;!q.equals(storage.root);q=q.getParent())if(Files.isSymbolicLink(q))throw new IOException("Symlinked managed path.");
        if(exists&&!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Managed file is missing.");return p;
    }
    public JsonNode submit(String id,String metadata,MultipartFile audio) {
        synchronized(practice){
            JsonNode r=parse(metadata);ContractValidator.fields(r,"metadata","durationMs");long duration=duration(r.get("durationMs"));var a=practice.attempt(id);state(practice.module(a).equals("SPEAKING"));
            Path stage=null,target=null;boolean committed=false;
            try {
                stage=Files.createTempDirectory(storage.root.resolve("staging"),"media-");Path upload=stage.resolve("audio.webm");
                try(var in=audio.getInputStream()){UploadReader.copy(in,upload,maxBytes);}byte[] bytes=Files.readAllBytes(upload);WebmAudio.validate(bytes,audio.getContentType());
                String audioHash=UploadReader.sha(bytes),fingerprint=UploadReader.sha((audioHash+":"+duration).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(a.status.equals("COMPLETED")){state(fingerprint.equals(a.submissionHash));return receipt(a);}
                practice.active(a);state(a.stage.equals("RECORDING"));if(!practice.session(a.sessionId).mode.equals("PRACTICE")&&!practice.testingEarlyFinish)state(duration>=practice.timing(a).answerSeconds()*1000L);
                String exercise=a.exerciseId.substring(0,Math.min(70,a.exerciseId.length()));String filename=LocalDate.now(ZoneId.systemDefault())+"_"+exercise+"_Attempt"+a.sequenceNumber+"_"+a.id+".webm";
                String relative="recordings/"+practice.pack(practice.session(a.sessionId)).storageKey+"/"+filename;target=safe(relative,false);Files.createDirectories(target.getParent());
                Files.writeString(stage.resolve("journal.json"),JSON.writeValueAsString(Map.of("attemptId",a.id,"path",relative)),StandardOpenOption.CREATE_NEW);
                try{Files.move(upload,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(upload,target);}
                a.audioPath=relative;a.audioHash=audioHash;a.audioBytes=bytes.length;a.durationMs=duration;a.submissionHash=fingerprint;
                practice.tx.executeWithoutResult(t->practice.complete(a));committed=true;return receipt(a);
            }catch(ImportProblem ex){throw ex;}catch(Exception ex){throw new ImportProblem(503,"RECORDING_SAVE_FAILED","audio","Recording was not acknowledged as saved. Retain the browser recording and retry.");}
            finally{try{if(!committed&&target!=null){var stored=practice.attempt(id);if(stored.audioPath==null)Files.deleteIfExists(target);}if(stage!=null)LocalStorage.deleteTree(stage);}catch(IOException ex){throw new ImportProblem(503,"RECORDING_CLEANUP_FAILED","audio","Restart to reconcile the interrupted recording save.");}}
        }
    }
    JsonNode receipt(PracticeAttempt a){var out=JSON.createObjectNode().put("attemptId",a.id).put("saved",true);out.set("session",practice.view(a.sessionId));return out;}
    Path audio(String id) throws IOException {var a=practice.attempt(id);state(a.status.equals("COMPLETED")&&a.audioPath!=null);return safe(a.audioPath,true);}
    Map<String,String> imagePaths(PracticeAttempt a){Map<String,String> paths=new LinkedHashMap<>();collect(practice.exercise(a).get("content"),paths,a);Set<String> visible=new HashSet<>();collectIds(practice.visible(a),visible);paths.keySet().retainAll(visible);return paths;}
    void collect(JsonNode node,Map<String,String> paths,PracticeAttempt a){if(node.isObject()){if(node.has("image")){String p=node.get("image").stringValue();paths.put(practice.assetId(a,p),"images/"+practice.pack(practice.session(a.sessionId)).storageKey+"/"+p.substring(7));}for(var e:node.properties())collect(e.getValue(),paths,a);}else if(node.isArray())for(var e:node)collect(e,paths,a);}
    void collectIds(JsonNode n,Set<String> ids){if(n.isObject()){if(n.has("image"))ids.add(n.get("image").stringValue());for(var e:n.properties())collectIds(e.getValue(),ids);}else if(n.isArray())for(var e:n)collectIds(e,ids);}
    Path image(String id,String sessionId,String attemptId) throws IOException {
        ImportProblem.require((sessionId==null)!=(attemptId==null),"scope","Supply exactly one sessionId or review attemptId.");PracticeAttempt a;
        if(sessionId!=null){var s=practice.session(sessionId);state(!s.status.equals("COMPLETED"));a=practice.attempt(s.currentAttemptId);}else{a=practice.attempt(attemptId);state(!a.status.equals("IN_PROGRESS"));}
        String path=imagePaths(a).get(id);if(path==null)throw new ImportProblem(404,"ASSET_NOT_FOUND","assetId","Image is not accessible in this scope.");return safe(path,true);
    }
}
