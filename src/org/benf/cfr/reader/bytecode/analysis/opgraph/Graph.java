package org.benf.cfr.reader.bytecode.analysis.opgraph;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 19/03/2012
 * Time: 06:37
 * To change this template use File | Settings | File Templates.
 */
public interface Graph<T> {
    List<T> getSources();
    List<T> getTargets();
}
