package com.ours.yours.app.ui.fragment.first.child;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.GravityCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.orhanobut.logger.Logger;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.auth.FirebaseAuth;
import com.ours.yours.R;
import com.ours.yours.app.adapter.BaseModelAdapter;
import com.ours.yours.app.adapter.CommentAdapter;
import com.ours.yours.app.adapter.PhotoAdapter;
import com.ours.yours.app.base.BaseChildFragment;
import com.ours.yours.app.entity.Photo;
import com.ours.yours.app.entity.Card;
import com.ours.yours.app.entity.CardList;
import com.ours.yours.app.entity.Comment;
import com.ours.yours.app.entity.CommentList;
import com.ours.yours.app.firebase.FirebaseDatabaseHelper;
import com.ours.yours.app.manager.CardManager;
import com.ours.yours.app.manager.CommentManager;
import com.ours.yours.app.ui.mvp.OurModel;
import com.ours.yours.app.ui.mvp.OurPresenter;
import com.ours.yours.app.ui.mvp.OurView;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FirstCardDetailFragment extends BaseChildFragment implements OurView {
    private final static String ARG_CARD_DETAIL = "ARG_CARD_DETAIL";

    private final static int HANDLER_MSG_WHAT_BASE                  = 10;
    private  final static int HANDLER_MSG_WHAT_EDIT_CARD             = HANDLER_MSG_WHAT_BASE + 1;
    private  final static int HANDLER_MSG_WHAT_REMOVE_CARD           = HANDLER_MSG_WHAT_BASE + 2;
    private  final static int HANDLER_MSG_WHAT_INIT_CARD_SUCCESS     = HANDLER_MSG_WHAT_BASE + 3;
    private  final static int HANDLER_MSG_WHAT_INIT_COMMENT_SUCCESS             = HANDLER_MSG_WHAT_BASE + 4;
    private  final static int HANDLER_MSG_WHAT_SYNC_COMMENT_DATA_SET_SUCCESS    = HANDLER_MSG_WHAT_BASE + 40;
    private  final static int HANDLER_MSG_WHAT_INIT_CARD_FAILURE     = HANDLER_MSG_WHAT_BASE + 5;
    private  final static int HANDLER_MSG_WHAT_INIT_COMMENT_FAILURE             = HANDLER_MSG_WHAT_BASE + 6;
    private  final static int HANDLER_MSG_WHAT_SYNC_COMMENT_DATA_SET_FAILURE    = HANDLER_MSG_WHAT_BASE + 60;
    private  final static int HANDLER_MSG_WHAT_INIT_NO_COMMENT                  = HANDLER_MSG_WHAT_BASE + 7;
    private  final static int HANDLER_MSG_WHAT_SYNC_COMMENT_DATA_SET_NO_COMMENT = HANDLER_MSG_WHAT_BASE + 70;
    private  final static int HANDLER_MSG_WHAT_CANCEL                  = HANDLER_MSG_WHAT_BASE + 8;
    private  final static int HANDLER_MSG_WHAT_POST_COMMENT_SUCCESS    = HANDLER_MSG_WHAT_BASE + 9;
    private  final static int HANDLER_MSG_WHAT_POST_COMMENT_FAILURE    = HANDLER_MSG_WHAT_BASE + 10;
    private  final static int HANDLER_MSG_WHAT_REMOVE_COMMENT_SUCCESS  = HANDLER_MSG_WHAT_BASE + 11;
    private  final static int HANDLER_MSG_WHAT_REMOVE_COMMENT_FAILURE  = HANDLER_MSG_WHAT_BASE + 12;

    private final static int POP_RESULT_BASE                    = 20;
    public  final static int POP_RESULT_INIT_CARD_FAILURE       = POP_RESULT_BASE + 0;
    public  final static int POP_RESULT_INIT_COMMENT_FAILURE    = POP_RESULT_BASE + 1;
    public  final static int POP_RESULT_CANCEL                  = POP_RESULT_BASE + 3;

    public  final static int POP_RESULT_REMOVE_CARD_SUCCESS_COMMENT_SUCCESS = POP_RESULT_BASE + 6;
    public  final static int POP_RESULT_REMOVE_CARD_SUCCESS_COMMENT_FAILURE = POP_RESULT_BASE + 7;
    public  final static int POP_RESULT_REMOVE_CARD_FAILURE_COMMENT_NO_TRY  = POP_RESULT_BASE + 8;

    private FirstHomeFragment mFirstHomeFragment;
    private int mIndexOfAdapter;
    private View mView;
    private RelativeLayout firstCardDetailRCVPhotoContainer;
    private Toolbar mToolbar;
    private TextView mProfileNameView;
    private TextView mArticleTitleView, mArticleContentView;
    private ImageView mProfilePhotoView;
    private RecyclerView mPhotoRCV;
    private RecyclerView mCommentRCV;
    private PhotoAdapter mPhotoAdapter;
    private CommentAdapter mCommentAdapter;
    private LinearLayoutManager mPhotoLayoutManager;
    private LinearLayoutManager mCommentLayoutManager;
    private EditText mCommentEditText;
    private Button mSendBtn, mSnackBtn;

    private Handler mHandler;
    /**
     * retrieves the card presented on this our view by invoking the presenter
     * to load a specified model from firebase and obtains the reference on the callback
     */
    private Card mCard;
    private String mClickedAuthorId; //passed from a picking up on home fragment
    private String mClickedCardId;   //passed from a picking up on home fragment
    private Comment mComment;

    private OnPop mOnPop;
    private OnShow mOnShow;
    private int mResultCode; //used to pass a result to OnPop in response to user

    private boolean is_test = false;
    private static boolean is_debug = true;

    public static FirstCardDetailFragment newInstance(String author_id, String card_id) {
        Bundle args = new Bundle();
        FirstCardDetailFragment fragment = new FirstCardDetailFragment();
        fragment.setClickedAuthorId(author_id);
        fragment.setClickedCardId(card_id);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * once Fragment is returned from back stack, its View would be destroyed and recreated.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        mView = inflater.inflate(R.layout.app_first_child_card_detail, container, false);
        initHandler();
        initView();
        initToolbar();

        initPhotoRecyclerView();
        initPhotoAdapter();

        initCommentAdapter();
        initCommentRecyclerView();

        initCard();
        return mView;
    }

    /**
     *  If any property is belonged to View, do the state saving/restoring inside View
     *  through having implements on View#onSaveInstanceState() and View#onRestoreInstanceState().
     *  If any property is belonged to Fragment, do it inside Fragment
     *  through having implements on Fragment#onSaveInstanceState() and Fragment#onActivityCreated().
     */
    @Override
    public void onResume() {
        //Logger.d(">>>");
        super.onResume();
        if (getArguments().getParcelable(ARG_CARD_DETAIL) == null) Logger.e("!!! no card saved in argument of this fragment");
        //bindCardData();
        //bindCommentData();
    }

    @Override
    public void onAttach(Context context) {
        Logger.d(">>>");
        super.onAttach(context);
    }

    @Override
    public void onSupportVisible() {
        Logger.d(">>>");
        super.onSupportVisible();
    }

    @Override
    public void onSupportInvisible() {
        Logger.d(">>>");
        super.onSupportInvisible();
    }

    @Override
    public void onDestroyView() {
        Logger.d(">>>");
        super.onDestroyView();
        if (mPhotoAdapter != null) mPhotoAdapter.detach(this);
        mPhotoRCV.clearOnScrollListeners();
        mCommentRCV.clearOnScrollListeners();
    }

    /**
     * pops this child fragment by popping the last back stack
     * which's on the top of the mBackStack of child fragment manager and records the last operations of fragments
     *
     * first fragment invokes loadRootFragment() to create a child fragment manager and uses it to start child fragments including this
     * therefore, this fragment is managed by a child manger belonging to a parent manager which is managing the first fragment
     */
    @Override
    public void pop() {
        Logger.d(">>> mResultCode:" + mResultCode);
        super.pop();
        mPhotoAdapter.cancel(true);
        if (mOnPop != null) mOnPop.onPop(mResultCode);
    }

    @Override
    public boolean onBackPressedSupport() {
        Message message = new Message();
        message.what = HANDLER_MSG_WHAT_CANCEL;
        mHandler.sendMessage(message);
        /**
         * consumes this back press event at TransactionDelegate#dispatchBackPressedEvent()
         * and let handler trigger pop() which has been overridden by this class
         * instead of ending in popChild() on the parent fragment "FirstFragment"
         */
        return true;
    }

    public void setHomeFragment(FirstHomeFragment fragment) {
        this.mFirstHomeFragment = fragment;
    }

    public void setIndexOfAdapter(int indexOfAdapter) {
        this.mIndexOfAdapter = indexOfAdapter;
    }

    public void setClickedAuthorId(String card_id) {
        this.mClickedAuthorId = card_id;
    }

    public void setClickedCardId(String card_id) {
        this.mClickedCardId = card_id;
    }

    public void setOnPop(OnPop onPop) {
        this.mOnPop = onPop;
    }

    public void setOnShow(OnShow onShow) {
        this.mOnShow = onShow;
    }

    @SuppressLint("HandlerLeak")
    private void initHandler() {
        mHandler = new UIHandler();
    }

    private void initView() {
        if (mView == null) {
            Logger.e("!!! mView is null");
            return;
        }
        firstCardDetailRCVPhotoContainer = mView.findViewById(R.id.firstCardDetailRCVPhotoContainer);
        mProfilePhotoView = mView.findViewById(R.id.firstCardDetailAuthorImageView);
        mProfileNameView = mView.findViewById(R.id.firstCardDetailAuthorNameTextView);
        mArticleTitleView = mView.findViewById(R.id.firstCardDetailArticleTitleTextView);
        mArticleContentView = mView.findViewById(R.id.firstCardDetailArticleContentTextView);
        mCommentEditText = mView.findViewById(R.id.firstCardDetailSendCommentEditText);
        mSendBtn = mView.findViewById(R.id.firstCardDetailSendCommentButton);
        mSnackBtn = mView.findViewById(R.id.firstCardDetailSnackBtn);
        mCommentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                mSendBtn.setEnabled(s.toString().trim().length() > 0);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mSendBtn.setEnabled(s.toString().trim().length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {
                mSendBtn.setEnabled(s.toString().trim().length() > 0);
            }
        });
        mSendBtn.setOnClickListener(new SendBtnOnClickListener());
    }

    private void initPhotoAdapter() {
        mPhotoAdapter = new PhotoAdapter(_mActivity);
        mPhotoAdapter.setClickedAuthorId(mClickedAuthorId);
        mPhotoAdapter.setClickedCardId(mClickedCardId);
        mPhotoAdapter.setRemoveBtnVisible(false);
        mPhotoAdapter.attach(this);
        mPhotoRCV.setAdapter(mPhotoAdapter);
        readPhotosFromMocks(mPhotoAdapter);
    }

    private void initPhotoRecyclerView() {
        mPhotoRCV = mView.findViewById(R.id.firstCardDetailPhotoRCV);
        mPhotoLayoutManager = new LinearLayoutManager(_mActivity, LinearLayoutManager.HORIZONTAL, false);
        //mPhotoLayoutManager.setAutoMeasureEnabled(true);
        //sets layout manager to position the items
        mPhotoRCV.setLayoutManager(mPhotoLayoutManager);
        mPhotoRCV.setHasFixedSize(false);
        mPhotoRCV.addOnScrollListener(new OnRCVScroll());
    }

    private void initCommentAdapter() {
        mCommentAdapter = new CommentAdapter(_mActivity, this);
        mCommentAdapter.setCommentClickListener(new CommentClickListener());
        mCommentAdapter.setCommentLoadListener(new CommentLoader());
        readCommentsFromMocks(mCommentAdapter);
    }

    private void initCommentRecyclerView() {
        mCommentRCV = mView.findViewById(R.id.firstCardDetailCommentRCV);
        mCommentLayoutManager = new LinearLayoutManager(_mActivity);
        //mCommentLayoutManager.setAutoMeasureEnabled(true);
        //sets layout manager to position the items
        mCommentRCV.setLayoutManager(mCommentLayoutManager);
        mCommentRCV.setHasFixedSize(false);
        mCommentRCV.setAdapter(mCommentAdapter);
    }

    /**
     * firstly sync. card with firebase through presenter (PhotoAdapter), secondly sync. comment
     *
     * clears the mock data set used to initial the adapter before
     *
     * no need to notify PhotoRCV item changes here due to
     * PhotoAdapter.LoadTask runs setItemsAsDataSet() which would trigger RCV UI updates
     * while completing reading data set from Firebase
     */
    private void initCard() {
        if (mCard == null) {
            Logger.d("... going to create a card");
            mCard = new Card();
            assert getArguments() != null;
            getArguments().putParcelable(ARG_CARD_DETAIL, mCard);
        }
        mPhotoAdapter.removeItemsFromDataSet(BaseModelAdapter.REMOVE_POSITION_AT_FIRST, BaseModelAdapter.REMOVE_POSITION_AT_LAST);
        mPhotoAdapter.load();
    }

    /**
     * after sync. card so got current card id, it's time to sync. comment through
     *
     * clears the mock data set just used to init. the adapter
     */
    private void initComments() {
        Logger.d(">>>");
        if (is_test) {
            readCommentsFromMocks(mCommentAdapter);
            Message msg = new Message();
            msg.what = HANDLER_MSG_WHAT_INIT_COMMENT_SUCCESS;
            mHandler.sendMessage(msg);
        } else {
            clearComments();
            syncCommentData();
        }
    }

    /**
     * synchronize some of data of this card with UI
     */
    private void bindCardData() {
        Logger.d(">>>");
        if (mCard == null) {
            Logger.e("!!! card is null");
            return;
        }
        if (mCard.getArticlePhoto() == null) {
            Logger.e("!!! ArticlePhoto is null");
            return;
        }
        if (mCard.getProfilePhoto() == null) {
            Logger.e("!!! ProfilePhoto is null");
            return;
        }
        if (mCard.getArticleCount() <= 0) {
            Logger.d("... going to set firstCardDetailRCVPhotoContainer gone");
            firstCardDetailRCVPhotoContainer.setVisibility(View.GONE);
        }
        String profile_photo = mCard.getProfilePhoto();
        Logger.d("profile photo:" + mCard.getProfilePhoto());
        Glide.with(_mActivity)
                .load(profile_photo)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .crossFade()
                .error(R.drawable.ic_stub)
                .into(mProfilePhotoView);
        mProfileNameView.setText(mCard.getProfileName());
        mArticleTitleView.setText(mCard.getArticleTitle());
        mArticleContentView.setText(mCard.getArticleContent());
        if (mCard.getArticlePhoto().size() != mCard.getArticleCount()) Logger.e("!!! record of count error");
        if (mPhotoAdapter == null) {
            Logger.e("!!! photo adapter is null");
            /**
             * no need to notify PhotoRCV item changes here because previously calling to initCard() has invoked
             * PhotoAdapter.LoadTask which runs setItemsAsDataSet() that would trigger RCV UI updates
             * while completing reading data set from Firebase
             */
            return;
        }
    }

    /**
     * just to synchronize the present data set with the display on UI
     * by calling RecyclerView.Adapter#notifyDataSetChanged() to trigger CommentAdapter#onBindViewHolder()
     * which leads to the recycler view carries out updating the display on its own
     *
     * don't call RecyclerView.Adapter#notifyDataSetChanged() in RecyclerView.Adapter#onBindViewHolder()
     * for avoiding endless loops
     */
    private void bindCommentData() {
        if (mCommentAdapter == null) {
            Logger.e("!!! comment adapter is null");
            return;
        }
        if (mCard == null) {
            Logger.e("!!! mCard is null");
            return;
        }
        Logger.d("... going to mCommentAdapter.notifyDataSetChanged()");
        mCommentAdapter.notifyDataSetChanged();
    }

    /**
     * just to synchronize the data set of adapter with firebase database
     * by calling readCommentsFromFirebase(dest)
     */
    private void syncCommentData() {
        if (mCommentAdapter == null) {
            Logger.e("!!! comment adapter is null");
            return;
        }

        if (mCard == null) {
            Logger.e("!!! mCard is null");
            return;
        }
        if (!mClickedCardId.equals(mCard.getCardId())) {
            Logger.e("!!! card id not match");
            return;
        }
        try {
            readCommentsFromFirebase(mClickedCardId, getComments());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void initToolbar() {
        mToolbar = mView.findViewById(R.id.firstCardDetailToolbar);
        mToolbar.inflateMenu(R.menu.card_detail_menu);
        mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.options:
                        final PopupMenu popupMenu = new PopupMenu(_mActivity, mToolbar, GravityCompat.END);
                        popupMenu.inflate(R.menu.card_detail_popup_menu);
                        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                switch (item.getItemId()) {
                                    case R.id.edit_card:
                                        Message msg_edit = new Message();
                                        msg_edit.what = HANDLER_MSG_WHAT_EDIT_CARD;
                                        mHandler.sendMessage(msg_edit);
                                        popupMenu.dismiss();
                                        break;
                                    case R.id.remove_card:
                                        Message msg_remove = new Message();
                                        msg_remove.what = HANDLER_MSG_WHAT_REMOVE_CARD;
                                        mHandler.sendMessage(msg_remove);
                                        popupMenu.dismiss();
                                        break;
                                }
                                return true;
                            }
                        });
                        popupMenu.show();
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        if (!hasAccessToModifyCard(FirebaseDatabaseHelper.getInstance().getUserId())) {
            Logger.d("... not my card");
            mToolbar.getMenu().findItem(R.id.options).setVisible(false);
        } else {
            Logger.d("... this is my card");
            mToolbar.getMenu().findItem(R.id.options).setVisible(true);
        }
    }

    /**
     * orientates recycler view along the edge of the item at specified posion
     */
    private void orientateRecyclerView(RecyclerView rcv, int position) {
        if (rcv == null) return;
        if (position < 0) return;
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) rcv.getLayoutManager();
        if (linearLayoutManager == null) return;
        int first = linearLayoutManager.findFirstVisibleItemPosition();
        int last = linearLayoutManager.findLastVisibleItemPosition();
        if (position < first) {
            rcv.smoothScrollToPosition(first);
        } else if (position > last) {
            rcv.smoothScrollToPosition(last);
        } else {
            rcv.smoothScrollBy((rcv.getChildAt(position-first)).getLeft(), 0);
        }
    }

    /**
     * reads a card from firebase real time database according to card id of the card which is clicked before
     *
     * this method is usually used to implement OurView#feed()
     */
    private void readCardFromFirebase(final String clicked_author_id, final String clicked_card_id, final Card dest) throws ClassNotFoundException {
        if (clicked_card_id != null) {
            showProgress("努力下載中...");
            CardManager.getInstance(_mActivity).subscribeCardInFirebase(clicked_author_id, clicked_card_id, new CardSubscriber() {
                @Override
                public void onUpdate(Card card) {
                    Logger.d(">>>");
                    hideProgress();
                    dest.setCard(card);
                    if (clicked_card_id.equals(dest.getCardId())) {
                        Logger.d("... got a matched card");
                        Message msg = new Message();
                        msg.what = HANDLER_MSG_WHAT_INIT_CARD_SUCCESS;
                        mHandler.sendMessage(msg);
                    } else {
                        Logger.e("!!! got a unmatched card");
                        Message msg = new Message();
                        msg.what = HANDLER_MSG_WHAT_INIT_CARD_FAILURE;
                        mHandler.sendMessage(msg);
                    }
                }

                @Override
                public void onClone(CardList cards) {}

                @Override
                public void onError(String error) {
                    Logger.e("!!! error:" + error);
                    hideProgress();
                    Message msg = new Message();
                    msg.what = HANDLER_MSG_WHAT_INIT_CARD_FAILURE;
                    mHandler.sendMessage(msg);
                }
            });
        }
    }

    private void tearoffCard() {
        try {
            CardManager.getInstance(_mActivity).tearoffCardFromFirebase(mCard, new CardRemover() {
                @Override
                public void onSuccess() {
                    try {
                        if (mCommentAdapter.getItemCount() <= 0) {
                            Logger.d("... no comment of card and going to notice whole success of tearoff");
                            hideProgress();
                            showDialogAfterRemoveCard(POP_RESULT_REMOVE_CARD_SUCCESS_COMMENT_SUCCESS);
                        } else {
                            Logger.d("... count of comment:" + mCommentAdapter.getItemCount() + " and delete them");
                            CommentManager.getInstance(_mActivity).tearoffCommentsOfCardFromFirebase(mClickedCardId, mClickedAuthorId
                                , new CommentRemover() {
                                    @Override
                                    public void onSuccess() {
                                        hideProgress();
                                        showDialogAfterRemoveCard(POP_RESULT_REMOVE_CARD_SUCCESS_COMMENT_SUCCESS);
                                    }

                                    @Override
                                    public void onFailure() {
                                        hideProgress();
                                        showDialogAfterRemoveCard(POP_RESULT_REMOVE_CARD_SUCCESS_COMMENT_FAILURE);
                                    }
                                });
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    Logger.d("... going to remove item of adapter of first home fragment at " + mIndexOfAdapter);
                    mFirstHomeFragment.getCardAdapter().removeItemFromDataSet(mIndexOfAdapter);
                }

                @Override
                public void onFailure() {
                    hideProgress();
                    showDialogAfterRemoveCard(POP_RESULT_REMOVE_CARD_FAILURE_COMMENT_NO_TRY);
                }
            });
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * synchronizes the data set of somewhere with firebase
     *
     * call bindComment
     */
    private void readCommentsFromFirebase(final String clicked_card_id, final List<Comment> dest) throws ClassNotFoundException {
        Logger.d(">>> card id:" + clicked_card_id);
        if (dest == null) {
            Logger.e("!!! dest is null");
            return;
        }
        if (is_test && mCommentAdapter != null) {
            readCommentsFromMocks(mCommentAdapter);
            return;
        }
        if (clicked_card_id != null) {
            CommentManager.getInstance(_mActivity).subscribeCommentsOfCardInFirebase(clicked_card_id, new CommentSubscriber() {
                @Override
                public void onClone(CommentList comments) {
                    Logger.d(">>>");
                    dest.clear();
                    dest.addAll(comments.getComments());
                    Logger.d("... got comments");
                    Message msg = new Message();
                    msg.what = HANDLER_MSG_WHAT_SYNC_COMMENT_DATA_SET_SUCCESS;
                    mHandler.sendMessage(msg);
                }

                @Override
                public void onError(String error) {
                    Logger.e("!!! error:" + error);
                    Message msg = new Message();
                    msg.what = HANDLER_MSG_WHAT_SYNC_COMMENT_DATA_SET_FAILURE;
                    mHandler.sendMessage(msg);
                }
            });
        }
    }

    /**
     * synchronizes the data set of adapter with firebase and notifies recycler view of changes
     */
    private void readCommentsFromFirebase(final String clicked_card_id, final BaseModelAdapter adapter) {
        Logger.d(">>>");
        if (adapter == null) {
            Logger.e("!!! comment adapter is null");
            return;
        }
        if (is_test) {
            readCommentsFromMocks((CommentAdapter) adapter);
            return;
        }
        if (clicked_card_id != null) {
            try {
                CommentManager.getInstance(_mActivity).subscribeCommentsOfCardInFirebase(clicked_card_id, new CommentSubscriber() {
                    @Override
                    public void onClone(CommentList comments) {
                        Logger.d(">>>");
                        if (adapter instanceof CommentAdapter) {
                            setComments(comments.getComments());
                        } else {
                            Logger.d("!!! adapter is not comment adapter");
                        }
                        if (getCountOfComments() > 0) {
                            if (clicked_card_id.equals(getComment(0).getCardId())) {
                                Logger.d("... got matched comments");
                                Message msg = new Message();
                                msg.what = HANDLER_MSG_WHAT_INIT_COMMENT_SUCCESS;
                                mHandler.sendMessage(msg);
                            } else {
                                Logger.e("!!! got unmatched comments");
                                Message msg = new Message();
                                msg.what = HANDLER_MSG_WHAT_INIT_COMMENT_FAILURE;
                                mHandler.sendMessage(msg);
                            }
                        } else {
                            Logger.d("... got a no comment");
                            Message msg = new Message();
                            msg.what = HANDLER_MSG_WHAT_INIT_NO_COMMENT;
                            mHandler.sendMessage(msg);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Logger.e("!!! error:" + error);
                        Message msg = new Message();
                        msg.what = HANDLER_MSG_WHAT_INIT_COMMENT_FAILURE;
                        mHandler.sendMessage(msg);
                    }
                });
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * for testing and fixing the issue
     * that RecyclerView.Adapter#onBindViewHolder() won't be invoked if RecyclerView.Adapter#getItemCount() return 0
     */
    private void readPhotosFromMocks(PhotoAdapter adapter) {
        Logger.d(">>>");
        Photo mock;
        String url = Photo.DEFAULT_URL;
        String name = Photo.DEFAULT_NAME;

        for (int i = 0; i < 1; i++) {
            mock = new Photo(url, name);
            adapter.addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_LAST, mock);
        }
    }

    /**
     * for testing and fixing the issue
     * that RecyclerView.Adapter#onBindViewHolder() won't be invoked if RecyclerView.Adapter#getItemCount() return 0
     */
    private void readCommentsFromMocks(CommentAdapter adapter) {
        Logger.d(">>>");
        Comment mock;
        String cardId = Comment.DEFAULT_CARD_ID;
        String commentId = Comment.DEFAULT_COMMENT_ID;
        String profilePhoto = Comment.DEFAULT_PROFILE_PHOTO_URL;
        String profileName = Comment.DEFAULT_PROFILE_NAME;
        String profileID = Comment.DEFAULT_PROFILE_ID;
        String comment = Comment.DEFAULT_COMMENT;
        int distance = Comment.DEFAULT_DISTANCE;
        long date = (new Date()).getTime();

        for (int i = 0; i < 1; i++) {
            mock = new Comment(cardId, commentId, profilePhoto, profileName, profileID, comment, distance, date);
            //mock.printCommentData();
            adapter.addItemToDataSet(CommentAdapter.ADD_POSITION_AT_LAST, mock);
        }
    }

    private boolean hasAccessToModifyCard(String user_id) {
        Logger.d("... user id:" + user_id);
        if (mClickedAuthorId == null) {
            Logger.e("!!! assigned card is null");
            return false;
        } else {
            return user_id.equals(mClickedAuthorId);
        }
    }

    private void showDialogAfterRemoveCard(int result) {
        Logger.d("... result:" + result);
        AlertDialog.Builder builder = new AlertDialog.Builder(_mActivity);
        if (result == POP_RESULT_REMOVE_CARD_SUCCESS_COMMENT_SUCCESS) {
            builder.setMessage("已經刪除貼文");
            builder.setPositiveButton("回到主頁", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mResultCode = POP_RESULT_REMOVE_CARD_SUCCESS_COMMENT_SUCCESS;
                    pop();
                }
            });
        } else if (result == POP_RESULT_REMOVE_CARD_SUCCESS_COMMENT_FAILURE) {
            builder.setMessage("刪掉貼文但是推文刪不掉耶");
            builder.setPositiveButton("回到主頁", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mResultCode = POP_RESULT_REMOVE_CARD_SUCCESS_COMMENT_FAILURE;
                    pop();
                }
            });
        } else if (result == POP_RESULT_REMOVE_CARD_FAILURE_COMMENT_NO_TRY) {
            builder.setMessage("刪不掉耶");
            builder.setNegativeButton("算了", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            builder.setPositiveButton("回到主頁", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mResultCode = POP_RESULT_REMOVE_CARD_FAILURE_COMMENT_NO_TRY;
                    pop();
                }
            });
        }
        builder.show();
    }

    public void showSnackBar(String msg) {
        showSnackBar(mSnackBtn, msg);
    }

    /**
     * indirectly accesses the data set of comment adapter
     */
    private void addComment(int position, Comment comment) {
        if (mCommentAdapter == null) {
            Logger.e("!!! comment adapter is null");
            return;
        }
        mCommentAdapter.addItemToDataSet(position, comment);
    }

    private void addComments(List<Comment> comments) {
        if (mCommentAdapter == null) {
            Logger.e("!!! comment adapter is null");
            return;
        }
        mCommentAdapter.addItemsToDataSet(comments);
    }

    private void setComments(List<Comment> comments) {
        if (mCommentAdapter == null) {
            Logger.e("!!! comment adapter is null");
            return;
        }
        mCommentAdapter.setItemsAsDataSet(comments);
    }

    private Comment getComment(int position) {
        if (mCommentAdapter == null) {
            Logger.e("!!! comment adapter is null");
            return null;
        }
        return mCommentAdapter.getItemOfDataSet(position);
    }

    private List<Comment> getComments() {
        if (mCommentAdapter == null) {
            Logger.e("!!! comment adapter is null");
            return null;
        }
        return mCommentAdapter.getItemsOfDataSet();
    }

    private int getCountOfComments() {
        if (mCommentAdapter == null) {
            Logger.e("!!! comment adapter is null");
            return 0;
        }
        return mCommentAdapter.getItemCount();
    }

    private Comment removeComment(int position) {
        if (mCommentAdapter == null) {
            Logger.e("!!! comment adapter is null");
            return null;
        }
        return mCommentAdapter.removeItemFromDataSet(position);
    }

    private int removeComments(int start, int end) {
        if (mCommentAdapter == null) {
            Logger.e("!!! comment adapter is null");
            return 0;
        }
        return mCommentAdapter.removeItemsFromDataSet(start, end);
    }

    private void clearComments() {
        if (mCommentAdapter == null) {
            Logger.e("!!! comment adapter is null");
            return;
        }
        mCommentAdapter.removeItemsFromDataSet(CommentAdapter.REMOVE_POSITION_AT_FIRST, CommentAdapter.REMOVE_POSITION_AT_LAST);
    }

    /**
     * posts a new comment which has un-specified comment id and article photo url, they will be assigned by comment manager later
     *
     * a poster implemented and used to respond to user on UI when comment manager gets the event
     * about result of task of write pojo of comment to firebase real time database
     */
    private void post(Comment comment) throws ClassNotFoundException {
        if (CommentManager.getInstance(_mActivity) == null) {
            Logger.e("!!! comment manager is null");
            return;
        }
        CommentPoster poster = new CommentPoster();
        poster.setComment(comment);
        CommentManager.getInstance(_mActivity).postupCommentOnFirebase(comment, poster);
    }

    private Point getSize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    /**
     * callback of view as MVP design pattern holding jobs of view while presenter task is done
     */
    @Override
    public void feed(OurModel model, OurPresenter presenter) {
        
    }

    @Override
    public void onNotice(int result, String message) {

    }

    @Override
    public void onFetch(int result, Object pojo) {

    }

    @Override
    public void onLoad(int result, Object pojo) {
        switch (result) {
            case BaseModelAdapter.RESULT_SUCCESS:
                Logger.d("... got BaseModelAdapter.RESULT_SUCCESS");
                hideProgress();
                if (pojo instanceof Card) {
                    mCard = (Card) pojo;
                    mCard.printCardData();
                }
                if (mClickedCardId.equals(mCard.getCardId())) {
                    Logger.d("... going to send HANDLER_MSG_WHAT_INIT_CARD_SUCCESS");
                    Message msg = new Message();
                    msg.what = HANDLER_MSG_WHAT_INIT_CARD_SUCCESS;
                    mHandler.sendMessage(msg);
                } else {
                    Message msg = new Message();
                    msg.what = HANDLER_MSG_WHAT_INIT_CARD_FAILURE;
                    mHandler.sendMessage(msg);
                }
                break;
            case BaseModelAdapter.RESULT_FAILURE:
                Logger.e("!!! got BaseModelAdapter.RESULT_FAILURE");
                mCard.printCardData();
                hideProgress();
                Message msg = new Message();
                msg.what = HANDLER_MSG_WHAT_INIT_CARD_FAILURE;
                mHandler.sendMessage(msg);
                break;
            default:
                break;
        }
    }

    @Override
    public void onRemove(int result, int position, Object model) {

    }

    @Override
    public void onRecover(int result, int position, Object model) {

    }

    /**
     * callback for pop() on add card fragment
     */
    class EditCardFragmentOnPop implements FirstEditCardFragment.OnPop {
        @Override
        public void onPop(int resultCode) {
            //pullCards();
            switch (resultCode) {
                case FirstAddCardFragment.POP_RESULT_POST_SUCCESS:
                    showSnackBar("編輯成功惹");
                    initCard();
                    break;
                case FirstAddCardFragment.POP_RESULT_UPLOAD_FAILURE:
                    showSnackBar("上傳圖片失敗耶");
                    break;
                case FirstAddCardFragment.POP_RESULT_POST_FAILURE:
                    showSnackBar("編輯失敗囉");
                    break;
                case FirstAddCardFragment.POP_RESULT_CANCEL:
                    showSnackBar("剛剛沒改了什麼");
                    break;
            }
        }
    }

    class OnRCVScroll extends RecyclerView.OnScrollListener {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                //Logger.d("... do nothing when not at scroll state idle");
                return;
            }
            int first_visible = ((LinearLayoutManager)recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            int first_complete = ((LinearLayoutManager)recyclerView.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
            int last_visible = ((LinearLayoutManager)recyclerView.getLayoutManager()).findLastVisibleItemPosition();
            int last_complete = ((LinearLayoutManager)recyclerView.getLayoutManager()).findLastCompletelyVisibleItemPosition();
            int destination = (first_visible == first_complete) ? first_visible : first_visible+1;
            if (first_visible == first_complete) {
                //Logger.d("... no need to scroll at first complete:" + first_complete);
                return;
            }
            if (destination == RecyclerView.NO_POSITION) {
                Logger.e("!!! no position to scroll");
                return;
            }
            if (last_complete == recyclerView.getAdapter().getItemCount()-1) {
                //Logger.d("... no need to scroll at last complete:" + last_complete);
                return;
            }
            orientateRecyclerView(recyclerView, destination); //bug happens when
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
        }
    }

    /**
     * callback for comment manager in response to user when to read the database of firebase to the data set of comment adapter
     */
    class CommentSubscriber implements CommentManager.Subscriber {
        @Override
        public void onUpdate(Comment card) {

        }

        @Override
        public void onClone(CommentList cards) {

        }

        @Override
        public void onError(String error) {}
    }

    /**
     * callback for card manager in response to user when to read the database of firebase to the data set of card adapter
     */
    class CardSubscriber implements CardManager.Subscriber {
        @Override
        public void onUpdate(Card card) {}

        @Override
        public void onClone(CardList cards) {}

        @Override
        public void onError(String error) {}

        @Override
        public void onAuthors(List<String> author_ids) {

        }

        @Override
        public void onGetParentIdsAndCounts(Map<String, Long> parentIdsAndSiblingCounts) {

        }
    }

    /**
     * callback for card manager in response to user when to remove the database from firebase
     */
    class CardRemover implements CardManager.Remover {
        @Override
        public void onFind(boolean isFound) {

        }

        @Override
        public void onSuccess(int process, int success_index) {

        }

        @Override
        public void onSuccess() {

        }

        @Override
        public void onFailure(int fail_index) {

        }

        @Override
        public void onFailure() {

        }
    }

    /**
     * callback for comment adapter to respond to user
     */
    class CommentClickListener implements CommentAdapter.CommentClickListener {
        @Override
        public void onCommentClick(Comment comment, View item_view) {

        }
    }

    /**
     * callback for comment adapter to call FirstCardDetailFragment#readCardsFromFirebase() outside
     */
    class CommentLoader implements CommentAdapter.CommentLoader {
        @Override
        public void onNeedLoad(List<Comment> comments) {
            if (mCard == null) {
                Logger.e("!!! card is null");
                return;
            }
            try {
                readCommentsFromFirebase(mClickedCardId, comments);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    class CommentRemover implements CommentManager.Remover {
        @Override
        public void onSuccess() {

        }

        @Override
        public void onFailure() {

        }
    }

    /**
     * commentId is going to be assigned by CardManager with key provided by Firebase
     * distance is going to be assigned by Google Map
     */
    class SendBtnOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (mClickedCardId == null) return;
            String cardId;
            String commentId = Comment.DEFAULT_COMMENT_ID;
            String profilePhoto = Comment.DEFAULT_PROFILE_PHOTO_URL;
            String profileName = Comment.DEFAULT_PROFILE_NAME;
            String profileID = Comment.DEFAULT_PROFILE_ID;
            String comment = Comment.DEFAULT_COMMENT;
            int distance = Comment.DEFAULT_DISTANCE;
            long date = (new Date()).getTime();

            v.setClickable(false); //blocks immediate repeating
            hideKeyboard();
            showProgress("努力上傳中...");
            cardId       = mClickedCardId;
            if (Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getPhotoUrl() != null) {
                profilePhoto = FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl().toString();
            }
            if (FirebaseDatabaseHelper.getInstance().getUserName() != null) profileName  = FirebaseDatabaseHelper.getInstance().getUserName();
            if (FirebaseDatabaseHelper.getInstance().getUserId() != null) profileID    = FirebaseDatabaseHelper.getInstance().getUserId();
            if (!mCommentEditText.getText().toString().equals("")) comment = mCommentEditText.getText().toString();
            mComment = new Comment(cardId, commentId, profilePhoto, profileName, profileID, comment, distance, date);
            try {
                post(mComment);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    class CommentPoster implements CommentManager.Poster {
        Comment comment;

        private void setComment(Comment new_comment) {
           comment = new_comment;
        }

        @Override
        public void onSuccess() {
            Message msg = new Message();
            msg.what = HANDLER_MSG_WHAT_POST_COMMENT_SUCCESS;
            mHandler.sendMessage(msg);
            if (comment != null) {
                Logger.d("... going to addComment()");
                addComment(BaseModelAdapter.ADD_POSITION_AT_FIRST, this.comment);
            }
        }

        @Override
        public void onFailure() {
            Message msg = new Message();
            msg.what = HANDLER_MSG_WHAT_POST_COMMENT_FAILURE;
            mHandler.sendMessage(msg);
        }
    }

    /**
     * callback for popping this fragment from back back
     */
    public interface OnPop {
        void onPop(int resultCode);
    }

    /**
     * callback for showing the numbers of comments of this card
     */
    public interface OnShow {
        void onShow(int number_of_comments);
    }

    @SuppressLint("HandlerLeak")
    private class UIHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_WHAT_EDIT_CARD:
                    FirstEditCardFragment editCardFragment = FirstEditCardFragment.newInstance();
                    editCardFragment.setCard(mCard);
                    if (editCardFragment == null) return;
                    editCardFragment.setOnPop(new EditCardFragmentOnPop());
                    start(editCardFragment);
                    break;
                case HANDLER_MSG_WHAT_REMOVE_CARD:
                    showProgress("努力刪除中...");
                    tearoffCard();
                    break;
                case HANDLER_MSG_WHAT_INIT_CARD_SUCCESS:
                    bindCardData();
                    initComments();
                    break;
                case HANDLER_MSG_WHAT_INIT_CARD_FAILURE:
                    mResultCode = POP_RESULT_INIT_CARD_FAILURE;
                    pop();
                    break;
                case HANDLER_MSG_WHAT_INIT_COMMENT_SUCCESS:

                    break;
                case HANDLER_MSG_WHAT_INIT_COMMENT_FAILURE:
                    mResultCode = POP_RESULT_INIT_COMMENT_FAILURE;
                    pop();
                    break;
                case HANDLER_MSG_WHAT_INIT_NO_COMMENT:

                    break;
                case HANDLER_MSG_WHAT_SYNC_COMMENT_DATA_SET_SUCCESS:
                    bindCommentData();
                    break;
                case HANDLER_MSG_WHAT_SYNC_COMMENT_DATA_SET_FAILURE:

                    break;
                case HANDLER_MSG_WHAT_SYNC_COMMENT_DATA_SET_NO_COMMENT:

                    break;
                case HANDLER_MSG_WHAT_CANCEL:
                    mResultCode = POP_RESULT_CANCEL;
                    pop();
                    break;
                case HANDLER_MSG_WHAT_POST_COMMENT_SUCCESS:
                    Logger.d("... got msg of send comment success");
                    hideProgress();
                    mSendBtn.setClickable(true);
                    mCommentEditText.clearFocus();
                    mCommentEditText.setText("");
                    hideKeyboard();
                    break;
                case HANDLER_MSG_WHAT_POST_COMMENT_FAILURE:
                    Logger.d("... got msg of send comment failure");
                    hideProgress();
                    mSendBtn.setClickable(true);
                    showProgress("推文失敗了...");
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            hideProgress();
                        }
                    }, 1000);
                    break;
                case HANDLER_MSG_WHAT_REMOVE_COMMENT_SUCCESS:
                    Logger.d("... got msg of remove comment success");
                    break;
                case HANDLER_MSG_WHAT_REMOVE_COMMENT_FAILURE:
                    Logger.d("... got msg of remove comment failure");
                    break;
                default:

                    break;
            }
        }
    }

    private static class InnerLogger {
        static void d(@Nullable Object object) {
            if (is_debug) com.orhanobut.logger.Logger.d(object);
        }

        static void e(@NonNull String message, @Nullable Object... args) {
            if (is_debug) com.orhanobut.logger.Logger.e(message, args);
        }
    }
}
