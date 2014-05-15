package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractConstructorInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationAnoynmousInner;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * This isn't static - we populate it from the decoded enum information.
 */
public class ClassFileDumperEnum extends AbstractClassFileDumper {
    private static final AccessFlag[] dumpableAccessFlagsEnum = new AccessFlag[]{
            AccessFlag.ACC_PUBLIC, AccessFlag.ACC_PRIVATE, AccessFlag.ACC_PROTECTED, AccessFlag.ACC_STRICT, AccessFlag.ACC_STATIC
    };

    private final List<Pair<StaticVariable, AbstractConstructorInvokation>> entries;

    public ClassFileDumperEnum(DCCommonState dcCommonState, List<Pair<StaticVariable, AbstractConstructorInvokation>> entries) {
        super(dcCommonState);
        this.entries = entries;
    }

    private static void dumpHeader(ClassFile c, Dumper d) {
        d.print(getAccessFlagsString(c.getAccessFlags(), dumpableAccessFlagsEnum));

        d.print("enum ").dump(c.getThisClassConstpoolEntry().getTypeInstance()).print(" ");

        ClassSignature signature = c.getClassSignature();
        List<JavaTypeInstance> interfaces = signature.getInterfaces();
        if (!interfaces.isEmpty()) {
            d.print("implements ");
            int size = interfaces.size();
            for (int x = 0; x < size; ++x) {
                JavaTypeInstance iface = interfaces.get(x);
                d.dump(iface).print((x < (size - 1) ? ",\n" : "\n"));
            }
        }
    }

    private static void dumpEntry(Dumper d, Pair<StaticVariable, AbstractConstructorInvokation> entry, boolean last) {
        StaticVariable staticVariable = entry.getFirst();
        AbstractConstructorInvokation constructorInvokation = entry.getSecond();
        d.print(staticVariable.getVarName());

        if (constructorInvokation instanceof ConstructorInvokationSimple) {
            List<Expression> args = constructorInvokation.getArgs();
            if (args.size() > 2) {
                d.print('(');
                for (int x = 2, len = args.size(); x < len; ++x) {
                    if (x > 2) d.print(", ");
                    d.dump(args.get(x));
                }
                d.print(')');
            }
        } else if (constructorInvokation instanceof ConstructorInvokationAnoynmousInner) {
            ((ConstructorInvokationAnoynmousInner) constructorInvokation).dumpForEnum(d);
        } else {
            // ???
        }
        if (last) {
            d.endCodeln();
        } else {
            d.print(",\n");
        }
    }

    @Override
    public Dumper dump(ClassFile classFile, boolean innerClass, Dumper d) {

        if (!innerClass) {
            dumpTopHeader(classFile, d);
            dumpImports(d, classFile);
        }

        dumpAnnotations(classFile, d);
        dumpHeader(classFile, d);
        d.print("{\n");
        d.indent(1);

        for (int x = 0, len = entries.size(); x < len; ++x) {
            dumpEntry(d, entries.get(x), (x == len - 1));
        }
        d.print("\n");

        List<ClassFileField> fields = classFile.getFields();
        for (ClassFileField field : fields) {
            if (field.shouldNotDisplay()) continue;
            field.dump(d);
        }
        List<Method> methods = classFile.getMethods();
        if (!methods.isEmpty()) {
            for (Method method : methods) {
                if (method.isHiddenFromDisplay()) continue;
                d.newln();
                method.dump(d, true);
            }
        }
        classFile.dumpNamedInnerClasses(d);
        d.indent(-1);
        d.print("}\n");

        return d;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        for (Pair<StaticVariable, AbstractConstructorInvokation> entry : entries) {
            collector.collectFrom(entry.getFirst());
            collector.collectFrom(entry.getSecond());
        }
    }
}
