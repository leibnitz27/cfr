package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.variables.Ident;
import org.benf.cfr.reader.bytecode.analysis.variables.Slot;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableFactory;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableNamerDefault;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.ArrayVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackEntry;
import org.benf.cfr.reader.bytecode.analysis.stack.StackEntryHolder;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.bytecode.opcode.DecodedLookupSwitch;
import org.benf.cfr.reader.bytecode.opcode.DecodedTableSwitch;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.bytecode.opcode.OperationFactoryMultiANewArray;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.bootstrap.BootstrapMethodInfo;
import org.benf.cfr.reader.entities.bootstrap.MethodHandleBehaviour;
import org.benf.cfr.reader.entities.constantpool.*;
import org.benf.cfr.reader.entities.exceptions.ExceptionAggregator;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageInformationEmpty;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.bytestream.BaseByteData;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;
import org.benf.cfr.reader.util.graph.GraphVisitorFIFO;
import org.benf.cfr.reader.util.lambda.LambdaUtils;
import org.benf.cfr.reader.util.output.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 09/03/2012
 * Time: 17:49
 * To change this template use File | Settings | File Templates.
 */
public class Op02WithProcessedDataAndRefs implements Dumpable, Graph<Op02WithProcessedDataAndRefs> {
    private static final Logger logger = LoggerFactory.create(Op02WithProcessedDataAndRefs.class);

    private InstrIndex index;

    private JVMInstr instr;
    private final int originalRawOffset;
    private final byte[] rawData;

    private List<BlockIdentifier> containedInTheseBlocks = ListFactory.newList();
    private List<ExceptionGroup> exceptionGroups = ListFactory.newList();
    private List<ExceptionGroup.Entry> catchExceptionGroups = ListFactory.newList();

    private final List<Op02WithProcessedDataAndRefs> targets = ListFactory.newList();
    private final List<Op02WithProcessedDataAndRefs> sources = ListFactory.newList();
    private final ConstantPool cp;
    private final ConstantPoolEntry[] cpEntries;
    private long stackDepthBeforeExecution = -1;
    private long stackDepthAfterExecution;
    private final List<StackEntryHolder> stackConsumed = ListFactory.newList();
    private final List<StackEntryHolder> stackProduced = ListFactory.newList();
    private StackSim unconsumedJoinedStack = null;

    private SSAIdentifiers<Slot> ssaIdentifiers;
    private Map<Integer, Ident> localVariablesBySlot = MapFactory.newLinkedMap();

    private Op02WithProcessedDataAndRefs(Op02WithProcessedDataAndRefs other) {
        this.instr = other.instr;
        this.rawData = other.rawData;
        this.index = null;
        this.cp = other.cp;
        this.cpEntries = other.cpEntries;
        this.originalRawOffset = other.originalRawOffset;
    }

    public Op02WithProcessedDataAndRefs(JVMInstr instr, byte[] rawData, int index, ConstantPool cp, ConstantPoolEntry[] cpEntries, int originalRawOffset) {
        this(instr, rawData, new InstrIndex(index), cp, cpEntries, originalRawOffset);
    }

    public Op02WithProcessedDataAndRefs(JVMInstr instr, byte[] rawData, InstrIndex index, ConstantPool cp, ConstantPoolEntry[] cpEntries, int originalRawOffset) {
        this.instr = instr;
        this.rawData = rawData;
        this.index = index;
        this.cp = cp;
        this.cpEntries = cpEntries;
        this.originalRawOffset = originalRawOffset;
    }

    public void resetStackInfo() {
        stackDepthBeforeExecution = -1;
        stackDepthAfterExecution = -1;
        stackConsumed.clear();
        stackProduced.clear();
        unconsumedJoinedStack = null;
    }

    public InstrIndex getIndex() {
        return index;
    }

    public void setIndex(InstrIndex index) {
        this.index = index;
    }

    public void addTarget(Op02WithProcessedDataAndRefs node) {
        targets.add(node);
    }

    public void removeTarget(Op02WithProcessedDataAndRefs node) {
        if (!targets.remove(node)) {
            throw new ConfusedCFRException("Invalid target, tried to remove " + node + "\nfrom " + this + "\nbut was not a target.");
        }
    }

    public void addSource(Op02WithProcessedDataAndRefs node) {
        sources.add(node);
    }

    public JVMInstr getInstr() {
        return instr;
    }

    public void replaceTarget(Op02WithProcessedDataAndRefs oldTarget, Op02WithProcessedDataAndRefs newTarget) {
        int index = targets.indexOf(oldTarget);
        if (index == -1) throw new ConfusedCFRException("Invalid target");
        targets.set(index, newTarget);
    }

    public void replaceSource(Op02WithProcessedDataAndRefs oldSource, Op02WithProcessedDataAndRefs newSource) {
        int index = sources.indexOf(oldSource);
        if (index == -1) throw new ConfusedCFRException("Invalid source");
        sources.set(index, newSource);
    }

    public void removeSource(Op02WithProcessedDataAndRefs oldSource) {
        if (!sources.remove(oldSource)) {
            throw new ConfusedCFRException("Invalid source");
        }
    }

    public void clearSources() {
        sources.clear();
    }
    /*
    public int getIndex() {
        return index;
    }

    public int getSubIndex() {
        return subindex;
    }
    */

    private int getInstrArgByte(int index) {
        return rawData[index];
    }

    private int getInstrArgShort(int index) {
        BaseByteData tmp = new BaseByteData(rawData);
        return tmp.getS2At(index);
    }

    @Override
    public List<Op02WithProcessedDataAndRefs> getTargets() {
        return targets;
    }

    @Override
    public List<Op02WithProcessedDataAndRefs> getSources() {
        return sources;
    }

    public void populateStackInfo(StackSim stackSim, Method method) {
        StackDelta stackDelta = instr.getStackDelta(rawData, cpEntries, stackSim, method);
        if (stackDepthBeforeExecution != -1) {
            /* Catch instructions are funny, as we know we'll get here with 1 thing on the stack. */
            if (instr == JVMInstr.FAKE_CATCH) {
                return;
            }

            if (stackSim.getDepth() != stackDepthBeforeExecution) {
                throw new ConfusedCFRException("Invalid stack depths @ " + this + " : trying to set " + stackSim.getDepth() + " previously set to " + stackDepthBeforeExecution);
            }


            List<StackEntryHolder> alsoConsumed = ListFactory.newList();
            List<StackEntryHolder> alsoProduced = ListFactory.newList();
            StackSim newStackSim = stackSim.getChange(stackDelta, alsoConsumed, alsoProduced);
            if (alsoConsumed.size() != stackConsumed.size()) {
                throw new ConfusedCFRException("Unexpected stack sizes on merge");
            }
            for (int i = 0; i < stackConsumed.size(); ++i) {
                stackConsumed.get(i).mergeWith(alsoConsumed.get(i));
            }
            /*
             * If unconsumed joined stack is set, see below, we must be merging something this instruction doesn't
             * know about.
             */
            if (unconsumedJoinedStack != null) {
                // Need to take the unconsumedJoinedStack, ignore the
                long depth = unconsumedJoinedStack.getDepth() - alsoProduced.size();
                List<StackEntryHolder> unconsumedEntriesOld = unconsumedJoinedStack.getHolders(alsoProduced.size(), depth);
                List<StackEntryHolder> unconsumedEntriesNew = newStackSim.getHolders(alsoProduced.size(), depth);
                for (int i = 0; i < unconsumedEntriesOld.size(); ++i) {
                    unconsumedEntriesOld.get(i).mergeWith(unconsumedEntriesNew.get(i));
                }
            }

        } else {

            if (instr == JVMInstr.FAKE_CATCH) {
                this.stackDepthBeforeExecution = 0;
            } else {
                this.stackDepthBeforeExecution = stackSim.getDepth();
            }
            this.stackDepthAfterExecution = stackDepthBeforeExecution + stackDelta.getChange();

            StackSim newStackSim = stackSim.getChange(stackDelta, stackConsumed, stackProduced);

            if (this.sources.size() > 1 && newStackSim.getDepth() > stackProduced.size()) {
                // We're merging stacks here, and haven't consumed everything from the branch we came
                // in on.
                //
                // eg f(a == 3 ? 1 : 0, 2) // the push for the 2 sees the merge, but doesn't consume either.
                //
                // This will potentially contain uneccessary references to BEFORE the stacks diverged.
                // TODO: eliminate this - I can see how an obfuscator would use that....
                this.unconsumedJoinedStack = newStackSim;
            }

            for (Op02WithProcessedDataAndRefs target : targets) {
                target.populateStackInfo(newStackSim, method);
            }
        }
    }

    public ExceptionGroup getSingleExceptionGroup() {
        if (exceptionGroups.size() != 1) {
            throw new ConfusedCFRException("Only expecting statement to be tagged with 1 exceptionGroup");
        }
        return exceptionGroups.iterator().next();
    }

    @Override
    public Dumper dump(Dumper d) {
        for (BlockIdentifier blockIdentifier : containedInTheseBlocks) {
            d.print(" " + blockIdentifier);
        }
        d.print(" " + index + " (" + originalRawOffset + ") : " + instr + "\t Stack:" + stackDepthBeforeExecution + "\t");
        d.print("Consumes:[");
        for (StackEntryHolder stackEntryHolder : stackConsumed) {
            d.print(stackEntryHolder.toString() + " ");
        }
        d.print("] Produces:[");
        for (StackEntryHolder stackEntryHolder : stackProduced) {
            d.print(stackEntryHolder.toString() + " ");
        }
        d.print("] sources ");
        for (Op02WithProcessedDataAndRefs source : sources) {
            d.print(" " + source.index);
        }
        d.print(" targets ");
        for (Op02WithProcessedDataAndRefs target : targets) {
            d.print(" " + target.index);
        }
        d.print("\n");
        return d;
    }

    private Statement buildInvoke(Method thisCallerMethod) {
        ConstantPoolEntryMethodRef function = (ConstantPoolEntryMethodRef) cpEntries[0];
        StackValue object = getStackRValue(stackConsumed.size() - 1);
                /*
                 * See above re invokespecial
                 */
        boolean special = false;
        boolean isSuper = false;
        if (instr == JVMInstr.INVOKESPECIAL) {
            // todo: Verify that the class being called is the super of the object.
            special = true;
            if (!thisCallerMethod.testAccessFlag(AccessFlagMethod.ACC_STATIC)) {
                JavaTypeInstance objType = object.getInferredJavaType().getJavaTypeInstance();
                JavaTypeInstance callType = function.getClassEntry().getTypeInstance();
                ConstantPoolEntryNameAndType nameAndType = function.getNameAndTypeEntry();
                String funcName = nameAndType.getName().getValue();
                boolean typesMatch = callType.equals(objType);
                if (funcName.equals(MiscConstants.INIT_METHOD)) {
                    if (!(typesMatch || objType.getRawName().equals("java.lang.Object"))) {
                        isSuper = true;
                    }
                } else {
                    // TODO : FIXME - this logic is overcomplicated - probably wrong.
                    if (!typesMatch) isSuper = true;
                }
            }
        }
        MethodPrototype methodPrototype = function.getMethodPrototype();
        List<Expression> args = getNStackRValuesAsExpressions(stackConsumed.size() - 1);
        /*
         * Use information about arguments to help us deduce lValue types.
         */
        methodPrototype.tightenArgs(object, args);
        methodPrototype.addExplicitCasts(object, args);
        AbstractFunctionInvokation funcCall = isSuper ?
                new SuperFunctionInvokation(cp, function, methodPrototype, object, args) :
                new MemberFunctionInvokation(cp, function, methodPrototype, object, special, args);
        /*
         * And, while we're at it, does this give us better information about the object?
         * ... But - this guess could be too specific.  If we have an untyped map, eg, this will bind
         * the first seen arg types (which is wrong).
         */
//        if (object != null) {
//            JavaTypeInstance objectType = object.getInferredJavaType().getJavaTypeInstance();
//            if (objectType instanceof JavaGenericBaseInstance) {
//                if (((JavaGenericBaseInstance) objectType).hasUnbound()) {
//                    GenericTypeBinder typeBinder = methodPrototype.getTypeBinderFor(args);
//                    JavaTypeInstance boundObjectType = typeBinder.getBindingFor(objectType);
//                    if (boundObjectType != null) {
//                        object.getInferredJavaType().deGenerify(boundObjectType);
//                    }
//                }
//            }
//        }
        if (!isSuper && function.isInitMethod()) {
            return new ConstructorStatement((MemberFunctionInvokation) funcCall);
        } else {
            if (stackProduced.size() == 0) {
                return new ExpressionStatement(funcCall);
            } else {
                return new AssignmentSimple(getStackLValue(0), funcCall);
            }
        }
    }


