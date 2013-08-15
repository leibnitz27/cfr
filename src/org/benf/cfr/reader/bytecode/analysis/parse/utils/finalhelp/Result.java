package org.benf.cfr.reader.bytecode.analysis.parse.utils.finalhelp;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;

import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/08/2013
 * Time: 21:51
 */
public class Result {
    public static Result FAIL = new Result();

    private final boolean res;
    private final Set<Op03SimpleStatement> toRemove;
    private final Op03SimpleStatement start;
    private final Op03SimpleStatement afterEnd; // throwProxy

    private Result() {
        this.res = false;
        this.toRemove = null;
        this.start = null;
        this.afterEnd = null;
    }

    public Result(Set<Op03SimpleStatement> toRemove, Op03SimpleStatement start, Op03SimpleStatement afterEnd) {
        this.res = true;
        this.toRemove = toRemove;
        this.start = start;
        this.afterEnd = afterEnd;
    }

    public boolean isFail() {
        return !res;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Result result = (Result) o;

        if (res != result.res) return false;
        if (start != null ? !start.equals(result.start) : result.start != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (res ? 1 : 0);
        result = 31 * result + (start != null ? start.hashCode() : 0);
        return result;
    }

    public Set<Op03SimpleStatement> getToRemove() {
        return toRemove;
    }

    public Op03SimpleStatement getStart() {
        return start;
    }

    public Op03SimpleStatement getAfterEnd() {
        return afterEnd;
    }
}
