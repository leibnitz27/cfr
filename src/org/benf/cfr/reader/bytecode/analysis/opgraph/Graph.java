package org.benf.cfr.reader.bytecode.analysis.opgraph;

import java.util.List;

public interface Graph<T> {
    List<T> getSources();
    List<T> getTargets();
}
