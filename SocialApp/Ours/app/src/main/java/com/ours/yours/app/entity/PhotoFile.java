package com.ours.yours.app.entity;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.ui.mvp.OurModel;

import java.util.Date;

public class PhotoFile implements OurModel {
    public static final String DEFAULT_URL = String.valueOf(R.drawable.ic_stub);
    public static final String DEFAULT_NAME = "";
    public static final String DEFAULT_TEMPLATE = "";
    public static final long DEFAULT_DATE = 0;

    private String url = DEFAULT_URL;
    private String name = DEFAULT_NAME;
    private String template = DEFAULT_TEMPLATE;
    private long date = DEFAULT_DATE;


    public void setUrl(String url) {
        this.url = url;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public String getTemplate() {
        return template;
    }

    public long getDate() {
        return date;
    }

    public void printPhotoData(){
        Logger.d(">>>");
        String log = "... "
                + "url:" + getUrl()
                + "\n"
                + "name:" + getName()
                + "\n"
                + "template:" + getTemplate()
                + "\n"
                + "date:" + new Date(getDate());
        Logger.d(log);
    }
}
