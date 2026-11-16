package com.celpip.practice.imports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.node.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static com.celpip.practice.imports.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ContractValidatorTest {
    final ContractValidator validator = new ContractValidator();
    static Stream<String> units() { return UNITS.stream(); }
    @ParameterizedTest @MethodSource("units") void acceptsEveryFormat(String unit) throws Exception { assertNotNull(validator.validate(bytes(unit))); }
    static Stream<Arguments> invalid() {
        return Stream.of(
            change("writing-1", r -> r.put("schemaVersion",2)),
            change("writing-1", r -> r.put("schemaVersion","1")),
            change("writing-1", r -> r.put("schemaVersion",1.0)),
            change("writing-1", r -> r.put("module","LISTENING")),
            change("writing-1", r -> r.put("legacy",true)),
            change("writing-1", r -> r.put("packageId","../unsafe")),
            change("writing-1", r -> r.remove("packageId")),
            change("writing-1", r -> object(r,"/exercises/0").put("taskNumber","1")),
            change("writing-1", r -> object(r,"/exercises/0").put("taskNumber",3)),
            change("writing-1", r -> object(r,"/exercises/0").put("answerKey","forbidden")),
            change("writing-1", r -> object(r,"/exercises/0").put("timing",90)),
            change("writing-1", r -> object(r,"/exercises/0/source").put("url","https://example.com")),
            change("writing-1", r -> { object(r,"/exercises/0/source").put("type","ONLINE"); object(r,"/exercises/0/source").put("url","http://example.com"); }),
            change("writing-1", r -> object(r,"/exercises/0/source").put("type","SYNTHETIC")),
            change("writing-1", r -> object(r,"/exercises/0/content").put("scenario","<script>alert(1)</script>")),
            change("writing-1", r -> object(r,"/exercises/0/content").put("scenario"," ")),
            change("writing-1", r -> object(r,"/exercises/0/content").put("scenario","lorem ipsum")),
            change("writing-1", r -> ((ArrayNode)r.at("/exercises/0/content/requirements")).remove(0)),
            change("writing-2", r -> object(r,"/exercises/0/content/options/0").put("id","B")),
            change("writing-2", r -> object(r,"/exercises/0/content/options/0").put("text",r.at("/exercises/0/content/options/1/text").stringValue())),
            change("reading-1", r -> object(r,"/exercises/0/answerKey/Q01").put("correctOption",4)),
            change("reading-1", r -> object(r,"/exercises/0/answerKey/Q01").put("correctOption","0")),
            change("reading-1", r -> object(r,"/exercises/0/answerKey").remove("Q01")),
            change("reading-1", r -> object(r,"/exercises/0/answerKey").set("Q99",r.at("/exercises/0/answerKey/Q01"))),
            change("reading-1", r -> ((ArrayNode)r.at("/exercises/0/content/questionGroups/0/questions")).remove(0)),
            change("reading-1", r -> object(r,"/exercises/0/content/questionGroups/0/questions/0").put("id","Q02")),
            change("reading-1", r -> ((ArrayNode)r.at("/exercises/0/content/questionGroups/0/questions/0/options")).set(1,"Tuesday")),
            change("reading-1", r -> object(r,"/exercises/0/content/questionGroups/1").put("text","Missing tokens")),
            change("reading-1", r -> object(r,"/exercises/0/content/questionGroups/1").put("text","{{Q08}} {{Q07}} {{Q09}} {{Q10}} {{Q11}}")),
            change("reading-1", r -> object(r,"/exercises/0/content/questionGroups/1").put("text","{{Q07}} {{Q08}} {{Q09}} {{Q10}} {{Q11}} {{Q11}}")),
            change("reading-1", r -> object(r,"/exercises/0/content/questionGroups/1/questions/0").put("prompt","forbidden")),
            change("reading-2", r -> ((ArrayNode)r.at("/exercises/0/content/passage/0/rows/0")).remove(0)),
            change("reading-2", r -> object(r,"/exercises/0/content/passage/0").remove("headers")),
            change("reading-3", r -> object(r,"/exercises/0/content/passage/1").put("label","A")),
            change("reading-3", r -> ((ArrayNode)r.at("/exercises/0/content/questionGroups/0/questions/0/options")).set(4,"F")),
            change("reading-4", r -> object(r,"/exercises/0/content/questionGroups/0").put("type","INLINE_BLANK")),
            change("speaking-3-4", r -> ((ArrayNode)r.get("exercises")).remove(1)),
            change("speaking-3-4", r -> object(r,"/exercises/0").put("taskNumber",4)),
            change("speaking-3-4", r -> object(r,"/exercises/1").put("exerciseId",r.at("/exercises/0/exerciseId").stringValue())),
            change("speaking-3-4", r -> object(r,"/exercises/1/content").put("image","images/asset-1.png")),
            change("speaking-5", r -> ((ArrayNode)r.at("/exercises/0/content/options")).remove(2)),
            change("speaking-5", r -> object(r,"/exercises/0/content/options/2").put("id","B")),
            change("speaking-5", r -> object(r,"/exercises/0/content/options/2").put("image","images/asset-0.png")),
            change("speaking-5", r -> object(r,"/exercises/0/content/options/0").put("details","not an array")),
            change("speaking-6", r -> ((ArrayNode)r.at("/exercises/0/content/alternatives")).remove(0)),
            change("speaking-8", r -> object(r,"/exercises/0/content").put("image","https://example.com/image.png")),
            change("speaking-8", r -> object(r,"/exercises/0/content").put("image","images/../scene.png")),
            change("speaking-8", r -> object(r,"/exercises/0/content").put("image","images\\scene.png")),
            change("speaking-1", r -> ((ArrayNode)r.get("exercises")).add(r.at("/exercises/0").deepCopy()))
        );
    }
    static Arguments change(String unit, Consumer<ObjectNode> mutation) { return Arguments.of(unit,mutation); }
    @ParameterizedTest @MethodSource("invalid") void rejectsContractViolations(String unit, Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode root=node(unit); mutation.accept(root); assertThrows(ImportProblem.class, () -> validator.validate(serialize(root)));
    }
    @ParameterizedTest @ValueSource(strings={"{} {}","{\"schemaVersion\":1,\"schemaVersion\":1}","{\"x\":1,}","// comment\n{}","[]","null",""})
    void rejectsMalformedJson(String json) { assertThrows(ImportProblem.class, () -> validator.validate(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
    @Test void rejectsMalformedUtf8() { assertThrows(ImportProblem.class, () -> validator.validate(new byte[]{(byte)0xc3,(byte)0x28})); }
    @Test void acceptsHttpsSourceAndEmptyTableCell() throws Exception {
        ObjectNode root=node("reading-2"); object(root,"/exercises/0/source").put("type","ONLINE").put("url","https://example.org/exercise");
        ((ArrayNode)root.at("/exercises/0/content/passage/0/rows/0")).set(1,""); assertNotNull(validator.validate(serialize(root)));
    }
}
