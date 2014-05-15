package org.benf.cfr.reader.util.getopt;

import java.util.List;

public interface PermittedOptionProvider {
    List<String> getFlags();

    List<? extends ArgumentParam<?, ?>> getArguments();

    public static class ArgumentParam<X, InputType> {
        private final String name;
        private final OptionDecoderParam<X, InputType> fn;
        private final String help;
        private final boolean hidden;

        public ArgumentParam(String name, OptionDecoderParam<X, InputType> fn, String help) {
            this(name, fn, help, false);
        }

        public ArgumentParam(String name, OptionDecoderParam<X, InputType> fn, String help, boolean hidden) {
            this.name = name;
            this.fn = fn;
            this.help = help;
            this.hidden = hidden;
        }

        public String getName() {
            return name;
        }

        public OptionDecoderParam<X, InputType> getFn() {
            return fn;
        }

        public boolean isHidden() {
            return hidden;
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

        public String shortDescribe() {
            StringBuilder sb = new StringBuilder();
            String defaultVal = fn.getDefaultValue();
            String range = fn.getRangeDescription();
            if (range != null && !(range.isEmpty())) sb.append(" (").append(range).append(") ");
            if (defaultVal != null && !(defaultVal.isEmpty()))
                sb.append(" default: ").append(defaultVal).append("");
            return sb.toString();
        }
    }

    public static class Argument<X> extends ArgumentParam<X, Void> {
        public Argument(String name, OptionDecoderParam<X, Void> fn, String help, boolean hidden) {
            super(name, fn, help, hidden);
        }

        public Argument(String name, OptionDecoderParam<X, Void> fn, String help) {
            super(name, fn, help, false);
        }
    }
}
