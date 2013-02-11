package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 01/02/2013
 * Time: 16:27
 */
public class GetOptParser {

    private static enum OptType {
        STRING,
        PRESENCE
    }

    public String getHelp(PermittedOptionProvider permittedOptionProvider) {
        StringBuilder sb = new StringBuilder();
        for (String flag : permittedOptionProvider.getFlagNames()) {
            sb.append("   [ --").append(flag).append(" ]\n");
        }
        for (String param : permittedOptionProvider.getArgumentNames()) {
            sb.append("   [ --").append(param).append(" value ]\n");
        }
        return sb.toString();
    }

    private static Map<String, OptType> buildOptTypeMap(PermittedOptionProvider optionProvider) {
        Map<String, OptType> optTypeMap = MapFactory.newMap();
        for (String flagName : optionProvider.getFlagNames()) {
            optTypeMap.put(flagName, OptType.PRESENCE);
        }
        for (String argName : optionProvider.getArgumentNames()) {
            optTypeMap.put(argName, OptType.STRING);
        }
        return optTypeMap;
    }

    public CFRState parse(String[] args) {
        PermittedOptionProvider permittedOptions = CFRState.getPermittedOptions();
        if (args.length < 1) throw new BadParametersException("missing filename\n", this, permittedOptions);

        String fname = args[0];
        int start = 1;
        String methname = null;
        if (args.length >= 2 && !(args[1].startsWith("-"))) {
            methname = args[1];
            start = 2;
        }

        Map<String, String> processed = process(Arrays.copyOfRange(args, start, args.length), permittedOptions);
        return new CFRState(fname, methname, processed);
    }

    private Map<String, String> process(String[] in, PermittedOptionProvider optionProvider) {
        Map<String, OptType> optTypeMap = buildOptTypeMap(optionProvider);
        Map<String, String> res = MapFactory.newMap();
        for (int x = 0; x < in.length; ++x) {
            if (in[x].startsWith("--")) {
                String name = in[x].substring(2);
                if (!optTypeMap.containsKey(name)) {
                    throw new BadParametersException("Unknown argument " + name, this, optionProvider);
                }
                switch (optTypeMap.get(name)) {
                    case PRESENCE:
                        res.put(name, null);
                        break;
                    case STRING:
                        if (x >= in.length - 1)
                            throw new BadParametersException("parameter " + name + " requires argument", this, optionProvider);
                        res.put(name, in[++x]);
                        break;
                }
            } else {
                throw new ConfusedCFRException("Unexpected argument " + in[x]);
            }
        }
        return res;
    }
}
