package com.celpip.practice.imports;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

@Component
public class LocalStorage implements DisposableBean {
    final Path root;
    private final FileChannel channel;
    private final FileLock lock;
    public LocalStorage(@Value("${celpip.data-dir}") String directory) throws IOException {
        Path configured = Path.of(directory);
        if (!configured.isAbsolute()) throw new IOException("CELPIP data directory must be absolute.");
        root = configured.normalize();
        if (root.toString().contains(";") || root.toString().contains("\n")) throw new IOException("Unsupported data directory characters.");
        for (Path p = root; p != null; p = p.getParent()) {
            if (Files.isSymbolicLink(p)) throw new IOException("Data directory must not traverse symlinks.");
            if (Files.exists(p.resolve(".git"))) throw new IOException("Data directory must be outside a Git checkout.");
        }
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root.resolve(".process.lock"))) throw new IOException("Unsafe storage lock.");
        channel = FileChannel.open(root.resolve(".process.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = channel.tryLock(); } catch (RuntimeException | IOException ex) { channel.close(); throw ex; }
        if (acquired == null) { channel.close(); throw new IOException("Another application process owns this data directory."); }
        lock = acquired;
        try {
            for (String name : List.of("database", "questions", "images", "recordings", "exports", "staging")) {
                Path child = root.resolve(name);
                if (Files.isSymbolicLink(child)) throw new IOException("Unsafe storage directory.");
                Files.createDirectories(child);
            }
        } catch (IOException ex) { destroy(); throw ex; }
        LoggerFactory.getLogger(getClass()).info("CELPIP data directory: {}", root);
    }
    Path stage() throws IOException { return Files.createTempDirectory(root.resolve("staging"), "import-"); }
    Path images(String key) { return root.resolve("images").resolve(key); }
    static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try (var walk = Files.walk(path)) { for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
    }
    public void destroy() throws IOException { lock.release(); channel.close(); }
}
