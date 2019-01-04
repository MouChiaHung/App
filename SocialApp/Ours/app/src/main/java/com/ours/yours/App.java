package com.ours.yours;

import android.app.Application;
import android.support.annotation.NonNull;

import me.yokeyword.fragmentation.Fragmentation;
import me.yokeyword.fragmentation.helper.ExceptionHandler;

/**
 * Created by Amo on 2018/3/26.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Fragmentation.builder().stackViewMode(Fragmentation.NONE)
                .debug(false).handleException(new ExceptionHandler() {
            @Override
            public void onException(@NonNull Exception e) {

            }
        })
        .install();
    }
}