    private Statement buildInvokeDynamic(Method method, DCCommonState dcCommonState) {
        ConstantPoolEntryInvokeDynamic invokeDynamic = (ConstantPoolEntryInvokeDynamic) cpEntries[0];

        ConstantPoolEntryNameAndType nameAndType = invokeDynamic.getNameAndTypeEntry();

        // Should have this as a member on name and type
        ConstantPoolEntryUTF8 descriptor = nameAndType.getDescriptor();
        // Todo : Not happy about hardcoding if this is an instance function.
        // also - we have a descriptor, but NOT a signature here.  Is that right?
        MethodPrototype dynamicPrototype = ConstantPoolUtils.parseJavaMethodPrototype(null, null, "", false, descriptor, cp, false, new VariableNamerDefault());

        int idx = invokeDynamic.getBootstrapMethodAttrIndex();

        BootstrapMethodInfo bootstrapMethodInfo = method.getClassFile().getBootstrapMethods().getBootStrapMethodInfo(idx);
        ConstantPoolEntryMethodRef methodRef = bootstrapMethodInfo.getConstantPoolEntryMethodRef();
        MethodPrototype prototype = methodRef.getMethodPrototype();
        MethodHandleBehaviour bootstrapBehaviour = bootstrapMethodInfo.getMethodHandleBehaviour();
        String methodName = methodRef.getName();

        DynamicInvokeType dynamicInvokeType = DynamicInvokeType.lookup(methodName);

        if (dynamicInvokeType == DynamicInvokeType.UNKNOWN) {
            throw new IllegalStateException("MetaFactory usage [" + methodName + "] not recognised.");
        }

        List<Expression> callargs;
        switch (dynamicInvokeType) {
            case METAFACTORY_1:
            case METAFACTORY_2:
                callargs = buildInvokeDynamicMetaFactoryArgs(prototype, dynamicPrototype, bootstrapBehaviour, bootstrapMethodInfo, methodRef);
                break;
            case ALTMETAFACTORY_1:
            case ALTMETAFACTORY_2:
                callargs = buildInvokeDynamicAltMetaFactoryArgs(prototype, dynamicPrototype, bootstrapBehaviour, bootstrapMethodInfo, methodRef);
                break;
            default:
                throw new IllegalStateException();
        }
        Expression strippedType = callargs.get(3);
        Expression instantiatedType = callargs.get(5);

        /*
         * Try to determine the relevant method on the functional interface.
         */
        JavaTypeInstance callSiteReturnType = dynamicPrototype.getReturnType();
        callSiteReturnType = determineDynamicGeneric(callSiteReturnType, dynamicPrototype, strippedType, instantiatedType, dcCommonState);

        List<Expression> dynamicArgs = getNStackRValuesAsExpressions(stackConsumed.size());
        dynamicPrototype.tightenArgs(null, dynamicArgs);
        dynamicPrototype.addExplicitCasts(null, dynamicArgs); // todo - useful?
        Expression funcCall = null;
        switch (bootstrapBehaviour) {
            case INVOKE_STATIC:
                funcCall = new StaticFunctionInvokation(methodRef, callargs);
                break;
            case NEW_INVOKE_SPECIAL:
            default:
                throw new UnsupportedOperationException("Only static invoke dynamic calls supported currently. This is " + bootstrapBehaviour);
        }

        funcCall = new DynamicInvokation(new InferredJavaType(callSiteReturnType, InferredJavaType.Source.OPERATION), funcCall, dynamicArgs);
        if (stackProduced.size() == 0) {
            return new ExpressionStatement(funcCall);
        } else {
            return new AssignmentSimple(getStackLValue(0), funcCall);
        }
    }


    private JavaTypeInstance determineDynamicGeneric(final JavaTypeInstance callsiteReturn, MethodPrototype proto, Expression stripped, Expression instantiated, DCCommonState dcCommonState) {

        ClassFile classFile = null;
        try {
            classFile = dcCommonState.getClassFile(proto.getReturnType());
        } catch (CannotLoadClassException e) {
        }
        if (classFile == null) return callsiteReturn;

        // Note - we need to examine the methods, but NOT look at their code.
        List<Method> methods = Functional.filter(classFile.getMethods(), new Predicate<Method>() {
            @Override
            public boolean test(Method in) {
                return !in.hasCodeAttribute();
            }
        });
        if (methods.size() != 1) return callsiteReturn;
        Method method = methods.get(0);
        MethodPrototype genericProto = method.getMethodPrototype();

        MethodPrototype boundProto = LambdaUtils.getLiteralProto(instantiated);
        GenericTypeBinder gtb = genericProto.getTypeBinderForTypes(boundProto.getArgs());

        JavaTypeInstance unboundReturn = genericProto.getReturnType();
        JavaTypeInstance boundReturn = boundProto.getReturnType();
        if (unboundReturn instanceof JavaGenericBaseInstance) {
            GenericTypeBinder gtb2 = GenericTypeBinder.extractBindings((JavaGenericBaseInstance) unboundReturn, boundReturn);
            gtb = gtb.mergeWith(gtb2, true);
        }

        JavaTypeInstance classType = classFile.getClassType();
        BindingSuperContainer b = classFile.getBindingSupers();
        classType = b.getBoundSuperForBase(classType);
        if (classType == null) return callsiteReturn;

        if (!callsiteReturn.getDeGenerifiedType().equals(classType.getDeGenerifiedType())) {
            // Something's gone wrong.
            return callsiteReturn;
        }

        JavaTypeInstance alternateCallSite = gtb.getBindingFor(classType);
        return alternateCallSite;
    }

    private static TypedLiteral getBootstrapArg(ConstantPoolEntry[] bootstrapArguments, int x, ConstantPool cp) {
        ConstantPoolEntry entry = bootstrapArguments[x];
        TypedLiteral typedLiteral = TypedLiteral.getConstantPoolEntry(cp, entry);
        return typedLiteral;
    }

    private List<Expression> buildInvokeDynamicAltMetaFactoryArgs(MethodPrototype prototype, MethodPrototype dynamicPrototype, MethodHandleBehaviour bootstrapBehaviour, BootstrapMethodInfo bootstrapMethodInfo, ConstantPoolEntryMethodRef methodRef) {

        /*
         * First 3 arguments to an invoke dynamic are stacked automatically by the JVM.
         *  MethodHandles.Lookup caller,
         *  String invokedName,
         *  MethodType invokedType,
         *
         * then we have
         * Object ... args
         *
         * Alternate meta-factory for conversion of lambda expressions or method references to functional interfaces,
         * which supports serialization and other uncommon options. The declared argument list for this method is:
         * CallSite altMetafactory(MethodHandles.Lookup caller, String invokedName, MethodType invokedType, Object... args)
         * but it behaves as if the argument list is: CallSite altMetafactory(MethodHandles.Lookup caller,
         * String invokedName, MethodType invokedType, MethodType samMethodType MethodHandle implMethod,
         * MethodType instantiatedMethodType, int flags,
         * IF flags has MARKERS set - int markerInterfaceCount,
         * IF flags has MARKERS set - Class... markerInterfaces
         * IF flags has BRIDGES set - int bridgeCount,
         * IF flags has BRIDGES set - MethodType... bridges )
         */
        List<JavaTypeInstance> argTypes = prototype.getArgs();
        ConstantPoolEntry[] bootstrapArguments = bootstrapMethodInfo.getBootstrapArguments();
        if (bootstrapArguments.length < 4) {
            throw new IllegalStateException("Dynamic invoke arg count mismatch ");
        }

        List<Expression> callargs = ListFactory.newList();
        Expression nullExp = new Literal(TypedLiteral.getNull());
        callargs.add(nullExp);
        callargs.add(nullExp);
        callargs.add(nullExp);

        /*
         * We can't really verify the bootstrap args against the arg type, as it's weak. (Object ... ).
         */
        TypedLiteral tlMethodType = getBootstrapArg(bootstrapArguments, 0, cp);
        TypedLiteral tlImplMethod = getBootstrapArg(bootstrapArguments, 1, cp);
        TypedLiteral tlInstantiatedMethodType = getBootstrapArg(bootstrapArguments, 2, cp);
        TypedLiteral flags = getBootstrapArg(bootstrapArguments, 3, cp);

        callargs.add(new Literal(tlMethodType));
        callargs.add(new Literal(tlImplMethod));
        callargs.add(new Literal(tlInstantiatedMethodType));


        /*
         * We slightly lie about the dynamic arguments, currently, by putting them in a structure which is
         * invalid java.  The alternative is to explicitly return a callsite, and call that, but that's
         * needless complexity, which we're going to unwind back into a lambda or the like as soon as possible
         * anyway,
         */
        return callargs;
    }


    private List<Expression> buildInvokeDynamicMetaFactoryArgs(MethodPrototype prototype, MethodPrototype dynamicPrototype, MethodHandleBehaviour bootstrapBehaviour, BootstrapMethodInfo bootstrapMethodInfo, ConstantPoolEntryMethodRef methodRef) {

        final int ARG_OFFSET = 3;
        /*
         * First 3 arguments to an invoke dynamic are stacked automatically by the JVM.
         *  MethodHandles.Lookup caller,
         *  String invokedName,
         *  MethodType invokedType,
         *
         * [ Guess (vaguely), see LambdaMetaFactory documentation, but it's not clear if that's special case. ]
         *
         * So we expect our prototype to be equal to these 3, plus the arguments from our bootstrap.
         */
        List<JavaTypeInstance> argTypes = prototype.getArgs();
        ConstantPoolEntry[] bootstrapArguments = bootstrapMethodInfo.getBootstrapArguments();
        if ((bootstrapArguments.length + ARG_OFFSET) != argTypes.size()) {
            throw new IllegalStateException("Dynamic invoke arg count mismatch " + bootstrapArguments.length + "(+3) vs " + argTypes.size());
        }

        List<Expression> callargs = ListFactory.newList();
        Expression nullExp = new Literal(TypedLiteral.getNull());
        callargs.add(nullExp);
        callargs.add(nullExp);
        callargs.add(nullExp);

        for (int x = 0; x < bootstrapArguments.length; ++x) {
            JavaTypeInstance expected = argTypes.get(ARG_OFFSET + x);
            TypedLiteral typedLiteral = getBootstrapArg(bootstrapArguments, x, cp);
            if (!expected.equals(typedLiteral.getInferredJavaType().getJavaTypeInstance())) {
                throw new IllegalStateException("Dynamic invoke Expected " + expected + ", got " + typedLiteral);
            }
            callargs.add(new Literal(typedLiteral));
        }

        return callargs;
    }

    private Pair<JavaTypeInstance, Integer> getRetrieveType() {
        JavaTypeInstance type = null;
        switch (instr) {
            case ALOAD:
            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3:
            case ALOAD_WIDE:
                type = RawJavaType.REF;
                break;
            case ILOAD:
            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3:
            case ILOAD_WIDE:
            case IINC:
            case IINC_WIDE:
                type = RawJavaType.INT;
                break;
            case LLOAD:
            case LLOAD_0:
            case LLOAD_1:
            case LLOAD_2:
            case LLOAD_3:
            case LLOAD_WIDE:
                type = RawJavaType.LONG;
                break;
            case DLOAD:
            case DLOAD_0:
            case DLOAD_1:
            case DLOAD_2:
            case DLOAD_3:
            case DLOAD_WIDE:
                type = RawJavaType.DOUBLE;
                break;
            case FLOAD:
            case FLOAD_0:
            case FLOAD_1:
            case FLOAD_2:
            case FLOAD_3:
            case FLOAD_WIDE:
                type = RawJavaType.FLOAT;
                break;
            default:
                return null;
        }
        int idx = 0;
        switch (instr) {
            case ALOAD:
            case ILOAD:
            case LLOAD:
            case DLOAD:
            case FLOAD:
            case IINC:
                idx = getInstrArgByte(0);
                break;
            case ALOAD_0:
            case ILOAD_0:
            case LLOAD_0:
            case DLOAD_0:
            case FLOAD_0:
                idx = 0;
                break;
            case ALOAD_1:
            case ILOAD_1:
            case LLOAD_1:
            case DLOAD_1:
            case FLOAD_1:
                idx = 1;
                break;
            case ALOAD_2:
            case ILOAD_2:
            case LLOAD_2:
            case DLOAD_2:
            case FLOAD_2:
                idx = 2;
                break;
            case ALOAD_3:
            case ILOAD_3:
            case LLOAD_3:
            case DLOAD_3:
            case FLOAD_3:
                idx = 3;
                break;
            case ALOAD_WIDE:
            case ILOAD_WIDE:
            case LLOAD_WIDE:
            case DLOAD_WIDE:
            case FLOAD_WIDE:
                throw new UnsupportedOperationException("LOAD_WIDE");
            default:
                return null;
        }
        return Pair.make(type, idx);
    }

