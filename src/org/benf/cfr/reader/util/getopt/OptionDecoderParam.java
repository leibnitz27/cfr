package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.functors.BinaryFunction;

public interface OptionDecoderParam<T, ARG> extends BinaryFunction<String, ARG, T> {
    String getRangeDescription();

    String getDefaultValue();
}