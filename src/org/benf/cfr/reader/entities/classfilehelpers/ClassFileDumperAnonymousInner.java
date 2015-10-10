package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class ClassFileDumperAnonymousInner extends AbstractClassFileDumper {

    public ClassFileDumperAnonymousInner() {
        super(null);
    }

    @Override
    public Dumper dump(ClassFile classFile, InnerClassDumpType innerClass, Dumper d) {
        return dumpWithArgs(classFile, null, ListFactory.<Expression>newList(), false, d);
    }

    public Dumper dumpWithArgs(ClassFile classFile, MethodPrototype usedMethod, List<Expression> args, boolean isEnum, Dumper d) {

        if (classFile == null) {
            d.print("/* Unavailable Anonymous Inner Class!! */");
            return d;
        }

        if (!d.canEmitClass(classFile.getClassType())) {
            return d;
        }

        if (!isEnum) {
            ClassSignature signature = classFile.getClassSignature();
            if (signature.getInterfaces().isEmpty()) {
                JavaTypeInstance superclass = signature.getSuperClass();
                d.dump(superclass);
            } else {
                JavaTypeInstance interfaceType = signature.getInterfaces().get(0);
                d.dump(interfaceType);
            }
        }
        if (!(isEnum && args.isEmpty())) {
            d.print("(");
            boolean first = true;
            for (int i = 0, len = args.size(); i < len; ++i) {
                if (usedMethod != null && usedMethod.isHiddenArg(i)) continue;
                Expression arg = args.get(i);
                first = StringUtils.comma(first, d);
                d.dump(arg);
            }
            d.print(")");
        }
        d.print("{\n");
        d.indent(1);
        int outcrs = d.getOutputCount();


        List<ClassFileField> fields = classFile.getFields();
        for (ClassFileField field : fields) {
            if (!field.shouldNotDisplay()) field.dump(d);
        }
        List<Method> methods = classFile.getMethods();
        if (!methods.isEmpty()) {
            for (Method method : methods) {
                if (method.isHiddenFromDisplay()) continue;
                // You can't see constructors on anonymous inner classes.
                if (method.isConstructor()) continue;
                d.newln();
                method.dump(d, true);
            }
        }
        classFile.dumpNamedInnerClasses(d);
        d.indent(-1);

        if (d.getOutputCount() == outcrs) {
            d.removePendingCarriageReturn();
        }
        d.print("}\n");

        return d;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {

    }
}
