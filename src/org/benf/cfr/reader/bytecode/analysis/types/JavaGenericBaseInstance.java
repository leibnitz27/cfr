package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;

import java.util.List;

public interface JavaGenericBaseInstance extends JavaTypeInstance {
    JavaTypeInstance getBoundInstance(GenericTypeBinder genericTypeBinder);

    boolean tryFindBinding(JavaTypeInstance other, GenericTypeBinder target);

    boolean hasUnbound();

    boolean hasL01Wildcard();

    JavaTypeInstance getWithoutL01Wildcard();

    boolean hasForeignUnbound(ConstantPool cp, int depth, boolean noWildcard);

    List<JavaTypeInstance> getGenericTypes();
}
