package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/08/2013
 * Time: 21:19
 */
public interface NamedVariable extends Dumpable {
    void forceName(String name);

    String getStringName();

    @Override
    Dumper dump(Dumper d);
}
