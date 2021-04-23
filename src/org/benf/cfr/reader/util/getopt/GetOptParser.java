package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.CfrVersionInfo;
import org.benf.cfr.reader.util.MiscConstants;

import java.util.*;

public class GetOptParser {

    private static class OptData {
        private final boolean isFlag;
        private final String name;
        private final PermittedOptionProvider.ArgumentParam<?, ?> argument;

        private OptData(String name) {
            this.name = name;
            this.isFlag = true;
            this.argument = null;
        }

        private OptData(PermittedOptionProvider.ArgumentParam<?, ?> argument) {
            this.argument = argument;
            this.isFlag = false;
            this.name = argument.getName();
        }

        boolean isFlag() {
            return isFlag;
        }

        public String getName() {
            return name;
        }

        public PermittedOptionProvider.ArgumentParam<?, ?> getArgument() {
            return argument;
        }
    }

    private static String getHelp(PermittedOptionProvider permittedOptionProvider) {
        StringBuilder sb = new StringBuilder();
        for (String flag : permittedOptionProvider.getFlags()) {
            sb.append("   --").append(flag).append("\n");
        }
        int max = 10;
        for (PermittedOptionProvider.ArgumentParam param : permittedOptionProvider.getArguments()) {
            int len = param.getName().length();
            max = len > max ? len : max;
        }
        max += 4;
        List<? extends PermittedOptionProvider.ArgumentParam<?, ?>> args = permittedOptionProvider.getArguments();
        Collections.sort(args, new Comparator<PermittedOptionProvider.ArgumentParam<?, ?>>() {
            @Override
            public int compare(PermittedOptionProvider.ArgumentParam<?, ?> a1, PermittedOptionProvider.ArgumentParam<?, ?> a2) {
                if (a1.getName().equals("help")) return 1;
                if (a2.getName().equals("help")) return -1;
                return a1.getName().compareTo(a2.getName());
            }
        });
        for (PermittedOptionProvider.ArgumentParam param : args) {
            if (!param.isHidden()) {
                String name = param.getName();
                int pad = max - name.length();
                sb.append("   --").append(param.getName());
                for (int x = 0; x < pad; ++x) { // there really should be a better way to do this.
                    sb.append(' ');
                }
                sb.append(param.shortDescribe()).append("\n");
            }
        }
        return sb.toString();
    }

    private static Map<String, OptData> buildOptTypeMap(PermittedOptionProvider optionProvider) {
        Map<String, OptData> optTypeMap = MapFactory.newMap();
        for (String flagName : optionProvider.getFlags()) {
            optTypeMap.put(flagName, new OptData(flagName));
        }
        for (PermittedOptionProvider.ArgumentParam arg : optionProvider.getArguments()) {
            optTypeMap.put(arg.getName(), new OptData(arg));
        }
        return optTypeMap;
    }

    public <T> Pair<List<String>, T> parse(String[] args, GetOptSinkFactory<T> getOptSinkFactory) {
        Pair<List<String>, Map<String, String>> processed = process(args, getOptSinkFactory);
        final List<String> positional = processed.getFirst();
        Map<String, String> named = processed.getSecond();
        /*
         * A bit of a hack, but if no positional arguments are specified, and 'help' is, then
         * we don't want to blow up, so work around this.
         */
        if (positional.isEmpty() && named.containsKey(OptionsImpl.HELP.getName())) {
            positional.add("ignoreMe.class");
        }
        T res = getOptSinkFactory.create(named);
        return Pair.make(positional, res);
    }

    private static void printErrHeader() {
        System.err.println("CFR " + CfrVersionInfo.VERSION_INFO + "\n");
    }

    private static void printUsage() {
        System.err.println("java -jar CFRJAR.jar class_or_jar_file [method] [options]\n");
    }

    public void showHelp(Exception e) {
        printErrHeader();
        printUsage();
        System.err.println("Parameter error : " + e.getMessage() + "\n");
        System.err.println("Please specify '--help' to get option list, or '--help optionname' for specifics, e.g.\n   --help " + OptionsImpl.PULL_CODE_CASE.getName());
    }

    public void showOptionHelp(PermittedOptionProvider permittedOptionProvider, Options options, PermittedOptionProvider.ArgumentParam<String, Void> helpArg) {
        printErrHeader();
        String relevantOption = options.getOption(helpArg);
        List<? extends PermittedOptionProvider.ArgumentParam<?, ?>> possible = permittedOptionProvider.getArguments();
        for (PermittedOptionProvider.ArgumentParam<?, ?> opt : possible) {
            if (opt.getName().equals(relevantOption)) {
                System.err.println(opt.describe());
                return;
            }
        }
        System.err.println(getHelp(permittedOptionProvider));
        if (relevantOption.equals("")) {
            System.err.println("Please specify '--help optionname' for specifics, eg\n   --help " + OptionsImpl.PULL_CODE_CASE.getName());
        } else {
            System.err.println("No such argument '" + relevantOption + "'");
        }
    }

    private static final String argPrefix = "--";

    private Pair<List<String>, Map<String, String>> process(String[] in, PermittedOptionProvider optionProvider) {
        Map<String, OptData> optTypeMap = buildOptTypeMap(optionProvider);
        Map<String, String> res = MapFactory.newMap();
        List<String> positional = ListFactory.newList();
        Options optionsSample = new OptionsImpl(res);
        for (int x = 0; x < in.length; ++x) {
            if (in[x].startsWith(argPrefix)) {
                String name = in[x].substring(2);
                OptData optData = optTypeMap.get(name);
                if (optData == null) {
                    throw new IllegalArgumentException("Unknown argument " + name);
                }
                if (optData.isFlag()) {
                    res.put(name, null);
                } else {
                    String next = x >= in.length - 1 ? argPrefix : in[x+1];
                    String value;
                    if (next.startsWith(argPrefix)) {
                        // Does it have a default?
                        if (name.equals(OptionsImpl.HELP.getName())) {
                            value = "";
                        } else {
                            value = optData.getArgument().getFn().getDefaultValue();
                            if (value == null) {
                                throw new BadParametersException("Requires argument", optData.getArgument());
                            }
                            // If they've specified a parameter with no value, and it has a default? Just ignore.
                            // (Apart from help.  We'll assume they meant it. )
                            continue;
                        }
                    } else {
                        value = in[++x];
                    }
                    res.put(name, value);
                    // invoke, to test that this is a valid argument early.
                    try {
                        optData.getArgument().getFn().invoke(res.get(name), null, optionsSample);
                    } catch (Exception e) {
                        throw new BadParametersException(e.toString(), optData.getArgument());
                    }
                }
            } else {
                positional.add(in[x]);
            }
        }
        return Pair.make(positional, res);
    }
}
