package com.celpip.practice.imports;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded EBML framing validation for the supported audio-only WebM/Opus profile. */
final class WebmAudio {
    record Element(long id,int start,int end) {}
    private final byte[] data;
    WebmAudio(byte[] data) {this.data=data;}
    static void validate(byte[] bytes,String mime) {
        if(mime==null||!Set.of("audio/webm","audio/webm;codecs=opus").contains(mime.toLowerCase(Locale.ROOT).replace(" ","")))bad();
        try {new WebmAudio(bytes).check();}catch(ImportProblem ex){throw ex;}catch(RuntimeException ex){bad();}
    }
    static void bad(){throw new ImportProblem(422,"INVALID_AUDIO","audio","Supply a complete nonempty audio-only WebM/Opus recording.");}
    long vint(int[] pos,boolean id) {
        if(pos[0]>=data.length) {bad();}int first=data[pos[0]++]&255,mask=128,n=1;while(n<=8&&(first&mask)==0){mask>>=1;n++;}
        if(n>8||(id&&n>4)||pos[0]+n-1>data.length)bad();long value=id?first:first&(mask-1);
        for(int i=1;i<n;i++)value=(value<<8)|(data[pos[0]++]&255);
        return !id&&value==((1L<<(7*n))-1)?-1:value;
    }
    List<Element> elements(int start,int end) {
        List<Element> result=new ArrayList<>();int[] pos={start};
        while(pos[0]<end){long id=vint(pos,true),size=vint(pos,false);if(size<0&&id!=0x18538067L&&id!=0x1f43b675L)bad();long finish=size<0?end:pos[0]+size;if(finish>end||finish<pos[0])bad();result.add(new Element(id,pos[0],(int)finish));pos[0]=(int)finish;if(result.size()>1000000)bad();}return result;
    }
    String string(Element e){return new String(data,e.start,e.end-e.start,StandardCharsets.US_ASCII);}
    long number(Element e){if(e.end-e.start>8)bad();long v=0;for(int i=e.start;i<e.end;i++)v=(v<<8)|(data[i]&255);return v;}
    void check() {
        var top=elements(0,data.length);if(top.size()!=2||top.get(0).id!=0x1a45dfa3L||top.get(1).id!=0x18538067L)bad();
        if(elements(top.get(0).start,top.get(0).end).stream().noneMatch(e->e.id==0x4282&&string(e).equals("webm")))bad();
        long track=-1;List<Element> clusters=new ArrayList<>();int tracks=0;
        for(var e:elements(top.get(1).start,top.get(1).end)) {
            if(e.id==0x1654ae6bL)for(var entry:elements(e.start,e.end))if(entry.id==0xae){tracks++;boolean opus=false,audio=false,head=false;for(var f:elements(entry.start,entry.end)){if(f.id==0xd7)track=number(f);if(f.id==0x83)audio=number(f)==2;if(f.id==0x86)opus=string(f).equals("A_OPUS");if(f.id==0x63a2)head=f.end-f.start>=19&&string(f).startsWith("OpusHead");if(f.id==0x6d80)bad();}if(!opus||!audio||!head)bad();}
            if(e.id==0x1f43b675L)clusters.add(e);
        }
        if(tracks!=1||track<1)bad();int blocks=0;
        for(var c:clusters)for(var e:elements(c.start,c.end)){if(e.id==0xa3){block(e,track);blocks++;}else if(e.id==0xa0)for(var b:elements(e.start,e.end))if(b.id==0xa1){block(b,track);blocks++;}}
        if(blocks==0)bad();
    }
    void block(Element e,long track){int[] p={e.start};if(vint(p,false)!=track||e.end-p[0]<4)bad();if((data[p[0]+2]&6)!=0)bad(); /* unlaced Chrome packets */}
}
