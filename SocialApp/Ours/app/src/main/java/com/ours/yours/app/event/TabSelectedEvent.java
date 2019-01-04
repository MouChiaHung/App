package com.ours.yours.app.event;

public class TabSelectedEvent {
    private int position;
    public TabSelectedEvent(int pos) {
        this.position = pos;
    }

    public int getPosition() {
        return position;
    }
}
