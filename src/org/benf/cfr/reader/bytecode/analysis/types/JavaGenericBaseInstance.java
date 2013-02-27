package org.benf.cfr.reader.bytecode.analysis.types;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 25/02/2013
 * Time: 19:22
 */
public interface JavaGenericBaseInstance extends JavaTypeInstance {
    public JavaTypeInstance getBoundInstance(GenericTypeBinder genericTypeBinder);

    public void tryFindBinding(JavaTypeInstance other, List<FormalTypeParameter> parameters, GenericTypeBinder target);
}
