package org.benf.cfr.reader.util;

import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Collection;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/09/2013
 * Time: 18:30
 */
public class DecompilerComments implements Dumpable {
    List<DecompilerComment> commentList = ListFactory.newList();

    public DecompilerComments() {
    }

    public void addComment(String comment) {
        DecompilerComment decompilerComment = new DecompilerComment(comment);
        commentList.add(decompilerComment);
    }

    public void addComment(DecompilerComment comment) {
        commentList.add(comment);
    }

    public void addComments(Collection<DecompilerComment> comments) {
        commentList.addAll(comments);
    }

    @Override
    public Dumper dump(Dumper d) {
        if (commentList.isEmpty()) return d;
        d.print("/*").newln();
        for (DecompilerComment comment : commentList) {
            d.print(" * ").dump(comment).newln();
        }
        d.print(" */").newln();
        return d;
    }

    public List<DecompilerComment> getCommentList() {
        return commentList;
    }

    public boolean hasErrorComment() {
        for (DecompilerComment comment : commentList) {
            if (null != comment.getSummaryMessage()) return true;
        }
        return false;
    }
}
