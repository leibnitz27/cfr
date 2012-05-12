package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/03/2012
 * Time: 18:36
 * To change this template use File | Settings | File Templates.
 */
public class GraphConversionHelper<X extends Graph<X>, Y extends MutableGraph<Y>> {
    private final Map<X, Y> correspondance;

    public GraphConversionHelper() {
        this.correspondance = MapFactory.newMap();
    }

    private Y findEntry(X key) {
        Y value = correspondance.get(key);
        if (value == null) throw new ConfusedCFRException("Missing key when tying up graph " + key);
        return value;
    }

    public void patchUpRelations() {
        for (Map.Entry<X, Y> entry : correspondance.entrySet()) {
            X orig = entry.getKey();
            Y newnode = entry.getValue();

            for (X source : orig.getSources()) {
                newnode.addSource(findEntry(source));
            }

            for (X target : orig.getTargets()) {
                newnode.addTarget(findEntry(target));
            }
        }
    }

    public void registerOriginalAndNew(X original, Y newnode) {
        correspondance.put(original, newnode);
    }
}
