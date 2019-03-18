package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.checker;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredCatch;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredDefinition;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredTry;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.DecompilerComments;

public class VoidVariableChecker implements Op04Checker {
    private boolean found = false;

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        if (found) return in;
        if (in instanceof StructuredDefinition) {
            InferredJavaType inferredJavaType = ((StructuredDefinition) in).getLvalue().getInferredJavaType();
            if (inferredJavaType != null && inferredJavaType.getJavaTypeInstance().getRawTypeOfSimpleType() == RawJavaType.VOID) {
                found = true;
                return in;
            }
        }

        in.transformStructuredChildren(this, scope);
        return in;
    }

    @Override
    public void commentInto(DecompilerComments comments) {
        if (found) {
            comments.addComment(DecompilerComment.VOID_DECLARATION);
        }
    }
}
