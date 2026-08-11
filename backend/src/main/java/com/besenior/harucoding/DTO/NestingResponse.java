package com.besenior.harucoding.DTO;

import java.util.List;

public class NestingResponse {

    private String status;
    private String formattedCode;
    private List<ChangeInfo> changes;

    public NestingResponse(String status, String formattedCode, List<ChangeInfo> changes) {
        this.status = status;
        this.formattedCode = formattedCode;
        this.changes = changes;
    }

    public String getStatus() { return status; }
    public String getFormattedCode() { return formattedCode; }
    public List<ChangeInfo> getChanges() { return changes; }

    public static class ChangeInfo {
        private int lineNumber;
        private String type;
        private int indentOffset;

        public ChangeInfo(int lineNumber, String type, int indentOffset) {
            this.lineNumber = lineNumber;
            this.type = type;
            this.indentOffset = indentOffset;
        }

        public int getLineNumber() { return lineNumber; }
        public String getType() { return type; }
        public int getIndentOffset() { return indentOffset; }
    }
}
