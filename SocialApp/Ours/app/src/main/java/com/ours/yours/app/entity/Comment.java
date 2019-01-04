package com.ours.yours.app.entity;

import android.os.Parcel;
import android.os.Parcelable;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;

import java.util.HashMap;
import java.util.Map;

public class Comment implements Parcelable{
    public static final String DEFAULT_CARD_ID           = "";
    public static final String DEFAULT_COMMENT_ID        = "";
    public static final String DEFAULT_PROFILE_PHOTO_URL = String.valueOf(R.drawable.ic_stub);
    public static final String DEFAULT_PROFILE_NAME      = "";
    public static final String DEFAULT_PROFILE_ID        = "";
    public static final String DEFAULT_COMMENT           = "";
    public static final int    DEFAULT_DISTANCE          = -1;
    public static final long   DEFAULT_DATE              = -1;
    /**
     * name of fields are same as the name of columns in the table representing a SQL database storing comments
     */
    private String CardId;
    private String CommentId;
    private String ProfilePhoto;
    private String ProfileName;
    private String ProfileID;
    private String Comment;
    private int Distance;
    private long Date;

    public Comment() {}

    private Comment(Parcel in) {
        CardId = in.readString();
        CommentId = in.readString();
        ProfilePhoto = in.readString();
        ProfileName = in.readString();
        ProfileID = in.readString();
        Comment = in.readString();
        Distance = in.readInt();
        Date = in.readLong();
    }

    public Comment(String cardId, String commentId, String profilePhoto, String profileName, String profileID
                    , String comment, int distance, long date) {
        CardId = cardId;
        CommentId = commentId;
        ProfilePhoto = profilePhoto;
        ProfileName = profileName;
        ProfileID = profileID;
        Comment = comment;
        Distance = distance;
        Date = date;
    }

    /**
     * getter
     */
    public String getCardId() {
        return CardId;
    }

    public String getCommentId() {
        return CommentId;
    }

    public String getProfilePhoto() {
        return ProfilePhoto;
    }

    public String getProfileName() {
        return ProfileName;
    }

    public String getProfileID() {
        return ProfileID;
    }

    public String getComment() {
        return Comment;
    }

    public int getDistance() {
        return Distance;
    }

    public long getDate() {
        return Date;
    }

    /**
     * setter
     */
    public void setCardId(String cardId) {
        CardId = cardId;
    }

    public void setCommentId(String commentId) {
        CommentId = commentId;
    }

    public void setProfilePhoto(String profilePhoto) {
        ProfilePhoto = profilePhoto;
    }

    public void setProfileName(String profileName) {
        ProfileName = profileName;
    }

    public void setProfileID(String profileID) {
        profileID = profileID;
    }

    public void setComment(String comment) {
        Comment = comment;
    }

    public void setDistance(int distance) {
        Distance = distance;
    }

    public void setDate(long date) {
        Date = date;
    }

    public void printCommentData(){
        Logger.d(">>>");
        String log = "... "
                + "CardId:" + getCardId()
                + "\n"
                + "CommentId:" + getCommentId()
                + "\n"
                + "ProfilePhoto:" + getProfilePhoto()
                + "\n"
                + "ProfileName:" + getProfileName()
                + "\n"
                + "ProfileID:" + getProfileID()
                + "\n"
                + "Comment:" + getComment()
                + "\n"
                + "Distance:" + getDistance()
                + "\n"
                + "Date:" + getDate();
        Logger.d(log);
    }

    /**
     * serializer
     */
    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("CardId", this.getCardId());
        map.put("CommentId", this.getCommentId());
        map.put("ProfilePhoto", this.getProfilePhoto());
        map.put("ProfileName", this.getProfileName());
        map.put("ProfileID", this.getProfileID());
        map.put("Comment", this.getComment());
        map.put("Distance", this.getDistance());
        map.put("Date", this.getDate());
        return map;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(CardId);
        dest.writeString(CommentId);
        dest.writeString(ProfilePhoto);
        dest.writeString(ProfileName);
        dest.writeString(ProfileID);
        dest.writeString(Comment);
        dest.writeInt(Distance);
        dest.writeLong(Date);
    }

    public static final Creator<Comment> CREATOR = new Creator<Comment>() {
        public Comment createFromParcel(Parcel in) {
            return new Comment(in);
        }

        public Comment[] newArray(int size) {
            return new Comment[size];
        }
    };


}
