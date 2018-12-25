package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.AnonymousClassUsage;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.CreationCollector;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.DCCommonState;

import java.util.List;

public class CondenseConstruction {
    /*
     * Find all the constructors and initialisers.  If something is initialised and
     * constructed in one place each, we can guarantee that the construction happened
     * after the initialisation, so replace
     *
     * a1 = new foo
     * a1.<init>(x, y, z)
     *
     * with
     *
     * a1 = new foo(x,y,z)
     */
    public static void condenseConstruction(DCCommonState state, Method method, List<Op03SimpleStatement> statements, AnonymousClassUsage anonymousClassUsage) {
        CreationCollector creationCollector = new CreationCollector(anonymousClassUsage);
        for (Op03SimpleStatement statement : statements) {
            statement.findCreation(creationCollector);
        }
        creationCollector.condenseConstructions(method, state);
    }

}
