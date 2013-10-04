package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/10/2013
 * Time: 06:39
 */
public class Op03Blocks {
    public static List<Op03SimpleStatement> topologicalSort(List<Op03SimpleStatement> statements) {
        /*
         *
         */
        List<Block3> blocks = ListFactory.newList(statements.size());
        int len = statements.size();
        for (int i = 0; i < len; ++i) {
            blocks.add(new Block3());
        }
        for (int i = 0; i < len; ++i) {
            blocks.get(i).append(statements.get(i));
        }

        return statements;
    }

    private static class Block3 {
        List<Op03SimpleStatement> content = ListFactory.newList();
        List<Block3> sources = ListFactory.newList();
        List<Block3> targets = ListFactory.newList();

        public Block3() {
        }

        public void append(Op03SimpleStatement s) {
            content.add(s);
        }
    }

}
