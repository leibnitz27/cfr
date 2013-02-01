package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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


    private static Map<String, OptType> optTypeMap = new HashMap<String, OptType>() {{
        put("nostringswitch", OptType.PRESENCE);
    }};

    private String dumpOptTypes() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, OptType> entry : optTypeMap.entrySet()) {
            switch (entry.getValue()) {
                case PRESENCE:
                    sb.append("   [ --").append(entry.getKey()).append("]\n");
                    break;
                case STRING:
                    sb.append("   [ --").append(entry.getKey()).append(" value ]\n");
                    break;
            }
        }
        return sb.toString();
    }

    public CFRParameters parse(String[] args) {
        if (args.length < 1) throw new ConfusedCFRException("filename [methodname]\n" + dumpOptTypes());

        String fname = args[0];
        int start = 1;
        String methname = null;
        if (args.length >= 2 && args[1].startsWith("-")) {
            methname = args[1];
            start = 2;
        }

        Map<String, String> processed = process(Arrays.copyOfRange(args, start, args.length));
        return new CFRParameters(fname, methname, processed);
    }

    private Map<String, String> process(String[] in) {
        Map<String, String> res = MapFactory.newMap();
        for (int x = 0; x < in.length; ++x) {
            if (in[x].startsWith("--")) {
                String name = in[x].substring(2);
                if (!optTypeMap.containsKey(name)) {
                    throw new ConfusedCFRException("Unknown argument " + name);
                }
                switch (optTypeMap.get(name)) {
                    case PRESENCE:
                        res.put(name, null);
                        break;
                    case STRING:
                        if (x >= in.length - 1)
                            throw new ConfusedCFRException("parameter " + name + " requires argument");
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
