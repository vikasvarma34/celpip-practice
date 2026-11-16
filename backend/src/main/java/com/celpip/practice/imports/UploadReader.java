package com.celpip.practice.imports;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageInputStream;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.CRC32;
import static com.celpip.practice.imports.ImportProblem.require;

@Component
public class UploadReader {
    private final ImportLimits limits;
    UploadReader(ImportLimits limits) { this.limits = limits; }
    public record Upload(Path json, Map<String, Path> images) {}
    Upload read(Path stage, MultipartFile file, String json, List<MultipartFile> assets, List<String> assetPaths) throws IOException {
        require((file != null) != (json != null), "upload", "Supply either file or json, not both.");
        require(assets.size() == assetPaths.size(), "assetPaths", "Supply one exact image path per asset in matching order.");
        if (file != null && Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT).endsWith(".zip")) {
            require(assets.isEmpty(), "assets", "ZIP imports must contain their own assets.");
            Path zip = stage.resolve("upload.zip");
            try (InputStream input = file.getInputStream()) { copy(input, zip, limits.compressedBytes()); }
            return unzip(zip, stage);
        }
        Path jsonPath = stage.resolve("exercises.json");
        if (file != null) {
            require(Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT).endsWith(".json"), "file", "Upload a JSON or ZIP file.");
            try (InputStream input = file.getInputStream()) { copy(input, jsonPath, limits.jsonBytes()); }
        } else {
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length > limits.jsonBytes()) throw ImportProblem.limit(); Files.write(jsonPath, bytes);
        }
        if (assets.size() + 1 > limits.maxEntries()) throw ImportProblem.limit();
        long total = Files.size(jsonPath); Map<String, Path> images = new LinkedHashMap<>();
        for (int i = 0; i < assets.size(); i++) {
            String name = assetPaths.get(i); require(ContractValidator.safeImagePath(name), "assetPaths", "Unsafe image path.");
            require(!images.containsKey(name), "assetPaths", "Duplicate image path.");
            Path target = stage.resolve(name); Files.createDirectories(target.getParent());
            try (InputStream input = assets.get(i).getInputStream()) { total += copy(input, target, Math.min(limits.imageBytes(), limits.extractedBytes() - total)); }
            images.put(name, target);
        }
        if (total > limits.extractedBytes()) throw ImportProblem.limit();
        return new Upload(jsonPath, images);
    }
    private Upload unzip(Path archive, Path stage) throws IOException {
        Map<String, Path> images = new LinkedHashMap<>(); Set<String> names = new HashSet<>();
        long total = 0; int count = 0;
        try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
            var entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); String name = entry.getName();
                require(Arrays.equals(entry.getRawName(), name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "zip", "Ambiguous or normalized ZIP paths are forbidden.");
                if (++count > limits.maxEntries()) throw ImportProblem.limit();
                require(names.add(name), "zip", "Duplicate ZIP entries are forbidden.");
                require(!entry.isUnixSymlink() && !entry.getGeneralPurposeBit().usesEncryption() && zip.canReadEntryData(entry), "zip", "Unsupported ZIP entry type.");
                int type = entry.getUnixMode() & 0170000;
                require(type == 0 || type == (entry.isDirectory() ? 0040000 : 0100000), "zip", "Only regular files and directories are allowed.");
                if (entry.isDirectory()) {
                    require(name.matches("images/(?:[A-Za-z0-9_-]+/)*") && entry.getSize() == 0, "zip", "Unexpected directory entry."); continue;
                }
                boolean isJson = name.equals("exercises.json");
                require(isJson || ContractValidator.safeImagePath(name), "zip", "ZIP must contain only exercises.json and safe image paths.");
                long cap = Math.min(isJson ? limits.jsonBytes() : limits.imageBytes(), limits.extractedBytes() - total);
                if (entry.getSize() > cap) throw ImportProblem.limit();
                Path target = stage.resolve(name); Files.createDirectories(target.getParent());
                CRC32 crc = new CRC32(); long written = 0;
                try (InputStream input = zip.getInputStream(entry); OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = input.read(buffer)) != -1) {
                        written += n;
                        if (written > cap || (written > 1048576 && written / (double)Math.max(1, entry.getCompressedSize()) > limits.maxRatio())) throw ImportProblem.limit();
                        crc.update(buffer, 0, n); out.write(buffer, 0, n);
                    }
                }
                require(written == entry.getSize() && crc.getValue() == entry.getCrc(), "zip", "Corrupt ZIP entry.");
                total += written; if (!isJson) images.put(name, target);
            }
        } catch (ImportProblem ex) { throw ex; }
        catch (IOException ex) { throw new ImportProblem(422, "INVALID_ARCHIVE", "zip", "Cannot read this ZIP archive; recreate it with standard ZIP compression."); }
        require(names.contains("exercises.json"), "zip", "ZIP needs root exercises.json.");
        return new Upload(stage.resolve("exercises.json"), images);
    }
    static long copy(InputStream in, Path target, long cap) throws IOException {
        long total = 0; byte[] buffer = new byte[8192]; int n;
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            while ((n = in.read(buffer)) != -1) { total += n; if (total > cap) throw ImportProblem.limit(); out.write(buffer, 0, n); }
        }
        return total;
    }
    Map<String, String> verifyImages(ContractValidator.Validated validated, Upload upload) throws IOException {
        require(validated.images().equals(upload.images().keySet()), "assets", "Supply exactly the referenced images; missing and extra assets are forbidden.");
        Map<String, String> hashes = new TreeMap<>();
        for (var asset : upload.images().entrySet()) {
            validateImage(asset.getKey(), asset.getValue());
            hashes.put(asset.getKey(), sha(Files.readAllBytes(asset.getValue())));
        }
        if (validated.module().equals("SPEAKING") && validated.exercises().getFirst().get("taskNumber").intValue() == 5)
            require(new HashSet<>(hashes.values()).size() == 3, "assets", "Task 5 requires three different image assets.");
        return hashes;
    }
    private void validateImage(String name, Path file) {
        try (var input = new FileImageInputStream(file.toFile())) {
            verifyImageContainer(name, file);
            var readers = ImageIO.getImageReaders(input);
            require(readers.hasNext(), "assets", "Asset is not a decodable image."); var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                require(format.equals(name.toLowerCase(Locale.ROOT).endsWith(".png") ? "png" : "jpeg"), "assets", "Image extension does not match its bytes.");
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || (long)w * h > limits.maxPixels()) throw ImportProblem.limit();
                boolean[] warning = {false}; reader.addIIOReadWarningListener((r, message) -> warning[0] = true);
                require(reader.read(0) != null && !warning[0], "assets", "Image is corrupt or incomplete.");
            } finally { reader.dispose(); }
        } catch (ImportProblem ex) { throw ex; }
        catch (IOException | RuntimeException ex) { throw new ImportProblem(422, "INVALID_IMAGE", "assets", "Image is corrupt or incomplete."); }
    }
    private static void verifyImageContainer(String name, Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) {
            require(bytes.length >= 4 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[bytes.length-2] & 255) == 255 && (bytes[bytes.length-1] & 255) == 217,
                "assets", "JPEG must be complete with no trailing payload.");
            return;
        }
        byte[] signature = {(byte)137,80,78,71,13,10,26,10};
        require(bytes.length >= 8 && Arrays.equals(signature, Arrays.copyOf(bytes,8)), "assets", "Invalid PNG signature.");
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes); buffer.position(8); boolean ended = false;
        while (buffer.remaining() >= 12) {
            int length = buffer.getInt();
            require(length >= 0 && length <= buffer.remaining()-8, "assets", "Truncated PNG chunk.");
            int start = buffer.position(); int type = buffer.getInt(); CRC32 crc = new CRC32(); crc.update(bytes, start, length+4);
            buffer.position(buffer.position()+length); long expected = Integer.toUnsignedLong(buffer.getInt());
            require(crc.getValue() == expected, "assets", "Corrupt PNG chunk.");
            if (type == 0x49454e44) { require(length == 0 && buffer.remaining() == 0, "assets", "Unexpected PNG trailing payload."); ended = true; break; }
        }
        require(ended, "assets", "PNG is incomplete.");
    }
    static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
