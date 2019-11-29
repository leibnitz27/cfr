package org.benf.cfr.reader.util.output;

abstract class AbstractDumper implements Dumper {
    final MovableDumperContext context;

    AbstractDumper(MovableDumperContext context) {
        this.context = context;
    }

    @Override
    public Dumper beginBlockComment(boolean inline) {
        if (context.inBlockComment != BlockCommentState.Not) {
            throw new IllegalStateException("Attempt to nest block comments.");
        }
        if (inline) {
            print("/* ");
        } else {
            print("/*").newln();
        }
        context.inBlockComment = inline ? BlockCommentState.InLine : BlockCommentState.In;
        return this;
    }

    @Override
    public Dumper endBlockComment() {

        if (context.inBlockComment == BlockCommentState.Not) {
            throw new IllegalStateException("Attempt to end block comment when not in one.");
        }
        BlockCommentState old = context.inBlockComment;
        context.inBlockComment = BlockCommentState.Not;
        if (old == BlockCommentState.In) {
            if (!context.atStart) {
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
        if (context.inBlockComment == BlockCommentState.Not) {
            print("// " + s);
        } else {
            print(s);
        }
        return newln();
    }

    @Override
    public void enqueuePendingCarriageReturn() {
        context.pendingCR = true;
    }

    @Override
    public Dumper removePendingCarriageReturn() {
        context.pendingCR = false;
        context.atStart = false;
        return this;
    }
}
