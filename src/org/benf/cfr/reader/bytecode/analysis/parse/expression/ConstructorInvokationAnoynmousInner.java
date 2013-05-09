package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntryClass;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodRef;
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
    private final JavaTypeInstance clazz;
    private final ConstantPool cp;

    public ConstructorInvokationAnoynmousInner(ConstantPool cp,
                                               ConstantPoolEntryClass type, List<Expression> args) {
        super(type, args);
        this.cp = cp;
        this.clazz = type.getTypeInstance();
    }

    @Override
    public Dumper dump(Dumper d) {
        // We need the inner classes on the anonymous class (!)
        ClassFile anonymousClassFile = cp.getCFRState().getClassFile(clazz, true);

        d.print("new ");
        ClassFileDumper cfd = new ClassFileDumperAnonymousInner();
        return cfd.dump(anonymousClassFile, true, d);
    }


}
