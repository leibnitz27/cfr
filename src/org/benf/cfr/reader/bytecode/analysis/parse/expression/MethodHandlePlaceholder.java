package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionVisitor;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.QuotingUtils;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredCatch;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredReturn;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredThrow;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredTry;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.AccessFlagMethod;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.FakeMethod;
import org.benf.cfr.reader.entities.bootstrap.MethodHandleBehaviour;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodHandle;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class MethodHandlePlaceholder extends AbstractExpression {
    private ConstantPoolEntryMethodHandle handle;
    private FakeMethod fake;

    public MethodHandlePlaceholder(ConstantPoolEntryMethodHandle handle) {
        super(new InferredJavaType(TypeConstants.METHOD_HANDLE, InferredJavaType.Source.FUNCTION, true));
        this.handle = handle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (!(o instanceof MethodHandlePlaceholder)) return false;
        return handle.equals(((MethodHandlePlaceholder) o).handle);
    }

    @Override
    public Precedence getPrecedence() {
        return fake == null ? Precedence.WEAKEST : Precedence.PAREN_SUB_MEMBER;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        if (fake == null) {
            d.print("/* method handle: ").dump(new Literal(TypedLiteral.getString(handle.getLiteralName()))).separator(" */ null");
        } else {
            d.methodName(fake.getName(), null, false, false).separator("(").separator(")");
        }
        return d;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof MethodHandlePlaceholder)) return false;
        return constraint.equivalent(handle, ((MethodHandlePlaceholder) o).handle);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new MethodHandlePlaceholder(handle);
    }

    public FakeMethod addFakeMethod(ClassFile classFile) {
        fake = classFile.addFakeMethod(handle, "ldc", new UnaryFunction<String, FakeMethod>() {
            @Override
            public FakeMethod invoke(String name) {
                return generateFake(name);
            }
        });
        return fake;
    }

    private FakeMethod generateFake(String name) {
        BlockIdentifier identifier = new BlockIdentifier(-1, BlockType.TRYBLOCK);
        StructuredTry trys = new StructuredTry(
                new Op04StructuredStatement(Block.getBlockFor(true, new StructuredReturn(from(handle), TypeConstants.METHOD_HANDLE))),
                identifier);
        LValue caught = new LocalVariable("except", new InferredJavaType(TypeConstants.THROWABLE, InferredJavaType.Source.EXPRESSION));
        List<JavaRefTypeInstance> catchTypes = ListFactory.newList(TypeConstants.NOSUCHMETHOD_EXCEPTION, TypeConstants.ILLEGALACCESS_EXCEPTION);
        StructuredCatch catche = new StructuredCatch(
                catchTypes,
                new Op04StructuredStatement(Block.getBlockFor(true,
                        new StructuredThrow(
                                new ConstructorInvokationExplicit(
                                        new InferredJavaType(TypeConstants.ILLEGALARGUMENT_EXCEPTION, InferredJavaType.Source.CONSTRUCTOR),
                                        TypeConstants.ILLEGALARGUMENT_EXCEPTION,
                                        ListFactory.<Expression>newList(new LValueExpression(caught)))))),
                caught,
                Collections.singleton(identifier)
        );
        trys.getCatchBlocks().add(new Op04StructuredStatement(catche));
        Op04StructuredStatement stm = new Op04StructuredStatement(Block.getBlockFor(true, trys));


        DecompilerComments comments = new DecompilerComments();
        comments.addComment("Works around MethodHandle LDC.");

        return new FakeMethod(name, EnumSet.of(AccessFlagMethod.ACC_STATIC), TypeConstants.METHOD_HANDLE, stm, comments);
    }

    private static Expression from(ConstantPoolEntryMethodHandle cpe) {
        Expression lookup = new StaticFunctionInvokationExplicit(
                new InferredJavaType(
                        TypeConstants.METHOD_HANDLES$LOOKUP, InferredJavaType.Source.EXPRESSION), TypeConstants.METHOD_HANDLES, "lookup",
                Collections.<Expression>emptyList()
        );

        String behaviourName = lookupFunction(cpe.getReferenceKind());
        ConstantPoolEntryMethodRef ref = cpe.getMethodRef();
        MethodPrototype refProto = ref.getMethodPrototype();
        String descriptor = ref.getNameAndTypeEntry().getDescriptor().getValue();

        return new MemberFunctionInvokationExplicit(
                new InferredJavaType(TypeConstants.METHOD_HANDLE, InferredJavaType.Source.EXPRESSION), TypeConstants.METHOD_HANDLES$LOOKUP, lookup, behaviourName
                , ListFactory.newList(
                new Literal(TypedLiteral.getClass(refProto.getClassType())),
                new Literal(TypedLiteral.getString(QuotingUtils.addQuotes(refProto.getName(), false))),
                getMethodType(new Literal(TypedLiteral.getString(QuotingUtils.enquoteString(descriptor))))
        ));

        /*
         * Ideally, we'd wrap this in an IIFE to hide the exceptions.

            public MethodHandle handleLDC() {
               return (MethodHandles.lookup().findStatic(NewLDCTypes.class, "handle", MethodType.fromMethodDescriptorString("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MutableCallSite;Ljava/lang/String;)Ljava/lang/Object;", null))).asFixedArity();
            }

            becomes

            public MethodHandle handleLDC() {
                return (
                        ((Supplier<MethodHandle>)() -> {
                            try {
                                return MethodHandles.lookup().findStatic(NewLDCTypes.class, "handle", MethodType.fromMethodDescriptorString("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MutableCallSite;Ljava/lang/String;)Ljava/lang/Object;", null));
                            } catch (NoSuchMethodException | IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }).get()
                ).asFixedArity();
            }

            Note - this is ugly - It might be nicer to emit a helper method

            T cfr_handle_ldc_exceptions(Supplier<T>).

            then generate

            public MethodHandle handleLDC() {
               return (
               cfr_handle_ldc_exceptions(() ->
               MethodHandles.lookup().findStatic(NewLDCTypes.class, "handle", MethodType.fromMethodDescriptorString("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MutableCallSite;Ljava/lang/String;)Ljava/lang/Object;", null))
               )
               ).asFixedArity();
            }

            This has the advantage of making it VERY obvious that this is an LDC artifact.
         *
         * However, right now we don't support lambda expressions at the point that this is created.  (Would lead to bad structuring.)
         * So we'll return, and the generated code will have bad exception handling.
         *
         *
         */
    }

    // Almost feels like this should be part of methodhandlebehaviour enum, BUT this is specific to Lookup,
    // so no.
    private static String lookupFunction(MethodHandleBehaviour behaviour) {
        switch (behaviour) {
            case GET_FIELD:
                return "findGetter";
            case GET_STATIC:
                return "findStaticGetter";
            case PUT_FIELD:
                return "findSetter";
            case PUT_STATIC:
                return "findStaticSetter";
            case INVOKE_VIRTUAL:
            case INVOKE_INTERFACE: // Probably wrong?
                return "findVirtual";
            case INVOKE_STATIC:
                return "findStatic";
            case INVOKE_SPECIAL:
            case NEW_INVOKE_SPECIAL: // Probably wrong?
                return "findSpecial";
        }
        throw new ConfusedCFRException("Unknown method handle behaviour.");
    }

    // This isn't the right place for this.  Needs moving into a 'HandleUtils' or some such.
    public static Expression getMethodType(Expression descriptorString) {
        return new StaticFunctionInvokationExplicit(
                new InferredJavaType(
                        TypeConstants.METHOD_TYPE, InferredJavaType.Source.EXPRESSION), TypeConstants.METHOD_TYPE, TypeConstants.fromMethodDescriptorString,
                Arrays.asList(descriptorString, new Literal(TypedLiteral.getNull()))
        );
    }
}


