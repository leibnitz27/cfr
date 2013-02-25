package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 25/02/2013
 * Time: 19:22
 */
public interface JavaGenericBaseInstance extends JavaTypeInstance {
    public JavaTypeInstance getBoundInstance(GenericTypeBinder genericTypeBinder);
}
