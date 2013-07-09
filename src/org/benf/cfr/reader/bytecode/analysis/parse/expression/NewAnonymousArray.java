package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.output.CommaHelp;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:44
 * <p/>
 * 1d array only.
 */
public class NewAnonymousArray extends AbstractNewArray {
    private JavaTypeInstance allocatedType;
    private int numDims;
    private List<Expression> values;

    public NewAnonymousArray(InferredJavaType type, int numDims, List<Expression> values) {
        super(type);
        this.values = values;
        this.numDims = numDims;
        this.allocatedType = type.getJavaTypeInstance().getArrayStrippedType();
        if (allocatedType instanceof RawJavaType) {
            for (Expression value : values) {
                value.getInferredJavaType().useAsWithoutCasting((RawJavaType) allocatedType);
            }
        }
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new NewAnonymousArray(getInferredJavaType(), numDims, cloneHelper.replaceOrClone(values));
    }

    @Override
    public Dumper dump(Dumper d) {
        d.print("new ").print(allocatedType.toString());
        for (int x = 0; x < numDims; ++x) d.print("[]");
        d.print("{");
        boolean first = true;
        for (Expression value : values) {
            first = CommaHelp.comma(first, d);
            d.dump(value);
        }
        d.print("}");
        return d;
    }

    public List<Expression> getValues() {
        return values;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        for (int x = 0; x < values.size(); ++x) {
            values.set(x, values.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        for (int x = 0; x < values.size(); ++x) {
            values.set(x, expressionRewriter.rewriteExpression(values.get(x), ssaIdentifiers, statementContainer, flags));
        }
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
    }


    @Override
    public int getNumDims() {
        return numDims;
    }

    @Override
    public int getNumSizedDims() {
        return 0;
    }

    @Override
    public Expression getDimSize(int dim) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JavaTypeInstance getInnerType() {
        return allocatedType;
    }
}
