package com.celpip.practice.imports;

public class ImportProblem extends RuntimeException {
    public final int status;
    public final String code;
    public final String field;
    public ImportProblem(int status, String code, String field, String message) {
        super(message); this.status = status; this.code = code; this.field = field;
    }
    static void require(boolean valid, String field, String message) {
        if (!valid) throw new ImportProblem(422, "INVALID_PACKAGE", field, message);
    }
    static ImportProblem limit() { return new ImportProblem(413, "IMPORT_LIMIT", "upload", "Import exceeds configured limits."); }
}
