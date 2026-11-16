package com.celpip.practice.imports;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.commons.compress.archivers.zip.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import static com.celpip.practice.imports.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UploadSafetyTest {
    @TempDir Path temporary;
    final ImportLimits defaults=new ImportLimits(31457280,62914560,2097152,15728640,25000000,16,100);
    @ParameterizedTest @ValueSource(strings={"../escape.png","/absolute.png","images/../escape.png","images\\escape.png","images/nested.zip","images/image.svg","extra.json","__MACOSX/metadata","images//asset.png","C:/absolute.png"})
    void rejectsUnsafeAndUnexpectedZipPaths(String name) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(raw)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("exercises.json")); zip.write(bytes("writing-1")); zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry(name)); zip.write(new byte[]{1}); zip.closeEntry();
        }
        byte[] archive=raw.toByteArray();
        assertThrows(ImportProblem.class,()->read(new UploadReader(defaults),archive));
        assertFalse(Files.exists(temporary.getParent().resolve("escape.png")));
    }
    @Test void rejectsDuplicateEntriesAndSymlinks() throws Exception {
        for(boolean symlink:List.of(false,true)) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(ZipArchiveOutputStream zip=new ZipArchiveOutputStream(out)) {
                for(int i=0;i<2;i++) {
                    ZipArchiveEntry entry=new ZipArchiveEntry(i==0?"exercises.json":symlink?"images/link.png":"exercises.json");
                    if(symlink && i==1) entry.setUnixMode(0120777);
                    zip.putArchiveEntry(entry);zip.write(bytes("writing-1"));zip.closeArchiveEntry();
                }
            }
            assertThrows(ImportProblem.class,()->read(new UploadReader(defaults),out.toByteArray()));
        }
    }
    @Test void acceptsHarmlessDirectoryEntries() throws Exception {
        byte[] archive=zip(Map.of("exercises.json",bytes("writing-1"),"images/",new byte[0]));
        assertNotNull(read(new UploadReader(defaults),archive));
    }
    @Test void rejectsInvalidZipAndMissingRootJson() throws Exception {
        assertThrows(ImportProblem.class,()->read(new UploadReader(defaults),new byte[]{1,2,3}));
        assertThrows(ImportProblem.class,()->read(new UploadReader(defaults),zip(Map.of("images/a.png",new byte[]{1}))));
    }
    @Test void rejectsCompressedJsonImageTotalAndEntryLimits() throws Exception {
        byte[] archive=zip("speaking-8");
        for(ImportLimits limits:List.of(
            new ImportLimits(10,62914560,2097152,15728640,25000000,16,100),
            new ImportLimits(31457280,10,2097152,15728640,25000000,16,100),
            new ImportLimits(31457280,62914560,10,15728640,25000000,16,100),
            new ImportLimits(31457280,62914560,2097152,10,25000000,16,100),
            new ImportLimits(31457280,62914560,2097152,15728640,25000000,1,100))) {
            assertEquals(413,assertThrows(ImportProblem.class,()->read(new UploadReader(limits),archive)).status);
        }
    }
    @Test void rejectsExpansionBombBeforeWritingBeyondCap() throws Exception {
        byte[] huge=new byte[1500000]; Arrays.fill(huge,(byte)'a');
        assertEquals(413,assertThrows(ImportProblem.class,()->read(new UploadReader(defaults),zip(Map.of("exercises.json",huge)))).status);
    }
    @Test void rejectsCrcMismatch() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] json=bytes("writing-1");
        try(ZipArchiveOutputStream zip=new ZipArchiveOutputStream(out)) {
            ZipArchiveEntry e=new ZipArchiveEntry("exercises.json");e.setMethod(ZipArchiveEntry.STORED);e.setSize(json.length);CRC32 crc=new CRC32();crc.update(json);e.setCrc(crc.getValue());
            zip.putArchiveEntry(e);zip.write(json);zip.closeArchiveEntry();
        }
        byte[] corrupt=out.toByteArray();int offset=30+"exercises.json".length();corrupt[offset+20]^=1;
        assertThrows(ImportProblem.class,()->read(new UploadReader(defaults),corrupt));
    }
    @Test void checksPixelsBeforeDecodingAndRejectsTruncation() throws Exception {
        var validator=new ContractValidator();var validated=validator.validate(bytes("speaking-8"));
        var reader=new UploadReader(new ImportLimits(31457280,62914560,2097152,15728640,100,16,100));
        Path stage=Files.createTempDirectory(temporary,"pixels-");Path png=stage.resolve("image.png");Files.copy(FIXTURES.resolve("images/asset-0.png"),png);
        assertEquals(413,assertThrows(ImportProblem.class,()->reader.verifyImages(validated,new UploadReader.Upload(stage.resolve("exercises.json"),Map.of("images/asset-0.png",png)))).status);
        Files.write(png,Arrays.copyOf(Files.readAllBytes(png),40));
        assertThrows(ImportProblem.class,()->new UploadReader(defaults).verifyImages(validated,new UploadReader.Upload(stage.resolve("exercises.json"),Map.of("images/asset-0.png",png))));
    }
    @Test void rejectsDuplicateAssetMappingsAndMissingPathMap() throws Exception {
        byte[] png=Files.readAllBytes(FIXTURES.resolve("images/asset-0.png"));
        var reader=new UploadReader(defaults);
        assertThrows(ImportProblem.class,()->reader.read(Files.createTempDirectory(temporary,"mapping-"),file("x.json",bytes("speaking-8")),null,List.of(file("a.png",png)),List.of()));
        assertThrows(ImportProblem.class,()->reader.read(Files.createTempDirectory(temporary,"mapping-"),file("x.json",bytes("speaking-8")),null,List.of(file("a.png",png),file("b.png",png)),List.of("images/asset-0.png","images/asset-0.png")));
    }
    @Test void fingerprintsNormalizeUnicodeAndLineEndings() throws Exception {
        var a=node("speaking-1");object(a,"/exercises/0/content").put("prompt","Café\r\nTest");
        var b=node("speaking-1");object(b,"/exercises/0/content").put("prompt","Cafe\u0301\nTest");
        assertEquals(Fingerprints.exercise("SPEAKING",a.at("/exercises/0"),Map.of(),false),Fingerprints.exercise("SPEAKING",b.at("/exercises/0"),Map.of(),false));
    }
    UploadReader.Upload read(UploadReader reader,byte[] archive) throws IOException {
        return reader.read(Files.createTempDirectory(temporary,"upload-"),file("unit.zip",archive),null,List.of(),List.of());
    }
}
