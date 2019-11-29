package org.benf.cfr.reader.util.output;

class MovableDumperContext {
    BlockCommentState inBlockComment = BlockCommentState.Not;
    boolean atStart = true;
    boolean pendingCR = false;
    int indent;
    int outputCount = 0;
}
