package org.benf.cfr.reader.state;

import java.util.Map;

public interface ClassNameFunction {
    Map<String, String> apply(Map<String, String> names);
}