    private Pair<JavaTypeInstance, Integer> getStorageType() {
        JavaTypeInstance type = null;
        switch (instr) {
            case ASTORE:
            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3:
            case ASTORE_WIDE:
                type = RawJavaType.REF;
                break;
            case ISTORE:
            case ISTORE_0:
            case ISTORE_1:
            case ISTORE_2:
            case ISTORE_3:
            case ISTORE_WIDE:
            case IINC:
            case IINC_WIDE:
                type = RawJavaType.INT;
                break;
            case LSTORE:
            case LSTORE_0:
            case LSTORE_1:
            case LSTORE_2:
            case LSTORE_3:
            case LSTORE_WIDE:
                type = RawJavaType.LONG;
                break;
            case DSTORE:
            case DSTORE_0:
            case DSTORE_1:
            case DSTORE_2:
            case DSTORE_3:
            case DSTORE_WIDE:
                type = RawJavaType.DOUBLE;
                break;
            case FSTORE:
            case FSTORE_0:
            case FSTORE_1:
            case FSTORE_2:
            case FSTORE_3:
            case FSTORE_WIDE:
                type = RawJavaType.FLOAT;
                break;
            default:
                return null;
        }
        int idx = 0;
        switch (instr) {
            case ASTORE:
            case ISTORE:
            case LSTORE:
            case DSTORE:
            case FSTORE:
            case IINC:
                idx = getInstrArgByte(0);
                break;
            case ASTORE_0:
            case ISTORE_0:
            case LSTORE_0:
            case DSTORE_0:
            case FSTORE_0:
                idx = 0;
                break;
            case ASTORE_1:
            case ISTORE_1:
            case LSTORE_1:
            case DSTORE_1:
            case FSTORE_1:
                idx = 1;
                break;
            case ASTORE_2:
            case ISTORE_2:
            case LSTORE_2:
            case DSTORE_2:
            case FSTORE_2:
                idx = 2;
                break;
            case ASTORE_3:
            case ISTORE_3:
            case LSTORE_3:
            case DSTORE_3:
            case FSTORE_3:
                idx = 3;
                break;
            case IINC_WIDE:
                idx = getInstrArgShort(1);
                break;
            case ASTORE_WIDE:
            case ISTORE_WIDE:
            case LSTORE_WIDE:
            case DSTORE_WIDE:
            case FSTORE_WIDE:
                throw new UnsupportedOperationException("STORE_WIDE");
            default:
                return null;
        }
        return Pair.make(type, idx);
    }

    private Statement mkAssign(VariableFactory variableFactory) {
        Pair<JavaTypeInstance, Integer> storageTypeAndIdx = getStorageType();
        int slot = storageTypeAndIdx.getSecond();
        Ident ident = localVariablesBySlot.get(slot);

        SSAIdent ssaIdent = ssaIdentifiers.getSSAIdent(new Slot(storageTypeAndIdx.getFirst(), slot));
        AssignmentSimple res = new AssignmentSimple(variableFactory.localVariable(slot, ident, originalRawOffset, ssaIdent.card() == 1), getStackRValue(0));
        if (ssaIdentifiers.isInitialAssign()) {
            res.setInitialAssign(true);
        }
        return res;
    }

    private Statement mkRetrieve(VariableFactory variableFactory) {
        Pair<JavaTypeInstance, Integer> storageTypeAndIdx = getRetrieveType();
        int slot = storageTypeAndIdx.getSecond();
        Ident ident = localVariablesBySlot.get(slot);

        SSAIdent ssaIdent = ssaIdentifiers.getSSAIdent(new Slot(storageTypeAndIdx.getFirst(), slot));
        return new AssignmentSimple(getStackLValue(0), new LValueExpression(variableFactory.localVariable(slot, ident, originalRawOffset, ssaIdent.card() == 1)));
    }

    public Statement createStatement(final Method method, VariableFactory variableFactory, BlockIdentifierFactory blockIdentifierFactory, DCCommonState dcCommonState) {
        switch (instr) {
            case ALOAD:
            case ILOAD:
            case LLOAD:
            case DLOAD:
            case FLOAD:
            case ALOAD_0:
            case ILOAD_0:
            case LLOAD_0:
            case DLOAD_0:
            case FLOAD_0:
            case ALOAD_1:
            case ILOAD_1:
            case LLOAD_1:
            case DLOAD_1:
            case FLOAD_1:
            case ALOAD_2:
            case ILOAD_2:
            case LLOAD_2:
            case DLOAD_2:
            case FLOAD_2:
            case ALOAD_3:
            case ILOAD_3:
            case LLOAD_3:
            case DLOAD_3:
            case FLOAD_3:
                return mkRetrieve(variableFactory);
            case ACONST_NULL:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getNull()));
            case ICONST_M1:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getInt(-1)));
            case ICONST_0:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getBoolean(0)));
            case ICONST_1:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getBoolean(1)));
            case ICONST_2:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getInt(2)));
            case ICONST_3:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getInt(3)));
            case ICONST_4:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getInt(4)));
            case ICONST_5:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getInt(5)));
            case LCONST_0:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getLong(0)));
            case LCONST_1:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getLong(1)));
            case FCONST_0:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getFloat(0)));
            case DCONST_0:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getDouble(0)));
            case FCONST_1:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getFloat(1)));
            case DCONST_1:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getDouble(1)));
            case FCONST_2:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getFloat(2)));
            case BIPUSH: // TODO: Try a boolean if value = 0.
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getInt(rawData[0])));
            case SIPUSH:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getInt(getInstrArgShort(0))));
            case ISTORE:
            case ASTORE:
            case LSTORE:
            case DSTORE:
            case FSTORE:
            case ISTORE_0:
            case ASTORE_0:
            case LSTORE_0:
            case DSTORE_0:
            case FSTORE_0:
            case ISTORE_1:
            case ASTORE_1:
            case LSTORE_1:
            case DSTORE_1:
            case FSTORE_1:
            case ISTORE_2:
            case ASTORE_2:
            case LSTORE_2:
            case DSTORE_2:
            case FSTORE_2:
            case ISTORE_3:
            case ASTORE_3:
            case LSTORE_3:
            case DSTORE_3:
            case FSTORE_3:
                return mkAssign(variableFactory);
            case NEW:
                return new AssignmentSimple(getStackLValue(0), new NewObject(cpEntries[0]));
            case NEWARRAY:
                return new AssignmentSimple(getStackLValue(0), new NewPrimitiveArray(getStackRValue(0), rawData[0]));
            case ANEWARRAY: {
                List<Expression> tmp = ListFactory.newList();
                tmp.add(getStackRValue(0));
                // Type of cpEntries[0] will be the type of the array slice being allocated.
                // i.e. for A a[][] = new A[2][] it will be [LA
                //      for A a[] = new A[2] it will be A.
                // Resulting type needs an extra dimension attached for the dim being allocated.
                ConstantPoolEntryClass clazz = (ConstantPoolEntryClass) (cpEntries[0]);
                JavaTypeInstance innerInstance = clazz.getTypeInstance();
                // Result instance is the same as inner instance with 1 extra dimension.
                JavaTypeInstance resultInstance = new JavaArrayTypeInstance(1, innerInstance);

                return new AssignmentSimple(getStackLValue(0), new NewObjectArray(tmp, resultInstance));
            }
            case MULTIANEWARRAY: {
                int numDims = rawData[OperationFactoryMultiANewArray.OFFSET_OF_DIMS];
                // Type of cpEntries[0] will be the type of the whole array.
                // I.e. for A a[][] = new A[2][3]  it will be [[LA
                ConstantPoolEntryClass clazz = (ConstantPoolEntryClass) (cpEntries[0]);
                JavaTypeInstance innerInstance = clazz.getTypeInstance();
                // Result instance is the same as innerInstance
                JavaTypeInstance resultInstance = innerInstance;

                return new AssignmentSimple(getStackLValue(0), new NewObjectArray(getNStackRValuesAsExpressions(numDims), resultInstance));
            }
            case ARRAYLENGTH:
                return new AssignmentSimple(getStackLValue(0), new ArrayLength(getStackRValue(0)));
            case AALOAD:
            case IALOAD:
            case BALOAD:
            case CALOAD:
            case FALOAD:
            case LALOAD:
            case DALOAD:
            case SALOAD:
                return new AssignmentSimple(getStackLValue(0), new ArrayIndex(getStackRValue(1), getStackRValue(0)));
            case AASTORE:
            case IASTORE:
            case BASTORE:
            case CASTORE:
            case FASTORE:
            case LASTORE:
            case DASTORE:
            case SASTORE:
                return new AssignmentSimple(new ArrayVariable(new ArrayIndex(getStackRValue(2), getStackRValue(1))), getStackRValue(0));
            case LCMP:
            case DCMPG:
            case DCMPL:
            case FCMPG:
            case FCMPL:
            case LSUB:
            case LADD:
            case IADD:
            case FADD:
            case DADD:
            case ISUB:
            case DSUB:
            case FSUB:
            case IREM:
            case FREM:
            case LREM:
            case DREM:
            case IDIV:
            case FDIV:
            case DDIV:
            case IMUL:
            case DMUL:
            case FMUL:
            case LMUL:
            case IAND:
            case LAND:
            case LDIV:
            case LOR:
            case IOR:
            case LXOR:
            case IXOR:
            case ISHR:
            case ISHL:
            case LSHL:
            case LSHR:
            case IUSHR:
            case LUSHR: {
                Expression op = new ArithmeticOperation(getStackRValue(1), getStackRValue(0), ArithOp.getOpFor(instr));
                return new AssignmentSimple(getStackLValue(0), op);
            }
            case I2B:
            case I2C:
            case I2D:
            case I2F:
            case I2L:
            case I2S:
            case L2D:
            case L2F:
            case L2I:
            case F2D:
            case F2I:
            case F2L:
            case D2F:
            case D2I:
            case D2L: {
                LValue lValue = getStackLValue(0);
                lValue.getInferredJavaType().useAsWithCast(instr.getRawJavaType());
                return new AssignmentSimple(lValue, getStackRValue(0));
            }
            case INSTANCEOF:
                return new AssignmentSimple(getStackLValue(0), new InstanceOfExpression(getStackRValue(0), cpEntries[0]));
            case CHECKCAST: {
                ConstantPoolEntryClass castTarget = (ConstantPoolEntryClass) cpEntries[0];
                JavaTypeInstance tgtJavaType = castTarget.getTypeInstance();
                JavaTypeInstance alreadyJavaType = getStackRValue(0).getInferredJavaType().getJavaTypeInstance();
                // Have to check against the degenerified type, as checkcast is performed at runtime,
                // i.e. without generic information.
                if (tgtJavaType.equals(alreadyJavaType.getDeGenerifiedType())) {
                    return new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                } else {
                    InferredJavaType castType = new InferredJavaType(tgtJavaType, InferredJavaType.Source.EXPRESSION, true);
                    return new AssignmentSimple(getStackLValue(0), new CastExpression(castType, getStackRValue(0)));
                }
            }
            case INVOKESTATIC: {
                ConstantPoolEntryMethodRef function = (ConstantPoolEntryMethodRef) cpEntries[0];
                MethodPrototype methodPrototype = function.getMethodPrototype();
                List<Expression> args = getNStackRValuesAsExpressions(stackConsumed.size());
                methodPrototype.tightenArgs(null, args);
                methodPrototype.addExplicitCasts(null, args);
                // FIXME - BIND RESULT.
                StaticFunctionInvokation funcCall = new StaticFunctionInvokation(function, args);
                if (stackProduced.size() == 0) {
                    return new ExpressionStatement(funcCall);
                } else {
                    return new AssignmentSimple(getStackLValue(0), funcCall);
                }
            }
            case INVOKEDYNAMIC: {
                // Java uses invokedynamic for lambda expressions.
                // see http://download.java.net/jdk8/docs/api/java/lang/invoke/LambdaMetafactory.html
                return buildInvokeDynamic(method, dcCommonState);
            }
            case INVOKESPECIAL:
                // Invoke special == invokenonvirtual.
                // In this case the specific class of the method is relevant.
                // In java, the only way you can (???) reference a non-local method with this is a super call
                // (inner class methods do not use this.)
            case INVOKEVIRTUAL:
            case INVOKEINTERFACE: {
                return buildInvoke(method);
            }
            case RETURN:
                return new ReturnNothingStatement();
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPNE:
            case IF_ICMPEQ:
            case IF_ICMPLE: {
                ConditionalExpression conditionalExpression = new ComparisonOperation(getStackRValue(1), getStackRValue(0), CompOp.getOpFor(instr));
                return new IfStatement(conditionalExpression);
            }
            case IFNONNULL: {
                ConditionalExpression conditionalExpression = new ComparisonOperation(getStackRValue(0), new Literal(TypedLiteral.getNull()), CompOp.NE);
                return new IfStatement(conditionalExpression);
            }
            case IFNULL: {
                ConditionalExpression conditionalExpression = new ComparisonOperation(getStackRValue(0), new Literal(TypedLiteral.getNull()), CompOp.EQ);
                return new IfStatement(conditionalExpression);
            }
            case IFEQ:
            case IFNE: {
                ConditionalExpression conditionalExpression = new ComparisonOperation(getStackRValue(0), new Literal(TypedLiteral.getBoolean(0)), CompOp.getOpFor(instr));
                return new IfStatement(conditionalExpression);
            }
            case IFLE:
            case IFLT:
            case IFGT:
            case IFGE: {
                ConditionalExpression conditionalExpression = new ComparisonOperation(getStackRValue(0), new Literal(TypedLiteral.getInt(0)), CompOp.getOpFor(instr));
                return new IfStatement(conditionalExpression);
            }
            case JSR_W:
            case JSR: {
                return new CompoundStatement(
                        new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getInt(originalRawOffset))),
                        new JSRCallStatement()
                );
            }
            case RET: {
                int slot = getInstrArgByte(0);
                // This ret could return to after any JSR instruction which it is reachable from.  This is ... tricky.
                Expression retVal = new LValueExpression(variableFactory.localVariable(slot, localVariablesBySlot.get(slot), originalRawOffset, false));
                return new JSRRetStatement(retVal);
            }
            case GOTO:
            case GOTO_W:
                return new GotoStatement();
            case ATHROW:
                return new ThrowStatement(getStackRValue(0));
            case IRETURN:
            case ARETURN:
            case LRETURN:
            case DRETURN:
            case FRETURN: {
                Expression retVal = getStackRValue(0);
                JavaTypeInstance tgtType = variableFactory.getReturn();
                if (tgtType instanceof RawJavaType) {
                    retVal.getInferredJavaType().useAsWithoutCasting((RawJavaType) tgtType);
                }
                return new ReturnValueStatement(retVal, tgtType);
            }
            case GETFIELD: {
                Expression fieldExpression = new LValueExpression(new FieldVariable(getStackRValue(0), method.getClassFile(), cpEntries[0]));
                return new AssignmentSimple(getStackLValue(0), fieldExpression);
            }
            case GETSTATIC:
                return new AssignmentSimple(getStackLValue(0), new LValueExpression(new StaticVariable(method.getClassFile(), cp, cpEntries[0])));
            case PUTSTATIC:
                return new AssignmentSimple(new StaticVariable(method.getClassFile(), cp, cpEntries[0]), getStackRValue(0));
            case PUTFIELD:
                return new AssignmentSimple(new FieldVariable(getStackRValue(1), method.getClassFile(), cpEntries[0]), getStackRValue(0));
            case SWAP: {
                Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(1));
                return new CompoundStatement(s1, s2);
            }
            case DUP: {
                Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(0));
                return new CompoundStatement(s1, s2);
            }
            case DUP_X1: {
                Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(1));
                Statement s3 = new AssignmentSimple(getStackLValue(2), getStackRValue(0));
                return new CompoundStatement(s1, s2, s3);
            }
            case DUP_X2: {
                if (stackConsumed.get(1).getStackEntry().getType().getComputationCategory() == 2) {
                    Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                    Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(1));
                    Statement s3 = new AssignmentSimple(getStackLValue(2), getStackRValue(0));
                    return new CompoundStatement(s1, s2, s3);
                } else {
                    Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                    Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(2));
                    Statement s3 = new AssignmentSimple(getStackLValue(2), getStackRValue(1));
                    Statement s4 = new AssignmentSimple(getStackLValue(3), getStackRValue(0));
                    return new CompoundStatement(s1, s2, s3, s4);
                }
            }
            case DUP2: {
                if (stackConsumed.get(0).getStackEntry().getType().getComputationCategory() == 2) {
                    Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                    Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(0));
                    return new CompoundStatement(s1, s2);
                } else {
                    Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                    Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(1));
                    Statement s3 = new AssignmentSimple(getStackLValue(2), getStackRValue(0));
                    Statement s4 = new AssignmentSimple(getStackLValue(3), getStackRValue(1));
                    return new CompoundStatement(s1, s2, s3, s4);
                }
            }
            case DUP2_X1: {
                if (stackConsumed.get(0).getStackEntry().getType().getComputationCategory() == 2) {
                    Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                    Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(1));
                    Statement s3 = new AssignmentSimple(getStackLValue(2), getStackRValue(0));
                    return new CompoundStatement(s1, s2, s3);
                } else {
                    Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(1));
                    Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(0));
                    Statement s3 = new AssignmentSimple(getStackLValue(2), getStackRValue(2));
                    Statement s4 = new AssignmentSimple(getStackLValue(3), getStackRValue(1));
                    Statement s5 = new AssignmentSimple(getStackLValue(4), getStackRValue(0));
                    return new CompoundStatement(s1, s2, s3, s4, s5);
                }
            }
            case DUP2_X2: {
                if (stackConsumed.get(0).getStackEntry().getType().getComputationCategory() == 2) {
                    if (stackConsumed.get(1).getStackEntry().getType().getComputationCategory() == 2) {
                        // form 4
                        Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                        Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(1));
                        Statement s3 = new AssignmentSimple(getStackLValue(2), getStackRValue(0));
                        return new CompoundStatement(s1, s2, s3);
                    } else {
                        // form 2
                        Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                        Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(1));
                        Statement s3 = new AssignmentSimple(getStackLValue(2), getStackRValue(2));
                        Statement s4 = new AssignmentSimple(getStackLValue(3), getStackRValue(0));
                        return new CompoundStatement(s1, s2, s3, s4);
                    }
                } else {
                    if (stackConsumed.get(2).getStackEntry().getType().getComputationCategory() == 2) {
                        // form 3
                        Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                        Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(1));
                        Statement s3 = new AssignmentSimple(getStackLValue(2), getStackRValue(2));
                        Statement s4 = new AssignmentSimple(getStackLValue(3), getStackRValue(0));
                        Statement s5 = new AssignmentSimple(getStackLValue(4), getStackRValue(1));
                        return new CompoundStatement(s1, s2, s3, s4, s5);
                    } else {
                        // form 1
                        Statement s1 = new AssignmentSimple(getStackLValue(0), getStackRValue(0));
                        Statement s2 = new AssignmentSimple(getStackLValue(1), getStackRValue(1));
                        Statement s3 = new AssignmentSimple(getStackLValue(2), getStackRValue(2));
                        Statement s4 = new AssignmentSimple(getStackLValue(3), getStackRValue(3));
                        Statement s5 = new AssignmentSimple(getStackLValue(4), getStackRValue(0));
                        Statement s6 = new AssignmentSimple(getStackLValue(5), getStackRValue(1));
                        return new CompoundStatement(s1, s2, s3, s4, s5, s6);
                    }
                }
            }
            case LDC:
            case LDC_W:
            case LDC2_W:
                return new AssignmentSimple(getStackLValue(0), new Literal(TypedLiteral.getConstantPoolEntry(cp, cpEntries[0])));
            case MONITORENTER:
                return new MonitorEnterStatement(getStackRValue(0), blockIdentifierFactory.getNextBlockIdentifier(BlockType.MONITOR));
            case MONITOREXIT:
                return new MonitorExitStatement(getStackRValue(0));
            case FAKE_TRY:
                return new TryStatement(getSingleExceptionGroup());
            case FAKE_CATCH:
                return new CatchStatement(catchExceptionGroups, getStackLValue(0));
            case NOP:
                return new Nop();
            case POP:
