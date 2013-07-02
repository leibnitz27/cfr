package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.getopt.CFRState;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 09/05/2013
 * Time: 05:50
 * <p/>
 * This isn't static - we populate it from the decoded enum information.
 */
public class ClassFileDumperEnum extends AbstractClassFileDumper {
    private static final AccessFlag[] dumpableAccessFlagsEnum = new AccessFlag[]{
            AccessFlag.ACC_PUBLIC, AccessFlag.ACC_PRIVATE, AccessFlag.ACC_PROTECTED, AccessFlag.ACC_STATIC, AccessFlag.ACC_ABSTRACT
    };

    private final CFRState cfrState;
    private final List<Pair<StaticVariable, ConstructorInvokationSimple>> entries;

    public ClassFileDumperEnum(CFRState cfrState, List<Pair<StaticVariable, ConstructorInvokationSimple>> entries) {
        this.cfrState = cfrState;
        this.entries = entries;
    }

    private static void dumpHeader(ClassFile c, Dumper d) {
        StringBuilder sb = new StringBuilder();
        sb.append(getAccessFlagsString(c.getAccessFlags(), dumpableAccessFlagsEnum));

        sb.append("enum ").append(c.getThisClassConstpoolEntry().getTypeInstance());
        sb.append(" {\n");
        d.print(sb.toString());
    }

    private static void dumpEntry(Dumper d, Pair<StaticVariable, ConstructorInvokationSimple> entry, boolean last) {
        StaticVariable staticVariable = entry.getFirst();
        ConstructorInvokationSimple constructorInvokationSimple = entry.getSecond();
        d.print(staticVariable.getVarName());
        List<Expression> args = constructorInvokationSimple.getArgs();
        if (args.size() > 2) {
            d.print('(');
            for (int x = 2, len = args.size(); x < len; ++x) {
                if (x > 2) d.print(", ");
                d.dump(args.get(x));
            }
            d.print(')');
        }
        if (last) {
            d.endCodeln();
        } else {
            d.print(",\n");
        }
    }

    @Override
    public Dumper dump(ClassFile classFile, boolean innerClass, Dumper d) {
        ConstantPool cp = classFile.getConstantPool();
        if (!innerClass) {
            d.print(getCFRHeader(cfrState));
            d.print("package ").print(classFile.getThisClassConstpoolEntry().getPackageName()).endCodeln().newln();
            dumpImports(d, cp.getClassCache(), classFile);
        }

        dumpHeader(classFile, d);
        d.print("{\n");
        d.indent(1);

        for (int x = 0, len = entries.size(); x < len; ++x) {
            dumpEntry(d, entries.get(x), (x == len - 1));
        }
        d.print("\n");

        List<ClassFileField> fields = classFile.getFields();
        for (ClassFileField field : fields) {
            if (field.isHidden()) continue;
            field.dump(d, cp);
        }
        List<Method> methods = classFile.getMethods();
        if (!methods.isEmpty()) {
            for (Method method : methods) {
                if (method.isHiddenFromDisplay()) continue;
                d.newln();
                method.dump(d, true);
            }
        }
        d.newln();
        classFile.dumpNamedInnerClasses(d);
        d.indent(-1);
        d.print("}\n");

        return d;
    }
}
