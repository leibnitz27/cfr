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
        private final String help;

        public ArgumentParam(String name, OptionDecoderParam<X, InputType> fn, String help) {
            this.name = name;
            this.fn = fn;
            this.help = help;
        }

        public String getName() {
            return name;
        }

        public OptionDecoderParam<X, InputType> getFn() {
            return fn;
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("'" + name + "':\n\n");
            sb.append(help).append('\n');
            String range = fn.getRangeDescription();
            if (range != null && !(range.isEmpty())) sb.append("\nRange : ").append(range).append("\n");
            String defaultVal = fn.getDefaultValue();
            if (defaultVal != null && !(defaultVal.isEmpty()))
                sb.append("\nDefault : ").append(defaultVal).append("\n");
            return sb.toString();
        }
    }

    public static class Argument<X> extends ArgumentParam<X, Void> {
        public Argument(String name, OptionDecoderParam<X, Void> fn, String help) {
            super(name, fn, help);
        }
    }
}
