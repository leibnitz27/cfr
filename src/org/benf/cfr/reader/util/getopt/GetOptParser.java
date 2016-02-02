package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.MiscConstants;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

        public boolean isFlag() {
            return isFlag;
        }

        public String getName() {
            return name;
        }

        public PermittedOptionProvider.ArgumentParam<?, ?> getArgument() {
            return argument;
        }
    }

    public static String getHelp(PermittedOptionProvider permittedOptionProvider) {
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
        for (PermittedOptionProvider.ArgumentParam param : permittedOptionProvider.getArguments()) {
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

    public <T> T parse(String[] args, GetOptSinkFactory<T> getOptSinkFactory) {
        Pair<List<String>, Map<String, String>> processed = process(args, getOptSinkFactory);
        return getOptSinkFactory.create(processed.getFirst(), processed.getSecond());
    }

    private static void printErrHeader() {
        System.err.println("CFR " + MiscConstants.CFR_VERSION + "\n");
    }

    private static void printUsage() {
        System.err.println("java --jar cfr_" + MiscConstants.CFR_VERSION + ".jar class_or_jar_file [method] [options]\n");
    }

    public <T> void showHelp(PermittedOptionProvider permittedOptionProvider) {
        printErrHeader();
        printUsage();
        System.err.println(getHelp(permittedOptionProvider));
    }

    public <T> void showHelp(PermittedOptionProvider permittedOptionProvider, Exception e) {
        printErrHeader();
        printUsage();
        System.err.println("Parameter error : " + e.toString() + "\n");
        System.err.println(getHelp(permittedOptionProvider));
    }

    public <T> void showOptionHelp(PermittedOptionProvider permittedOptionProvider, Options options, PermittedOptionProvider.ArgumentParam<String, Void> helpArg) {
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
        System.err.println("No such argument '" + relevantOption + "'");
    }

    private Pair<List<String>, Map<String, String>> process(String[] in, PermittedOptionProvider optionProvider) {
        Map<String, OptData> optTypeMap = buildOptTypeMap(optionProvider);
        Map<String, String> res = MapFactory.newMap();
        List<String> positional = ListFactory.newList();
        Options optionsSample = new OptionsImpl("", "", res);
        for (int x = 0; x < in.length; ++x) {
            if (in[x].startsWith("--")) {
                String name = in[x].substring(2);
                OptData optData = optTypeMap.get(name);
                if (optData == null) {
                    throw new IllegalArgumentException("Unknown argument " + name);
                }
                if (optData.isFlag()) {
                    res.put(name, null);
                } else {
                    if (x >= in.length - 1)
                        throw new BadParametersException("Requires argument", optData.getArgument());
                    res.put(name, in[++x]);
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
