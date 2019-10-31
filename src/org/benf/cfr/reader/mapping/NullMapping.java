package org.benf.cfr.reader.mapping;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.innerclass.InnerClassAttributeInfo;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class NullMapping implements ObfuscationMapping {
    public static NullMapping INSTANCE = new NullMapping();

    private static UnaryFunction<JavaTypeInstance, JavaTypeInstance> id = new UnaryFunction<JavaTypeInstance, JavaTypeInstance>() {
        @Override
        public JavaTypeInstance invoke(JavaTypeInstance arg) {
            return arg;
        }
    };

    @Override
    public UnaryFunction<JavaTypeInstance, JavaTypeInstance> getter() {
        return id;
    }

    @Override
    public boolean providesInnerClassInfo() {
        return false;
    }

    @Override
    public Dumper wrap(Dumper d) {
        return d;
    }

    @Override
    public JavaTypeInstance get(JavaTypeInstance t) {
        return t;
    }

    @Override
    public List<JavaTypeInstance> get(List<JavaTypeInstance> types) {
        return types;
    }

    @Override
    public List<InnerClassAttributeInfo> getInnerClassInfo(JavaTypeInstance classType) {
        return null;
    }
}
