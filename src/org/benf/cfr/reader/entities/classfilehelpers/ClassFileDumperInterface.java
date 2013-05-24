package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.util.MiscConstants;
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
            AccessFlag.ACC_PUBLIC, AccessFlag.ACC_PRIVATE, AccessFlag.ACC_PROTECTED, AccessFlag.ACC_STATIC, AccessFlag.ACC_FINAL
    };

    private void dumpHeader(ClassFile c, Dumper d) {
        StringBuilder sb = new StringBuilder();
        sb.append(getAccessFlagsString(c.getAccessFlags(), dumpableAccessFlagsInterface));

        ClassSignature signature = c.getClassSignature();

        sb.append("interface ").append(c.getThisClassConstpoolEntry().getTypeInstance());
        sb.append(getFormalParametersText(signature));
        sb.append("\n");
        d.print(sb.toString());
        List<JavaTypeInstance> interfaces = signature.getInterfaces();
        if (!interfaces.isEmpty()) {
            d.print("extends ");
            int size = interfaces.size();
            for (int x = 0; x < size; ++x) {
                JavaTypeInstance iface = interfaces.get(x);
                d.print("" + iface + (x < (size - 1) ? ",\n" : "\n"));
            }
        }
        d.removePendingCarriageReturn().print(" ");
    }

    @Override
    public Dumper dump(ClassFile classFile, boolean innerClass, Dumper d) {
        ConstantPool cp = classFile.getConstantPool();
        if (!innerClass) {
            d.print(MiscConstants.CFR_HEADER);
            d.print("package ").print(classFile.getThisClassConstpoolEntry().getPackageName()).endCodeln().newln();
            dumpImports(d, cp.getClassCache(), classFile);
        }

        dumpHeader(classFile, d);
        d.print("{\n");
        d.indent(1);
        // Horrid, but an interface can have fields....
        List<ClassFileField> fields = classFile.getFields();
        for (ClassFileField field : fields) {
            field.dump(d, cp);
        }
        List<Method> methods = classFile.getMethods();
        if (!methods.isEmpty()) {
            for (Method meth : methods) {
                d.newln();
                // Java 8 supports 'defender' interfaces, i.e. method bodies on interfaces (eww).
                meth.dump(d, false);
            }
        }
        d.newln();
        classFile.dumpNamedInnerClasses(d);
        d.indent(-1);
        d.print("}\n");
        return d;
    }
}