//                return new Nop();
                return new ExpressionStatement(getStackRValue(0));
            case POP2:
                if (stackConsumed.get(0).getStackEntry().getType().getComputationCategory() == 2) {
                    return new ExpressionStatement(getStackRValue(0));
//                    return new Nop();
                } else {
                    Statement s1 = new ExpressionStatement(getStackRValue(0));
                    Statement s2 = new ExpressionStatement(getStackRValue(1));
                    return new CompoundStatement(s1, s2);
                }
            case TABLESWITCH:
                return new RawSwitchStatement(getStackRValue(0), new DecodedTableSwitch(rawData, originalRawOffset));
            case LOOKUPSWITCH:
                return new RawSwitchStatement(getStackRValue(0), new DecodedLookupSwitch(rawData, originalRawOffset));
            case IINC: {
                int variableIndex = getInstrArgByte(0);
                int incrAmount = getInstrArgByte(1);
                ArithOp op = ArithOp.PLUS;
                if (incrAmount < 0) {
                    incrAmount = -incrAmount;
                    op = ArithOp.MINUS;
                }

                // Can we have ++ / += instead?
                LValue lvalue = variableFactory.localVariable(variableIndex, localVariablesBySlot.get(variableIndex), originalRawOffset, false);
                return new AssignmentSimple(lvalue,
                        new ArithmeticOperation(new LValueExpression(lvalue), new Literal(TypedLiteral.getInt(incrAmount)), op));
            }
            case IINC_WIDE: {
                int variableIndex = getInstrArgShort(1);
                int incrAmount = getInstrArgShort(3);
                ArithOp op = ArithOp.PLUS;
                if (incrAmount < 0) {
                    incrAmount = -incrAmount;
                    op = ArithOp.MINUS;
                }

                // Can we have ++ / += instead?
                LValue lvalue = variableFactory.localVariable(variableIndex, localVariablesBySlot.get(variableIndex), originalRawOffset, false);

                return new AssignmentSimple(lvalue,
                        new ArithmeticOperation(new LValueExpression(lvalue), new Literal(TypedLiteral.getInt(incrAmount)), op));
            }

            case DNEG:
            case FNEG:
            case LNEG:
            case INEG: {
                return new AssignmentSimple(getStackLValue(0),
                        new ArithmeticMonOperation(getStackRValue(0), ArithOp.MINUS));
            }
            default:
                throw new ConfusedCFRException("Not implemented - conversion to statement from " + instr);
        }
    }

    private StackValue getStackRValue(int idx) {
        StackEntryHolder stackEntryHolder = stackConsumed.get(idx);
        StackEntry stackEntry = stackEntryHolder.getStackEntry();
        stackEntry.incrementUsage();
        return new StackValue(stackEntry.getLValue());
    }

    private LValue getStackLValue(int idx) {
        StackEntryHolder stackEntryHolder = stackProduced.get(idx);
        StackEntry stackEntry = stackEntryHolder.getStackEntry();
        return stackEntry.getLValue();
    }

    private List<Expression> getNStackRValuesAsExpressions(int count) {
        List<Expression> res = ListFactory.newList();
        for (int i = count - 1; i >= 0; --i) {
            res.add(getStackRValue(i));
        }
        return res;
    }

    @Override
    public String toString() {
        return "" + index + " : " + instr;
    }


    public static void populateStackInfo(List<Op02WithProcessedDataAndRefs> op2list, Method method) {
        // We might have two passes if there are JSRS.  Reset.
        for (Op02WithProcessedDataAndRefs op : op2list) {
            op.resetStackInfo();
        }

        // This dump block only exists because we're debugging bad stack size calcuations.
        Op02WithProcessedDataAndRefs o2start = op2list.get(0);
        try {
            o2start.populateStackInfo(new StackSim(), method);
        } catch (ConfusedCFRException e) {
            Dumper dmp = new ToStringDumper();
            dmp.print("----[known stack info]------------\n\n");
            for (Op02WithProcessedDataAndRefs op : op2list) {
                op.dump(dmp);
            }
            System.err.print(dmp.toString());
            throw e;
        }

    }

    public static void unlinkUnreachable(List<Op02WithProcessedDataAndRefs> op2list) {

        final Set<Op02WithProcessedDataAndRefs> reached = SetFactory.newSet();
        GraphVisitor<Op02WithProcessedDataAndRefs> reachableVisitor =
                new GraphVisitorDFS<Op02WithProcessedDataAndRefs>(op2list.get(0),
                        new BinaryProcedure<Op02WithProcessedDataAndRefs, GraphVisitor<Op02WithProcessedDataAndRefs>>() {
                            @Override
                            public void call(Op02WithProcessedDataAndRefs arg1, GraphVisitor<Op02WithProcessedDataAndRefs> arg2) {
                                reached.add(arg1);
                                for (Op02WithProcessedDataAndRefs target : arg1.getTargets()) {
                                    arg2.enqueue(target);
                                }
                            }
                        });
        reachableVisitor.process();

        /* Since we only look at nodes reachable from the start, we'll have the whole set we need to eliminate now.
         *
         */
        for (Op02WithProcessedDataAndRefs op : op2list) {
            if (!reached.contains(op)) {
                /* Unlink node - remove as source from all its targets
                 * (It doesn't have any reachable sources) */
                for (Op02WithProcessedDataAndRefs target : op.targets) {
                    target.removeSource(op);
                }
                op.instr = JVMInstr.NOP;
                op.targets.clear();
            }
        }
    }

    private void collectLocallyMutatedVariables(SSAIdentifierFactory<Slot> ssaIdentifierFactory) {
        Pair<JavaTypeInstance, Integer> storage = getStorageType();
        if (storage != null) {
            ssaIdentifiers = new SSAIdentifiers<Slot>(new Slot(storage.getFirst(), storage.getSecond()), ssaIdentifierFactory);
            return;
        }

        ssaIdentifiers = new SSAIdentifiers<Slot>();
        return;
    }

    public static void assignSSAIdentifiers(SSAIdentifierFactory<Slot> ssaIdentifierFactory, Method method, List<Op02WithProcessedDataAndRefs> statements) {
        assignSSAIdentifiersInner(ssaIdentifierFactory, method, statements);

        /*
         * We can walk all the reads to see if there are any reads of 'uninitialised' slots.
         * These are masking hidden parameters. (usually synthetic ones?).
         */
        Map<Integer, JavaTypeInstance> missing = MapFactory.newTreeMap();

        for (Op02WithProcessedDataAndRefs op02 : statements) {
            Pair<JavaTypeInstance, Integer> load = op02.getRetrieveType();
            if (load == null) continue;

            SSAIdent ident = op02.ssaIdentifiers.getSSAIdent(new Slot(load.getFirst(), load.getSecond()));
            if (ident == null) {
                missing.put(load.getSecond(), load.getFirst());
            }
        }

        if (missing.isEmpty()) return;

        if (!method.getConstructorFlag().isConstructor()) {
            throw new IllegalStateException("Invisible function parameters on a non-constructor");
        }

        /*
         * The (now known) missing arguments should be in the first available slots.
         * Add them to the method prototype, and re-scan.
         */
        method.getMethodPrototype().setSyntheticConstructorParameters(missing);

        assignSSAIdentifiersInner(ssaIdentifierFactory, method, statements);
    }

    public static void assignSSAIdentifiersInner(SSAIdentifierFactory<Slot> ssaIdentifierFactory, Method method, List<Op02WithProcessedDataAndRefs> statements) {

        /*
         * before we do anything, we need to generate identifiers for the parameters.
         */
        Map<Slot, SSAIdent> idents = method.getMethodPrototype().collectInitialSlotUsage(method.getConstructorFlag(), ssaIdentifierFactory);

        for (Op02WithProcessedDataAndRefs statement : statements) {
            statement.collectLocallyMutatedVariables(ssaIdentifierFactory);
        }
        statements.get(0).ssaIdentifiers = new SSAIdentifiers<Slot>(idents);

        final BinaryPredicate<Slot, Slot> testSlot = new BinaryPredicate<Slot, Slot>() {
            @Override
            public boolean test(Slot a, Slot b) {
                StackType t1 = a.getJavaTypeInstance().getStackType();
                StackType t2 = b.getJavaTypeInstance().getStackType();
                if (t1 == t2) return true;
                return false;
            }
        };

        LinkedList<Op02WithProcessedDataAndRefs> toProcess = ListFactory.newLinkedList();
        toProcess.addAll(statements);
        while (!toProcess.isEmpty()) {
            Op02WithProcessedDataAndRefs statement = toProcess.remove();
            SSAIdentifiers<Slot> ssaIdentifiers = statement.ssaIdentifiers;
            boolean changed = false;
            for (Op02WithProcessedDataAndRefs source : statement.getSources()) {
                if (ssaIdentifiers.mergeWith(source.ssaIdentifiers, testSlot)) {
                    changed = true;
                }
            }
            // If anything's changed, we need to check this statements children.
            if (changed) {
                toProcess.addAll(statement.getTargets());
            }
        }
    }

    /*
     * If we have
     *
     * if (a) {
     *   int b =  1;
     *
     * } else {
     *   int c = 1;
     * }
     *
     * return
     *
     * Then b and c may share the same slot. (say ,slot 2).  This means that (because we don't know any better)
     * they both appear to be live at the return, which means that they get aliased to the same variable.
     *
     * Detect that this aliasing is never READ, and therefore these two can be considered to be seperate.
     */
    private static void removeUnusedSSAIdentifiers(SSAIdentifierFactory<Slot> ssaIdentifierFactory, Method method, List<Op02WithProcessedDataAndRefs> op2list) {
        final List<Op02WithProcessedDataAndRefs> endPoints = ListFactory.newList();
        GraphVisitor<Op02WithProcessedDataAndRefs> gv = new GraphVisitorDFS<Op02WithProcessedDataAndRefs>(
                op2list.get(0),
                new BinaryProcedure<Op02WithProcessedDataAndRefs, GraphVisitor<Op02WithProcessedDataAndRefs>>() {
                    @Override
                    public void call(Op02WithProcessedDataAndRefs arg1, GraphVisitor<Op02WithProcessedDataAndRefs> arg2) {
                        if (arg1.getTargets().isEmpty()) {
                            endPoints.add(arg1);
                        } else {
                            arg2.enqueue(arg1.getTargets());
                        }
                    }
                });
        gv.process();
        /*
         * If there's an identifier which /hasn't/ been used, remove the back propagation.
         */
        Set<Op02WithProcessedDataAndRefs> seenOnce = SetFactory.newSet();
        Set<Op02WithProcessedDataAndRefs> toProcessContent = SetFactory.newSet();
        LinkedList<Op02WithProcessedDataAndRefs> toProcess = ListFactory.newLinkedList();

        toProcess.addAll(endPoints);
        toProcessContent.addAll(endPoints);

        List<Op02WithProcessedDataAndRefs> storeWithoutRead = ListFactory.newList();
        while (!toProcess.isEmpty()) {
            Op02WithProcessedDataAndRefs node = toProcess.removeFirst();
            toProcessContent.remove(node);

            Pair<JavaTypeInstance, Integer> retrieved = node.getRetrieveType();
            Pair<JavaTypeInstance, Integer> stored = node.getStorageType();
            /*
             * If there's an SSA identifier which DOESN'T exist in targets, and isn't read here
             * simply remove it.
             */
            SSAIdentifiers<Slot> ssaIdents = node.ssaIdentifiers;
            Map<Slot, SSAIdent> idents = ssaIdents.getKnownIdentifiers();
            Iterator<Map.Entry<Slot, SSAIdent>> iterator = idents.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Slot, SSAIdent> entry = iterator.next();
                Slot slot = entry.getKey();
                SSAIdent thisIdent = entry.getValue();
                /*
                 * Is this either used here, or used in a child?
                 * (if it's used in a child, it must not be SET in that child).
                 */
                boolean used = false;
                if (retrieved != null && retrieved.getSecond() == slot.getIdx()) {
                    used = true;
                }
                if (!used) {
                    for (Op02WithProcessedDataAndRefs target : node.targets) {
                        if (target.ssaIdentifiers.getSSAIdent(slot) != null) {
                            used = true;
                            break;
                        }
                    }
                }

                /*
                 * This is not, strictly speaking, necessary, but leads to nicer code.
                 * otherwise i = i + (i= 2) + (i= 5) introduces pointless variables.
                 */
                if (!used) {
                    if (stored != null) {
                        /*
                         * If one of the SOURCES
                         */
                        for (Op02WithProcessedDataAndRefs source : node.sources) {
                            SSAIdent sourceIdent = source.ssaIdentifiers.getSSAIdent(slot);
                            if (sourceIdent != null && thisIdent.isSuperSet(sourceIdent)) {
                                used = true;
                                break;
                            }
                        }
                    }
                }

                if (!used) {
                    for (Op02WithProcessedDataAndRefs source : node.sources) {
                        if (!toProcessContent.contains(source)) {
                            toProcessContent.add(source);
                            toProcess.add(source);
                            seenOnce.add(source);
                        }
                    }
                    if (stored != null && stored.getSecond() == slot.getIdx()) {
                        storeWithoutRead.add(node);
                    }
                    iterator.remove();
                } else {
                    /*
                     * we only need to process sources if they've never been seen, OR if we changed something.
                     */
                    for (Op02WithProcessedDataAndRefs source : node.sources) {
                        if (!seenOnce.contains(source)) {
                            if (!toProcessContent.contains(source)) {
                                toProcessContent.add(source);
                                toProcess.add(source);
                                seenOnce.add(source);
                            }
                        }
                    }
                }
            }
        }

        for (Op02WithProcessedDataAndRefs store : storeWithoutRead) {
            Pair<JavaTypeInstance, Integer> storage = store.getStorageType();
            Slot slot = new Slot(storage.getFirst(), storage.getSecond());
            SSAIdent ident = ssaIdentifierFactory.getIdent(slot);
            store.ssaIdentifiers.getKnownIdentifiers().put(slot, ident);
        }
    }

    public static void discoverStorageLiveness(Method method, List<Op02WithProcessedDataAndRefs> op2list) {
        SSAIdentifierFactory<Slot> ssaIdentifierFactory = new SSAIdentifierFactory<Slot>();

        assignSSAIdentifiers(ssaIdentifierFactory, method, op2list);

        removeUnusedSSAIdentifiers(ssaIdentifierFactory, method, op2list);
        // Now we've assigned SSA identifiers, we want to find, for each ident, the 'most encompassing'
        // set - so if we have one store which has S{0} = 1, one which has S{0} = 2, and one which has
        // S{0} = {1,2}, then all three should be S{0} = {1,2}
        // Way to handle this is to walk the graph again, testing each ssa ident against target's idents
        // If the target's ident is a superset of the source's, then copy the target into the source.
        // (of course we don't want to rewind every time, or we'll be n^2).
        Map<Slot, Map<SSAIdent, Set<SSAIdent>>> identChain = MapFactory.newLinkedLazyMap(
                new UnaryFunction<Slot, Map<SSAIdent, Set<SSAIdent>>>() {
                    @Override
                    public Map<SSAIdent, Set<SSAIdent>> invoke(Slot arg) {
                        return MapFactory.newLinkedLazyMap(new UnaryFunction<SSAIdent, Set<SSAIdent>>() {
                            @Override
                            public Set<SSAIdent> invoke(SSAIdent arg) {
                                return SetFactory.newOrderedSet();
                            }
                        });
                    }
                });

        for (Op02WithProcessedDataAndRefs op : op2list) {
            SSAIdentifiers<Slot> identifiers = op.ssaIdentifiers;

            Slot fixedHere = identifiers.getFixedHere();
            if (fixedHere != null) {
                SSAIdent finalIdent = identifiers.getSSAIdent(fixedHere);
                SSAIdent fixedIdent = identifiers.getValFixedHere();
                if (fixedIdent.isFirstIn(finalIdent)) {
                    identifiers.setInitialAssign();
                }
            }

            Map<Slot, SSAIdent> identMap = identifiers.getKnownIdentifiers();
            for (Map.Entry<Slot, SSAIdent> entry : identMap.entrySet()) {
                Slot thisSlot = entry.getKey();
                SSAIdent thisIdents = entry.getValue();
                Map<SSAIdent, Set<SSAIdent>> map = identChain.get(thisSlot);
                // Note - because this is a lazy map, this will force us to get a key.
                // This is needed for later, as we use this to determine the universe of keys.
                Set<SSAIdent> thisNextSet = map.get(thisIdents);
                for (Op02WithProcessedDataAndRefs tgt : op.getTargets()) {
                    SSAIdent nextIdents = tgt.ssaIdentifiers.getSSAIdent(thisSlot);
                    if (nextIdents != null && nextIdents.isSuperSet(thisIdents)) {
                        thisNextSet.add(nextIdents);
                    }
                }
            }
        }
        // Now rationalise this map.
        final Map<Pair<Slot, SSAIdent>, Ident> combinedMap = MapFactory.newLinkedMap();

        final IdentFactory identFactory = new IdentFactory();

        for (Map.Entry<Slot, Map<SSAIdent, Set<SSAIdent>>> entry : identChain.entrySet()) {
            // This is a map of all the things that are ever in slot Slot, if they alias.
            // If they have aliases, they might not resolve to the same eventual chain, if one of them
            // is an early return - we need to get the superest-set...
            final Slot slot = entry.getKey();
            // This map contains a->{a,b},{a,c} etc.
            //                   b->{a,b}
            //                   c->{a,c}
            // (and maybe {a,b}->{a,b,c} etc if we're lucky and have no early returns, but we can't
            // guarantee that).
            // We need to find a 'full relationship' - i.e. create a reverse map as well, and then fully
            // walk both.
            final Map<SSAIdent, Set<SSAIdent>> downMap = entry.getValue();
            final Map<SSAIdent, Set<SSAIdent>> upMap = createReverseMap(downMap);
            Set<SSAIdent> keys = SetFactory.newOrderedSet();
            keys.addAll(downMap.keySet());
            keys.addAll(upMap.keySet());  // this is probably not necessary.....
            // Remember that this represents MULTIPLE maps, all usages of this slot superimposed.
            // there will disjoint sets.

            for (SSAIdent key : keys) {
                Pair<Slot, SSAIdent> slotkey = Pair.make(slot, key);
                if (combinedMap.containsKey(slotkey)) continue;
                final Ident thisIdent = identFactory.getNextIdent(slot.getIdx());
                GraphVisitor<SSAIdent> gv = new GraphVisitorDFS<SSAIdent>(key, new BinaryProcedure<SSAIdent, GraphVisitor<SSAIdent>>() {
                    @Override
                    public void call(SSAIdent arg1, GraphVisitor<SSAIdent> arg2) {
                        Pair<Slot, SSAIdent> slotkey = Pair.make(slot, arg1);
                        if (combinedMap.containsKey(slotkey)) return;
                        combinedMap.put(slotkey, thisIdent);
                        arg2.enqueue(downMap.get(arg1));
                        arg2.enqueue(upMap.get(arg1));
                    }
                });
                gv.process();
            }
        }

        /*
         * Now, finally, map the SSAIdents on the individual statements back to
         */
        for (Op02WithProcessedDataAndRefs op2 : op2list) {
            op2.mapSSASlots(combinedMap);
        }

        method.getMethodPrototype().computeParameters(method.getConstructorFlag(), op2list.get(0).localVariablesBySlot);
    }

    private void mapSSASlots(Map<Pair<Slot, SSAIdent>, Ident> identmap) {
        Map<Slot, SSAIdent> knownIdents = ssaIdentifiers.getKnownIdentifiers();
        for (Map.Entry<Slot, SSAIdent> entry : knownIdents.entrySet()) {
            Ident ident = identmap.get(Pair.make(entry.getKey(), entry.getValue()));
            if (ident == null) {
                throw new IllegalStateException("Null ident");
            }
            localVariablesBySlot.put(entry.getKey().getIdx(), ident);
        }
    }

    private static class IdentFactory {
        int nextIdx = 0;

        public Ident getNextIdent(int slot) {
            return new Ident(slot, nextIdx++);
        }
    }

    private static Map<SSAIdent, Set<SSAIdent>> createReverseMap(Map<SSAIdent, Set<SSAIdent>> downMap) {
        Map<SSAIdent, Set<SSAIdent>> res = MapFactory.newLinkedLazyMap(new UnaryFunction<SSAIdent, Set<SSAIdent>>() {
            @Override
            public Set<SSAIdent> invoke(SSAIdent arg) {
                return SetFactory.newOrderedSet();
            }
        });
        for (Map.Entry<SSAIdent, Set<SSAIdent>> entry : downMap.entrySet()) {
            SSAIdent revValue = entry.getKey();
            Set<SSAIdent> revKeys = entry.getValue();
            for (SSAIdent revKey : revKeys) {
                res.get(revKey).add(revValue);
            }
        }
        return res;
    }


    public static List<Op03SimpleStatement> convertToOp03List(List<Op02WithProcessedDataAndRefs> op2list,
                                                              final Method method,
                                                              final VariableFactory variableFactory,
                                                              final BlockIdentifierFactory blockIdentifierFactory,
                                                              final DCCommonState dcCommonState) {


        final List<Op03SimpleStatement> op03SimpleParseNodesTmp = ListFactory.newList();
        // Convert the op2s into a simple set of statements.
        // Do these need to be processed in a sensible order?  Could just iterate?

        final GraphConversionHelper<Op02WithProcessedDataAndRefs, Op03SimpleStatement> conversionHelper = new GraphConversionHelper<Op02WithProcessedDataAndRefs, Op03SimpleStatement>();
        // By only processing reachable bytecode, we ignore deliberate corruption.   However, we could
        // Nop out unreachable code, so as to not have this ugliness.
        // We start at 0 as that's not controversial ;)
        GraphVisitor<Op02WithProcessedDataAndRefs> o2Converter = new GraphVisitorFIFO<Op02WithProcessedDataAndRefs>(op2list.get(0),
                new BinaryProcedure<Op02WithProcessedDataAndRefs, GraphVisitor<Op02WithProcessedDataAndRefs>>() {
                    @Override
                    public void call(Op02WithProcessedDataAndRefs arg1, GraphVisitor<Op02WithProcessedDataAndRefs> arg2) {
                        Op03SimpleStatement res = new Op03SimpleStatement(arg1, arg1.createStatement(method, variableFactory, blockIdentifierFactory, dcCommonState));
                        conversionHelper.registerOriginalAndNew(arg1, res);
                        op03SimpleParseNodesTmp.add(res);
                        for (Op02WithProcessedDataAndRefs target : arg1.getTargets()) {
                            arg2.enqueue(target);
                        }
                    }
                }
        );
        o2Converter.process();
        conversionHelper.patchUpRelations();
        return op03SimpleParseNodesTmp;
    }

    private static class ExceptionTempStatement implements Comparable<ExceptionTempStatement> {
        private final ExceptionGroup triggeringGroup;
        private final Op02WithProcessedDataAndRefs op;
        private final boolean isTry; // else catch;

        private ExceptionTempStatement(ExceptionGroup triggeringGroup, Op02WithProcessedDataAndRefs op) {
            this.triggeringGroup = triggeringGroup;
            this.op = op;
            this.isTry = (op.instr == JVMInstr.FAKE_TRY);
        }

        public ExceptionGroup getTriggeringGroup() {
            return triggeringGroup;
        }

        public Op02WithProcessedDataAndRefs getOp() {
            return op;
        }

        public boolean isTry() {
            return isTry;
        }

        // A try statement cannot DIRECTLY preceed a catch statement from an EARLIER try (should be after)
        // two try statements should be ordered according to their reach - first by start, and if that's equal, by
        // reverse ordering of end.
        @Override
        public int compareTo(ExceptionTempStatement other) {
            if (other == this) return 0;
            int startCompare = triggeringGroup.getBytecodeIndexFrom() - other.triggeringGroup.getBytecodeIndexFrom();
            if (startCompare != 0) return startCompare;
            int endCompare = triggeringGroup.getByteCodeIndexTo() - triggeringGroup.getByteCodeIndexTo();
            return 0 - endCompare;
//            throw new ConfusedCFRException("Can't compare these exception groups.");
        }

        @Override
        public String toString() {
            return op.toString();
        }
    }

    /* We want to place newNode in front of infrontOf.
     * If there's something there already (a fake try and a fake catch co-incide)
     * then we need to find where this /should/ go.
     */
    private static Op02WithProcessedDataAndRefs adjustOrdering(
            Map<InstrIndex, List<ExceptionTempStatement>> insertions,
            Op02WithProcessedDataAndRefs infrontOf,
            ExceptionGroup exceptionGroup,
            Op02WithProcessedDataAndRefs newNode) {
        InstrIndex idxInfrontOf = infrontOf.getIndex();
        List<ExceptionTempStatement> collides = insertions.get(idxInfrontOf);
        ExceptionTempStatement exceptionTempStatement = new ExceptionTempStatement(exceptionGroup, newNode);
        if (collides.isEmpty()) {
            collides.add(exceptionTempStatement);
            return infrontOf;
        }

        logger.finer("Adding " + newNode + " ident " + exceptionGroup.getTryBlockIdentifier());
        logger.finer("Already have " + collides);


        // If there's already something, we need to figure out which belongs in what order.
        int insertionPos = Collections.binarySearch(collides, exceptionTempStatement);
        if (insertionPos >= 0) {
            // throw new ConfusedCFRException("Already exists?");
            insertionPos++;
        } else {
            insertionPos = -(insertionPos + 1);
        }
        if (insertionPos == 0) { // start
            collides.add(0, exceptionTempStatement);
            /* Anything which was */
            throw new ConfusedCFRException("EEk.");
        }

        Op02WithProcessedDataAndRefs afterThis;
        logger.finer("Insertion position = " + insertionPos);

        if (insertionPos == collides.size()) { // end.
            collides.add(exceptionTempStatement);
            afterThis = infrontOf;
        } else { // middle
            afterThis = collides.get(insertionPos).getOp();
            collides.add(insertionPos, exceptionTempStatement);
        }
        /* Relabel the nodes, for subsequent sorting */
        for (ExceptionTempStatement ets : collides) {
            // Note the repeated use of justBefore - the last one called is REALLY just before.
            ets.getOp().setIndex(infrontOf.getIndex().justBefore());
        }
        return afterThis;
    }

    private static void tidyMultipleInsertionIdentifiers(Collection<List<ExceptionTempStatement>> etsList) {
        for (List<ExceptionTempStatement> ets : etsList) {
            /*
             * For each of these, find the ones which mark try statements, and remove that block from anything infront.
             */
            if (ets.size() <= 1) continue;

            for (int idx = 0; idx < ets.size(); ++idx) {
                ExceptionTempStatement et = ets.get(idx);
                if (et.isTry()) {
                    BlockIdentifier tryGroup = et.triggeringGroup.getTryBlockIdentifier();
                    logger.finer("Removing try group identifier " + tryGroup + " idx " + idx);
                    for (int idx2 = 0; idx2 < idx; ++idx2) {
                        logger.finest("" + ets.get(idx2).getOp());
                        logger.finest("" + ets.get(idx2).getOp().containedInTheseBlocks + " -->");
                        ets.get(idx2).getOp().containedInTheseBlocks.remove(tryGroup);
                        logger.finest("" + ets.get(idx2).getOp().containedInTheseBlocks);
                    }
                }
            }
        }
    }

    private static int getLastIndex(Map<Integer, Integer> lutByOffset, int op2count, long codeLength, int offset) {
        Integer iinclusiveLastIndex = lutByOffset.get(offset);
        if (iinclusiveLastIndex == null) {
            if (offset == codeLength) {
                iinclusiveLastIndex = op2count - 1;
            } else {
                throw new ConfusedCFRException("Last index of " + offset + " is not a valid entry into the code block");
            }
        }
        return iinclusiveLastIndex;
    }

