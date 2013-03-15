package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.getopt.CFRState;

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
    private JavaTypeInstance result;
    private final VariableNamer variableNamer;
    private final boolean instanceMethod;
    private final boolean varargs;
    private final String name;
    private final ConstantPool cp;
    private final ClassFile classFile;
    private transient List<LocalVariable> parameterLValues = null;

    public MethodPrototype(ClassFile classFile, String name, boolean instanceMethod, List<FormalTypeParameter> formalTypeParameters, List<JavaTypeInstance> args, JavaTypeInstance result, boolean varargs, VariableNamer variableNamer, ConstantPool cp) {
        this.formalTypeParameters = formalTypeParameters;
        this.instanceMethod = instanceMethod;
        this.args = args;
        this.result = "<init>".equals(name) ? null : result;
        this.varargs = varargs;
        this.variableNamer = variableNamer;
        this.name = name;
        this.cp = cp;
        this.classFile = classFile;
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

    public List<LocalVariable> getParameters() {
        if (parameterLValues != null) return parameterLValues;

        parameterLValues = ListFactory.newList();
        int offset = instanceMethod ? 1 : 0;
        int argssize = args.size();
        for (int i = 0; i < argssize; ++i) {
            JavaTypeInstance arg = args.get(i);
            parameterLValues.add(new LocalVariable(offset, variableNamer, 0, new InferredJavaType(arg, InferredJavaType.Source.FIELD, true)));
            offset += arg.getStackType().getComputationCategory();
        }
        return parameterLValues;
    }

    public JavaTypeInstance getReturnType() {
        return result;
    }

    public String getName() {
        return name;
    }

    public boolean hasFormalTypeParameters() {
        return formalTypeParameters != null && !formalTypeParameters.isEmpty();
    }

    public JavaTypeInstance getReturnType(JavaTypeInstance thisTypeInstance, List<Expression> invokingArgs) {
        if (result == null) {
            if ("<init>".equals(getName())) {
                if (classFile != null) {
                    result = classFile.getClassSignature().getThisGeneralTypeClass(thisTypeInstance, cp);
                } else {
                    // best we can say is 'this'.
                    result = thisTypeInstance;
                }
            } else {
                throw new IllegalStateException();
            }
        }
        if (classFile == null) return result;
        if (hasFormalTypeParameters() || classFile.hasFormalTypeParameters()) {
            // We're calling a method against a generic object.
            // we should be able to figure out more information
            // I.e. iterator on List<String> returns Iterator<String>, not Iterator.

            JavaGenericRefTypeInstance genericRefTypeInstance = null;
            if (thisTypeInstance instanceof JavaGenericRefTypeInstance) {
                genericRefTypeInstance = (JavaGenericRefTypeInstance) thisTypeInstance;
                thisTypeInstance = genericRefTypeInstance.getDeGenerifiedType();
            }
//            ClassFile classFile = null;
//
//            try {
//                // Wouldn't be neccessary if we kept a back ref?
//                // However, we should /always/ get a hit immediately here?
//                classFile = cp.getCFRState().getClassFile(thisTypeInstance);
//            } catch (CannotLoadClassException _) {
//                return result;
//            }

            /*
             * Now we need to specialise the method according to the existing specialisation on
             * the instance.
             *
             * i.e. given that genericRefTypeInstance has the correct bindings, apply those to method.
             */
            JavaTypeInstance boundResult = getResultBoundAccordingly(classFile.getClassSignature(), genericRefTypeInstance, invokingArgs);
            return boundResult;
        } else {
            return result;
        }
    }

    public List<JavaTypeInstance> getArgs() {
        return args;
    }

    public boolean isInstanceMethod() {
        return instanceMethod;
    }

    public Expression getAppropriatelyCastedArgument(Expression expression, int argidx) {
        JavaTypeInstance type = args.get(argidx);
        if (type.isComplexType()) {
            return expression;
        } else {
            RawJavaType expectedRawJavaType = type.getRawTypeOfSimpleType();
            RawJavaType providedRawJavaType = expression.getInferredJavaType().getRawType();
            if (expectedRawJavaType == providedRawJavaType) {
                return expression;
            }
            return new CastExpression(new InferredJavaType(expectedRawJavaType, InferredJavaType.Source.EXPRESSION, true), expression);
        }

    }

    // Saves us using the above if we don't need to create the cast expression.
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
        sb.append(getName()).append('(');
        for (JavaTypeInstance arg : args) {
            sb.append(arg).append(" ");
        }
        sb.append(')');
        return sb.toString();
    }

    public boolean equalsGeneric(MethodPrototype other) {
        List<FormalTypeParameter> otherTypeParameters = other.formalTypeParameters;
        List<JavaTypeInstance> otherArgs = other.args;

        if (otherArgs.size() != args.size()) {
            return false;
        }
        // TODO : This needs a bit of work ... (!)
        // TODO : Will return false positives at the moment.

        GenericTypeBinder genericTypeBinder = new GenericTypeBinder();
        for (int x = 0; x < args.size(); ++x) {
            JavaTypeInstance lhs = args.get(x);
            JavaTypeInstance rhs = otherArgs.get(x);
            JavaTypeInstance deGenerifiedLhs = lhs.getDeGenerifiedType();
            JavaTypeInstance deGenerifiedRhs = rhs.getDeGenerifiedType();
            if (!deGenerifiedLhs.equals(deGenerifiedRhs)) {
                if (lhs instanceof JavaGenericBaseInstance) {
                    if (!((JavaGenericBaseInstance) lhs).tryFindBinding(rhs, genericTypeBinder)) return false;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    public JavaTypeInstance getResultBoundAccordingly(ClassSignature classSignature, JavaGenericRefTypeInstance boundInstance, List<Expression> invokingArgs) {
        if (!(result instanceof JavaGenericBaseInstance)) {
            // Don't care - (i.e. iterator<E> hasNext)
            return result;
        }

        List<JavaTypeInstance> invokingTypes = ListFactory.newList();
        for (Expression invokingArg : invokingArgs) {
            invokingTypes.add(invokingArg.getInferredJavaType().getJavaTypeInstance());
        }

        /*
         * For each of the formal type parameters of the class signature, what has it been bound to in the
         * instance?
         */
        GenericTypeBinder genericTypeBinder = new GenericTypeBinder().bind(formalTypeParameters, classSignature, args, boundInstance, invokingTypes);

        JavaGenericBaseInstance genericResult = (JavaGenericBaseInstance) result;
        return genericResult.getBoundInstance(genericTypeBinder);
    }

}
