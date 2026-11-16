package com.celpip.practice.imports;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.*;
import java.text.Normalizer;
import java.util.*;

final class Fingerprints {
    private Fingerprints() {}
    static String canonical(JsonNode node) { return ContractValidator.JSON.writeValueAsString(normalize(node, Map.of())); }
    static String exercise(String module, JsonNode exercise, Map<String, String> images, boolean promptOnly) {
        ObjectNode value = ContractValidator.JSON.createObjectNode();
        value.put("module", module); value.set("taskNumber", exercise.get("taskNumber"));
        JsonNode content = exercise.get("content");
        value.set("content", promptOnly && module.equals("READING") ? content.get("passage") : content);
        if (!promptOnly && exercise.has("answerKey")) value.set("answerKey", exercise.get("answerKey"));
        return UploadReader.sha(ContractValidator.JSON.writeValueAsBytes(normalize(value, images)));
    }
    static String unit(List<String> hashes) { return UploadReader.sha(ContractValidator.JSON.writeValueAsBytes(hashes)); }
    private static JsonNode normalize(JsonNode node, Map<String, String> images) {
        if (node.isObject()) {
            ObjectNode result = ContractValidator.JSON.createObjectNode();
            for (String key : new TreeSet<>(node.propertyNames())) {
                JsonNode value = node.get(key);
                result.set(key, key.equals("image") && value.isString() && images.containsKey(value.stringValue())
                    ? StringNode.valueOf(images.get(value.stringValue())) : normalize(value, images));
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = ContractValidator.JSON.createArrayNode();
            for (JsonNode value : node) result.add(normalize(value, images));
            return result;
        }
        if (node.isString()) return StringNode.valueOf(Normalizer.normalize(node.stringValue().replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC));
        return node;
    }
}
