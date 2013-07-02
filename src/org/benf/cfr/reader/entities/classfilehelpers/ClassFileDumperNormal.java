package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.getopt.CFRState;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 09/05/2013
 * Time: 05:50
 */
public class ClassFileDumperNormal extends AbstractClassFileDumper {

    private static final AccessFlag[] dumpableAccessFlagsClass = new AccessFlag[]{
            AccessFlag.ACC_PUBLIC, AccessFlag.ACC_PRIVATE, AccessFlag.ACC_PROTECTED, AccessFlag.ACC_STATIC, AccessFlag.ACC_FINAL, AccessFlag.ACC_ABSTRACT
    };
    private final CFRState cfrState;

    public ClassFileDumperNormal(CFRState cfrState) {
        this.cfrState = cfrState;
    }

    private void dumpHeader(ClassFile c, Dumper d) {
        StringBuilder sb = new StringBuilder();
        sb.append(getAccessFlagsString(c.getAccessFlags(), dumpableAccessFlagsClass));

        ClassSignature signature = c.getClassSignature();

        sb.append("class ").append(c.getThisClassConstpoolEntry().getTypeInstance());
        sb.append(getFormalParametersText(signature));
        sb.append("\n");
        d.print(sb.toString());

        JavaTypeInstance superClass = signature.getSuperClass();
        if (superClass != null) {
            if (!superClass.getRawName().equals("java.lang.Object")) {
                d.print("extends " + superClass + "\n");
            }
        }

        List<JavaTypeInstance> interfaces = signature.getInterfaces();
        if (!interfaces.isEmpty()) {
            d.print("implements ");
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
            d.print(getCFRHeader(cfrState));
            d.print("package ").print(classFile.getThisClassConstpoolEntry().getPackageName()).endCodeln().newln();
            dumpImports(d, cp.getClassCache(), classFile);
        }

        dumpHeader(classFile, d);
        d.print("{\n");
        d.indent(1);

        List<ClassFileField> fields = classFile.getFields();
        for (ClassFileField field : fields) {
            if (!field.isHidden()) {
                field.dump(d, cp);
            }
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
