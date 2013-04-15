package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:44
 */
public class NewObjectArray extends AbstractNewArray {
    private List<Expression> dimSizes;
    private final JavaTypeInstance allocatedType;
    private final JavaTypeInstance resultType;

    public NewObjectArray(List<Expression> dimSizes, JavaTypeInstance innerInstance, JavaTypeInstance resultInstance) {
        super(new InferredJavaType(resultInstance, InferredJavaType.Source.EXPRESSION));
        this.dimSizes = dimSizes;
        this.allocatedType = innerInstance;
        this.resultType = resultInstance;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("new ").append(resultType.getArrayStrippedType());
        int numDims = resultType.getNumArrayDimensions();
        for (Expression dimSize : dimSizes) {
            sb.append("[").append(dimSize).append("]");
        }
        for (int x = dimSizes.size(); x < numDims; ++x) {
            sb.append("[]");
        }
        return sb.toString();
    }

    @Override
    public int getNumDims() {
        return dimSizes.size();
    }

    @Override
    public Expression getDimSize(int dim) {
        if (dim >= dimSizes.size()) throw new ConfusedCFRException("Out of bounds");
        return dimSizes.get(dim);
    }

    @Override
    public JavaTypeInstance getInnerType() {
        return resultType;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        for (int x = 0; x < dimSizes.size(); ++x) {
            dimSizes.set(x, dimSizes.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        for (int x = 0; x < dimSizes.size(); ++x) {
            dimSizes.set(x, expressionRewriter.rewriteExpression(dimSizes.get(x), ssaIdentifiers, statementContainer, flags));
        }
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
    }

}
