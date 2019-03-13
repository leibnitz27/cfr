package org.benf.cfr.reader.bytecode.analysis.parse;

import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.DeepCloneable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.output.DumpableWithPrecedence;
import org.benf.cfr.reader.util.output.Dumper;

public interface LValue extends DumpableWithPrecedence, DeepCloneable<LValue>, TypeUsageCollectable {
    int getNumberOfCreators();

    <T> void collectLValueAssignments(Expression assignedTo, StatementContainer<T> statementContainer, LValueAssignmentCollector<T> lValueAssigmentCollector);

    boolean doesBlackListLValueReplacement(LValue replace, Expression with);

    void collectLValueUsage(LValueUsageCollector lValueUsageCollector);

    SSAIdentifiers<LValue> collectVariableMutation(SSAIdentifierFactory<LValue, ?> ssaIdentifierFactory);

    LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer);

    LValue applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags);

    InferredJavaType getInferredJavaType();

    JavaAnnotatedTypeInstance getAnnotatedCreationType();

    boolean canThrow(ExceptionCheck caught);

    void markFinal();

    boolean isFinal();

    void markVar();

    boolean isVar();

    class Creation {
        public static Dumper dump(Dumper d, LValue lValue) {
            JavaAnnotatedTypeInstance annotatedCreationType = lValue.getAnnotatedCreationType();
            if (annotatedCreationType != null) {
                annotatedCreationType.dump(d);
            } else {
                if (lValue.isVar()) {
                    d.print("var");
                } else {
                    InferredJavaType inferredJavaType = lValue.getInferredJavaType();
                    JavaTypeInstance t = inferredJavaType.getJavaTypeInstance();
                    d.dump(t);
                }
            }
            return d;
        }
    }
}
