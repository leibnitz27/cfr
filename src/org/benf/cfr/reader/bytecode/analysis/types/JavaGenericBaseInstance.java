package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 25/02/2013
 * Time: 19:22
 */
public interface JavaGenericBaseInstance extends JavaTypeInstance {
    public JavaTypeInstance getBoundInstance(GenericTypeBinder genericTypeBinder);

    public boolean tryFindBinding(JavaTypeInstance other, GenericTypeBinder target);

    public boolean hasUnbound();

    public boolean hasForeignUnbound(ConstantPool cp);
}
