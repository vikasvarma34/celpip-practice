package com.celpip.practice.imports;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static com.celpip.practice.imports.ContractValidator.*;

@Service
public class PracticeService {
    final SessionRepository sessions;
    final AttemptRepository attempts;
    final PackageRepository packages;
    final TransactionTemplate tx;
    PracticeService(SessionRepository sessions, AttemptRepository attempts, PackageRepository packages, PlatformTransactionManager manager) {
        this.sessions=sessions; this.attempts=attempts; this.packages=packages; tx=new TransactionTemplate(manager);
    }
    @PostConstruct void recover() {
        tx.executeWithoutResult(t -> {
            for(var s:sessions.findAll()) if(s.status.equals("IN_PROGRESS")) {
                s.status="INTERRUPTED"; s.reason="APPLICATION_RESTART"; sessions.save(s);
                for(var a:attempts.findBySessionIdOrderByCreatedAt(s.id)) if(!a.status.equals("COMPLETED")) { a.status="INTERRUPTED"; attempts.save(a); }
            }
        });
    }
    static JsonNode parse(String raw) {
        if(raw.length()>2097152) throw ImportProblem.limit();
        try { return JSON.readTree(raw); } catch(Exception ex) { throw new ImportProblem(400,"INVALID_JSON","request","Expected one JSON value with unique keys."); }
    }
    static void state(boolean ok) { if(!ok) throw new ImportProblem(409,"INVALID_STATE","request","Request conflicts with the current session or saved response."); }
    static long duration(JsonNode n) { ImportProblem.require(n!=null && n.isIntegralNumber() && n.canConvertToLong() && n.longValue()>=0,"durationMs","Expected a nonnegative integer duration in milliseconds."); return n.longValue(); }
    PracticeSession session(String id) { return sessions.findById(id).orElseThrow(()->new ImportProblem(404,"SESSION_NOT_FOUND","sessionId","Session not found.")); }
    PracticeAttempt attempt(String id) { return attempts.findById(id).orElseThrow(()->new ImportProblem(404,"ATTEMPT_NOT_FOUND","attemptId","Attempt not found.")); }
    ExercisePackage pack(PracticeSession s) { return packages.findById(s.packageId).orElseThrow(); }
    JsonNode exercise(PracticeAttempt a) {
        for(JsonNode e:parse(pack(session(a.sessionId)).canonicalJson).get("exercises")) if(e.get("exerciseId").stringValue().equals(a.exerciseId)) return e;
        throw new IllegalStateException("Missing immutable exercise");
    }
    String module(PracticeAttempt a) { return pack(session(a.sessionId)).module; }
    FoundationController.Timing timing(PracticeAttempt a) { return FoundationController.TIMINGS.stream().filter(t->t.module().equals(module(a))&&t.taskNumber()==a.taskNumber).findFirst().orElseThrow(); }
    void active(PracticeAttempt a) { var s=session(a.sessionId); state(s.status.equals("IN_PROGRESS") && a.status.equals("IN_PROGRESS") && s.currentAttemptId.equals(a.id)); }
    public synchronized JsonNode start(JsonNode request) {
        fields(request,"request","packageId","mode"); String packageId=text(request.get("packageId"),"packageId"), mode=text(request.get("mode"),"mode");
        ImportProblem.require(Set.of("PRACTICE","TIMED","EXAM").contains(mode),"mode","Unsupported mode.");
        var p=packages.findById(packageId).orElseThrow(()->new ImportProblem(404,"PACKAGE_NOT_FOUND","packageId","Package not found."));
        String id=tx.execute(t->{var s=new PracticeSession();s.id=UUID.randomUUID().toString();s.packageId=p.id;s.mode=mode;s.status="IN_PROGRESS";s.createdAt=Instant.now();sessions.saveAndFlush(s);newAttempt(s,parse(p.canonicalJson).get("exercises").get(0));sessions.save(s);return s.id;});
        return view(id);
    }
    void newAttempt(PracticeSession s,JsonNode e) {
        var a=new PracticeAttempt();a.id=UUID.randomUUID().toString();a.sessionId=s.id;a.exerciseId=e.get("exerciseId").stringValue();a.taskNumber=e.get("taskNumber").intValue();
        a.sequenceNumber=Math.toIntExact(attempts.countByExerciseId(a.exerciseId)+1);a.createdAt=Instant.now();a.status="IN_PROGRESS";
        a.stage=pack(s).module.equals("SPEAKING")?(a.taskNumber==5?"SELECTION":"PREPARATION"):"RESPONSE";
        attempts.saveAndFlush(a);s.currentAttemptId=a.id;
    }
    public synchronized JsonNode view(String id) {
        var s=session(id);ObjectNode out=JSON.createObjectNode();out.put("sessionId",s.id).put("packageId",s.packageId).put("mode",s.mode).put("status",s.status);
        if(s.reason!=null)out.put("interruptionReason",s.reason);
        var a=attempt(s.currentAttemptId);out.put("attemptId",a.id).put("taskNumber",a.taskNumber).put("stage",a.stage);
        out.set("timing",JSON.valueToTree(timing(a)));out.set("draft",a.draftJson==null?NullNode.getInstance():parse(a.draftJson));
        if(!s.status.equals("COMPLETED"))out.set("content",visible(a));
        return out;
    }
    JsonNode visible(PracticeAttempt a) {
        ObjectNode c=(ObjectNode)exercise(a).get("content").deepCopy();
        if(module(a).equals("SPEAKING") && a.taskNumber==5) {
            ArrayNode options=JSON.createArrayNode();
            for(JsonNode o:c.get("options")) {String id=o.get("id").stringValue();if(a.choice==null?!id.equals("C"):id.equals(a.choice)||id.equals("C"))options.add(o);}
            c.set("options",options);if(a.choice==null)c.remove("persuasionPrompt");
        }
        replaceImages(c,a);
        return c;
    }
    void replaceImages(JsonNode n,PracticeAttempt a) {
        if(n.isObject()) {
            ObjectNode object=(ObjectNode)n;
            if(object.has("image")) {String path=object.get("image").stringValue();object.put("image",assetId(a,path));}
            for(var value:object.properties())replaceImages(value.getValue(),a);
        } else if(n.isArray())for(JsonNode v:n)replaceImages(v,a);
    }
    String assetId(PracticeAttempt a,String path) { return UUID.nameUUIDFromBytes((pack(session(a.sessionId)).storageKey+":"+path).getBytes(StandardCharsets.UTF_8)).toString(); }
    public synchronized JsonNode advance(String id,JsonNode r) {
        fields(r,"request","attemptId","action","elapsedMs","choice","timeout");var s=session(id);var a=attempt(text(r.get("attemptId"),"attemptId"));state(a.sessionId.equals(id));active(a);
        String action=text(r.get("action"),"action");long elapsed=duration(r.get("elapsedMs"));ImportProblem.require(r.get("timeout").isBoolean(),"timeout","Expected boolean.");
        state(module(a).equals("SPEAKING"));
        if(action.equals("LOCK_SELECTION")) {
            state(a.stage.equals("SELECTION"));boolean timeout=r.get("timeout").booleanValue();
            if(timeout)state(!s.mode.equals("PRACTICE")&&elapsed>=timing(a).selectionSeconds()*1000L);
            String choice=r.get("choice").isNull()?null:text(r.get("choice"),"choice");ImportProblem.require(choice==null||Set.of("A","B").contains(choice),"choice","Choose A or B.");state(choice!=null||timeout);
            a.automaticChoice=choice==null;a.choice=choice==null?"A":choice;a.selectionMs=elapsed;a.stage="PREPARATION";
        } else {
            state(action.equals("START_RECORDING")&&a.stage.equals("PREPARATION"));state(r.get("choice").isNull()&&!r.get("timeout").booleanValue());
            if(!s.mode.equals("PRACTICE"))state(elapsed>=timing(a).preparationSeconds()*1000L);
            a.preparationMs=elapsed;a.stage="RECORDING";
        }
        attempts.saveAndFlush(a);return view(id);
    }
    JsonNode response(PracticeAttempt a,JsonNode r) {
        String mod=module(a);
        if(mod.equals("READING")) {
            fields(r,"response","answers");JsonNode answers=r.get("answers");ImportProblem.require(answers.isObject(),"answers","Expected question-to-index object.");
            Map<String,Integer> counts=new HashMap<>();for(JsonNode group:exercise(a).get("content").get("questionGroups"))for(JsonNode q:group.get("questions"))counts.put(q.get("id").stringValue(),q.get("options").size());
            for(String key:answers.propertyNames()) {ImportProblem.require(counts.containsKey(key),"answers","Unknown question ID.");JsonNode v=answers.get(key);if(!v.isNull()){int index=integer(v,"answers");ImportProblem.require(index>=0&&index<counts.get(key),"answers","Option index out of range.");}}
        } else {
            state(mod.equals("WRITING"));if(a.taskNumber==2)fields(r,"response","text","choice");else fields(r,"response","text");
            ImportProblem.require(r.get("text").isString(),"text","Expected text, including empty text.");
            if(a.taskNumber==2)ImportProblem.require(r.get("choice").isNull()||(r.get("choice").isString()&&Set.of("A","B").contains(r.get("choice").stringValue())),"choice","Choose A, B or null.");
        }
        return r;
    }
    public synchronized JsonNode draft(String id,JsonNode r) {var a=attempt(id);active(a);state(a.stage.equals("RESPONSE"));a.draftJson=JSON.writeValueAsString(response(a,r));attempts.saveAndFlush(a);return JSON.createObjectNode().put("attemptId",id).put("saved",true);}
    static int wordCount(String text) { return (int)java.util.regex.Pattern.compile("\\S+",java.util.regex.Pattern.UNICODE_CHARACTER_CLASS).matcher(text).results().count(); }
    JsonNode result(PracticeAttempt a,JsonNode r) {
        ObjectNode result=JSON.createObjectNode();
        if(module(a).equals("READING")) {
            int correct=0;ArrayNode items=JSON.createArrayNode();JsonNode key=exercise(a).get("answerKey");
            for(String qid:new TreeSet<>(key.propertyNames())){JsonNode keyItem=key.get(qid),chosen=r.get("answers").get(qid);boolean right=chosen!=null&&!chosen.isNull()&&chosen.intValue()==keyItem.get("correctOption").intValue();if(right)correct++;
                ObjectNode item=JSON.createObjectNode().put("questionId",qid).put("correct",right);item.set("selectedOption",chosen==null?NullNode.getInstance():chosen);item.set("correctOption",keyItem.get("correctOption"));item.set("explanation",keyItem.get("explanation"));items.add(item);}
            result.put("correct",correct).put("total",key.size()).set("questions",items);
        }else if(module(a).equals("WRITING")){int count=wordCount(r.get("text").stringValue());result.put("wordCount",count).put("outsideTarget",count<150||count>200).put("missingChoice",a.taskNumber==2&&r.get("choice").isNull());}
        return result;
    }
    static JsonNode sorted(JsonNode n) {
        if(n.isObject()){ObjectNode result=JSON.createObjectNode();for(String key:new TreeSet<>(n.propertyNames()))result.set(key,sorted(n.get(key)));return result;}
        if(n.isArray()){ArrayNode result=JSON.createArrayNode();for(JsonNode item:n)result.add(sorted(item));return result;}return n;
    }
    String hash(JsonNode r) {return UploadReader.sha(JSON.writeValueAsBytes(sorted(r)));}
    public synchronized JsonNode submitText(String id,JsonNode r) {
        fields(r,"request","response","durationMs");var a=attempt(id);long ms=duration(r.get("durationMs"));response(a,r.get("response"));String hash=hash(r);
        if(a.status.equals("COMPLETED")){state(hash.equals(a.submissionHash));return review(id);}
        active(a);state(a.stage.equals("RESPONSE"));a.responseJson=JSON.writeValueAsString(r.get("response"));a.resultJson=JSON.writeValueAsString(result(a,r.get("response")));a.durationMs=ms;a.submissionHash=hash;
        tx.executeWithoutResult(t->complete(a));return review(id);
    }
    void complete(PracticeAttempt a) {
        a.status="COMPLETED";a.stage="COMPLETED";a.completedAt=Instant.now();attempts.saveAndFlush(a);
        var s=session(a.sessionId);JsonNode list=parse(pack(s).canonicalJson).get("exercises");
        if(module(a).equals("SPEAKING")&&a.taskNumber==3)newAttempt(s,list.get(1));else s.status="COMPLETED";
        sessions.saveAndFlush(s);
    }
    public synchronized JsonNode interrupt(String id,JsonNode r) {
        fields(r,"request","reason");String reason=text(r.get("reason"),"reason");ImportProblem.require(Set.of("ABANDONED","BROWSER_RELOAD","DEVICE_ERROR","SUSPENDED").contains(reason),"reason","Unsupported interruption reason.");var s=session(id);
        if(s.status.equals("INTERRUPTED"))return view(id);state(s.status.equals("IN_PROGRESS"));
        tx.executeWithoutResult(t->{s.status="INTERRUPTED";s.reason=reason;sessions.save(s);var a=attempt(s.currentAttemptId);a.status="INTERRUPTED";attempts.save(a);});return view(id);
    }
    public synchronized JsonNode review(String id) {
        var a=attempt(id);state(!a.status.equals("IN_PROGRESS"));ObjectNode out=summary(a);out.set("content",visible(a));out.set("draft",a.draftJson==null?NullNode.getInstance():parse(a.draftJson));
        if(a.status.equals("COMPLETED")){out.set("response",a.responseJson==null?NullNode.getInstance():parse(a.responseJson));out.set("result",a.resultJson==null?JSON.createObjectNode():parse(a.resultJson));if(a.audioPath!=null)out.put("audioUrl","/api/attempts/"+id+"/audio");}return out;
    }
    ObjectNode summary(PracticeAttempt a) {
        var s=session(a.sessionId);return JSON.createObjectNode().put("attemptId",a.id).put("sessionId",a.sessionId).put("exerciseId",a.exerciseId).put("module",module(a)).put("taskNumber",a.taskNumber).put("attemptNumber",a.sequenceNumber).put("mode",s.mode).put("status",a.status).put("stage",a.stage).put("createdAt",a.createdAt.toString()).put("durationMs",a.durationMs).put("preparationMs",a.preparationMs).put("selectionMs",a.selectionMs).put("choice",a.choice).put("automaticChoice",a.automaticChoice);
    }
    public synchronized JsonNode history() {
        ArrayNode rows=JSON.createArrayNode();for(var s:sessions.findAllByOrderByCreatedAtDesc()) {ObjectNode row=JSON.createObjectNode().put("sessionId",s.id).put("packageId",s.packageId).put("mode",s.mode).put("status",s.status).put("createdAt",s.createdAt.toString());ArrayNode list=JSON.createArrayNode();for(var a:attempts.findBySessionIdOrderByCreatedAt(s.id)){ObjectNode entry=summary(a);if(a.status.equals("COMPLETED")&&module(a).equals("READING")){JsonNode r=parse(a.resultJson);entry.set("correct",r.get("correct"));entry.set("total",r.get("total"));}list.add(entry);}row.set("attempts",list);rows.add(row);}return rows;
    }
}
