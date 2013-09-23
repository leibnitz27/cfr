package org.benf.cfr.reader.util;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/09/2013
 * Time: 18:30
 */
public class DecompilerComments {
    List<DecompilerComment> commentList = ListFactory.newList();

    public DecompilerComments() {
    }

    public void addComment(String comment) {
        DecompilerComment decompilerComment = new DecompilerComment(comment);
        commentList.add(decompilerComment);
    }
}
