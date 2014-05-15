package org.benf.cfr.reader.bytecode.analysis.parse;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.DeepCloneable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.DumpableWithPrecedence;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Map;
import java.util.Set;

public interface Expression extends DumpableWithPrecedence, DeepCloneable<Expression>, ComparableUnderEC, TypeUsageCollectable {
    // Can /PROBABLY/ replace LValueRewriter with expression rewriter.
    Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer);

    Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags);

    boolean isSimple();

    void collectUsedLValues(LValueUsageCollector lValueUsageCollector);

    boolean canPushDownInto();

    Expression pushDown(Expression toPush, Expression parent);

    InferredJavaType getInferredJavaType();

    boolean equivalentUnder(Object o, EquivalenceConstraint constraint);

    boolean canThrow(ExceptionCheck caught);

    // If this expression has any side effects, other than updating stackVar/locals it MUST return null, regardless.
    Literal getComputedLiteral(Map<LValue, Literal> display);

    @Override
    Dumper dump(Dumper d);
}
