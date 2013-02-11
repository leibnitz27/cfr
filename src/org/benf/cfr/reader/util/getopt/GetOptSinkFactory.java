package org.benf.cfr.reader.util.getopt;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/02/2013
 * Time: 17:09
 */
public interface GetOptSinkFactory<T> extends PermittedOptionProvider {
    T create(List<String> args, Map<String, String> opts);
}
