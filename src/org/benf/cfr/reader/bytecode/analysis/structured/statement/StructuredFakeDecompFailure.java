package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.util.output.Dumper;

public class StructuredFakeDecompFailure extends StructuredComment {
    private Exception e;

    public StructuredFakeDecompFailure(Exception e) {
        super("");
        this.e = e;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.separator("{");
        dumper.indent(1);
        dumper.newln();
        dumper.comment("This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.");
        dumper.comment(e.toString());
        for (StackTraceElement ste : e.getStackTrace()) {
            dumper.comment(ste.toString());
        }
        dumper.print("throw new IllegalStateException(\"Decompilation failed\")").endCodeln();
        dumper.indent(-1);
        dumper.separator("}");
        dumper.enqueuePendingCarriageReturn();

        return dumper;
    }
}
