package com.celpip.practice.imports;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Derived AAC sidecars never change the original audio or attempt record. */
@Service
public class AudioConversion {
    final PracticeMedia media;
    final String configured;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Map<String,Status> statuses=new HashMap<>();
    volatile Process process;
    public record Status(String status,String message,String audioUrl) {}
    AudioConversion(PracticeMedia media,@Value("${celpip.ffmpeg:}")String configured){this.media=media;this.configured=configured;}
    Path target(String id)throws IOException {
        Path source=media.audio(id);String name=source.getFileName().toString();
        Path target=source.resolveSibling(name.substring(0,name.lastIndexOf('.'))+".m4a");
        return media.safe(media.storage.root.relativize(target).toString(),false);
    }
    Path available(String id)throws IOException {Path p=target(id);return Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&Files.size(p)>0?p:null;}
    Path ffmpeg(){
        List<String> paths=new ArrayList<>();if(!configured.isBlank())paths.add(configured);
        for(String dir:System.getenv().getOrDefault("PATH","").split(java.io.File.pathSeparator))if(!dir.isBlank())paths.add(Path.of(dir,"ffmpeg").toString());
        paths.add("/opt/homebrew/bin/ffmpeg");paths.add("/usr/local/bin/ffmpeg");
        return paths.stream().map(Path::of).filter(Files::isExecutable).filter(Files::isRegularFile).findFirst().orElse(null);
    }
    public synchronized Status request(String id,boolean retry)throws IOException {
        if(available(id)!=null)return new Status("READY","M4A/AAC is ready for ChatGPT evaluation.","/api/attempts/"+id+"/audio?format=m4a");
        Status current=statuses.get(id);
        if(current!=null&&(!retry||Set.of("QUEUED","CONVERTING").contains(current.status())))return current;
        Path executable=ffmpeg();
        if(executable==null){Status unavailable=new Status("UNAVAILABLE","FFmpeg is unavailable. Install with: brew install ffmpeg, then retry conversion.",null);statuses.put(id,unavailable);return unavailable;}
        statuses.put(id,new Status("QUEUED","M4A conversion queued; original WebM is saved.",null));
        worker.submit(()->convert(id,executable));return statuses.get(id);
    }
    void convert(String id,Path executable){
        Path temporary=null;
        try{
            synchronized(this){statuses.put(id,new Status("CONVERTING","Converting the saved WebM to M4A/AAC…",null));}
            Path source=media.audio(id),destination=target(id);
            temporary=Files.createTempFile(media.storage.root.resolve("staging"),"aac-",".m4a");
            process=new ProcessBuilder(executable.toString(),"-nostdin","-hide_banner","-loglevel","error","-y","-i",source.toString(),"-map","0:a:0","-vn","-c:a","aac","-ac","1","-b:a","64k","-map_metadata","-1","-movflags","+faststart",temporary.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!process.waitFor(120,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor();throw new Exception("timeout");}
            if(process.exitValue()!=0||Files.size(temporary)==0)throw new Exception("conversion failed");
            Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);
            synchronized(this){statuses.put(id,new Status("READY","M4A/AAC is ready for ChatGPT evaluation.","/api/attempts/"+id+"/audio?format=m4a"));}
        }catch(Exception ex){synchronized(this){statuses.put(id,new Status("FAILED","M4A conversion failed. The original WebM and completed attempt are preserved. Retry conversion.",null));}}
        finally{Process running=process;if(running!=null&&running.isAlive())running.destroyForcibly();process=null;if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(Exception ignored){/* A derived temporary file must never affect original saving. */}}
    }
    Path preferred(String id)throws IOException {Path converted=available(id);return converted!=null?converted:media.audio(id);}
    Path m4a(String id)throws IOException {Path p=available(id);if(p==null)throw new ImportProblem(404,"M4A_NOT_READY","audio","M4A is not available. Use the original WebM or retry conversion.");return p;}
    @PreDestroy void close(){worker.shutdownNow();Process running=process;if(running!=null)running.destroyForcibly();}
}
