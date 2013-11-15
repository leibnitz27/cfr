package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.functors.BinaryFunction;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/02/2013
 * Time: 06:51
 */
public interface PermittedOptionProvider {
    List<String> getFlags();

    List<? extends ArgumentParam<?, ?>> getArguments();

    public static class ArgumentParam<X, InputType> {
        private final String name;
        private final OptionDecoderParam<X, InputType> fn;

        public ArgumentParam(String name, OptionDecoderParam<X, InputType> fn) {
            this.name = name;
            this.fn = fn;
        }

        public String getName() {
            return name;
        }

        public OptionDecoderParam<X, InputType> getFn() {
            return fn;
        }
    }

    public static class Argument<X> extends ArgumentParam<X, Void> {
        public Argument(String name, OptionDecoderParam<X, Void> fn) {
            super(name, fn);
        }
    }
}
