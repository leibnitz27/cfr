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
    private final List<FormalTypeParameter> formalTypeParameters;
    private final List<JavaTypeInstance> args;
    private final JavaTypeInstance result;
    private final VariableNamer variableNamer;
    private final boolean instanceMethod;
    private final boolean varargs;

    public MethodPrototype(boolean instanceMethod, List<FormalTypeParameter> formalTypeParameters, List<JavaTypeInstance> args, JavaTypeInstance result, boolean varargs, VariableNamer variableNamer) {
        this.formalTypeParameters = formalTypeParameters;
        this.instanceMethod = instanceMethod;
        this.args = args;
        this.result = result;
        this.varargs = varargs;
        this.variableNamer = variableNamer;
    }

    public String getDeclarationSignature(String methName, boolean isConstructor) {
        StringBuilder sb = new StringBuilder();
        if (formalTypeParameters != null) {
            sb.append('<');
            boolean first = true;
            for (FormalTypeParameter formalTypeParameter : formalTypeParameters) {
                if (!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append(formalTypeParameter.toString());
            }
            sb.append("> ");
        }
        if (!isConstructor) {
            sb.append(result.toString()).append(" ");
        }
        sb.append(methName).append("(");
        /* We don't get a vararg type to change itself, as it's a function of the method, not the type
         *
         */

        int offset = instanceMethod ? 1 : 0;
        int argssize = args.size();
        for (int i = 0; i < argssize; ++i) {
            JavaTypeInstance arg = args.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            if (varargs && (i == argssize - 1)) {
                if (!(arg instanceof JavaArrayTypeInstance)) {
                    throw new ConfusedCFRException("VARARGS method doesn't have an array as last arg!!");
                }
                sb.append(((JavaArrayTypeInstance) arg).toVarargString());
            } else {
                sb.append(arg.toString());
            }
            sb.append(" ").append(variableNamer.getName(0, offset));
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (JavaTypeInstance arg : args) {
            sb.append(arg).append(" ");
        }
        return sb.toString();
    }
}
