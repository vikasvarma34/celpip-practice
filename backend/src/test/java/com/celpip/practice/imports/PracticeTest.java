package com.celpip.practice.imports;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.zip.ZipInputStream;
import java.util.stream.Stream;
import static com.celpip.practice.imports.TestSupport.*;
import static com.celpip.practice.imports.ContractValidator.JSON;
import static com.celpip.practice.imports.PracticeService.parse;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PracticeTest {
    @TempDir static Path temporary;
    ConfigurableApplicationContext app;PracticeService service;PracticeMedia media;EvaluationService eval;
    @BeforeAll void open()throws Exception{app=ImportIntegrationTest.open(temporary.toRealPath());service=app.getBean(PracticeService.class);media=app.getBean(PracticeMedia.class);eval=app.getBean(EvaluationService.class);for(String unit:UNITS)importFixture(app.getBean(ImportService.class),unit);}
    @AfterAll void close(){app.close();}
    JsonNode start(String unit,String mode){return service.start(JSON.createObjectNode().put("packageId","SYNTHETIC-"+unit.toUpperCase()).put("mode",mode));}
    JsonNode advance(JsonNode session,String action,String choice,boolean timeout,long elapsed){return service.advance(session.get("sessionId").stringValue(),JSON.createObjectNode().put("attemptId",session.get("attemptId").stringValue()).put("action",action).put("choice",choice).put("timeout",timeout).put("elapsedMs",elapsed));}
    String id(JsonNode s){return s.get("attemptId").stringValue();}
    JsonNode submit(String id,JsonNode response,long ms){return service.submitText(id,JSON.createObjectNode().set("response",response).put("durationMs",ms));}
    @ParameterizedTest @ValueSource(ints={1,2,3,4}) void readingGradesAndFreezesOriginal(int task){
        var s=start("READING-"+task,"TIMED");assertFalse(s.toString().contains("correctOption"));String id=id(s);
        var response=JSON.createObjectNode().set("answers",JSON.createObjectNode().put("Q01",task==3?4:0));service.draft(id,response);
        var result=submit(id,response,1000);assertEquals(1,result.at("/result/correct").intValue());assertTrue(result.at("/result/total").intValue()>1);
        assertEquals(result,submit(id,response,1000));assertThrows(ImportProblem.class,()->submit(id,response,1001));assertThrows(ImportProblem.class,()->service.draft(id,response));
        assertTrue(eval.context(id,false).text().contains("Raw score"));assertFalse(service.history().toString().contains("explanation"));
    }
    @ParameterizedTest @ValueSource(ints={1,2}) void writingExactTextAndEmptySubmission(int task){
        for(String text:List.of("", "  Exact\r\nresponse café\u00a0two-words ","word ".repeat(220))){var s=start("WRITING-"+task,"EXAM");ObjectNode r=JSON.createObjectNode().put("text",text);if(task==2)r.putNull("choice");service.draft(id(s),r);var result=submit(id(s),r,0);assertEquals(text,result.at("/response/text").stringValue());assertEquals(PracticeService.wordCount(text),result.at("/result/wordCount").intValue());assertFalse(result.at("/result").has("score"));}
    }
    static Stream<Arguments> speaking(){return Stream.of("PRACTICE","TIMED","EXAM").flatMap(mode->Stream.of(1,2,3,5,6,7,8).map(t->Arguments.of(t,mode)));}
    @ParameterizedTest @MethodSource("speaking") void speakingStagesAudioAndPairs(int task,String mode)throws Exception{
        var s=start("SPEAKING-"+(task==3?"3-4":task),mode);String session=s.get("sessionId").stringValue();
        assertThrows(ImportProblem.class,()->media.submit(id(s),"{\"durationMs\":90000}",audio()));
        JsonNode current=s;
        if(task==5){assertFalse(s.get("content").has("persuasionPrompt"));assertEquals(2,s.at("/content/options").size());current=advance(current,"LOCK_SELECTION","B",false,4000);assertEquals("B",current.at("/content/options/0/id").stringValue());assertEquals("C",current.at("/content/options/1/id").stringValue());}
        current=advance(current,"START_RECORDING",null,false,60000);String first=id(current);media.submit(first,"{\"durationMs\":90000}",audio());assertArrayEquals(webm(),Files.readAllBytes(media.audio(first)));assertTrue(Files.size(media.audio(first))>0);
        media.submit(first,"{\"durationMs\":90000}",audio());assertThrows(ImportProblem.class,()->media.submit(first,"{\"durationMs\":90001}",audio()));
        if(task==3){var next=service.view(session);assertEquals(4,next.get("taskNumber").intValue());assertNotEquals(first,id(next));assertEquals(s.at("/content/image"),next.at("/content/image"));assertThrows(ImportProblem.class,()->eval.context(first,true));
            next=advance(next,"START_RECORDING",null,false,30000);media.submit(id(next),"{\"durationMs\":60000}",audio());assertEquals(3,eval.context(first,true).assets().size());assertTrue(Files.exists(media.audio(first)));}
        assertEquals("COMPLETED",service.view(session).get("status").stringValue());assertFalse(service.history().toString().contains("persuasionPrompt"));
    }
    @Test void taskFiveFallbackAndScopedImageDenial()throws Exception{
        var s=start("SPEAKING-5","TIMED");var a=service.attempt(id(s));String c=service.assetId(a,"images/asset-2.png");String rejected=service.assetId(a,"images/asset-1.png");String sid=s.get("sessionId").stringValue();
        assertThrows(ImportProblem.class,()->media.image(c,sid,null));assertThrows(ImportProblem.class,()->advance(s,"LOCK_SELECTION",null,true,59999));
        var locked=advance(s,"LOCK_SELECTION",null,true,60000);assertTrue(service.attempt(id(s)).automaticChoice);assertEquals("A",service.attempt(id(s)).choice);assertTrue(Files.exists(media.image(c,sid,null)));assertThrows(ImportProblem.class,()->media.image(rejected,sid,null));assertThrows(ImportProblem.class,()->advance(locked,"LOCK_SELECTION","B",false,60000));
    }
    @Test void invalidReadingAndAssociations(){var s=start("READING-1","PRACTICE");assertThrows(ImportProblem.class,()->service.draft(id(s),parse("{\"answers\":{\"Q99\":0}}")));assertThrows(ImportProblem.class,()->service.draft(id(s),parse("{\"answers\":{\"Q01\":\"0\"}}")));var speaking=start("SPEAKING-1","TIMED");assertThrows(ImportProblem.class,()->advance(speaking,"START_RECORDING",null,false,1));assertThrows(ImportProblem.class,()->service.review(id(s)));}
    @Test void interruptPreservesDraftAndRejectsSubmission(){var s=start("WRITING-1","PRACTICE");var r=parse("{\"text\":\"unsaved final, saved draft\"}");service.draft(id(s),r);service.interrupt(s.get("sessionId").stringValue(),parse("{\"reason\":\"ABANDONED\"}"));assertEquals(r,service.review(id(s)).get("draft"));assertThrows(ImportProblem.class,()->submit(id(s),r,100));}
    @Test void exportsAndFinderReferences()throws Exception{var s=start("SPEAKING-5","PRACTICE");var selected=advance(s,"LOCK_SELECTION","A",false,3);var recording=advance(selected,"START_RECORDING",null,false,4);media.submit(id(recording),"{\"durationMs\":5}",audio());
        var r=JSON.createObjectNode().put("attemptId",id(s)).put("paired",false);var exported=eval.export(r);Path file=eval.exportPath(exported.get("exportId").stringValue());List<String> names=new ArrayList<>();String context="";try(var zip=new ZipInputStream(Files.newInputStream(file))){java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null){names.add(e.getName());byte[] bytes=zip.readAllBytes();if(e.getName().equals("evaluation.txt"))context=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}}
        assertEquals(4,names.size());assertFalse(context.contains("Synthetic choice B"));assertFalse(context.contains(temporary.toString()));
        assertEquals(file,eval.revealPath(JSON.createObjectNode().put("kind","EXPORT").put("id",exported.get("exportId").stringValue()).putNull("attemptId")));
        assertThrows(Exception.class,()->eval.exportPath("../../etc/passwd"));assertThrows(Exception.class,()->media.safe("../outside",false));
        Path link=temporary.resolve("exports/link.zip");Files.createSymbolicLink(link,file);assertThrows(Exception.class,()->media.safe("exports/link.zip",true));Files.delete(link);
    }
    @Test void rejectsAudioCorruptionAndFailedDiskSave()throws Exception{var s=start("SPEAKING-3-4","PRACTICE");advance(s,"START_RECORDING",null,false,0);String id=id(s);for(byte[] b:List.of(new byte[0],new byte[]{1,2,3},Arrays.copyOf(webm(),webm().length-2)))assertThrows(ImportProblem.class,()->media.submit(id,"{\"durationMs\":3}",new MockMultipartFile("audio","x.webm","audio/webm",b)));
        Path directory=temporary.resolve("recordings"),backup=temporary.resolve("recordings-backup");Files.move(directory,backup);Files.writeString(directory,"obstruction");try{assertThrows(ImportProblem.class,()->media.submit(id,"{\"durationMs\":3}",audio()));assertEquals(3,service.view(s.get("sessionId").stringValue()).get("taskNumber").intValue());assertNull(service.attempt(id).audioPath);}finally{Files.delete(directory);Files.move(backup,directory);}media.submit(id,"{\"durationMs\":3}",audio());assertEquals(4,service.view(s.get("sessionId").stringValue()).get("taskNumber").intValue());}
    @Test void transactionFailureAndAudioLimitDoNotAdvance() throws Exception {
        var s=start("SPEAKING-3-4","PRACTICE");advance(s,"START_RECORDING",null,false,0);String id=id(s);
        var broken=org.mockito.Mockito.mock(AttemptRepository.class, org.mockito.AdditionalAnswers.delegatesTo(service.attempts));
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic transaction failure")).when(broken).saveAndFlush(org.mockito.ArgumentMatchers.any(PracticeAttempt.class));
        var failing=new PracticeService(service.sessions,broken,service.packages,app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var failingMedia=new PracticeMedia(failing,media.storage,app.getBean(ImportService.class),52428800);
        assertThrows(ImportProblem.class,()->failingMedia.submit(id,"{\"durationMs\":5}",audio()));
        assertNull(service.attempt(id).audioPath);assertEquals(3,service.view(s.get("sessionId").stringValue()).get("taskNumber").intValue());
        var limited=new PracticeMedia(service,media.storage,app.getBean(ImportService.class),10);
        assertEquals(413,assertThrows(ImportProblem.class,()->limited.submit(id,"{\"durationMs\":5}",audio())).status);
    }
    @Test void httpLifecyclePlaybackAndContext() throws Exception {
        var client=java.net.http.HttpClient.newHttpClient();String base="http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port");
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/sessions")).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"packageId\":\"SYNTHETIC-WRITING-1\",\"mode\":\"PRACTICE\"}")).build();
        var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());String id=parse(response.body()).get("attemptId").stringValue();
        var submit=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/attempts/"+id+"/submit")).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"response\":{\"text\":\"exact response\"},\"durationMs\":100}")).build();
        assertEquals(200,client.send(submit,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        var review=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/attempts/"+id+"/evaluation-context")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,review.statusCode());assertTrue(review.body().contains("exact response"));assertEquals("no-store",review.headers().firstValue("Cache-Control").orElseThrow());
    }
    @Test void taskFourFailurePreservesFirstAudioAndRangePlaybackWorks() throws Exception {
        var s=start("SPEAKING-3-4","PRACTICE");advance(s,"START_RECORDING",null,false,0);String first=id(s);media.submit(first,"{\"durationMs\":10}",audio());
        var next=service.view(s.get("sessionId").stringValue());advance(next,"START_RECORDING",null,false,0);
        assertThrows(ImportProblem.class,()->media.submit(id(next),"{\"durationMs\":10}",new MockMultipartFile("audio","bad.webm","audio/webm",new byte[]{1})));
        assertEquals("COMPLETED",service.attempt(first).status);assertEquals("IN_PROGRESS",service.session(s.get("sessionId").stringValue()).status);assertArrayEquals(webm(),Files.readAllBytes(media.audio(first)));
        var client=java.net.http.HttpClient.newHttpClient();String base="http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port");
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/attempts/"+first+"/audio")).header("Range","bytes=0-3").build();
        var result=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofByteArray());assertEquals(206,result.statusCode());assertArrayEquals(Arrays.copyOf(webm(),4),result.body());
    }
    static MockMultipartFile audio(){return new MockMultipartFile("audio","recording.webm","audio/webm;codecs=opus",webm());}
    static byte[] webm(){try{byte[] head="OpusHead".getBytes(java.nio.charset.StandardCharsets.US_ASCII);ByteArrayOutputStream privateData=new ByteArrayOutputStream();privateData.write(head);privateData.write(new byte[]{1,1,0,0,(byte)0x80,(byte)0xbb,0,0,0,0,0});
        byte[] track=element("ae",concat(element("d7",new byte[]{1}),element("83",new byte[]{2}),element("86","A_OPUS".getBytes()),element("63a2",privateData.toByteArray())));
        return concat(element("1a45dfa3",element("4282","webm".getBytes())),element("18538067",concat(element("1654ae6b",track),element("1f43b675",concat(element("e7",new byte[]{0}),element("a3",new byte[]{(byte)0x81,0,0,(byte)0x80,(byte)0xf8,(byte)0xff,(byte)0xfe}))))));
    }catch(Exception e){throw new RuntimeException(e);}}
    static byte[] element(String id,byte[] payload)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(HexFormat.of().parseHex(id));if(payload.length<127)out.write(128|payload.length);else{out.write(0x40|(payload.length>>8));out.write(payload.length&255);}out.write(payload);return out.toByteArray();}
    static byte[] concat(byte[]...arrays)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] a:arrays)out.write(a);return out.toByteArray();}
}
