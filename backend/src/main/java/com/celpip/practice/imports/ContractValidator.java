package com.celpip.practice.imports;

import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.Pattern;
import static com.celpip.practice.imports.ImportProblem.require;

@Component
public class ContractValidator {
    static final JsonMapper JSON = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Pattern MARKUP = Pattern.compile("(?is)<\\s*[!/?a-z][^>]*>|javascript\\s*:|data\\s*:");
    public record Validated(JsonNode document, String packageId, String module, List<JsonNode> exercises, Set<String> images) {}

    public Validated validate(byte[] bytes) {
        JsonNode root;
        try {
            String utf8 = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            root = JSON.readTree(utf8);
        } catch (Exception ex) {
            throw new ImportProblem(400, "INVALID_JSON", "$", "Use one valid UTF-8 JSON object with unique keys and no trailing data.");
        }
        fields(root, "$", "schemaVersion", "packageId", "module", "exercises");
        require(integer(root.get("schemaVersion"), "schemaVersion") == 1, "schemaVersion", "Only schemaVersion 1 is supported.");
        String id = identifier(root.get("packageId"), "packageId");
        String module = text(root.get("module"), "module");
        require(Set.of("READING", "WRITING", "SPEAKING").contains(module), "module", "Choose READING, WRITING or SPEAKING.");
        JsonNode exercises = array(root.get("exercises"), "exercises", 1, 2);
        Set<String> ids = new HashSet<>(), images = new LinkedHashSet<>();
        List<JsonNode> list = new ArrayList<>();
        for (int i = 0; i < exercises.size(); i++) {
            JsonNode e = exercises.get(i); String p = "exercises[" + i + "]";
            if (module.equals("READING")) fields(e, p, "exerciseId", "taskNumber", "source", "content", "answerKey");
            else fields(e, p, "exerciseId", "taskNumber", "source", "content");
            require(ids.add(identifier(e.get("exerciseId"), p + ".exerciseId")), p + ".exerciseId", "Exercise IDs must be distinct.");
            int task = integer(e.get("taskNumber"), p + ".taskNumber");
            int max = module.equals("READING") ? 4 : module.equals("WRITING") ? 2 : 8;
            require(task >= 1 && task <= max, p + ".taskNumber", "Task number is outside the module range.");
            source(e.get("source"), p + ".source");
            if (module.equals("READING")) reading(e, task, p, images);
            else if (module.equals("WRITING")) writing(e.get("content"), task, p + ".content");
            else speaking(e.get("content"), task, p + ".content", images);
            list.add(e);
        }
        boolean pair = module.equals("SPEAKING") && (list.getFirst().get("taskNumber").intValue() == 3 || list.getFirst().get("taskNumber").intValue() == 4 || list.size() == 2);
        if (pair) {
            require(list.size() == 2 && list.get(0).get("taskNumber").intValue() == 3 && list.get(1).get("taskNumber").intValue() == 4,
                "exercises", "Speaking 3 and 4 must be one ordered pair.");
            require(list.get(0).get("content").get("image").equals(list.get(1).get("content").get("image")), "exercises", "The pair must reference exactly the same image.");
        } else require(list.size() == 1, "exercises", "Import exactly one practice unit.");
        return new Validated(root, id, module, List.copyOf(list), Set.copyOf(images));
    }
    static void fields(JsonNode n, String p, String... names) {
        require(n != null && n.isObject(), p, "Expected an object.");
        require(n.propertyNames().equals(Set.of(names)), p, "Required fields are " + String.join(", ", names) + "; additional fields are forbidden.");
    }
    static String text(JsonNode n, String p) { return plainText(n, p, false); }
    static String plainText(JsonNode n, String p, boolean inline) {
        require(n != null && n.isString() && !n.stringValue().isBlank(), p, "Expected nonblank text.");
        String s = n.stringValue();
        require(inline || (!s.contains("{{") && !s.contains("}}")), p, "Unexpanded placeholders are forbidden.");
        require(!MARKUP.matcher(s).find() && !s.contains("```") && !s.toLowerCase(Locale.ROOT).contains("lorem ipsum") && !s.strip().equals("..."), p, "Use plain text without executable markup or placeholder content.");
        require(s.codePoints().noneMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'), p, "Unsupported control character.");
        return s;
    }
    static String identifier(JsonNode n, String p) {
        String s = text(n, p); require(s.matches("[A-Za-z0-9_-]+"), p, "IDs may contain ASCII letters, digits, underscores and hyphens only."); return s;
    }
    static int integer(JsonNode n, String p) {
        require(n != null && n.isIntegralNumber() && n.canConvertToInt(), p, "Expected an integer, without numeric coercion."); return n.intValue();
    }
    static JsonNode array(JsonNode n, String p, int min, int max) {
        require(n != null && n.isArray() && n.size() >= min && n.size() <= max, p, "Unexpected array size or type."); return n;
    }
    static void strings(JsonNode n, String p, int min, int max, boolean distinct) {
        array(n, p, min, max); Set<String> seen = new HashSet<>();
        for (JsonNode v : n) { String s = text(v, p); require(!distinct || seen.add(s), p, "Options must be distinct."); }
    }
    static void source(JsonNode s, String p) {
        fields(s, p, "type", "url"); String type = text(s.get("type"), p + ".type");
        if (type.equals("AI_GENERATED")) require(s.get("url").isNull(), p + ".url", "Generated sources require a null URL.");
        else {
            require(type.equals("ONLINE"), p + ".type", "Unsupported source type.");
            String url = text(s.get("url"), p + ".url");
            try { URI u = URI.create(url); require("https".equals(u.getScheme()) && u.getHost() != null && u.getUserInfo() == null, p + ".url", "Online sources require an absolute HTTPS URL."); }
            catch (IllegalArgumentException ex) { throw new ImportProblem(422, "INVALID_PACKAGE", p + ".url", "Online sources require an absolute HTTPS URL."); }
        }
    }
    static String image(JsonNode n, String p, Set<String> images) {
        String path = text(n, p); require(safeImagePath(path), p, "Use a safe relative JPG/JPEG/PNG path under images/."); images.add(path); return path;
    }
    static boolean safeImagePath(String path) {
        return path.matches("images/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.(?i:png|jpg|jpeg)") && !path.contains("..");
    }
    static void writing(JsonNode c, int task, String p) {
        if (task == 1) {
            fields(c, p, "scenario", "instructions", "requirements"); text(c.get("scenario"), p + ".scenario"); text(c.get("instructions"), p + ".instructions"); strings(c.get("requirements"), p + ".requirements", 3, 4, false);
        } else {
            fields(c, p, "title", "scenario", "instructions", "options");
            for (String key : List.of("title", "scenario", "instructions")) text(c.get(key), p + "." + key);
            JsonNode options = array(c.get("options"), p + ".options", 2, 2); Set<String> values = new HashSet<>();
            for (int i = 0; i < 2; i++) {
                JsonNode o = options.get(i); fields(o, p + ".options", "id", "text");
                require(text(o.get("id"), p + ".options.id").equals(i == 0 ? "A" : "B"), p + ".options", "Options must be ordered A, B.");
                require(values.add(text(o.get("text"), p + ".options.text")), p + ".options", "Alternatives must be distinct.");
            }
        }
    }
    static void speaking(JsonNode c, int task, String p, Set<String> images) {
        switch (task) {
            case 1, 2, 7 -> fields(c, p, "prompt");
            case 3, 4, 8 -> { fields(c, p, "prompt", "image"); image(c.get("image"), p + ".image", images); }
            case 6 -> { fields(c, p, "prompt", "alternatives"); strings(c.get("alternatives"), p + ".alternatives", 2, 2, false); }
            case 5 -> {
                fields(c, p, "prompt", "persuasionPrompt", "options"); text(c.get("persuasionPrompt"), p + ".persuasionPrompt");
                JsonNode options = array(c.get("options"), p + ".options", 3, 3); Set<String> paths = new HashSet<>();
                for (int i = 0; i < 3; i++) {
                    JsonNode o = options.get(i); fields(o, p + ".options", "id", "title", "image", "details");
                    require(text(o.get("id"), p + ".options.id").equals(String.valueOf((char)('A' + i))), p + ".options", "Options must be ordered A, B, C.");
                    text(o.get("title"), p + ".options.title"); strings(o.get("details"), p + ".options.details", 1, Integer.MAX_VALUE, false);
                    require(paths.add(image(o.get("image"), p + ".options.image", images)), p + ".options.image", "Task 5 requires three distinct image files.");
                }
            }
            default -> throw new IllegalStateException();
        }
        text(c.get("prompt"), p + ".prompt");
    }
    static void reading(JsonNode e, int task, String p, Set<String> images) {
        JsonNode c = e.get("content"); p += ".content";
        fields(c, p, "instructions", "passage", "questionGroups"); text(c.get("instructions"), p + ".instructions");
        JsonNode passage = array(c.get("passage"), p + ".passage", task == 3 ? 4 : 1, task == 3 ? 4 : Integer.MAX_VALUE);
        boolean visual = false;
        for (int i = 0; i < passage.size(); i++) {
            JsonNode b = passage.get(i); String bp = p + ".passage[" + i + "]";
            require(b.isObject(), bp, "Expected a passage block."); String type = text(b.get("type"), bp + ".type");
            if (task == 3) require(type.equals("PARAGRAPH"), bp, "Part 3 requires four labelled paragraphs.");
            else if (task == 1 || task == 4) require(type.equals("TEXT"), bp, "This part accepts TEXT blocks only.");
            else require(Set.of("TEXT", "IMAGE", "TABLE").contains(type), bp, "Part 2 accepts TEXT, IMAGE and TABLE blocks.");
            switch (type) {
                case "TEXT" -> { fields(b, bp, "type", "text"); text(b.get("text"), bp + ".text"); }
                case "PARAGRAPH" -> { fields(b, bp, "type", "label", "text"); text(b.get("text"), bp + ".text"); require(text(b.get("label"), bp + ".label").equals(String.valueOf((char)('A' + i))), bp, "Paragraph labels must be A, B, C, D in order."); }
                case "IMAGE" -> { fields(b, bp, "type", "image"); image(b.get("image"), bp + ".image", images); visual = true; }
                case "TABLE" -> {
                    fields(b, bp, "type", "headers", "rows"); strings(b.get("headers"), bp + ".headers", 1, Integer.MAX_VALUE, false);
                    for (JsonNode row : array(b.get("rows"), bp + ".rows", 1, Integer.MAX_VALUE)) {
                        array(row, bp + ".rows", b.get("headers").size(), b.get("headers").size());
                        for (JsonNode cell : row) { require(cell.isString(), bp + ".rows", "Table cells must be strings."); if (!cell.stringValue().isEmpty()) text(cell, bp + ".rows"); }
                    }
                    visual = true;
                }
                default -> throw new IllegalStateException();
            }
        }
        require(task != 2 || visual, p + ".passage", "Part 2 needs an IMAGE or TABLE.");
        String[] types = task == 3 ? new String[]{"PARAGRAPH_MATCH"} : task == 2 ? new String[]{"INLINE_BLANK", "MULTIPLE_CHOICE"} : new String[]{"MULTIPLE_CHOICE", "INLINE_BLANK"};
        int[] counts = switch (task) { case 1 -> new int[]{6, 5}; case 2 -> new int[]{5, 3}; case 3 -> new int[]{9}; default -> new int[]{5, 5}; };
        JsonNode groups = array(c.get("questionGroups"), p + ".questionGroups", types.length, types.length);
        Set<String> expectedKeys = new HashSet<>(); JsonNode key = e.get("answerKey"); require(key != null && key.isObject(), "answerKey", "Expected answer key object.");
        int number = 1;
        for (int g = 0; g < types.length; g++) {
            JsonNode group = groups.get(g); String gp = p + ".questionGroups[" + g + "]"; boolean inline = types[g].equals("INLINE_BLANK");
            if (inline) fields(group, gp, "type", "text", "questions"); else fields(group, gp, "type", "questions");
            require(text(group.get("type"), gp + ".type").equals(types[g]), gp, "Incorrect question group type/order.");
            List<String> tokens = new ArrayList<>();
            if (inline) {
                String t = plainText(group.get("text"), gp + ".text", true); var m = Pattern.compile("\\{\\{(Q[0-9]{2})}}").matcher(t);
                while (m.find()) tokens.add(m.group(1));
                require(!m.replaceAll("").matches("(?s).*[{}].*") && !t.contains("___") && !t.contains("[blank]"), gp + ".text", "Only canonical inline blank tokens are supported.");
            }
            List<String> questionIds = new ArrayList<>();
            for (JsonNode q : array(group.get("questions"), gp + ".questions", counts[g], counts[g])) {
                if (inline) fields(q, gp + ".questions", "id", "options"); else { fields(q, gp + ".questions", "id", "prompt", "options"); text(q.get("prompt"), gp + ".questions.prompt"); }
                String qid = String.format(Locale.ROOT, "Q%02d", number++);
                require(text(q.get("id"), gp + ".questions.id").equals(qid), gp + ".questions.id", "Question IDs must be consecutive in display order.");
                int optionCount = task == 3 ? 5 : 4; strings(q.get("options"), gp + ".questions.options", optionCount, optionCount, !inline);
                if (task == 3) for (int j = 0; j < 5; j++) require(q.get("options").get(j).stringValue().equals(String.valueOf((char)('A' + j))), gp + ".questions.options", "Paragraph options must be A, B, C, D, E.");
                expectedKeys.add(qid); questionIds.add(qid);
                JsonNode answer = key.get(qid); fields(answer, "answerKey." + qid, "correctOption", "explanation");
                int correct = integer(answer.get("correctOption"), "answerKey." + qid + ".correctOption");
                require(correct >= 0 && correct < optionCount, "answerKey." + qid, "Answer index is outside the option range."); text(answer.get("explanation"), "answerKey." + qid + ".explanation");
            }
            require(!inline || tokens.equals(questionIds), gp + ".text", "Each question needs exactly one inline token in question order.");
        }
        require(key.propertyNames().equals(expectedKeys), "answerKey", "Answer key must cover every question exactly once without extra keys.");
    }
}
