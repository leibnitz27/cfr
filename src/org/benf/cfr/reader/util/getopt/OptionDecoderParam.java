package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.functors.TrinaryFunction;

public interface OptionDecoderParam<T, ARG> extends TrinaryFunction<String, ARG, Options, T> {
    String getRangeDescription();

    String getDefaultValue();
}