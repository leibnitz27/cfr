package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.util.output.Dumper;

public class StructuredFakeDecompFailure extends StructuredComment {
    private final Exception e;
    private final boolean dumpStackTrace;

    public StructuredFakeDecompFailure(Exception e, boolean dumpStackTrace) {
        super("");
        this.e = e;
        this.dumpStackTrace = dumpStackTrace;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.separator("{");
        dumper.indent(1);
        dumper.newln();
        dumper.beginBlockComment(false);
        dumper.print("This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.").newln().newln();
        dumper.print(e.toString()).newln();
        if (dumpStackTrace) {
            for (StackTraceElement ste : e.getStackTrace()) {
                dumper.explicitIndent().print("at ").print(ste.toString()).newln();
            }
        }
        dumper.endBlockComment();
        dumper.keyword("throw new ").print("IllegalStateException").separator("(").literal("\"Decompilation failed\"", "\"Decompilation failed\"").separator(")").endCodeln();
        dumper.indent(-1);
        dumper.separator("}");
        dumper.enqueuePendingCarriageReturn();

        return dumper;
    }
}
