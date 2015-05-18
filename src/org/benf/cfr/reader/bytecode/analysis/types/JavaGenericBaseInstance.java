package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;

import java.util.List;

public interface JavaGenericBaseInstance extends JavaTypeInstance {
    public JavaTypeInstance getBoundInstance(GenericTypeBinder genericTypeBinder);

    public boolean tryFindBinding(JavaTypeInstance other, GenericTypeBinder target);

    public boolean hasUnbound();

    public boolean hasL01Wildcard();

    public JavaTypeInstance getWithoutL01Wildcard();

    public boolean hasForeignUnbound(ConstantPool cp);

    public List<JavaTypeInstance> getGenericTypes();
}