//    private static Integer getAStoreIdx(Op02WithProcessedDataAndRefs op) {
//        switch (op.instr) {
//            case ASTORE:
//                return op.getInstrArgByte(0);
//            case ASTORE_WIDE:
//                throw new UnsupportedOperationException();
//            case ASTORE_0:
//                return 0;
//            case ASTORE_1:
//                return 1;
//            case ASTORE_2:
//                return 2;
//            case ASTORE_3:
//                return 3;
//        }
//        return null;
//    }
//
//    private static Integer getALoadIdx(Op02WithProcessedDataAndRefs op) {
//        switch (op.instr) {
//            case ALOAD:
//                return op.getInstrArgByte(0);
//            case ALOAD_WIDE:
//                throw new UnsupportedOperationException();
//            case ALOAD_0:
//                return 0;
//            case ALOAD_1:
//                return 1;
//            case ALOAD_2:
//                return 2;
//            case ALOAD_3:
//                return 3;
//        }
//        return null;
//    }

    private static boolean nextTarget(Op02WithProcessedDataAndRefs op, int idx, List<Op02WithProcessedDataAndRefs> op2list) {
        if (op.getTargets().size() != 1) return false;
        Op02WithProcessedDataAndRefs target = op.getTargets().get(0);
        if (idx + 1 >= op2list.size()) return false;
        if (target != op2list.get(idx + 1)) return false;
        return true;
    }

    /*
     * This is a peculiarly perverse condition which appears to be generated by android sdk.
     * We throw BACK into a block which unlocks mutexes.
     *
     * In general, we don't want to do this - this is a VERY special case.
     */
