package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/11/2013
 * Time: 07:52
 */
public interface TypeUsageInformation {
    public Set<JavaRefTypeInstance> getUsedClassTypes();

    public String getName(JavaTypeInstance type);
}
