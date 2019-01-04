package com.ours.yours.app.entity;

import android.os.Parcel;
import android.os.Parcelable;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Card implements Parcelable{
    public static final String DEFAULT_CARD_ID                  = "";
    public static final String DEFAULT_PROFILE_PHOTO_URL        = String.valueOf(R.drawable.ic_stub);
    public static final String DEFAULT_PROFILE_TITLE            = "";
    public static final String DEFAULT_PROFILE_NAME             = "";
    public static final String DEFAULT_PROFILE_ID               = "";
    public static final String DEFAULT_ARTICLE_TITLE            = "";
    public static final String DEFAULT_ARTICLE_CONTENT          = "";
    public static final int    DEFAULT_ARTICLE_COUNT            = 0;
    public static final String DEFAULT_ARTICLE_PHOTO_URL        = "";
    public static final String DEFAULT_ARTICLE_PHOTO_FILE_NAME  = "";
    public static final int    DEFAULT_DISTANCE                 = -1;
    //public static final long   DEFAULT_DATE                   = -1;

    /**
     * name of fields are same as the name of columns in the table representing a SQL database storing cards
     */
    private String CardId;
    private String ProfilePhoto;
    private String ProfileTitle;
    private String ProfileName;
    private String ProfileID;
    private String ArticleTitle;
    private String ArticleContent;
    private int ArticleCount;
    private List<String> ArticlePhoto = new ArrayList<>();
    private List<String> ArticlePhotoFileName = new ArrayList<>();
    private int Distance;
    private long Date;

    public Card() {
        //Logger.d(">>> constructor accepting null");
    }

    private Card(Parcel in) {
        Logger.d(">>> constructor accepting parcel");
        CardId = in.readString();
        ProfilePhoto = in.readString();
        ProfileTitle = in.readString();
        ProfileName = in.readString();
        ProfileID = in.readString();
        ArticleTitle = in.readString();
        ArticleContent = in.readString();
        ArticleCount = in.readInt();
        if (ArticleCount > 0) {
            in.readStringList(ArticlePhoto);
            in.readStringList(ArticlePhotoFileName);
        }
        Distance = in.readInt();
        Date = in.readLong();
    }

    public Card(String cardId, String profilePhoto, String profileTitle, String profileName, String profileID
            , String articleName, String articleContent, int articleCount, int distance, long date) {
        CardId = cardId;
        ProfilePhoto = profilePhoto;
        ProfileTitle = profileTitle;
        ProfileName = profileName;
        ProfileID = profileID;
        ArticleTitle = articleName;
        ArticleContent = articleContent;
        ArticleCount = articleCount;
        Distance = distance;
        Date = date;
    }

    /**
     * getter
     */
    public String getCardId() {
        return CardId;
    }

    public String getProfilePhoto() {
        return ProfilePhoto;
    }

    public String getProfileTitle() {
        return ProfileTitle;
    }

    public String getProfileName() {
        return ProfileName;
    }

    public String getProfileID() {
        return ProfileID;
    }

    public String getArticleTitle() {
        return ArticleTitle;
    }

    public String getArticleContent() {
        return ArticleContent;
    }

    public int getArticleCount() {
        return ArticleCount;
    }

    public List<String> getArticlePhoto() {
        return ArticlePhoto;
    }

    public List<String> getArticlePhotoFileName() {
        return ArticlePhotoFileName;
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

    private void setProfilePhoto(String profilePhoto) {
        ProfilePhoto = profilePhoto;
    }

    private void setProfileTitle(String profileTitle) {
        ProfileTitle = profileTitle;
    }

    private void setProfileName(String profileName) {
        ProfileName = profileName;
    }

    private void setProfileID(String profileID) {
        ProfileID = profileID;
    }

    public void setArticleTitle(String articleTitle) {
        ArticleTitle = articleTitle;
    }

    public void setArticleContent(String articleContent) {
        ArticleContent = articleContent;
    }

    public void setArticleCount(int articleCount) {
        ArticleCount = articleCount;
    }

    public void addArticlePhoto(String articlePhoto) {
        //Logger.d("... going to add article photo:" + articlePhoto);
        if (ArticlePhoto == null) {
            Logger.e("ArticlePhoto == null");
            return;
        }
        ArticlePhoto.add(articlePhoto);
    }

    private void setArticlePhoto(List<String> articlePhoto) {
        ArticlePhoto.clear();
        ArticlePhoto.addAll(articlePhoto);
    }

    public void setArticlePhotoFileName(List<String> articlePhotoFileName) {
        ArticlePhotoFileName.clear();
        ArticlePhotoFileName.addAll(articlePhotoFileName);
    }

    private void setDistance(int distance) {
        Distance = distance;
    }

    public void setDate(long date) {
        Date = date;
    }

    public void setCard(Card card) {
        setCardId(card.getCardId());
        setProfilePhoto(card.getProfilePhoto());
        setProfileTitle(card.getProfileTitle());
        setProfileName(card.getProfileName());
        setProfileID(card.getProfileID());
        setArticleTitle(card.getArticleTitle());
        setArticleContent(card.getArticleContent());
        setArticleCount(card.getArticleCount());
        if (ArticleCount > 0) setArticlePhotoFileName(card.getArticlePhotoFileName());
        if (ArticleCount > 0) setArticlePhoto(card.getArticlePhoto());
        setDistance(card.getDistance());
        setDate(card.getDate());
    }

    public void printCardData(){
        if (false) return;
        String log = "... "
                + "CardId:" + getCardId()
                + "\n"
                + "ProfilePhoto:" + getProfilePhoto()
                + "\n"
                + "ProfileName:" + getProfileName()
                + "\n"
                + "ProfileTitle:" + getProfileTitle()
                + "\n"
                + "ProfileID:" + getProfileID()
                + "\n"
                + "ArticleContent:" + getArticleContent()
                + "\n"
                + "ArticleTitle:" + getArticleTitle()
                + "\n"
                + "ArticleCount:" + getArticleCount()
                + "\n";
        if (ArticlePhoto.size() > 0) {
            for (int i=0; i<ArticlePhoto.size(); i++)
                log += "ArticlePhoto" + i + ":" + ArticlePhoto.get(i) + "\n";
        }
        if (ArticlePhotoFileName.size() > 0) {
            for (int i=0; i<ArticlePhotoFileName.size(); i++)
                log += "ArticlePhotoFileName" + i + ":" + ArticlePhotoFileName.get(i) + "\n";
        }
        log = log + "Distance:" + getDistance() + "\n" + "Date:" + getDate();
        Logger.d(log);
    }

    /**
     * serializer
     */
    public Map<String, Object> toMap() {
        //Logger.d(">>>");
        HashMap<String, Object> map = new HashMap<>();
        int i;
        map.put("CardId", this.getCardId());
        map.put("ProfilePhoto", this.getProfilePhoto());
        map.put("ProfileTitle", this.getProfileTitle());
        map.put("ProfileName", this.getProfileName());
        map.put("ProfileID", this.getProfileID());
        map.put("ArticleTitle", this.getArticleTitle());
        map.put("ArticleContent", this.getArticleContent());
        map.put("ArticleCount", this.getArticleCount());
        if (getArticlePhoto().size() == getArticlePhotoFileName().size()) {
            if (getArticleCount() > 0) {
                map.put("ArticlePhoto",ArticlePhoto);
            }
            if (getArticleCount() > 0) {
                for (i=0; i<getArticleCount(); i++)
                    map.put("ArticlePhotoFileName", ArticlePhotoFileName);
            }
        } else {
            Logger.e("!!! count of photos(" + getArticlePhoto().size() + ") != count of file names(" + getArticlePhotoFileName().size() + ")");
        }
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
        Logger.d(">>>");
        dest.writeString(CardId);
        dest.writeString(ProfilePhoto);
        dest.writeString(ProfileTitle);
        dest.writeString(ProfileName);
        dest.writeString(ProfileID);
        dest.writeString(ArticleTitle);
        dest.writeString(ArticleContent);
        dest.writeInt(ArticleCount);
        if (ArticleCount > 0) {
            for (int i=0; i<ArticleCount; i++) dest.writeStringList(ArticlePhoto);
            for (int i=0; i<ArticleCount; i++) dest.writeStringList(ArticlePhotoFileName);
        }
        dest.writeInt(Distance);
        dest.writeLong(Date);
    }

    public static final Parcelable.Creator<Card> CREATOR = new Parcelable.Creator<Card>() {
        public Card createFromParcel(Parcel in) {
            return new Card(in);
        }

        public Card[] newArray(int size) {
            return new Card[size];
        }
    };
}
