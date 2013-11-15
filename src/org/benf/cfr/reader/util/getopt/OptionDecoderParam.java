package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.functors.BinaryFunction;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/11/2013
 * Time: 17:19
 */
public interface OptionDecoderParam<T, ARG> extends BinaryFunction<String, ARG, T> {
    String getRangeDescription();
}