package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 05/11/2013
 * Time: 17:17
 */
public class TypeUsageCollector {
    private final JavaRefTypeInstance analysisType;
    private final Set<JavaRefTypeInstance> typeInstanceSet = SetFactory.newSet();
    private final Set<JavaTypeInstance> seen = SetFactory.newSet();

    public TypeUsageCollector(ClassFile analysisClass) {
        this.analysisType = (JavaRefTypeInstance) analysisClass.getClassType().getDeGenerifiedType();
    }

    public void collectRefType(JavaRefTypeInstance type) {
        typeInstanceSet.add(type);
    }

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

    public void collect(Collection<? extends JavaTypeInstance> types) {
        if (types == null) return;
        for (JavaTypeInstance type : types) collect(type);
    }

    public void collectFrom(TypeUsageCollectable collectable) {
        if (collectable != null) collectable.collectTypeUsages(this);
    }

    public void collectFrom(Collection<? extends TypeUsageCollectable> collectables) {
        if (collectables != null) {
            for (TypeUsageCollectable collectable : collectables) {
                if (collectable != null) collectable.collectTypeUsages(this);
            }
        }
    }

    public TypeUsageInformation getTypeUsageInformation() {
        /* Figure out what the imports are */
        return new TypeUsageInformationImpl(analysisType, typeInstanceSet);
    }
}
