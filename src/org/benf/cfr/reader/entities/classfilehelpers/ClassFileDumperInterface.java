package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.FormalTypeParameter;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class ClassFileDumperInterface extends AbstractClassFileDumper {

    private static final AccessFlag[] dumpableAccessFlagsInterface = new AccessFlag[]{
            AccessFlag.ACC_PUBLIC, AccessFlag.ACC_PRIVATE, AccessFlag.ACC_PROTECTED, AccessFlag.ACC_STRICT, AccessFlag.ACC_STATIC, AccessFlag.ACC_FINAL
    };

    public ClassFileDumperInterface(DCCommonState dcCommonState) {
        super(dcCommonState);
    }

    private void dumpHeader(ClassFile c, InnerClassDumpType innerClassDumpType, Dumper d) {

        d.print(getAccessFlagsString(c.getAccessFlags(), dumpableAccessFlagsInterface));

        d.print("interface ");
        c.dumpClassIdentity(d);
        d.newln();

        ClassSignature signature = c.getClassSignature();
        List<JavaTypeInstance> interfaces = signature.getInterfaces();
        if (!interfaces.isEmpty()) {
            d.print("extends ");
            int size = interfaces.size();
            for (int x = 0; x < size; ++x) {
                JavaTypeInstance iface = interfaces.get(x);
                d.dump(iface).print((x < (size - 1) ? "," : "")).newln();
            }
        }
        d.removePendingCarriageReturn().print(" ");
    }

    @Override
    public Dumper dump(ClassFile classFile, InnerClassDumpType innerClass, Dumper d) {

        if (!innerClass.isInnerClass()) {
            dumpTopHeader(classFile, d);
            dumpImports(d, classFile);
        }

        dumpAnnotations(classFile, d);
        dumpHeader(classFile, innerClass, d);
        boolean first = true;
        d.separator("{").newln();
        d.indent(1);
        // Horrid, but an interface can have fields....
        List<ClassFileField> fields = classFile.getFields();
        for (ClassFileField field : fields) {
            field.dump(d, classFile);
            first = false;
        }
        dumpMethods(classFile, d, first, false);
        classFile.dumpNamedInnerClasses(d);
        d.indent(-1);
        d.print("}").newln();
        return d;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {

    }
}
