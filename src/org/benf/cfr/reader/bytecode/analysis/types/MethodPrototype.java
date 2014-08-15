package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
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
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.output.CommaHelp;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MethodPrototype implements TypeUsageCollectable {
    private final List<FormalTypeParameter> formalTypeParameters;
    private final List<JavaTypeInstance> args;
    private final Set<Integer> hidden = SetFactory.newSet();
    private JavaTypeInstance result;
    private final VariableNamer variableNamer;
    private final boolean instanceMethod;
    private final boolean varargs;
    private final String name;
    private final ClassFile classFile;
    private final Method.MethodConstructor constructorFlag;
    // Synthetic args are arguments which are not VISIBLY present in the method prototype at all, but
    // are nonetheless used by the method body.
    private final List<Slot> syntheticArgs = ListFactory.newList();
    private transient List<LocalVariable> parameterLValues = null;

    public MethodPrototype(ClassFile classFile, JavaTypeInstance classType, String name, boolean instanceMethod, Method.MethodConstructor constructorFlag, List<FormalTypeParameter> formalTypeParameters, List<JavaTypeInstance> args, JavaTypeInstance result, boolean varargs, VariableNamer variableNamer, boolean synthetic) {
        this.formalTypeParameters = formalTypeParameters;
        this.instanceMethod = instanceMethod;
        this.constructorFlag = constructorFlag;
        /*
         * We add a fake String and Int argument onto NON SYNTHETIC methods.
         * Can't add onto synthetic methods, as they don't get mutilated to have their args removed.
         *
         * Strictly speaking, we should check to see if this is a forwarding method, not just check the synthetic flag.
         */
        if (constructorFlag.equals(Method.MethodConstructor.ENUM_CONSTRUCTOR) && !synthetic) {
            List<JavaTypeInstance> args2 = ListFactory.newList();
            args2.add(TypeConstants.STRING);
            args2.add(RawJavaType.INT);
            args2.addAll(args);
            hide(0);
            hide(1);

            args = args2;
        }

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
        this.classFile = classFile;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(result);
        collector.collect(args);
        collector.collectFrom(formalTypeParameters);
    }

    public void hide(int x) {
        hidden.add(x);
    }

    public boolean isHiddenArg(int x) {
        return hidden.contains(x);
    }

    public void dumpDeclarationSignature(Dumper d, String methName, Method.MethodConstructor isConstructor, MethodPrototypeAnnotationsHelper annotationsHelper) {

        if (formalTypeParameters != null) {
            d.print('<');
            boolean first = true;
            for (FormalTypeParameter formalTypeParameter : formalTypeParameters) {
                first = CommaHelp.comma(first, d);
                d.dump(formalTypeParameter);
            }
            d.print("> ");
        }
        if (!isConstructor.isConstructor()) {
            d.dump(result).print(" ");
        }
        d.print(methName).print("(");
        /* We don't get a vararg type to change itself, as it's a function of the method, not the type
         */

        List<LocalVariable> parameterLValues = getComputedParameters();
        int argssize = args.size();
        if (parameterLValues.size() != args.size()) {
            int x = 1;
        }
        boolean first = true;
        for (int i = 0; i < argssize; ++i) {
            JavaTypeInstance arg = args.get(i);
            if (hidden.contains(i)) continue;
            first = CommaHelp.comma(first, d);
            annotationsHelper.addAnnotationTextForParameterInto(i, d);
            if (varargs && (i == argssize - 1)) {
                if (!(arg instanceof JavaArrayTypeInstance)) {
                    throw new ConfusedCFRException("VARARGS method doesn't have an array as last arg!!");
                }
                ((JavaArrayTypeInstance) arg).toVarargString(d);
            } else {
                d.dump(arg);
            }
            d.print(" ").dump(parameterLValues.get(i).getName());
        }
        d.print(")");
    }

    public boolean parametersComputed() {
        return parameterLValues != null;
    }

    public List<LocalVariable> getComputedParameters() {
        if (parameterLValues == null) {
            throw new IllegalStateException("Parameters not created");
        }
        return parameterLValues;
    }

    public void setSyntheticConstructorParameters(Method.MethodConstructor constructorFlag, DecompilerComments comments, Map<Integer, JavaTypeInstance> synthetics) {
        syntheticArgs.clear();

        int offset = 0;
        switch (constructorFlag) {
            case ENUM_CONSTRUCTOR: {
                offset = 3;
                break;
            }
            default: {
                if (isInstanceMethod()) offset = 1;
            }
        }

        List<Slot> tmp = ListFactory.newList();
        for (Map.Entry<Integer, JavaTypeInstance> entry : synthetics.entrySet()) {
            tmp.add(new Slot(entry.getValue(), entry.getKey()));
        }

        if (!tmp.isEmpty()) {
            Slot test = tmp.get(0);
            if (offset != test.getIdx()) {
                /*
                 * Synthetics have come through out of location - we have to realign.
                 *
                 * The problem here is that we have a constructor signature which does not match
                 * the usages of the parameters.  See PluginRunner (in CFR!) for an example.
                 */
                List<Slot> replacements = ListFactory.newList();
                for (Slot synthetic : tmp) {
                    JavaTypeInstance type = synthetic.getJavaTypeInstance();
                    Slot replacement = new Slot(type, offset);
                    offset += type.getStackType().getComputationCategory();
                    replacements.add(replacement);
                }
                syntheticArgs.addAll(replacements);
                comments.addComment(DecompilerComment.PARAMETER_CORRUPTION);
            } else {
                syntheticArgs.addAll(tmp);
            }
        }
    }

//    public List<Slot> getSyntheticArgs() {
//        return syntheticArgs;
//    }

    public Map<Slot, SSAIdent> collectInitialSlotUsage(Method.MethodConstructor constructorFlag, SSAIdentifierFactory<Slot> ssaIdentifierFactory) {
        Map<Slot, SSAIdent> res = MapFactory.newLinkedMap();
        int offset = 0;
        switch (constructorFlag) {
//            case ENUM_CONSTRUCTOR: {
////                Slot tgt0 = new Slot(classFile.getClassType(), 0);
////                res.put(tgt0, ssaIdentifierFactory.getIdent(tgt0));
////                Slot tgt1 = new Slot(RawJavaType.REF, 1);
////                res.put(tgt1, ssaIdentifierFactory.getIdent(tgt1));
////                Slot tgt2 = new Slot(RawJavaType.INT, 2);
////                res.put(tgt2, ssaIdentifierFactory.getIdent(tgt2));
////                offset = 3;
//                break;
//            }
            default: {
                if (instanceMethod) {
                    Slot tgt = new Slot(classFile.getClassType(), 0);
                    res.put(tgt, ssaIdentifierFactory.getIdent(tgt));
                    offset = 1;
                }
                break;
            }
        }
        if (!syntheticArgs.isEmpty()) {
            for (Slot synthetic : syntheticArgs) {
                if (offset != synthetic.getIdx()) {
                    throw new IllegalStateException("Synthetic arg - offset is " + offset + ", but got " + synthetic.getIdx());
                }
                res.put(synthetic, ssaIdentifierFactory.getIdent(synthetic));
                offset += synthetic.getJavaTypeInstance().getStackType().getComputationCategory();
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
            return parameterLValues;
        }

        parameterLValues = ListFactory.newList();
        int offset = 0;
        if (instanceMethod) {
            variableNamer.forceName(slotToIdentMap.get(0), 0, MiscConstants.THIS);
            offset = 1;
        }
        if (constructorFlag == Method.MethodConstructor.ENUM_CONSTRUCTOR) {
//            parameterLValues.add(new LocalVariable(offset, new Ident(offset, 0), variableNamer, offset, new InferredJavaType(TypeConstants.STRING, InferredJavaType.Source.UNKNOWN, true), false));
//            offset++;
//            hide(0);
//            parameterLValues.add(new LocalVariable(offset, new Ident(offset, 0), variableNamer, offset, new InferredJavaType(RawJavaType.INT, InferredJavaType.Source.UNKNOWN, true), false));
//            offset++;
//            hide(1);
        } else {
            // TODO : It's not a valid assumption that synthetic args are at the front!
            for (Slot synthetic : syntheticArgs) {
                JavaTypeInstance typeInstance = synthetic.getJavaTypeInstance();
                parameterLValues.add(new LocalVariable(offset, slotToIdentMap.get(synthetic.getIdx()), variableNamer, 0, new InferredJavaType(typeInstance, InferredJavaType.Source.FIELD, true)));
                offset += typeInstance.getStackType().getComputationCategory();
            }
        }

        for (JavaTypeInstance arg : args) {
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

    public JavaTypeInstance getClassType() {
        if (classFile == null) return null;
        return classFile.getClassType();
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
            }

            /*
             * Now we need to specialise the method according to the existing specialisation on
             * the instance.
             *
             * i.e. given that genericRefTypeInstance has the correct bindings, apply those to method.
             */
            JavaTypeInstance boundResult = getResultBoundAccordingly(result, genericRefTypeInstance, invokingArgs);
            /*
             * If there are any parameters in this binding which are method parameters, this means we've been unable
             * to correctly bind them.  We can't leave them in, it'll confuse the issue.
             */
            return boundResult;
        } else {
            return result;
        }
    }

    public List<JavaTypeInstance> getArgs() {
        return args;
    }

    public int getVisibleArgCount() {
        return args.size() - hidden.size();
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

    public void addExplicitCasts(Expression object, List<Expression> expressions) {
        int length = expressions.size();

        GenericTypeBinder genericTypeBinder = null;
        if (object != null && object.getInferredJavaType().getJavaTypeInstance() instanceof JavaGenericBaseInstance) {
            List<JavaTypeInstance> invokingTypes = ListFactory.newList();
            for (Expression invokingArg : expressions) {
                invokingTypes.add(invokingArg.getInferredJavaType().getJavaTypeInstance());
            }

            /*
             * For each of the formal type parameters of the class signature, what has it been bound to in the
             * instance?
             */
            JavaGenericRefTypeInstance boundInstance = (object instanceof JavaGenericRefTypeInstance) ? (JavaGenericRefTypeInstance) object : null;
            if (classFile != null) {
                genericTypeBinder = GenericTypeBinder.bind(formalTypeParameters, classFile.getClassSignature(), args, boundInstance, invokingTypes);
            }
        }

        /*
         * And then (daft, I know) place an explicit cast infront of the arg.  These will get stripped out later
         * IF that's appropriate.
         */
        for (int x = 0; x < length; ++x) {
            Expression expression = expressions.get(x);
            JavaTypeInstance type = args.get(x);
            //
            // But... we can't put a cast infront of it to arg type if it's a generic.
            // Otherwise we lose type propagation information.
            // AARGH.
            JavaTypeInstance exprType = expression.getInferredJavaType().getJavaTypeInstance();
            if (isGenericArg(exprType)) {
                continue;
            }
            if (genericTypeBinder != null) {
                type = genericTypeBinder.getBindingFor(type);
            }
            if (isGenericArg(type)) {
                continue;
            }
            expressions.set(x, new CastExpression(new InferredJavaType(type, InferredJavaType.Source.FUNCTION, true), expression));
        }
    }

    private static boolean isGenericArg(JavaTypeInstance arg) {
        arg = arg.getArrayStrippedType();
        if (arg instanceof JavaGenericBaseInstance) return true;
        return false;
    }

    public String getComparableString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append('(');
        for (JavaTypeInstance arg : args) {
            sb.append(arg.getRawName()).append(" ");
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public String toString() {
        return getComparableString();
    }

    public boolean equalsGeneric(MethodPrototype other) {
        GenericTypeBinder genericTypeBinder = GenericTypeBinder.createEmpty();
        // TODO : This needs a bit of work ... (!)
        // TODO : Will return false positives at the moment.
        return equalsGeneric(other, genericTypeBinder);
    }

    public boolean equalsGeneric(MethodPrototype other, GenericTypeBinder genericTypeBinder) {
        List<FormalTypeParameter> otherTypeParameters = other.formalTypeParameters;
        List<JavaTypeInstance> otherArgs = other.args;

        if (otherArgs.size() != args.size()) {
            return false;
        }

        // TODO : Actually, really dislike tryFindBinding, replace.
        for (int x = 0; x < args.size(); ++x) {
            JavaTypeInstance lhs = args.get(x);
            JavaTypeInstance rhs = otherArgs.get(x);
            JavaTypeInstance deGenerifiedLhs = lhs.getDeGenerifiedType();
            JavaTypeInstance deGenerifiedRhs = rhs.getDeGenerifiedType();
            if (!deGenerifiedLhs.equals(deGenerifiedRhs)) {
                // TODO : This doesn't feel like it's right.  Rethink.
                // This will match if it COULD bind, not if it does.
                if (lhs instanceof JavaGenericBaseInstance) {
                    if (!((JavaGenericBaseInstance) lhs).tryFindBinding(rhs, genericTypeBinder)) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    /*
     * These are the /exact/ types of the arguments, not the possible supers.
     */
    public GenericTypeBinder getTypeBinderForTypes(List<JavaTypeInstance> invokingArgTypes) {
        if (classFile == null) {
            return null;
        }

        /*
         * For each of the formal type parameters of the class signature, what has it been bound to in the
         * instance?
         */
        if (invokingArgTypes.size() != args.size()) {
            return null;
        }

        GenericTypeBinder genericTypeBinder = GenericTypeBinder.bind(formalTypeParameters, classFile.getClassSignature(), args, null, invokingArgTypes);
        return genericTypeBinder;
    }

    public GenericTypeBinder getTypeBinderFor(List<Expression> invokingArgs) {

        List<JavaTypeInstance> invokingTypes = ListFactory.newList();
        for (Expression invokingArg : invokingArgs) {
            invokingTypes.add(invokingArg.getInferredJavaType().getJavaTypeInstance());
        }
        return getTypeBinderForTypes(invokingTypes);
    }

    private JavaTypeInstance getResultBoundAccordingly(JavaTypeInstance result, JavaGenericRefTypeInstance boundInstance, List<Expression> invokingArgs) {
        if (result instanceof JavaArrayTypeInstance) {
            JavaArrayTypeInstance arrayTypeInstance = (JavaArrayTypeInstance) result;
            JavaTypeInstance stripped = result.getArrayStrippedType();
            JavaTypeInstance tmp = getResultBoundAccordinglyInner(stripped, boundInstance, invokingArgs);
            if (tmp == stripped) return result;
            return new JavaArrayTypeInstance(arrayTypeInstance.getNumArrayDimensions(), tmp);
        } else {
            return getResultBoundAccordinglyInner(result, boundInstance, invokingArgs);
        }
    }

    private JavaTypeInstance getResultBoundAccordinglyInner(JavaTypeInstance result, JavaGenericRefTypeInstance boundInstance, List<Expression> invokingArgs) {
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
        GenericTypeBinder genericTypeBinder = GenericTypeBinder.bind(formalTypeParameters, classFile.getClassSignature(), args, boundInstance, invokingTypes);
        if (genericTypeBinder == null) {
            return result;
        }

        JavaGenericBaseInstance genericResult = (JavaGenericBaseInstance) result;
        JavaTypeInstance boundResultInstance = genericResult.getBoundInstance(genericTypeBinder);
        /*
         * This is a result type - if it contains an unbound wildcard, we have to strip it.
         */
        if (boundResultInstance instanceof JavaWildcardTypeInstance) {
            boundResultInstance = ((JavaWildcardTypeInstance) boundResultInstance).getUnderlyingType();
        }
        return boundResultInstance;
    }


    public boolean isVarArgs() {
        return varargs;
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
