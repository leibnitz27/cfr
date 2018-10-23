package org.benf.cfr.reader.util.getopt;

public class BadParametersException extends IllegalArgumentException {
    private final PermittedOptionProvider.ArgumentParam<?, ?> option;

    BadParametersException(String s, PermittedOptionProvider.ArgumentParam<?, ?> option) {
        super(s);
        this.option = option;
    }

    @Override
    public String toString() {
        String sb = "While processing argument '" + option.getName() + "':\n" +
                super.getMessage() + "\n" +
                "Valid argument range: " + option.getFn().getRangeDescription() + "\n";
        return sb;
    }
}
