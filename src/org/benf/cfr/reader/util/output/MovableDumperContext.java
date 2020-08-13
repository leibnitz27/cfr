package org.benf.cfr.reader.util.output;

class MovableDumperContext {
    BlockCommentState inBlockComment = BlockCommentState.Not;
    boolean atStart = true;
    boolean pendingCR = false;
    int indent;
    int blockCommentIndent;
    int outputCount = 0;
    int currentLine = 1; // lines are 1 based.  Sigh.
}
