package com.celpip.practice.imports;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import static com.celpip.practice.imports.ContractValidator.*;
import static com.celpip.practice.imports.PracticeService.*;

@Service
public class EvaluationService {
    final PracticeService practice; final PracticeMedia media; final AudioConversion conversion;
    EvaluationService(PracticeService practice,PracticeMedia media,AudioConversion conversion){this.practice=practice;this.media=media;this.conversion=conversion;}
    public record Asset(String id,String url,String kind) {}
    public record Context(String text,List<Asset> assets) {}
    List<PracticeAttempt> included(String id,boolean pair) {
        var a=practice.attempt(id);state(a.status.equals("COMPLETED"));
        if(pair){state(practice.module(a).equals("SPEAKING")&&(a.taskNumber==3||a.taskNumber==4));var list=practice.attempts.findBySessionIdOrderByCreatedAt(a.sessionId);state(list.size()==2&&list.stream().allMatch(x->x.status.equals("COMPLETED")));return list;}
        return List.of(a);
    }
    public Context context(String id,boolean pair){
        synchronized(practice){StringBuilder text=new StringBuilder();Map<String,Asset> assets=new LinkedHashMap<>();
            if(pair)text.append("Linked Speaking Tasks 3 and 4. Attach the shared scene image once and both M4A recordings.\n\n");
            for(var a:included(id,pair)){
                var s=practice.session(a.sessionId);String module=practice.module(a);JsonNode content=practice.visible(a);
                if(module.equals("READING")||module.equals("WRITING")){
                    text.append(module.equals("READING")?"Reading part ":"Writing task ").append(a.taskNumber).append("\nPractice mode: ").append(s.mode).append("\nTime taken: ").append(elapsed(a.durationMs)).append("\n\n");
                    if(module.equals("READING")){
                        JsonNode result=parse(a.resultJson);text.append("Raw score: ").append(result.get("correct")).append('/').append(result.get("total")).append("\n\nPassage:\n");
                        for(JsonNode block:content.get("passage")){
                            String type=block.get("type").stringValue();
                            if(type.equals("IMAGE"))text.append("Attach the passage diagram image accompanying this evaluation.\n");
                            else if(type.equals("TABLE")){for(JsonNode row:block.get("headers"))text.append(row.stringValue()).append(" | ");text.append('\n');for(JsonNode row:block.get("rows")){for(JsonNode cell:row)text.append(cell.stringValue()).append(" | ");text.append('\n');}}
                            else{if(block.has("label"))text.append("Paragraph ").append(block.get("label").stringValue()).append(":\n");text.append(block.get("text").stringValue()).append("\n\n");}
                        }
                        for(JsonNode group:content.get("questionGroups")){
                            boolean shown=false;
                            for(JsonNode q:group.get("questions"))for(JsonNode item:result.get("questions"))if(q.get("id").stringValue().equals(item.get("questionId").stringValue())&&!item.get("correct").booleanValue()){
                                if(group.has("text")&&!shown){text.append("\nQuestion context:\n").append(group.get("text").stringValue().replaceAll("\\{\\{Q0?([0-9]+)\\}\\}", "[Blank $1]")).append('\n');shown=true;}
                                text.append("\nQuestion ").append(Integer.parseInt(q.get("id").stringValue().substring(1))).append(": ").append(q.has("prompt")?q.get("prompt").stringValue():"Choose the word or phrase for this blank.").append('\n');
                                for(JsonNode option:q.get("options"))text.append("• ").append(option.stringValue()).append('\n');
                                JsonNode selected=item.get("selectedOption");text.append("My selected answer: ").append(selected==null||selected.isNull()?"Unanswered":q.get("options").get(selected.intValue()).stringValue()).append("\nCorrect answer: ").append(q.get("options").get(item.get("correctOption").intValue()).stringValue()).append("\nExplanation: ").append(item.get("explanation").stringValue()).append('\n');
                            }
                        }
                    }else{
                        for(String field:List.of("title","scenario","instructions"))if(content.has(field))text.append(content.get(field).stringValue()).append("\n\n");
                        if(content.has("requirements"))for(JsonNode requirement:content.get("requirements"))text.append("• ").append(requirement.stringValue()).append('\n');
                        JsonNode response=parse(a.responseJson);
                        if(content.has("options")){for(JsonNode option:content.get("options"))text.append("Option ").append(option.get("id").stringValue()).append(": ").append(option.get("text").stringValue()).append('\n');text.append("Selected survey option: ");JsonNode choice=response.get("choice");boolean found=false;for(JsonNode option:content.get("options"))if(choice!=null&&!choice.isNull()&&choice.stringValue().equals(option.get("id").stringValue())){text.append(option.get("text").stringValue());found=true;}if(!found)text.append("Not selected");text.append('\n');}
                        text.append("Word count: ").append(parse(a.resultJson).get("wordCount")).append("\n\nMy exact written response:\n").append(response.get("text").stringValue());
                    }
                }else if(Set.of(1,2,3,4,6,7,8).contains(a.taskNumber)){
                    text.append("Speaking task ").append(a.taskNumber).append(" — ").append(switch(a.taskNumber){case 1->"Giving Advice";case 2->"Talking about a Personal Experience";case 3->"Describing a Scene";case 4->"Making Predictions";case 6->"Dealing with a Difficult Situation";case 7->"Expressing Opinions";case 8->"Describing an Unusual Situation";default->"Speaking";}).append("\nPractice mode: ").append(s.mode)
                        .append("\nPreparation: ").append(elapsed(a.preparationMs))
                        .append("\nSpeaking: ").append(elapsed(a.durationMs)).append("\n\n")
                        .append(content.get("prompt").stringValue()).append('\n');
                    if(!s.mode.equals("PRACTICE")&&(a.preparationMs<practice.timing(a).preparationSeconds()*1000L||a.durationMs<practice.timing(a).answerSeconds()*1000L))text.append("Testing: ended early; shortened examination conditions.\n");
                    if(content.has("alternatives"))for(JsonNode alternative:content.get("alternatives"))text.append(alternative.stringValue()).append('\n');
                    if(content.has("image")&&!pair)text.append("Attach this task's original image and M4A recording for evaluation.\n");
                }else{
                    text.append(module).append(" task ").append(a.taskNumber).append("\nMode: ").append(s.mode).append("; attempt: ").append(a.sequenceNumber).append("\nSelection/preparation/response (ms): ").append(a.selectionMs).append(" / ").append(a.preparationMs).append(" / ").append(a.durationMs).append("\n");
                    render(content,text);if(a.responseJson!=null){text.append("Original response:\n");render(parse(a.responseJson),text);}if(a.resultJson!=null)render(parse(a.resultJson),text);
                    if(a.choice!=null)text.append("Chosen option: ").append(a.choice).append("; automatic fallback: ").append(a.automaticChoice).append('\n');
                }
                for(String asset:media.imagePaths(a).keySet())assets.putIfAbsent(asset,new Asset(asset,"/api/assets/"+asset+"?attemptId="+a.id,"IMAGE"));
                if(a.audioPath!=null)assets.put(a.id,new Asset(a.id,"/api/attempts/"+a.id+"/audio","AUDIO"));text.append('\n');
            }return new Context(text.toString(),List.copyOf(assets.values()));
        }
    }
    static String elapsed(long ms){long seconds=Math.round(ms/1000.0);return String.format("%02d:%02d",seconds/60,seconds%60);}
    static void render(JsonNode n,StringBuilder out){if(n==null||n.isNull())return;if(n.isObject()){for(var e:n.properties()){out.append(e.getKey()).append(": ");render(e.getValue(),out);}}else if(n.isArray()){for(var e:n)render(e,out);}else out.append(n.isString()?n.stringValue():n.toString()).append('\n');}
    public JsonNode export(JsonNode r) throws IOException {
        fields(r,"request","attemptId","paired");String id=text(r.get("attemptId"),"attemptId");ImportProblem.require(r.get("paired").isBoolean(),"paired","Expected boolean.");boolean pair=r.get("paired").booleanValue();
        synchronized(practice){Context context=context(id,pair);String exportId=UUID.randomUUID().toString();Path stage=Files.createTempFile(media.storage.root.resolve("staging"),"export-",".zip");Path target=media.safe("exports/"+exportId+".zip",false);
            try{try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(stage))){zip.putNextEntry(new ZipEntry("evaluation.txt"));zip.write(context.text().getBytes(StandardCharsets.UTF_8));zip.closeEntry();Set<String> added=new HashSet<>();
                for(var a:included(id,pair)){if(a.audioPath!=null)add(zip,media.audio(a.id),"recordings/"+Path.of(a.audioPath).getFileName(),added);for(var image:media.imagePaths(a).entrySet()){Path path=media.safe(image.getValue(),true);String extension=path.getFileName().toString().substring(path.getFileName().toString().lastIndexOf('.'));add(zip,path,"images/"+image.getKey()+extension,added);}}
            }Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(stage);}
            return JSON.createObjectNode().put("exportId",exportId).put("downloadUrl","/api/exports/"+exportId);
        }
    }
    static void add(ZipOutputStream zip,Path file,String name,Set<String> added)throws IOException{if(added.add(name)){zip.putNextEntry(new ZipEntry(name));Files.copy(file,zip);zip.closeEntry();}}
    Path exportPath(String id)throws IOException {try{UUID.fromString(id);}catch(Exception ex){throw new ImportProblem(404,"EXPORT_NOT_FOUND","exportId","Export not found.");}return media.safe("exports/"+id+".zip",true);}
    Path revealPath(JsonNode r)throws IOException {
        fields(r,"request","kind","id","attemptId");String kind=text(r.get("kind"),"kind"),id=text(r.get("id"),"id");
        return switch(kind){case "AUDIO"->conversion.preferred(id);case "EXPORT"->exportPath(id);case "IMAGE"->media.image(id,null,text(r.get("attemptId"),"attemptId"));default->throw new ImportProblem(422,"INVALID_REFERENCE","kind","Choose AUDIO, IMAGE or EXPORT.");};
    }
    public JsonNode reveal(JsonNode r)throws Exception {
        Path file=revealPath(r);if(!System.getProperty("os.name").startsWith("Mac"))throw new ImportProblem(503,"FINDER_UNAVAILABLE","request","Finder is only available on macOS.");
        Process process=new ProcessBuilder("/usr/bin/open","-R",file.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroy();throw new IOException("Finder timed out.");}if(process.exitValue()!=0)throw new IOException("Finder failed.");return JSON.createObjectNode().put("revealed",true);
    }
}
