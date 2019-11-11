package org.benf.cfr.reader.util.output;

abstract class AbstractDumper implements Dumper {

    enum BlockCommentState {
        InLine,
        In,
        Not
    }

    BlockCommentState inBlockComment = BlockCommentState.Not;
    boolean atStart = true;
    boolean pendingCR = false;

    @Override
    public Dumper beginBlockComment(boolean inline) {
        if (inBlockComment != BlockCommentState.Not) {
            throw new IllegalStateException("Attempt to nest block comments.");
        }
        if (inline) {
            print("/* ");
        } else {
            print("/*").newln();
        }
        inBlockComment = inline ? BlockCommentState.InLine : BlockCommentState.In;
        return this;
    }

    @Override
    public Dumper endBlockComment() {

        if (inBlockComment == BlockCommentState.Not) {
            throw new IllegalStateException("Attempt to end block comment when not in one.");
        }
        BlockCommentState old = inBlockComment;
        inBlockComment = BlockCommentState.Not;
        if (old == BlockCommentState.In) {
            if (!atStart) {
                newln();
            }
            print(" */").newln();
        } else {
            print(" */ ");
        }
        return this;
    }

    @Override
    public Dumper comment(String s) {
        if (inBlockComment == BlockCommentState.Not) {
            print("// " + s);
        } else {
            print(s);
        }
        return newln();
    }

    @Override
    public void enqueuePendingCarriageReturn() {
        pendingCR = true;
    }

    @Override
    public Dumper removePendingCarriageReturn() {
        pendingCR = false;
        atStart = false;
        return this;
    }
}
