package com.ours.yours.app.ui.mvp;

public interface OurView extends OurPresenter.MVPCallback{
    void feed(OurModel model, OurPresenter presenter);
}
