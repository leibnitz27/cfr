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

    List<? extends Argument<?, ?>> getArguments();

    public final static class Argument<X, InputType> {
        private final String name;
        private final BinaryFunction<String, InputType, X> fn;

        public Argument(String name, BinaryFunction<String, InputType, X> fn) {
            this.name = name;
            this.fn = fn;
        }

        public String getName() {
            return name;
        }

        public BinaryFunction<String, InputType, X> getFn() {
            return fn;
        }
    }
}
