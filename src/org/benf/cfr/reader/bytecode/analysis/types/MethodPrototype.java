package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdent;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.variables.Ident;
import org.benf.cfr.reader.bytecode.analysis.variables.Slot;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.CommaHelp;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Map;

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
    private transient boolean explicitThisRemoval = false;

    public MethodPrototype(ClassFile classFile, JavaTypeInstance classType, String name, boolean instanceMethod, List<FormalTypeParameter> formalTypeParameters, List<JavaTypeInstance> args, JavaTypeInstance result, boolean varargs, VariableNamer variableNamer, ConstantPool cp) {
        this.formalTypeParameters = formalTypeParameters;
        this.instanceMethod = instanceMethod;
        this.args = args;
        JavaTypeInstance resultType;
        if (MiscConstants.INIT_METHOD.equals(name)) {
            if (classFile == null) {
                resultType = classType;
            } else {
                resultType = null;
            }
        } else {
            resultType = result;
        }
        this.result = resultType;
        this.varargs = varargs;
        this.variableNamer = variableNamer;
        this.name = name;
        this.cp = cp;
        this.classFile = classFile;
    }

    public void setExplicitThisRemoval(boolean explicitThisRemoval) {
        this.explicitThisRemoval = explicitThisRemoval;
    }

    public void dumpDeclarationSignature(Dumper d, String methName, Method.MethodConstructor isConstructor, MethodPrototypeAnnotationsHelper annotationsHelper) {

        if (formalTypeParameters != null) {
            d.print('<');
            boolean first = true;
            for (FormalTypeParameter formalTypeParameter : formalTypeParameters) {
                first = CommaHelp.comma(first, d);
                d.print(formalTypeParameter.toString());
            }
            d.print("> ");
        }
        if (!isConstructor.isConstructor()) {
            d.print(result.toString()).print(" ");
        }
        d.print(methName).print("(");
        /* We don't get a vararg type to change itself, as it's a function of the method, not the type
         *
         */

        List<LocalVariable> parameterLValues = getComputedParameters();
        int argssize = args.size();
        int start = explicitThisRemoval ? 1 : 0;
        boolean first = true;
        for (int i = start; i < argssize; ++i) {
            JavaTypeInstance arg = args.get(i);
            first = CommaHelp.comma(first, d);
            annotationsHelper.addAnnotationTextForParameterInto(i, d);
            if (varargs && (i == argssize - 1)) {
                if (!(arg instanceof JavaArrayTypeInstance)) {
                    throw new ConfusedCFRException("VARARGS method doesn't have an array as last arg!!");
                }
                d.print(((JavaArrayTypeInstance) arg).toVarargString());
            } else {
                d.print(arg.toString());
            }
            d.print(" ").dump(parameterLValues.get(i).getName());
        }
        d.print(")");
    }

    public int getNumHiddenArguments() {
        return explicitThisRemoval ? 1 : 0;
    }

    public List<LocalVariable> getComputedParameters() {
        if (parameterLValues == null) {
            throw new IllegalStateException("Parameters not created");
        }
        return parameterLValues;
    }

    public Map<Slot, SSAIdent> collectInitialSlotUsage(Method.MethodConstructor constructorFlag, SSAIdentifierFactory<Slot> ssaIdentifierFactory) {
        Map<Slot, SSAIdent> res = MapFactory.newOrderedMap();
        int offset = 0;
        switch (constructorFlag) {
            case ENUM_CONSTRUCTOR: {
                offset = 3;
                Slot tgt0 = new Slot(classFile.getClassType(), 0);
                res.put(tgt0, ssaIdentifierFactory.getIdent(tgt0));
                Slot tgt1 = new Slot(RawJavaType.REF, 1);
                res.put(tgt1, ssaIdentifierFactory.getIdent(tgt1));
                Slot tgt2 = new Slot(RawJavaType.INT, 2);
                res.put(tgt2, ssaIdentifierFactory.getIdent(tgt2));
                break;
            }
            default: {
                if (instanceMethod) {
                    Slot tgt = new Slot(classFile.getClassType(), 0);
                    res.put(tgt, ssaIdentifierFactory.getIdent(tgt));
                }
                offset = instanceMethod ? 1 : 0;
                break;
            }
        }
        for (JavaTypeInstance arg : args) {
            Slot tgt = new Slot(arg, offset);
            res.put(tgt, ssaIdentifierFactory.getIdent(tgt));
            offset += arg.getStackType().getComputationCategory();
        }
        return res;
    }

    public List<LocalVariable> computeParameters(Method.MethodConstructor constructorFlag, Map<Integer, Ident> slotToIdentMap) {
        if (parameterLValues != null) {
            throw new IllegalStateException("Parameters already created");
        }

        parameterLValues = ListFactory.newList();
        int offset = 0;
        if (instanceMethod) {
            variableNamer.forceName(slotToIdentMap.get(0), 0, MiscConstants.THIS);
            offset = 1;
        }
        if (constructorFlag == Method.MethodConstructor.ENUM_CONSTRUCTOR) offset += 2;
        int argssize = args.size();
        for (int i = 0; i < argssize; ++i) {
            JavaTypeInstance arg = args.get(i);
            // TODO : This should share a variable factory with the method, so we're sure they're
            // the same instance.
            parameterLValues.add(new LocalVariable(offset, slotToIdentMap.get(offset), variableNamer, 0, new InferredJavaType(arg, InferredJavaType.Source.FIELD, true)));
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
        if (classFile == null) {
            return result;
        }

        if (result == null) {
            if (MiscConstants.INIT_METHOD.equals(getName())) {
                result = classFile.getClassSignature().getThisGeneralTypeClass(classFile.getClassType(), classFile.getConstantPool());
            } else {
                throw new IllegalStateException();
            }
        }
        if (hasFormalTypeParameters() || classFile.hasFormalTypeParameters()) {
            // We're calling a method against a generic object.
            // we should be able to figure out more information
            // I.e. iterator on List<String> returns Iterator<String>, not Iterator.

            JavaGenericRefTypeInstance genericRefTypeInstance = null;
            if (thisTypeInstance instanceof JavaGenericRefTypeInstance) {
                genericRefTypeInstance = (JavaGenericRefTypeInstance) thisTypeInstance;
                thisTypeInstance = genericRefTypeInstance.getDeGenerifiedType();
            }

            /*
             * Now we need to specialise the method according to the existing specialisation on
             * the instance.
             *
             * i.e. given that genericRefTypeInstance has the correct bindings, apply those to method.
             */
            JavaTypeInstance boundResult = getResultBoundAccordingly(result, classFile.getClassSignature(), genericRefTypeInstance, invokingArgs);
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
            // Ideally, this would be >= 0, but if we remove an explicit cast, then we might call the wrong method.
            if (expectedRawJavaType.compareAllPriorityTo(providedRawJavaType) == 0) {
                return expression;
            }
            return new CastExpression(new InferredJavaType(expectedRawJavaType, InferredJavaType.Source.EXPRESSION, true), expression);
        }

    }

    public Dumper dumpAppropriatelyCastedArgumentString(Expression expression, int argidx, Dumper d) {
        return expression.dump(d);
    }
//    // Saves us using the above if we don't need to create the cast expression.
//    public Dumper dumpAppropriatelyCastedArgumentString(Expression expression, int argidx, Dumper d) {
//        JavaTypeInstance type = args.get(argidx);
//        if (type.isComplexType()) {
//            return expression.dump(d);
//        } else {
//            RawJavaType expectedRawJavaType = type.getRawTypeOfSimpleType();
//            RawJavaType providedRawJavaType = expression.getInferredJavaType().getRawType();
//            // Ideally, this would be >= 0, but if we remove an explicit cast, then we might call the wrong method.
//            if (expectedRawJavaType.compareAllPriorityTo(providedRawJavaType) == 0) {
//                return expression.dump(d);
//            }
//            return d.print("(" + expectedRawJavaType.getCastString() + ")").dump(expression);
//        }
//    }


    public void tightenArgs(Expression object, List<Expression> expressions) {
        if (expressions.size() != args.size()) {
            throw new ConfusedCFRException("expr arg size mismatch");
        }
        if (object != null && classFile != null && !MiscConstants.INIT_METHOD.equals(name)) {
            object.getInferredJavaType().noteUseAs(classFile.getClassType());
        }
        int length = args.size();
        for (int x = 0; x < length; ++x) {
            Expression expression = expressions.get(x);
            JavaTypeInstance type = args.get(x);
            expression.getInferredJavaType().useAsWithoutCasting(type);
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

        // TODO : Actually, really dislike tryFindBinding, replace.
        GenericTypeBinder genericTypeBinder = GenericTypeBinder.createEmpty();
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

    private JavaTypeInstance getResultBoundAccordingly(JavaTypeInstance result, ClassSignature classSignature, JavaGenericRefTypeInstance boundInstance, List<Expression> invokingArgs) {
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
        GenericTypeBinder genericTypeBinder = GenericTypeBinder.bind(formalTypeParameters, classSignature, args, boundInstance, invokingTypes);

        JavaGenericBaseInstance genericResult = (JavaGenericBaseInstance) result;
        return genericResult.getBoundInstance(genericTypeBinder);
    }

    /*
     * I don't want this to be complete equality, so let's not call it that.
     */
    public boolean equalsMatch(MethodPrototype other) {
        if (other == this) return true;
        if (other == null) return false;
        if (!name.equals(other.name)) return false;
        List<JavaTypeInstance> otherArgs = other.getArgs();
        if (!args.equals(otherArgs)) return false;
        return true;
    }

}
