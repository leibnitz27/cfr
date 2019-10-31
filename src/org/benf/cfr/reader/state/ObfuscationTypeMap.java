package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.innerclass.InnerClassAttributeInfo;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.List;

public interface ObfuscationTypeMap {
    boolean providesInnerClassInfo();

    JavaTypeInstance get(JavaTypeInstance type);

    UnaryFunction<JavaTypeInstance, JavaTypeInstance> getter();

    List<InnerClassAttributeInfo> getInnerClassInfo(JavaTypeInstance classType);
}
