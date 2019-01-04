package com.ours.yours.app.entity;

import java.util.List;

public class CommentList {
    private long mLastDateOfComment;
    private List<Comment> mComments;

    /**
     * getter
     */
    public long getLastDateOfComment() {
        return mLastDateOfComment;
    }

    public List<Comment> getComments() {
        return mComments;
    }

    /**
     * setter
     */
    public void setLastDateOfComment(long mLastItemDate) {
        this.mLastDateOfComment = mLastItemDate; //shallow copy
    }

    public void setComments(List<Comment> comments) {
        this.mComments = comments;
    }
}
