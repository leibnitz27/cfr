package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
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
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:26
 */
public class ConstructorInvokationAnoynmousInner extends AbstractConstructorInvokation {
    private final MemberFunctionInvokation constructorInvokation;

    public ConstructorInvokationAnoynmousInner(MemberFunctionInvokation constructorInvokation,
                                               InferredJavaType inferredJavaType, List<Expression> args) {
        super(inferredJavaType, args);
        this.constructorInvokation = constructorInvokation;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ConstructorInvokationAnoynmousInner((MemberFunctionInvokation) cloneHelper.replaceOrClone(constructorInvokation), getInferredJavaType(), cloneHelper.replaceOrClone(getArgs()));
    }

    @Override
    public Dumper dump(Dumper d) {
        // We need the inner classes on the anonymous class (!)
        ConstantPool cp = constructorInvokation.getCp();
        ClassFile anonymousClassFile = cp.getCFRState().getClassFile(getTypeInstance(), true);

        d.print("new ");
        ClassFileDumperAnonymousInner cfd = new ClassFileDumperAnonymousInner();
        List<Expression> args = getArgs();
        MethodPrototype prototype = this.constructorInvokation.getMethodPrototype();
        try {
            prototype = anonymousClassFile.getMethodByPrototype(prototype).getMethodPrototype();
        } catch (NoSuchMethodException e) {
        }

        return cfd.dumpWithArgs(anonymousClassFile, true, args.subList(prototype.getNumHiddenArguments(), args.size()), false, d);
    }

    public Dumper dumpForEnum(Dumper d) {
        ConstantPool cp = constructorInvokation.getCp();
        ClassFile anonymousClassFile = cp.getCFRState().getClassFile(getTypeInstance(), true);
        ClassFileDumperAnonymousInner cfd = new ClassFileDumperAnonymousInner();
        List<Expression> args = getArgs();

        /* Enums always have 2 initial arguments */
        return cfd.dumpWithArgs(anonymousClassFile, true, args.subList(2, args.size()), true, d);
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
