package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 09/05/2013
 * Time: 05:50
 */
public class ClassFileDumperInterface extends AbstractClassFileDumper {

    private static final AccessFlag[] dumpableAccessFlagsInterface = new AccessFlag[]{
            AccessFlag.ACC_PUBLIC, AccessFlag.ACC_PRIVATE, AccessFlag.ACC_PROTECTED, AccessFlag.ACC_STRICT, AccessFlag.ACC_STATIC, AccessFlag.ACC_FINAL
    };

    public ClassFileDumperInterface(DCCommonState dcCommonState) {
        super(dcCommonState);
    }

    private void dumpHeader(ClassFile c, Dumper d) {

        d.print(getAccessFlagsString(c.getAccessFlags(), dumpableAccessFlagsInterface));

        ClassSignature signature = c.getClassSignature();

        d.print("interface ").dump(c.getThisClassConstpoolEntry().getTypeInstance());
        getFormalParametersText(signature, d);
        d.print("\n");

        List<JavaTypeInstance> interfaces = signature.getInterfaces();
        if (!interfaces.isEmpty()) {
            d.print("extends ");
            int size = interfaces.size();
            for (int x = 0; x < size; ++x) {
                JavaTypeInstance iface = interfaces.get(x);
                d.dump(iface).print((x < (size - 1) ? ",\n" : "\n"));
            }
        }
        d.removePendingCarriageReturn().print(" ");
    }

    @Override
    public Dumper dump(ClassFile classFile, boolean innerClass, Dumper d) {
        ConstantPool cp = classFile.getConstantPool();
        if (!innerClass) {
            dumpTopHeader(classFile, d);
            d.print("package ").print(classFile.getThisClassConstpoolEntry().getPackageName()).endCodeln().newln();
            dumpImports(d, classFile);
        }

        dumpAnnotations(classFile, d);
        dumpHeader(classFile, d);
        boolean first = true;
        d.print("{\n");
        d.indent(1);
        // Horrid, but an interface can have fields....
        List<ClassFileField> fields = classFile.getFields();
        for (ClassFileField field : fields) {
            field.dump(d, cp);
            first = false;
        }
        List<Method> methods = classFile.getMethods();
        if (!methods.isEmpty()) {
            for (Method method : methods) {
                if (method.isHiddenFromDisplay()) continue;
                if (!first) {
                    d.newln();
                }
                first = false;
                // Java 8 supports 'defender' interfaces, i.e. method bodies on interfaces (eww).
                method.dump(d, false);
            }
        }
        classFile.dumpNamedInnerClasses(d);
        d.indent(-1);
        d.print("}\n");
        return d;
    }
}
