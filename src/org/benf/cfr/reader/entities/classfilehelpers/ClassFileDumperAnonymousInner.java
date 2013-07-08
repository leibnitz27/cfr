package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.CommaHelp;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 09/05/2013
 * Time: 05:50
 */
public class ClassFileDumperAnonymousInner extends AbstractClassFileDumper {

    @Override
    public Dumper dump(ClassFile classFile, boolean innerClass, Dumper d) {
        return dumpWithArgs(classFile, innerClass, ListFactory.<Expression>newList(), false, d);
    }

    public Dumper dumpWithArgs(ClassFile classFile, boolean innerClass, List<Expression> args, boolean isEnum, Dumper d) {

        ConstantPool cp = classFile.getConstantPool();

        if (!isEnum) {
            ClassSignature signature = classFile.getClassSignature();
            if (signature.getInterfaces().isEmpty()) {
                JavaTypeInstance superclass = signature.getSuperClass();
                d.print(superclass.toString());
            } else {
                JavaTypeInstance interfaceType = signature.getInterfaces().get(0);
                d.print(interfaceType.toString());
            }
        }
        if (!(isEnum && args.isEmpty())) {
            d.print("(");
            boolean first = true;
            for (Expression arg : args) {
                first = CommaHelp.comma(first, d);
                d.dump(arg);
            }
            d.print(")");
        }
        d.print("{\n");
        d.indent(1);


        List<ClassFileField> fields = classFile.getFields();
        for (ClassFileField field : fields) {
            field.dump(d, cp);
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
        d.newln();
        classFile.dumpNamedInnerClasses(d);
        d.indent(-1);
        d.print("}\n");

        return d;
    }
}
