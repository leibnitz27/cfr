package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumperAnonymousInner;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class ConstructorInvokationAnonymousInner extends AbstractConstructorInvokation {
    private final MemberFunctionInvokation constructorInvokation;
    private final ClassFile classFile;
    private final JavaTypeInstance anonymousTypeInstance;

    public ConstructorInvokationAnonymousInner(MemberFunctionInvokation constructorInvokation,
                                               InferredJavaType inferredJavaType, List<Expression> args,
                                               DCCommonState dcCommonState, JavaTypeInstance anonymousTypeInstance) {
        super((inferredJavaType), constructorInvokation.getFunction(), args);
        this.constructorInvokation = constructorInvokation;
        this.anonymousTypeInstance = anonymousTypeInstance;
        /*
         * As much as I'd rather not tie this to its use, we have to make sure that the target variables etc
         * are available at the time of usage, so we can hide anonymous inner member clones.
         */
        ClassFile classFile = null;
        try {
            classFile = dcCommonState.getClassFile(constructorInvokation.getMethodPrototype().getReturnType());
        } catch (CannotLoadClassException e) {
            // Can't find class - live with it.
        }
        this.classFile = classFile;
    }

    private ConstructorInvokationAnonymousInner(ConstructorInvokationAnonymousInner other, CloneHelper cloneHelper) {
        super(other, cloneHelper);
        this.constructorInvokation = (MemberFunctionInvokation) cloneHelper.replaceOrClone(other.constructorInvokation);
        this.classFile = other.classFile;
        this.anonymousTypeInstance = other.anonymousTypeInstance;
    }

    public ClassFile getClassFile() {
        return classFile;
    }


    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ConstructorInvokationAnonymousInner(this, cloneHelper);
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER; // TODO - really?
    }

    @Override
    public Dumper dumpInner(Dumper d) {

        // We need the inner classes on the anonymous class (!)
        MethodPrototype prototype = improveMethodPrototype(d);

        ClassFileDumperAnonymousInner cfd = new ClassFileDumperAnonymousInner();
        List<Expression> args = getArgs();
        cfd.dumpWithArgs(classFile, prototype, args, false, d);
        d.removePendingCarriageReturn();
        return d;
    }

    private MethodPrototype improveMethodPrototype(Dumper d) {
        ConstantPool cp = constructorInvokation.getCp();

        ClassFile anonymousClassFile;
        try {
            anonymousClassFile = cp.getDCCommonState().getClassFile(anonymousTypeInstance);
        } catch (CannotLoadClassException e) {
            anonymousClassFile = classFile;
        }
        if (anonymousClassFile != classFile) {
            throw new IllegalStateException("Inner class got unexpected class file - revert this change");
        }

        d.print("new ");
        MethodPrototype prototype = this.constructorInvokation.getMethodPrototype();
        try {
            if (classFile != null) prototype = classFile.getMethodByPrototype(prototype).getMethodPrototype();
        } catch (NoSuchMethodException ignore) {
        }
        return prototype;
    }

    public void dumpForEnum(Dumper d) {
        // ConstantPool cp = constructorInvokation.getCp();
        ClassFileDumperAnonymousInner cfd = new ClassFileDumperAnonymousInner();
        List<Expression> args = getArgs();

        /* Enums always have 2 initial arguments */
        cfd.dumpWithArgs(classFile, null, args.subList(2, args.size()), true, d);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;

        if (!(o instanceof ConstructorInvokationAnonymousInner)) return false;
        ConstructorInvokationAnonymousInner other = (ConstructorInvokationAnonymousInner) o;

        // This is particularly hairy - two identical looking anonymous classes ARE NOT THE SAME.
        if (getClassFile() != other.getClassFile()) return false;
        if (!getTypeInstance().equals(other.getTypeInstance())) return false;
        //noinspection RedundantIfStatement
        if (!getArgs().equals(other.getArgs())) return false;
        return true;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (!(o instanceof ConstructorInvokationAnonymousInner)) return false;
        if (!super.equivalentUnder(o, constraint)) return false;
        ConstructorInvokationAnonymousInner other = (ConstructorInvokationAnonymousInner) o;
        //noinspection RedundantIfStatement
        if (!constraint.equivalent(constructorInvokation, other.constructorInvokation)) return false;
        return true;
    }

}
