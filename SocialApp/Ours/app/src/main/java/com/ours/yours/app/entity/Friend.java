package com.ours.yours.app.entity;

import android.os.Parcel;
import android.os.Parcelable;

public class Friend implements Parcelable{
    private String name;
    private String urlPhoto;

    public Friend(String name) {
        this.name = name;
    }

    public Friend(String url_photo, String name) {
        this.urlPhoto = url_photo;
        this.name = name;
    }

    private Friend(Parcel source) {
        this.urlPhoto = source.readString();
        this.name = source.readString();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setUrlPhoto(String urlPhoto) {
        this.urlPhoto = urlPhoto;
    }

    public String getName() {
        return name;
    }

    public String getUrlPhoto() {
        return urlPhoto;
    }

    public static final Creator<Friend> CREATOR = new Creator<Friend>() {
        @Override
        public Friend createFromParcel(Parcel source) {
            return new Friend(source);
        }

        @Override
        public Friend[] newArray(int size) {
            return new Friend[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(urlPhoto);
        dest.writeString(name);
    }
}
