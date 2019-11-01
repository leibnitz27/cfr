package org.benf.cfr.reader.util;

import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Collection;
import java.util.Set;

public class DecompilerComments implements Dumpable {
    private Set<DecompilerComment> comments = SetFactory.newOrderedSet();

    public DecompilerComments() {
    }

    public void addComment(String comment) {
        DecompilerComment decompilerComment = new DecompilerComment(comment);
        comments.add(decompilerComment);
    }

    public void addComment(DecompilerComment comment) {
        comments.add(comment);
    }

    public void addComments(Collection<DecompilerComment> comments) {
        this.comments.addAll(comments);
    }

    @Override
    public Dumper dump(Dumper d) {
        if (comments.isEmpty()) return d;
        d.comment("/*").newln();
        for (DecompilerComment comment : comments) {
            d.comment(" * ").dump(comment).newln();
        }
        d.comment(" */").newln();
        return d;
    }

    public Collection<DecompilerComment> getCommentCollection() {
        return comments;
    }

}
