package com.ours.yours.app.entity;

import com.ours.yours.app.ui.mvp.OurModel;

public class Photo implements OurModel {
    public static final String DEFAULT_URL = "DEFAULT_URL";
    public static final String DEFAULT_NAME = "DEFAULT_NAME";

    String url;
    String name;

    public Photo(String url, String name) {
        this.url = url;
        this.name = name;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Photo)) return false;
        Photo compare = (Photo) obj;
        if (compare.getUrl().equals(getUrl()) && compare.getName().equals(getName())) {
            return true;
        } else {
            return false;
        }
    }
}
