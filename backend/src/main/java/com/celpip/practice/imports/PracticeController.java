package com.celpip.practice.imports;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.core.io.*;
import tools.jackson.databind.JsonNode;
import java.nio.file.*;
import static com.celpip.practice.imports.PracticeService.parse;

@RestController
@RequestMapping("/api")
public class PracticeController {
    final PracticeService practice;final PracticeMedia media;final EvaluationService evaluation;final AudioConversion conversion;
    PracticeController(PracticeService practice,PracticeMedia media,EvaluationService evaluation,AudioConversion conversion){this.practice=practice;this.media=media;this.evaluation=evaluation;this.conversion=conversion;}
    @PostMapping("/sessions") public JsonNode start(@RequestBody String body){return practice.start(parse(body));}
    @GetMapping("/sessions/{id}") public JsonNode session(@PathVariable String id){return practice.view(id);}
    @PostMapping("/sessions/{id}/advance") public JsonNode advance(@PathVariable String id,@RequestBody String body){return practice.advance(id,parse(body));}
    @PostMapping("/sessions/{id}/interrupt") public JsonNode interrupt(@PathVariable String id,@RequestBody String body){return practice.interrupt(id,parse(body));}
    @PatchMapping("/attempts/{id}/draft") public JsonNode draft(@PathVariable String id,@RequestBody String body){return practice.draft(id,parse(body));}
    @PostMapping(value="/attempts/{id}/submit",consumes="application/json") public JsonNode submit(@PathVariable String id,@RequestBody String body){return practice.submitText(id,parse(body));}
    @PostMapping(value="/attempts/{id}/submit",consumes="multipart/form-data") public JsonNode audio(@PathVariable String id,@RequestParam String metadata,@RequestParam MultipartFile audio,org.springframework.web.multipart.MultipartHttpServletRequest request){
        ImportProblem.require(request.getMultiFileMap().keySet().equals(java.util.Set.of("audio")) && request.getFiles("audio").size()==1 && request.getParameterMap().keySet().equals(java.util.Set.of("metadata")) && request.getParameterValues("metadata").length==1,"request","Supply exactly one audio file and one metadata field.");
        JsonNode result=media.submit(id,metadata,audio);
        try{conversion.request(id,false);}catch(Exception ignored){/* Original save acknowledgement is independent of derived conversion. */}
        return result;
    }
    @GetMapping("/attempts/{id}/review") public JsonNode review(@PathVariable String id){return practice.review(id);}
    @GetMapping("/history") public JsonNode history(){return practice.history();}
    @GetMapping("/attempts/{id}/evaluation-context") public EvaluationService.Context context(@PathVariable String id,@RequestParam(defaultValue="false")boolean paired){return evaluation.context(id,paired);}
    @PostMapping("/exports") public JsonNode export(@RequestBody String body)throws Exception{return evaluation.export(parse(body));}
    @GetMapping("/exports/{id}") public ResponseEntity<Resource> download(@PathVariable String id)throws Exception{return resource(evaluation.exportPath(id),"application/zip");}
    @PostMapping("/reveal") public JsonNode reveal(@RequestBody String body)throws Exception{return evaluation.reveal(parse(body));}
    @GetMapping("/attempts/{id}/audio") public ResponseEntity<Resource> audio(@PathVariable String id,@RequestParam(defaultValue="webm")String format)throws Exception{ImportProblem.require(java.util.Set.of("webm","m4a").contains(format),"format","Choose webm or m4a.");return resource(format.equals("m4a")?conversion.m4a(id):media.audio(id),format.equals("m4a")?"audio/mp4":"audio/webm");}
    @GetMapping("/attempts/{id}/audio-conversion") public AudioConversion.Status conversion(@PathVariable String id)throws Exception{return conversion.request(id,false);}
    @PostMapping("/attempts/{id}/audio-conversion") public AudioConversion.Status retryConversion(@PathVariable String id)throws Exception{return conversion.request(id,true);}
    @GetMapping("/assets/{id}") public ResponseEntity<Resource> image(@PathVariable String id,@RequestParam(required=false)String sessionId,@RequestParam(required=false)String attemptId)throws Exception{Path path=media.image(id,sessionId,attemptId);return resource(path,path.toString().toLowerCase().endsWith(".png")?"image/png":"image/jpeg");}
    static ResponseEntity<Resource> resource(Path file,String type)throws Exception{return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).contentLength(Files.size(file)).body(new FileSystemResource(file));}
}
