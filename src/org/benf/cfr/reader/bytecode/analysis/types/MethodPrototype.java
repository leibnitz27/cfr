package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamer;
import org.benf.cfr.reader.util.ConfusedCFRException;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 07:49
 */
public class MethodPrototype {
    private final List<JavaTypeInstance> args;
    private final JavaTypeInstance result;
    private final VariableNamer variableNamer;
    private final boolean instanceMethod;

    public MethodPrototype(boolean instanceMethod, List<JavaTypeInstance> args, JavaTypeInstance result, VariableNamer variableNamer) {
        this.instanceMethod = instanceMethod;
        this.args = args;
        this.result = result;
        this.variableNamer = variableNamer;
    }

    public String getPrototype(String methName) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.toString()).append(" ").append(methName).append("(");
        boolean first = true;
        int offset = instanceMethod ? 1 : 0;
        for (JavaTypeInstance arg : args) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }
            sb.append(arg.toString()).append(" ").append(variableNamer.getName(0, offset));
            offset += arg.getStackType().getComputationCategory();
        }
        sb.append(")");
        return sb.toString();
    }

    public JavaTypeInstance getReturnType() {
        return result;
    }

    public List<JavaTypeInstance> getArgs() {
        return args;
    }

    public boolean isInstanceMethod() {
        return instanceMethod;
    }

    public String getAppropriatelyCastedArgumentString(Expression expression, int argidx) {
        JavaTypeInstance type = args.get(argidx);
        if (type.isComplexType()) {
            return expression.toString();
        } else {
            RawJavaType expectedRawJavaType = type.getRawTypeOfSimpleType();
            RawJavaType providedRawJavaType = expression.getInferredJavaType().getRawType();
            if (expectedRawJavaType == providedRawJavaType) {
                return expression.toString();
            }
            return expectedRawJavaType.getCastString() + expression.toString();
        }
    }


    public void tightenArgs(List<Expression> expressions) {
        if (expressions.size() != args.size()) {
            throw new ConfusedCFRException("expr arg size mismatch");
        }
        int length = args.size();
        for (int x = 0; x < length; ++x) {
            Expression expression = expressions.get(x);
            JavaTypeInstance type = args.get(x);
            expression.getInferredJavaType().useAsWithoutCasting(type.getRawTypeOfSimpleType());
        }
    }
}
