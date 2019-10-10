package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.attributes.AttributeCode;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.collections.ListFactory;
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

        /*
         * Why might we try to emit an anonymous class twice?  If it's been declared in a field initialiser
         * it'll be expanded into copies in constructors.
         *
         * If we've FAILED to re-gather into the field initialiser, we'll be here.
         */
        if (!d.canEmitClass(classFile.getClassType())) {
            d.print("/* invalid duplicate definition of identical inner class */");
            return d;
        }

        if (!isEnum) {
            JavaTypeInstance typeInstance = ClassFile.getAnonymousTypeBase(classFile);
            d.dump(typeInstance);
        }
        if (!(isEnum && args.isEmpty())) {
            d.separator("(");
            boolean first = true;
            for (int i = 0, len = args.size(); i < len; ++i) {
                if (usedMethod != null && usedMethod.isHiddenArg(i)) continue;
                Expression arg = args.get(i);
                first = StringUtils.comma(first, d);
                d.dump(arg);
            }
            d.separator(")");
        }
        d.print("{").newln();
        d.indent(1);
        int outcrs = d.getOutputCount();


        List<ClassFileField> fields = classFile.getFields();
        for (ClassFileField field : fields) {
            if (!field.shouldNotDisplay()) field.dump(d, classFile);
        }
        List<Method> methods = classFile.getMethods();
        if (!methods.isEmpty()) {
            for (Method method : methods) {
                if (method.hiddenState() != Method.Visibility.Visible) continue;
                // Constructors on anonymous inners don't have arguments.
                // (the initializer block ends up getting dumped into the first constructor).
                if (method.isConstructor()) {
                    AttributeCode anonymousConstructor = method.getCodeAttribute();
                    if (anonymousConstructor != null) {
                        // But we don't bother dumping if it's empty...
                        Op04StructuredStatement stm = anonymousConstructor.analyse();
                        if (!stm.isEmptyInitialiser()) {
                            anonymousConstructor.dump(d);
                        }
                    }
                    continue;
                }
                d.newln();
                method.dump(d, true);
            }
        }
        classFile.dumpNamedInnerClasses(d);
        d.indent(-1);

        if (d.getOutputCount() == outcrs) {
            d.removePendingCarriageReturn();
        }
        d.print("}").newln();

        return d;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {

    }
}
