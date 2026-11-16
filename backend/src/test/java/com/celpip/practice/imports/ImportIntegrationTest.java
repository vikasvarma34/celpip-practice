package com.celpip.practice.imports;

import com.celpip.practice.CelpipPracticeApplication;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static com.celpip.practice.imports.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportIntegrationTest {
    @TempDir static Path temporary;
    ConfigurableApplicationContext context;
    ImportService service;
    PackageRepository packages;
    Path root;
    String base;
    final HttpClient http = HttpClient.newHttpClient();
    @BeforeAll void start() throws Exception {
        root=temporary.toRealPath(); context=open(root);
        service=context.getBean(ImportService.class); packages=context.getBean(PackageRepository.class);
        base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");
    }
    static ConfigurableApplicationContext open(Path root) {
        return new SpringApplicationBuilder(CelpipPracticeApplication.class).run("--celpip.data-dir="+root,"--server.address=127.0.0.1","--server.port=0","--logging.level.root=ERROR","--spring.main.banner-mode=off","--debug=false");
    }
    @AfterAll void stop() { if(context!=null) context.close(); }
    static Stream<String> units() { return UNITS.stream(); }
    @ParameterizedTest @MethodSource("units") void importsEveryUnitAsZipAndReusesExactDuplicate(String unit) throws Exception {
        var receipt=importFixture(service,unit); assertTrue(Set.of("IMPORTED","EXISTING").contains(receipt.status()));
        var again=importFixture(service,unit); assertEquals("EXISTING",again.status()); assertEquals(receipt.packageId(),again.packageId());
        assertEquals("READY",service.ready(receipt.packageId()).status()); assertClean();
    }
    @ParameterizedTest @MethodSource("units") void importsEveryUnitFromJsonWithMappedAssets(String unit) throws Exception {
        var validated = new ContractValidator().validate(bytes(unit));
        var paths = new ArrayList<>(validated.images());
        var assets = new ArrayList<org.springframework.web.multipart.MultipartFile>();
        for (String path : paths) assets.add(file("asset.png", Files.readAllBytes(FIXTURES.resolve(path))));
        var receipt = service.importUnit(file("unit.json", bytes(unit)), null, assets, paths, validated.module());
        assertEquals(validated.exercises().size(), receipt.exerciseCount()); assertClean();
    }
    @Test void importsStandaloneJsonAndPasteWithExplicitImages() throws Exception {
        var receipt=service.importUnit(file("test.json",bytes("writing-1")),null,List.of(),List.of(),"WRITING");
        assertEquals("WRITING",receipt.module());
        var pasted=service.importUnit(null,new String(bytes("speaking-8"),StandardCharsets.UTF_8),
            List.of(new MockMultipartFile("assets","unrelated-filename.png","image/png",Files.readAllBytes(FIXTURES.resolve("images/asset-0.png")))),List.of("images/asset-0.png"),"SPEAKING");
        assertEquals(List.of(8),pasted.taskNumbers()); assertClean();
    }
    @Test void duplicateIdsContentAndPromptNeverOverwrite() throws Exception {
        importFixture(service,"reading-1"); var original=packages.findById("SYNTHETIC-READING-1").orElseThrow().canonicalJson;
        var changed=node("reading-1"); object(changed,"/exercises/0/answerKey/Q01").put("correctOption",1);
        assertEquals("PACKAGE_ID_CONFLICT",assertThrows(ImportProblem.class,()->service.importUnit(file("x.json",serialize(changed)),null,List.of(),List.of(),null)).code);
        changed.put("packageId","DIFFERENT");
        assertEquals("EXERCISE_ID_CONFLICT",assertThrows(ImportProblem.class,()->service.importUnit(file("x.json",serialize(changed)),null,List.of(),List.of(),null)).code);
        object(changed,"/exercises/0").put("exerciseId","DIFFERENT-EXERCISE");
        assertEquals("PROMPT_DUPLICATE",assertThrows(ImportProblem.class,()->service.importUnit(file("x.json",serialize(changed)),null,List.of(),List.of(),null)).code);
        var copied=node("reading-1"); copied.put("packageId","COPY"); object(copied,"/exercises/0").put("exerciseId","COPY-EXERCISE");
        assertEquals("SYNTHETIC-READING-1",service.importUnit(file("x.json",serialize(copied)),null,List.of(),List.of(),null).packageId());
        assertEquals(original,packages.findById("SYNTHETIC-READING-1").orElseThrow().canonicalJson); assertClean();
    }
    @Test void hashesIncludeImageBytesAndIgnoreImageFilenameAndSource() throws Exception {
        importFixture(service,"speaking-8"); var unit=node("speaking-8"); unit.put("packageId","RENAMED-IMAGE"); object(unit,"/exercises/0").put("exerciseId","RENAMED-EXERCISE");
        object(unit,"/exercises/0/content").put("image","images/renamed.png");
        var first=service.importUnit(null,new String(serialize(unit),StandardCharsets.UTF_8),List.of(file("a.png",Files.readAllBytes(FIXTURES.resolve("images/asset-0.png")))),List.of("images/renamed.png"),null);
        assertEquals("EXISTING",first.status());
        var second=service.importUnit(null,new String(serialize(unit),StandardCharsets.UTF_8),List.of(file("a.png",Files.readAllBytes(FIXTURES.resolve("images/asset-1.png")))),List.of("images/renamed.png"),null);
        assertEquals("IMPORTED",second.status()); assertClean();
    }
    @Test void rejectsMissingExtraCorruptAndMismatchedImages() throws Exception {
        long before=packages.count();
        assertThrows(ImportProblem.class,()->service.importUnit(file("x.json",bytes("speaking-8")),null,List.of(),List.of(),null));
        for (byte[] image : List.of(new byte[]{1,2,3}, bytes("writing-1")))
            assertThrows(ImportProblem.class,()->service.importUnit(null,new String(bytes("speaking-8"),StandardCharsets.UTF_8),List.of(file("a.png",image)),List.of("images/asset-0.png"),null));
        var renamed=node("speaking-8"); object(renamed,"/exercises/0/content").put("image","images/asset-0.jpg");
        assertThrows(ImportProblem.class,()->service.importUnit(null,new String(serialize(renamed),StandardCharsets.UTF_8),List.of(file("a.jpg",Files.readAllBytes(FIXTURES.resolve("images/asset-0.png")))),List.of("images/asset-0.jpg"),null));
        assertThrows(ImportProblem.class,()->service.importUnit(file("x.json",bytes("writing-1")),null,List.of(file("a.png",Files.readAllBytes(FIXTURES.resolve("images/asset-0.png")))),List.of("images/asset-0.png"),null));
        assertEquals(before,packages.count()); assertClean();
    }
    @Test void refusesTaskFiveIdenticalBytesInDifferentFiles() throws Exception {
        byte[] png=Files.readAllBytes(FIXTURES.resolve("images/asset-0.png"));
        assertThrows(ImportProblem.class,()->service.importUnit(null,new String(bytes("speaking-5"),StandardCharsets.UTF_8),List.of(file("a.png",png),file("b.png",png),file("c.png",png)),List.of("images/asset-0.png","images/asset-1.png","images/asset-2.png"),null)); assertClean();
    }
    @Test void transactionFailureRollsBackRowsAndInstalledImages() throws Exception {
        long before=packages.count(), images=count(root.resolve("images"));
        ExerciseRepository broken=mock(ExerciseRepository.class);
        when(broken.findById(anyString())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("Synthetic database failure")).when(broken).flush();
        ImportService failing=new ImportService(context.getBean(LocalStorage.class),context.getBean(UploadReader.class),context.getBean(ContractValidator.class),packages,broken,context.getBean(PlatformTransactionManager.class));
        var unit=node("speaking-8"); unit.put("packageId","FAILURE"); object(unit,"/exercises/0").put("exerciseId","FAILURE-EXERCISE"); object(unit,"/exercises/0/content").put("prompt","Unique synthetic failure-injection prompt.");
        ImportProblem error=assertThrows(ImportProblem.class,()->failing.importUnit(null,new String(serialize(unit),StandardCharsets.UTF_8),List.of(file("a.png",Files.readAllBytes(FIXTURES.resolve("images/asset-0.png")))),List.of("images/asset-0.png"),null));
        assertEquals(503,error.status); assertEquals(before,packages.count()); assertEquals(images,count(root.resolve("images"))); assertClean();
    }
    @Test void filesystemInstallFailureLeavesNoRecordsOrStaging() throws Exception {
        long before = packages.count(); Path images = root.resolve("images"), backup = root.resolve("images-backup");
        Files.move(images, backup); Files.writeString(images, "synthetic filesystem obstruction");
        try {
            var unit = node("speaking-8"); unit.put("packageId", "DISK-FAILURE");
            object(unit, "/exercises/0").put("exerciseId", "DISK-FAILURE-EXERCISE");
            object(unit, "/exercises/0/content").put("prompt", "Unique synthetic filesystem failure prompt.");
            assertEquals(503, assertThrows(ImportProblem.class, () -> service.importUnit(null, new String(serialize(unit), StandardCharsets.UTF_8),
                List.of(file("a.png", Files.readAllBytes(FIXTURES.resolve("images/asset-0.png")))), List.of("images/asset-0.png"), null)).status);
            assertEquals(before, packages.count()); assertClean();
        } finally { Files.delete(images); Files.move(backup, images); }
    }
    @Test void mismatchedModuleAndInvalidJsonLeaveNoData() throws Exception {
        long before=packages.count();
        assertThrows(ImportProblem.class,()->service.importUnit(file("x.json",bytes("writing-1")),null,List.of(),List.of(),"READING"));
        assertThrows(ImportProblem.class,()->service.importUnit(file("x.json","{broken".getBytes()),null,List.of(),List.of(),null));
        assertEquals(before,packages.count()); assertClean();
    }
    @Test void httpContractIsNeutralAndNoStore() throws Exception {
        var response=post(List.of(new Part("file","exercise.json",bytes("reading-4"))),Map.of());
        assertTrue(Set.of(200,201).contains(response.statusCode()), response.body());
        var json=ContractValidator.JSON.readTree(response.body());
        assertEquals(Set.of("packageId","module","schemaVersion","taskNumbers","exerciseCount","status"),json.propertyNames());
        var ready=get("/api/packages/"+json.get("packageId").stringValue()); assertEquals(200,ready.statusCode());
        assertEquals(json.propertyNames(),ContractValidator.JSON.readTree(ready.body()).propertyNames());
        assertEquals("no-store",ready.headers().firstValue("Cache-Control").orElseThrow());
        for (String secret : List.of("passage","correctOption","explanation","questionGroups","Synthetic Part")) assertFalse(ready.body().contains(secret));
        assertEquals(200,get("/api/health").statusCode());
        var config=ContractValidator.JSON.readTree(get("/api/config").body()); assertEquals(14,config.get("timings").size());
        assertEquals(404,get("/api/packages/missing").statusCode());
        assertNotEquals(200,get("/images/asset-0.png").statusCode());
    }
    @Test void httpZipAndPastedAssetsWork() throws Exception {
        assertTrue(Set.of(200,201).contains(post(List.of(new Part("file","unit.zip",zip("speaking-3-4"))),Map.of()).statusCode()));
        var response=post(List.of(new Part("json",null,bytes("speaking-8")),new Part("assets","random.png",Files.readAllBytes(FIXTURES.resolve("images/asset-0.png"))),new Part("assetPaths",null,"images/asset-0.png".getBytes(StandardCharsets.UTF_8))),Map.of());
        assertTrue(Set.of(200,201).contains(response.statusCode()),response.body());
    }
    @Test void httpRejectsForeignOriginUnknownFieldsAndSpoilerErrors() throws Exception {
        assertEquals(403,post(List.of(new Part("file","x.json",bytes("writing-1"))),Map.of("Origin","https://evil.example")).statusCode());
        assertEquals(422,post(List.of(new Part("json",null,bytes("writing-1")),new Part("extra",null,"secret".getBytes())),Map.of()).statusCode());
        var bad=post(List.of(new Part("json",null,"{\"secret prompt\":\"answer\"}".getBytes())),Map.of());
        assertEquals(422,bad.statusCode()); assertFalse(bad.body().contains("secret prompt")); assertFalse(bad.body().contains("stackTrace"));
    }
    record Part(String name,String filename,byte[] bytes) {}
    HttpResponse<String> post(List<Part> parts,Map<String,String> headers) throws Exception {
        String boundary="celpip-test-boundary"; ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(Part part:parts) {
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+part.name()+"\""+(part.filename()==null?"":"; filename=\""+part.filename()+"\"")+"\r\nContent-Type: "+(part.filename()==null?"text/plain; charset=UTF-8":"application/octet-stream")+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(part.bytes()); out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create(base+"/api/imports")).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
        headers.forEach(request::header); return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(String path) throws Exception { return http.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString()); }
    static long count(Path directory) throws IOException { try(var files=Files.list(directory)) { return files.count(); } }
    void assertClean() throws IOException { assertEquals(0,count(root.resolve("staging"))); }
}
