package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:44
 */
public class NewObjectArray extends AbstractExpression {
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
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        for (int x = 0; x < dimSizes.size(); ++x) {
            dimSizes.set(x, dimSizes.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
    }

}
