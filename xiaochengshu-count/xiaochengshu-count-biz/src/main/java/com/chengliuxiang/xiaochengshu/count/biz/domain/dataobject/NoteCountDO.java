package com.chengliuxiang.xiaochengshu.count.biz.domain.dataobject;

public class NoteCountDO {
    private Long id;

    private Long noteId;

    private Long likeTotal;

    private Long collectTotal;

    private Long commentTotal;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNoteId() {
        return noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public Long getLikeTotal() {
        return likeTotal;
    }

    public void setLikeTotal(Long likeTotal) {
        this.likeTotal = likeTotal;
    }

    public Long getCollectTotal() {
        return collectTotal;
    }

    public void setCollectTotal(Long collectTotal) {
        this.collectTotal = collectTotal;
    }

    public Long getCommentTotal() {
        return commentTotal;
    }

    public void setCommentTotal(Long commentTotal) {
        this.commentTotal = commentTotal;
    }
}