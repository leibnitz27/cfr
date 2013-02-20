package org.benf.cfr.reader.util.getopt;

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

    List<? extends Argument<?>> getArguments();

    public final static class Argument<X> {
        private final String name;
        private final UnaryFunction<String, X> fn;

        public Argument(String name, UnaryFunction<String, X> fn) {
            this.name = name;
            this.fn = fn;
        }

        public String getName() {
            return name;
        }

        public UnaryFunction<String, X> getFn() {
            return fn;
        }
    }
}