//    private static boolean testSyncUnlock(int idx, List<Op02WithProcessedDataAndRefs> op2list) {
//        Op02WithProcessedDataAndRefs testStore = op2list.get(idx);
//        Integer storeByte = getAStoreIdx(testStore);
//        if (storeByte == null) return false;
//        if (!nextTarget(testStore, idx, op2list)) return false;
//        Op02WithProcessedDataAndRefs testLoad = op2list.get(idx + 1);
//        Integer loadByte = getALoadIdx(testLoad);
//        if (loadByte == null) return false;
//        if (!nextTarget(testLoad, idx + 1, op2list)) return false;
//        Op02WithProcessedDataAndRefs monitorExit = op2list.get(idx + 2);
//        if (monitorExit.instr != JVMInstr.MONITOREXIT) return false;
//        if (!nextTarget(monitorExit, idx + 2, op2list)) return false;
//        Op02WithProcessedDataAndRefs testLoad2 = op2list.get(idx + 3);
//        Integer loadStored = getALoadIdx(testLoad2);
//        if (!storeByte.equals(loadStored)) return false;
//        if (!nextTarget(testLoad2, idx + 3, op2list)) return false;
//        Op02WithProcessedDataAndRefs throwI = op2list.get(idx + 4);
//        if (throwI.instr != JVMInstr.ATHROW) return false;
//        return true;
//    }

    public static List<Op02WithProcessedDataAndRefs> insertExceptionBlocks(
            List<Op02WithProcessedDataAndRefs> op2list,
            ExceptionAggregator exceptions,
            Map<Integer, Integer> lutByOffset,
            ConstantPool cp,
            long codeLength,
            DCCommonState dcCommonState
    ) {
        Options options = dcCommonState.getOptions();
        int originalInstrCount = op2list.size();

        if (exceptions.getExceptionsGroups().isEmpty()) return op2list;

        Map<InstrIndex, List<ExceptionTempStatement>> insertions = MapFactory.newLazyMap(
                new UnaryFunction<InstrIndex, List<ExceptionTempStatement>>() {
                    @Override
                    public List<ExceptionTempStatement> invoke(InstrIndex ignore) {
                        return ListFactory.newList();
                    }
                });

        // First pass - decorate blocks with identifiers, so that when we introduce try/catch statements
        // they get the correct identifiers
        for (ExceptionGroup exceptionGroup : exceptions.getExceptionsGroups()) {
            BlockIdentifier tryBlockIdentifier = exceptionGroup.getTryBlockIdentifier();
            int originalIndex = lutByOffset.get((int) exceptionGroup.getBytecodeIndexFrom());
            int exclusiveLastIndex = getLastIndex(lutByOffset, originalInstrCount, codeLength, (int) exceptionGroup.getByteCodeIndexTo());

//            System.out.println("Adding try block identifier " + tryBlockIdentifier + "[" + originalIndex + " -> " + inclusiveLastIndex + "]" + exceptionGroup);
            for (int x = originalIndex; x < exclusiveLastIndex; ++x) {
                op2list.get(x).containedInTheseBlocks.add(tryBlockIdentifier);
            }
        }
        // What if the exception handler terminates early, eg before a return or a goto?


        // Add entries from the exception table.  Since these are stored in terms of offsets, they're
        // only usable here until we mess around with the instruction structure, so do it early!
        for (ExceptionGroup exceptionGroup : exceptions.getExceptionsGroups()) {

            List<ExceptionGroup.Entry> rawes = exceptionGroup.getEntries();
            int originalIndex = lutByOffset.get((int) exceptionGroup.getBytecodeIndexFrom());
            Op02WithProcessedDataAndRefs startInstruction = op2list.get(originalIndex);

            int inclusiveLastIndex = getLastIndex(lutByOffset, originalInstrCount, codeLength, (int) exceptionGroup.getByteCodeIndexTo());
            Op02WithProcessedDataAndRefs lastTryInstruction = op2list.get(inclusiveLastIndex);


            List<Pair<Op02WithProcessedDataAndRefs, ExceptionGroup.Entry>> handlerTargets = ListFactory.newList();
            for (ExceptionGroup.Entry exceptionEntry : rawes) {
                short handler = exceptionEntry.getBytecodeIndexHandler();
                int handlerIndex = lutByOffset.get((int) handler);
                if (handlerIndex <= originalIndex) {
                    // Handle a particularly odd case with Android exceptions.
//                    if (testSyncUnlock(handlerIndex, op2list)) {
//                        continue;
//                    }
                    if (!options.getOption(OptionsImpl.LENIENT)) {
                        throw new ConfusedCFRException("Back jump on a try block " + exceptionEntry);
                    }
                }
                Op02WithProcessedDataAndRefs handerTarget = op2list.get(handlerIndex);
                handlerTargets.add(Pair.make(handerTarget, exceptionEntry));
            }

            // Unlink startInstruction from its source, add a new instruction in there, which has a
            // default target of startInstruction, but additional targets of handlerTargets.
            Op02WithProcessedDataAndRefs tryOp =
                    new Op02WithProcessedDataAndRefs(JVMInstr.FAKE_TRY, null, startInstruction.getIndex().justBefore(), cp, null, -1);
            // It might turn out we want to insert it before something which has already been added BEFORE startInstruction!
            startInstruction = adjustOrdering(insertions, startInstruction, exceptionGroup, tryOp);
            tryOp.containedInTheseBlocks.addAll(startInstruction.containedInTheseBlocks);
            tryOp.containedInTheseBlocks.remove(exceptionGroup.getTryBlockIdentifier());
            tryOp.exceptionGroups.add(exceptionGroup);

            // All forward jumping operations which pointed to start should now point to our TRY.
            // (we leave back jumps where they are, or they might interfere with loop analysis).
//            if (startInstruction.getSources().isEmpty()) {
//                throw new ConfusedCFRException("Can't install exception handler infront of nothing");
//            }
            List<Op02WithProcessedDataAndRefs> removeThese = ListFactory.newList();
            for (Op02WithProcessedDataAndRefs source : startInstruction.getSources()) {
                // If it's a back jump from WITHIN the try block, we don't want to repoint at 'try'.
                // However, we haven't yet 'splayed' the try block out to cover extra instructions, so we might
                // miss something.....
                if (startInstruction.getIndex().isBackJumpFrom(source.getIndex()) &&
                        !lastTryInstruction.getIndex().isBackJumpFrom(source.getIndex())) {
                    // it was a backjump inside the block.
                } else {
                    source.replaceTarget(startInstruction, tryOp);
                    removeThese.add(source);
                    tryOp.addSource(source);
                }
            }
            for (Op02WithProcessedDataAndRefs remove : removeThese) {
                startInstruction.removeSource(remove);
            }

            // Add tryBlockIdentifier to each block in the original.

            // Given that we're protecting a certain block,
            // these are the different catch blocks, one for each caught type.

            for (Pair<Op02WithProcessedDataAndRefs, ExceptionGroup.Entry> catchTargets : handlerTargets) {
                Op02WithProcessedDataAndRefs tryTarget = catchTargets.getFirst();
                /*
                * tryTarget should not have a previous FAKE_CATCH source.
                */
                List<Op02WithProcessedDataAndRefs> tryTargetSources = tryTarget.getSources();
                Op02WithProcessedDataAndRefs preCatchOp = null;

                boolean addFakeCatch = false;

                if (tryTargetSources.isEmpty()) {
                    addFakeCatch = true;
                } else {
                    /* try target sources /SHOULD/ be empty (unless already processed)
                     * because you can't fall into a catch block.
                     *
                     * If you ARE doing this, then it's probable that you're dealing with
                     * obfuscated code.....
                     *
                     * (Previously, was checking sources = 1, source = CATCH).
                     */
                    for (Op02WithProcessedDataAndRefs source : tryTargetSources) {
                        if (source.getInstr() == JVMInstr.FAKE_CATCH) {
                            preCatchOp = source;
                        } else {
                            if (!options.getOption(OptionsImpl.LENIENT)) {
                                throw new ConfusedCFRException("non catch before exception catch block");
                            }
                        }
                    }
                    if (preCatchOp == null) {
                        addFakeCatch = true;
                    }
                }

                if (addFakeCatch) {
                    ExceptionGroup.Entry entry = catchTargets.getSecond();
                    byte[] data = null;
                    if (entry.isJustThrowable()) {
                        data = new byte[0];
                    }
                    // Add a fake catch here.  This injects a stack value, which will later be popped into the
                    // actual exception value.  (probably with an astore... but not neccessarily!)
                    preCatchOp = new Op02WithProcessedDataAndRefs(JVMInstr.FAKE_CATCH, data, tryTarget.getIndex().justBefore(), cp, null, -1);
                    tryTarget = adjustOrdering(insertions, tryTarget, exceptionGroup, preCatchOp);
                    preCatchOp.containedInTheseBlocks.addAll(tryTarget.getContainedInTheseBlocks());
                    preCatchOp.addTarget(tryTarget);
                    tryTarget.addSource(preCatchOp);
                    op2list.add(preCatchOp);
                }

                if (preCatchOp == null) {
                    throw new IllegalStateException("Bad precatch op state.");
                }
                preCatchOp.addSource(tryOp);
                tryOp.addTarget(preCatchOp);
                preCatchOp.catchExceptionGroups.add(catchTargets.getSecond());
            }
            tryOp.targets.add(0, startInstruction);
            startInstruction.addSource(tryOp);
            op2list.add(tryOp);

            if (tryOp.sources.isEmpty()) {
                int x = 1;
            }
        }

        /*
         * 3rd pass - extend try blocks if the instructions after them can't throw, and are in
         * identical blocks except the try block.  (and aren't catch statemetns... etc.. ;)
         *
         * basically just returns and gotos....
         */
        for (ExceptionGroup exceptionGroup : exceptions.getExceptionsGroups()) {
            BlockIdentifier tryBlockIdentifier = exceptionGroup.getTryBlockIdentifier();
            int beforeLastIndex = getLastIndex(lutByOffset, originalInstrCount, codeLength, (int) exceptionGroup.getByteCodeIndexTo()) - 1;

            Op02WithProcessedDataAndRefs lastStatement = op2list.get(beforeLastIndex);
            Set<BlockIdentifier> blocks = SetFactory.newSet(lastStatement.containedInTheseBlocks);
            int x = beforeLastIndex + 1;
            if (lastStatement.targets.size() == 1 && op2list.get(x) == lastStatement.targets.get(0)) {
                Op02WithProcessedDataAndRefs next = op2list.get(x);
                boolean bOk = true;
                if (next.sources.size() > 1) {
                    for (Op02WithProcessedDataAndRefs source : next.sources) {
                        Set<BlockIdentifier> blocks2 = SetFactory.newSet(source.containedInTheseBlocks);
                        if (!blocks.equals(blocks2)) bOk = false;
                    }
                }
                // If all sources are in same block....
                Set<BlockIdentifier> blocksWithoutTry = SetFactory.newSet(blocks);
                blocksWithoutTry.remove(tryBlockIdentifier);
                if (bOk) {
                    switch (next.instr) {
                        case GOTO:
                        case GOTO_W:
                        case RETURN:
                        case ARETURN:
                        case IRETURN:
                        case LRETURN:
                        case DRETURN:
                        case FRETURN: {
                            Set<BlockIdentifier> blocks2 = SetFactory.newSet(next.containedInTheseBlocks);
                            if (blocksWithoutTry.equals(blocks2)) {
                                next.containedInTheseBlocks.add(tryBlockIdentifier);
                            }
                        }
                    }
                }
            }

        }

        /* For all lists where we've got multiple fake insertions in a single location now, make sure that
        * try block insertions aren't referenced before they exist.  I.e. for each try block in the ExceptionTemps
        * remove it from previous ones.
        */
        tidyMultipleInsertionIdentifiers(insertions.values());
        return op2list;
    }

    public List<BlockIdentifier> getContainedInTheseBlocks() {
        return containedInTheseBlocks;
    }

