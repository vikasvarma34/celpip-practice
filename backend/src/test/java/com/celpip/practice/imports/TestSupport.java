package com.celpip.practice.imports;

import tools.jackson.databind.node.ObjectNode;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.commons.compress.archivers.zip.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class TestSupport {
    static final Path FIXTURES = Path.of("../fixtures");
    static final List<String> UNITS = List.of("reading-1","reading-2","reading-3","reading-4","writing-1","writing-2","speaking-1","speaking-2","speaking-3-4","speaking-5","speaking-6","speaking-7","speaking-8");
    static byte[] bytes(String unit) throws IOException { return Files.readAllBytes(FIXTURES.resolve(unit + ".json")); }
    static ObjectNode node(String unit) throws IOException { return (ObjectNode) ContractValidator.JSON.readTree(bytes(unit)); }
    static ObjectNode object(ObjectNode root, String pointer) { return (ObjectNode)root.at(pointer); }
    static byte[] serialize(ObjectNode root) { return ContractValidator.JSON.writeValueAsBytes(root); }
    static MockMultipartFile file(String name, byte[] bytes) { return new MockMultipartFile("file",name,"application/octet-stream",bytes); }
    static byte[] zip(String unit) throws IOException {
        var validated = new ContractValidator().validate(bytes(unit)); Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("exercises.json", bytes(unit));
        for (String image : validated.images()) entries.put(image, Files.readAllBytes(FIXTURES.resolve(image)));
        return zip(entries);
    }
    static byte[] zip(Map<String,byte[]> entries) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(buffer)) {
            for (var e : entries.entrySet()) { zip.putArchiveEntry(new ZipArchiveEntry(e.getKey())); zip.write(e.getValue()); zip.closeArchiveEntry(); }
        }
        return buffer.toByteArray();
    }
    static ImportService.Receipt importFixture(ImportService service, String unit) throws IOException { return service.importUnit(file(unit+".zip",zip(unit)),null,List.of(),List.of(),null); }
    private TestSupport() {}
}
