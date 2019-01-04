package com.ours.yours.app.manager;

import android.content.Context;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.ValueEventListener;
import com.ours.yours.app.firebase.FirebaseDatabaseHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseManager {
    private Map<Context, List<ValueEventListener>> mValueListeners = new HashMap<>();
    private Map<Context, List<ChildEventListener>> mChildListeners = new HashMap<>();

    protected void registerValueEventListeners(Context context, ValueEventListener listener) {
        if (mValueListeners.containsKey(context)) {
            mValueListeners.get(context).add(listener);
        } else {
            ArrayList<ValueEventListener> list = new ArrayList<>();
            list.add(listener);
            mValueListeners.put(context, list);
        }
    }

    protected void unregisterValueEventListeners(Context context) {
        if (mValueListeners.containsKey(context)) {
            for (ValueEventListener listener : mValueListeners.get(context)) {
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(listener);
            }
            mValueListeners.remove(context);
        }
    }

    protected void registerChildEventListeners(Context context, ChildEventListener listener) {
        if (mChildListeners.containsKey(context)) {
            mChildListeners.get(context).add(listener);
        } else {
            ArrayList<ChildEventListener> list = new ArrayList<>();
            list.add(listener);
            mChildListeners.put(context, list);
        }
    }

    protected void unregisterChildEventListeners(Context context) {
        if (mChildListeners.containsKey(context)) {
            for (ChildEventListener listener : mChildListeners.get(context)) {
                FirebaseDatabaseHelper.getInstance().detachChildEventListener(listener);
            }
            mChildListeners.remove(context);
        }
    }
}
