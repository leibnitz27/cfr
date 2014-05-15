package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumper;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumperAnonymousInner;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class ConstructorInvokationAnoynmousInner extends AbstractConstructorInvokation {
    private final MemberFunctionInvokation constructorInvokation;
    private final ClassFile classFile;

    public ConstructorInvokationAnoynmousInner(MemberFunctionInvokation constructorInvokation,
                                               InferredJavaType inferredJavaType, List<Expression> args,
                                               DCCommonState dcCommonState) {
        super(inferredJavaType, constructorInvokation.getFunction(), args);
        this.constructorInvokation = constructorInvokation;
        /*
         * As much as I'd rather not tie this to its use, we have to make sure that the target variables etc
         * are available at the time of usage, so we can hide anonymous inner member clones.
         */
        ClassFile classFile = null;
        try {
            classFile = dcCommonState.getClassFile(constructorInvokation.getMethodPrototype().getReturnType());
        } catch (CannotLoadClassException e) {
        }
        this.classFile = classFile;
        if (classFile != null) {
            classFile.noteAnonymousUse(this);
        }
    }

    protected ConstructorInvokationAnoynmousInner(ConstructorInvokationAnoynmousInner other, CloneHelper cloneHelper) {
        super(other, cloneHelper);
        this.constructorInvokation = (MemberFunctionInvokation) cloneHelper.replaceOrClone(other.constructorInvokation);
        this.classFile = other.classFile;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ConstructorInvokationAnoynmousInner(this, cloneHelper);
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER; // TODO - really?
    }

    @Override
    public Dumper dumpInner(Dumper d) {

        // We need the inner classes on the anonymous class (!)
        ConstantPool cp = constructorInvokation.getCp();

        ClassFile anonymousClassFile = null;
        JavaTypeInstance typeInstance = getTypeInstance();
        try {
            anonymousClassFile = cp.getDCCommonState().getClassFile(typeInstance);
        } catch (CannotLoadClassException e) {
            anonymousClassFile = classFile;
        }
        if (anonymousClassFile != classFile) {
            throw new IllegalStateException("Inner class got unexpected class file - revert this change");
        }

        d.print("new ");
        ClassFileDumperAnonymousInner cfd = new ClassFileDumperAnonymousInner();
        List<Expression> args = getArgs();
        MethodPrototype prototype = this.constructorInvokation.getMethodPrototype();
        try {
            if (classFile != null) prototype = classFile.getMethodByPrototype(prototype).getMethodPrototype();
        } catch (NoSuchMethodException e) {
        }

        cfd.dumpWithArgs(classFile, prototype, args, false, d);
        d.removePendingCarriageReturn();
        return d;
    }

    public Dumper dumpForEnum(Dumper d) {
        // ConstantPool cp = constructorInvokation.getCp();
        ClassFile anonymousClassFile = classFile; //cp.getCFRState().getClassFile(getTypeInstance());
        ClassFileDumperAnonymousInner cfd = new ClassFileDumperAnonymousInner();
        List<Expression> args = getArgs();

        /* Enums always have 2 initial arguments */
        return cfd.dumpWithArgs(anonymousClassFile, null, args.subList(2, args.size()), true, d);
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (!(o instanceof ConstructorInvokationAnoynmousInner)) return false;
        if (!super.equivalentUnder(o, constraint)) return false;
        ConstructorInvokationAnoynmousInner other = (ConstructorInvokationAnoynmousInner) o;
        if (!constraint.equivalent(constructorInvokation, other.constructorInvokation)) return false;
        return true;
    }

}
