package org.benf.cfr.reader.util.getopt;

public class BadParametersException extends IllegalArgumentException {
    private final PermittedOptionProvider.ArgumentParam<?, ?> option;

    public BadParametersException(String s, PermittedOptionProvider.ArgumentParam<?, ?> option) {
        super(s);
        this.option = option;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("While processing argument '").append(option.getName()).append("':\n");
        sb.append(super.getMessage()).append("\n");
        sb.append("Valid argument range: ").append(option.getFn().getRangeDescription()).append("\n");
        return sb.toString();
    }
}
