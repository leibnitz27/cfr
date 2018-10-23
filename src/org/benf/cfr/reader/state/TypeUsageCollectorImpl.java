package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.Set;

public class TypeUsageCollectorImpl extends AbstractTypeUsageCollector {
    private final JavaRefTypeInstance analysisType;
    private final Set<JavaRefTypeInstance> typeInstanceSet = SetFactory.newSet();
    private final Set<JavaTypeInstance> seen = SetFactory.newSet();

    public TypeUsageCollectorImpl(ClassFile analysisClass) {
        this.analysisType = (JavaRefTypeInstance) analysisClass.getClassType().getDeGenerifiedType();
    }

    @Override
    public void collectRefType(JavaRefTypeInstance type) {
        typeInstanceSet.add(type);
    }

    @Override
    public void collect(JavaTypeInstance type) {
        if (type == null) return;
        if (seen.add(type)) {
            type.collectInto(this);
            InnerClassInfo innerClassInfo = type.getInnerClassHereInfo();
            if (innerClassInfo.isInnerClass()) {
                collect(innerClassInfo.getOuterClass());
            }
        }
    }

    @Override
    public TypeUsageInformation getTypeUsageInformation() {
        /* Figure out what the imports are */
        return new TypeUsageInformationImpl(analysisType, typeInstanceSet);
    }
}
