package com.celpip.practice.imports;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.celpip.practice.imports.ContractValidator.*;
@Service
public class NativeClipboard {
 final EvaluationService evaluation;final PracticeMedia media;final AudioConversion conversion;
 NativeClipboard(EvaluationService evaluation,PracticeMedia media,AudioConversion conversion){this.evaluation=evaluation;this.media=media;this.conversion=conversion;}
 public JsonNode copy(JsonNode r)throws Exception{
  if(r.has("assetId"))fields(r,"request","attemptId","kind","paired","path","assetId");else fields(r,"request","attemptId","kind","paired","path");String id=text(r.get("attemptId"),"attemptId"),kind=text(r.get("kind"),"kind");
  ImportProblem.require(r.get("paired").isBoolean()&&r.get("path").isBoolean(),"request","Expected boolean flags.");
  ImportProblem.require(Set.of("AUDIO","IMAGE","ALL","ALL_FILES").contains(kind),"kind","Choose AUDIO, IMAGE, ALL or ALL_FILES.");
  Set<Path> files=new LinkedHashSet<>();for(var attempt:evaluation.included(id,r.get("paired").booleanValue())){
   if(!kind.equals("IMAGE"))files.add(conversion.m4a(attempt.id));
   if(r.has("assetId")){
    ImportProblem.require(kind.equals("IMAGE")&&!r.get("paired").booleanValue(),"assetId","Asset selection requires individual image copying.");
    String asset=text(r.get("assetId"),"assetId");ImportProblem.require(media.imagePaths(attempt).containsKey(asset),"assetId","Image is not part of this completed attempt.");files.add(media.image(asset,null,attempt.id));
   }else if(!kind.equals("AUDIO"))for(String relative:media.imagePaths(attempt).values())files.add(media.safe(relative,true));
  }
  String metadata=null;
  if((kind.equals("ALL")||kind.equals("ALL_FILES"))&&!r.get("path").booleanValue()){
   metadata=evaluation.context(id,r.get("paired").booleanValue()).text();
   String hash=UploadReader.sha(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   Path note=media.safe("exports/evaluation-for-chatgpt-"+hash+".txt",false);
   try{Files.writeString(note,metadata,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException exists){
    if(!Files.readString(media.safe(media.storage.root.relativize(note).toString(),true)).equals(metadata))throw new java.io.IOException("Evaluation attachment integrity check failed.");
   }
   files.add(media.safe(media.storage.root.relativize(note).toString(),true));
  }
  ImportProblem.require(!files.isEmpty(),"request","No completed attachments available.");
  if(!System.getProperty("os.name").startsWith("Mac"))throw new java.io.IOException("Native clipboard requires macOS.");
  String script="ObjC.import('AppKit');function run(args){let mode=args.shift(),text=mode==='mixed'?args.shift():null;let board=$.NSPasteboard.generalPasteboard;board.clearContents;let ok;if(mode==='path'){ok=board.setStringForType($(args.join('\\n')),$.NSPasteboardTypeString)}else{let items=args.map(p=>$.NSURL.fileURLWithPath($(p)));if(text!==null){let note=$.NSPasteboardItem.alloc.init;if(!note.setStringForType($(text),$.NSPasteboardTypeString))throw Error('Text write failed');items.push(note)}ok=board.writeObjects($(items))}if(!ok)throw Error('Clipboard write failed');return 'copied'}";
  boolean mixed=kind.equals("ALL")&&!r.get("path").booleanValue();
  List<String> command=new ArrayList<>(List.of("/usr/bin/osascript","-l","JavaScript","-e",script,"--",r.get("path").booleanValue()?"path":mixed?"mixed":"files"));if(mixed)command.add(metadata);for(Path file:files)command.add(file.toString());
  Process process=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
  if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();throw new java.io.IOException("Clipboard timed out.");}if(process.exitValue()!=0)throw new java.io.IOException("Native clipboard failed. Use Show in Finder.");
  return JSON.createObjectNode().put("copied",true);
 }
}
