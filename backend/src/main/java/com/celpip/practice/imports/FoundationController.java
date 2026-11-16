package com.celpip.practice.imports;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import java.nio.file.Files;
import java.util.*;

@RestController
@RequestMapping("/api")
public class FoundationController {
    private final ImportService imports;
    private final LocalStorage storage;
    private final JdbcTemplate jdbc;
    private final ImportLimits limits;
    FoundationController(ImportService imports, LocalStorage storage, JdbcTemplate jdbc, ImportLimits limits) {
        this.imports = imports; this.storage = storage; this.jdbc = jdbc; this.limits = limits;
    }
    public record Health(String status, boolean storageReady, boolean databaseReady) {}
    public record Timing(String module, int taskNumber, int selectionSeconds, int preparationSeconds, int answerSeconds) {}
    public record Config(int schemaVersion, List<String> modules, List<String> modes, List<Timing> timings, ImportLimits importLimits) {}
    static final List<Timing> TIMINGS = List.of(
        new Timing("READING",1,0,0,660), new Timing("READING",2,0,0,540), new Timing("READING",3,0,0,600), new Timing("READING",4,0,0,780),
        new Timing("WRITING",1,0,0,1620), new Timing("WRITING",2,0,0,1560),
        new Timing("SPEAKING",1,0,30,90), new Timing("SPEAKING",2,0,30,60), new Timing("SPEAKING",3,0,30,60), new Timing("SPEAKING",4,0,30,60),
        new Timing("SPEAKING",5,60,60,60), new Timing("SPEAKING",6,0,60,60), new Timing("SPEAKING",7,0,30,90), new Timing("SPEAKING",8,0,30,60));
    @GetMapping("/health")
    public ResponseEntity<Health> health() {
        boolean disk = List.of("database","questions","images","recordings","exports","staging").stream().allMatch(n -> Files.isWritable(storage.root.resolve(n)));
        boolean database;
        try { database = Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class)); } catch (Exception ex) { database = false; }
        return ResponseEntity.status(disk && database ? 200 : 503).body(new Health(disk && database ? "UP" : "DOWN", disk, database));
    }
    @GetMapping("/config")
    public Config config() { return new Config(1, List.of("READING","WRITING","SPEAKING"), List.of("PRACTICE","TIMED","EXAM"), TIMINGS, limits); }
    @GetMapping("/packages/{id}")
    public ImportService.Receipt ready(@PathVariable String id) { return imports.ready(id); }
    @PostMapping(value="/imports", consumes="multipart/form-data")
    public ResponseEntity<ImportService.Receipt> upload(MultipartHttpServletRequest request,
        @RequestParam(required=false) MultipartFile file, @RequestParam(required=false) String json,
        @RequestParam(required=false) List<MultipartFile> assets, @RequestParam(required=false) List<String> assetPaths,
        @RequestParam(required=false) String expectedModule) {
        ImportProblem.require(Set.of("file","assets").containsAll(request.getMultiFileMap().keySet()) &&
            Set.of("json","assetPaths","expectedModule").containsAll(request.getParameterMap().keySet()), "upload", "Unexpected multipart fields.");
        ImportProblem.require(request.getFiles("file").size() <= 1 && (request.getParameterValues("json") == null || request.getParameterValues("json").length == 1), "upload", "Supply one file or JSON value.");
        ImportProblem.require(request.getParameterValues("expectedModule") == null || request.getParameterValues("expectedModule").length == 1, "expectedModule", "Supply one expected module.");
        var result = imports.importUnit(file, json, assets == null ? List.of() : assets, assetPaths == null ? List.of() : assetPaths, expectedModule);
        return ResponseEntity.status(result.status().equals("IMPORTED") ? 201 : 200).body(result);
    }
}
