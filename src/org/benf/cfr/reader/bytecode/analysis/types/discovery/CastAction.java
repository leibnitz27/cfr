package org.benf.cfr.reader.bytecode.analysis.types.discovery;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;

public enum CastAction {
    None {
        public Expression performCastAction(Expression orig, InferredJavaType tgtType) {
            return orig;
        }
    },
    InsertExplicit {
        public Expression performCastAction(Expression orig, InferredJavaType tgtType) {
            if (tgtType.getJavaTypeInstance() == RawJavaType.BOOLEAN) return orig;
            return new CastExpression(tgtType, orig);
        }
    };

    public abstract Expression performCastAction(Expression orig, InferredJavaType tgtType);
}