//    /*
//     * Find which JSRs this block is the target of.  This /WILL/ get confused by nested JSRs, and REALLY confused when
//     * the ret doesn't match the JSR.  Will need to revisit.
//     */
//    private static void linkRetToJSR(Op02WithProcessedDataAndRefs ret, List<Op02WithProcessedDataAndRefs> ops) {
//        final Set<Op02WithProcessedDataAndRefs> jsrParents = SetFactory.newSet();
//
//        GraphVisitor<Op02WithProcessedDataAndRefs> graphVisitor = new GraphVisitorDFS<Op02WithProcessedDataAndRefs>(
//                ret,
//                new BinaryProcedure<Op02WithProcessedDataAndRefs, GraphVisitor<Op02WithProcessedDataAndRefs>>() {
//                    @Override
//                    public void call(Op02WithProcessedDataAndRefs arg1, GraphVisitor<Op02WithProcessedDataAndRefs> arg2) {
//                        if (arg1.instr == JVMInstr.JSR || arg1.instr == JVMInstr.JSR_W) {
//                            jsrParents.add(arg1);
//                            return;
//                        }
//                        for (Op02WithProcessedDataAndRefs source : arg1.sources) {
//                            arg2.enqueue(source);
//                        }
//                    }
//                });
//        graphVisitor.process();
//
//        for (Op02WithProcessedDataAndRefs jsr : jsrParents) {
//            int i = ops.indexOf(jsr);
//            Op02WithProcessedDataAndRefs jsrAfter = ops.get(i + 1);
//            ret.addTarget(jsrAfter);
//            jsrAfter.addSource(ret);
//        }
//    }
//
//    public static void linkRetsToJSR(List<Op02WithProcessedDataAndRefs> ops) {
//        for (Op02WithProcessedDataAndRefs op : ops) {
//            if (op.instr == JVMInstr.RET || op.instr == JVMInstr.RET_WIDE) {
//                linkRetToJSR(op, ops);
//            }
//        }
//    }

    private static boolean isJSR(Op02WithProcessedDataAndRefs op) {
        JVMInstr instr = op.instr;
        return (instr == JVMInstr.JSR) || (instr == JVMInstr.JSR_W);
    }

    private static boolean isRET(Op02WithProcessedDataAndRefs op) {
        JVMInstr instr = op.instr;
        return (instr == JVMInstr.RET) || (instr == JVMInstr.RET_WIDE);
    }

    /*
     * JSR are used in two different ways - one as an actual GOSUB simulation,
     * and one as a faked up goto. (JSR followed by something which eventually
     * pops and discards the callee.)
     *
     * The problem comes when a JSR calls itself OR performs a RET (or, heaven
     * forfend) a RET acts as a RET for different JSRs.
     *
     * (fairly) naive approach - mark each instruction that is the START of a sub
     * (i.e. the target of a JSR) as individual subs.
     *
     * We can work with - RETURNADDRESS can't be loaded, or used for anything other than a
     * pop or a store.  So if we can detect which stores are used, we could work out which
     * ret corresponds.
     *
     * PROBLEM : what if a store is a common target of a goto after two JSR targets?
     */
    public static boolean processJSR(List<Op02WithProcessedDataAndRefs> ops) {
        List<Op02WithProcessedDataAndRefs> jsrInstrs = Functional.filter(ops, new Predicate<Op02WithProcessedDataAndRefs>() {
            @Override
            public boolean test(Op02WithProcessedDataAndRefs in) {
                return isJSR(in);
            }
        });
        if (jsrInstrs.isEmpty()) return false;
        return processJSRs(jsrInstrs, ops);
    }

    private static boolean processJSRs(List<Op02WithProcessedDataAndRefs> jsrs, List<Op02WithProcessedDataAndRefs> ops) {
        // Find the common start instructions for a JSR (i.e. pull out the set of all targets for the JSRs
        // Then, for each of these, find out if it's possible to get BACK to the JSR instruction without
        // RETTING.  If that's the case, the JSR has been used as a loop, and we need to treat it as if it's just a
        // fancy (albeit confusing) GOTO.
        Map<Op02WithProcessedDataAndRefs, List<Op02WithProcessedDataAndRefs>> targets = Functional.groupToMapBy(jsrs, new UnaryFunction<Op02WithProcessedDataAndRefs, Op02WithProcessedDataAndRefs>() {
            @Override
            public Op02WithProcessedDataAndRefs invoke(Op02WithProcessedDataAndRefs arg) {
                return arg.getTargets().get(0);
            }
        });
        boolean result = false;
        Set<Op02WithProcessedDataAndRefs> inlineCandidates = SetFactory.newSet();
        for (final Op02WithProcessedDataAndRefs target : targets.keySet()) {
            GraphVisitor<Op02WithProcessedDataAndRefs> gv = new GraphVisitorDFS<Op02WithProcessedDataAndRefs>(target.getTargets(), new BinaryProcedure<Op02WithProcessedDataAndRefs, GraphVisitor<Op02WithProcessedDataAndRefs>>() {
                @Override
                public void call(Op02WithProcessedDataAndRefs arg1, GraphVisitor<Op02WithProcessedDataAndRefs> arg2) {
                    if (isRET(arg1)) {
                        return;
                    }
                    if (arg1 == target) {
                        arg2.abort();
                        return;
                    }
                    arg2.enqueue(arg1.getTargets());
                }
            });
            gv.process();
            if (gv.wasAborted()) continue;
            // Otherwise, this set of nodes is in the subroutine.
            Set<Op02WithProcessedDataAndRefs> nodes = SetFactory.newSet(gv.getVisitedNodes());
            // Verify that none of these nodes has a parent NOT in the set!
            for (Op02WithProcessedDataAndRefs node : nodes) {
                if (!nodes.containsAll(node.getSources())) {
                    continue;
                }
            }
            // explicitly add the JSR start to the nodes.
            nodes.add(target);
            // Have any of these nodes already been marked as candidates?
            if (SetUtil.hasIntersection(inlineCandidates, nodes)) {
                continue;
            }
            // Ok, this is a candidate for inlining.
            inlineCandidates.addAll(nodes);

            inlineJSR(target, nodes, ops);
            result = true;
        }

        /*
         * If there's a JSR we couldn't handle, we might still be able to refactor it, IF it's only got one call site.
         * (unlikely, but allows us to do nasty tricks!)
         *
         * Frankly, this pass is a bit of a gimmick.
         */
        for (final Op02WithProcessedDataAndRefs jsr : jsrs) {
            if (!isJSR(jsr)) continue;
            final Op02WithProcessedDataAndRefs target = jsr.targets.get(0);
            List<Op02WithProcessedDataAndRefs> sources = targets.get(target);
            if (sources == null || sources.size() > 1) continue;

            // Process everything, but no longer abort if we cycle, just don't retrace.
            final List<Op02WithProcessedDataAndRefs> rets = ListFactory.newList();
            GraphVisitor<Op02WithProcessedDataAndRefs> gv = new GraphVisitorDFS<Op02WithProcessedDataAndRefs>(target.getTargets(), new BinaryProcedure<Op02WithProcessedDataAndRefs, GraphVisitor<Op02WithProcessedDataAndRefs>>() {
                @Override
                public void call(Op02WithProcessedDataAndRefs arg1, GraphVisitor<Op02WithProcessedDataAndRefs> arg2) {
                    if (isRET(arg1)) {
                        rets.add(arg1);
                        return;
                    }
                    if (arg1 == target) {
                        return;
                    }
                    arg2.enqueue(arg1.getTargets());
                }
            });
            gv.process();

            int idx = ops.indexOf(jsr) + 1;
            if (idx >= ops.size()) continue;
            Op02WithProcessedDataAndRefs afterJsr = ops.get(idx);

            for (Op02WithProcessedDataAndRefs ret : rets) {
                ret.instr = JVMInstr.GOTO;
                ret.targets.clear();
                ret.addTarget(afterJsr);
                afterJsr.addSource(ret);
            }
            inlineReplaceJSR(jsr, ops);
        }

        /*
         * Go through the remaining JSRs, and convert them into gotos.  Remaining RETs?
         */
        for (final Op02WithProcessedDataAndRefs jsr : jsrs) {
            if (!isJSR(jsr)) continue;
            /*
             * Replace with a aconst_null, goto.
             */
            inlineReplaceJSR(jsr, ops);
        }

        return result;
    }

    private static void inlineReplaceJSR(Op02WithProcessedDataAndRefs jsrCall, List<Op02WithProcessedDataAndRefs> ops) {
        Op02WithProcessedDataAndRefs jsrTarget = jsrCall.getTargets().get(0);

        Op02WithProcessedDataAndRefs newGoto = new Op02WithProcessedDataAndRefs(
                JVMInstr.GOTO,
                null,
                jsrCall.getIndex().justAfter(),
                jsrCall.cp,
                null,
                -1); // offset is a fake, obviously, as it's synthetic.
        jsrTarget.removeSource(jsrCall);
        jsrCall.removeTarget(jsrTarget);
        newGoto.addTarget(jsrTarget);
        newGoto.addSource(jsrCall);
        jsrCall.addTarget(newGoto);
        jsrTarget.addSource(newGoto);
        jsrCall.instr = JVMInstr.ACONST_NULL;
        int jsrIdx = ops.indexOf(jsrCall);
        ops.add(jsrIdx + 1, newGoto);
    }

    private static void inlineJSR(Op02WithProcessedDataAndRefs start, Set<Op02WithProcessedDataAndRefs> nodes,
                                  List<Op02WithProcessedDataAndRefs> ops) {
        List<Op02WithProcessedDataAndRefs> instrs = ListFactory.newList(nodes);
        Collections.sort(instrs, new Comparator<Op02WithProcessedDataAndRefs>() {
            @Override
            public int compare(Op02WithProcessedDataAndRefs o1, Op02WithProcessedDataAndRefs o2) {
                return o1.getIndex().compareTo(o2.getIndex());
            }
        });
        ops.removeAll(instrs);
        // Take a copy, as we're going to be hacking this....
        List<Op02WithProcessedDataAndRefs> sources = ListFactory.newList(start.getSources());
        //
        // Now, insert an ACONST_NULL infront of the first instruction, to fake production of the original
        // stack value (this avoids us having inconsistent local usage, alternately we could simply pretend it
        // never existed, but then we'd have to find the store/pop if it happened much later on.
        //
        Op02WithProcessedDataAndRefs newStart = new Op02WithProcessedDataAndRefs(
                JVMInstr.ACONST_NULL,
                null,
                start.getIndex().justBefore(),
                start.cp,
                null,
                -1); // offset is a fake, obviously, as it's synthetic.
        instrs.add(0, newStart);
        start.getSources().clear();
        start.addSource(newStart);
        newStart.addTarget(start);

        for (Op02WithProcessedDataAndRefs source : sources) {
            // For each of these JSR instructions, we want to remove it,
            source.removeTarget(start);
            // We take a copy of the ENTIRE instrs block, and add it inline after the JSR. The JSR is replaced
            // with a NOP.
            List<Op02WithProcessedDataAndRefs> instrCopy = copyBlock(instrs, source.getIndex());
            // Find each RET in instrCopy, and point them at the node immediately following source.
            // If there's no following instruction, there can be no ret. (cunning, eh?)
            int idx = ops.indexOf(source) + 1;
            if (idx < ops.size()) {
                Op02WithProcessedDataAndRefs retTgt = ops.get(idx);
                for (Op02WithProcessedDataAndRefs op : instrCopy) {
                    if (isRET(op)) {
                        op.instr = JVMInstr.GOTO;
                        op.addTarget(retTgt);
                        retTgt.addSource(op);
                    }
                }
            }
            /*
             * Replace the original JSR / JSR_WIDE with a NOP.
             */
            source.instr = JVMInstr.NOP;
            int sourceIdx = ops.indexOf(source);
            ops.addAll(sourceIdx + 1, instrCopy);
            Op02WithProcessedDataAndRefs blockStart = instrCopy.get(0);
            blockStart.addSource(source);
            source.addTarget(blockStart);
        }
    }

    private static List<Op02WithProcessedDataAndRefs> copyBlock(List<Op02WithProcessedDataAndRefs> orig, InstrIndex afterThis) {
        List<Op02WithProcessedDataAndRefs> output = ListFactory.newList(orig.size());
        Map<Op02WithProcessedDataAndRefs, Op02WithProcessedDataAndRefs> fromTo = MapFactory.newMap();
        for (Op02WithProcessedDataAndRefs in : orig) {
            Op02WithProcessedDataAndRefs copy = new Op02WithProcessedDataAndRefs(in);
            afterThis = afterThis.justAfter();
            copy.index = afterThis;
            fromTo.put(in, copy);
            output.add(copy);
        }
        for (int x = 0, len = orig.size(); x < len; ++x) {
            Op02WithProcessedDataAndRefs in = orig.get(x);
            Op02WithProcessedDataAndRefs copy = output.get(x);
            copy.exceptionGroups = ListFactory.newList(in.exceptionGroups);
            copy.containedInTheseBlocks = ListFactory.newList(in.containedInTheseBlocks);
            copy.catchExceptionGroups = ListFactory.newList(in.catchExceptionGroups);

            // Now, create copies of the sources and targets.
            tieUpRelations(copy.getSources(), in.getSources(), fromTo);
            tieUpRelations(copy.getTargets(), in.getTargets(), fromTo);
        }
        return output;
    }

    private static void tieUpRelations(List<Op02WithProcessedDataAndRefs> out, List<Op02WithProcessedDataAndRefs> in, Map<Op02WithProcessedDataAndRefs, Op02WithProcessedDataAndRefs> map) {
        out.clear();
        for (Op02WithProcessedDataAndRefs i : in) {
            Op02WithProcessedDataAndRefs mapped = map.get(i);
            if (mapped == null) {
                throw new ConfusedCFRException("Missing node tying up JSR block");
            }
            out.add(mapped);
        }
    }
}
