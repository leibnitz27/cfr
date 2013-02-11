package org.benf.cfr.reader.util.getopt;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/02/2013
 * Time: 06:51
 */
public interface PermittedOptionProvider {
    List<String> getFlagNames();

    List<String> getArgumentNames();
}
