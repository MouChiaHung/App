package com.ours.yours.app.ui.fragment.first.child;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.MainActivity;
import com.ours.yours.app.adapter.BaseModelAdapter;
import com.ours.yours.app.adapter.CardAdapter;
import com.ours.yours.app.base.BaseChildFragment;
import com.ours.yours.app.entity.Card;
import com.ours.yours.app.event.TabSelectedEvent;
import com.ours.yours.app.ui.mvp.OurModel;
import com.ours.yours.app.ui.mvp.OurPresenter;
import com.ours.yours.app.ui.mvp.OurView;
import org.greenrobot.eventbus.Subscribe;

import java.util.Date;
import java.util.List;

import me.yokeyword.eventbusactivityscope.EventBusActivityScope;
import me.yokeyword.fragmentation.SupportHelper;

public class FirstHomeFragment extends BaseChildFragment implements SwipeRefreshLayout.OnRefreshListener, OurView {
    private final static int HANDLER_MSG_WHAT_BASE = 10;

    @SuppressLint("StaticFieldLeak")
    private static FirstHomeFragment instance;
    @SuppressLint("StaticFieldLeak")
    private Handler mHandler;
    private View mView;
    private FloatingActionButton mAddCardBtn;
    private RecyclerView mCardRCV;
    private SwipeRefreshLayout mRefresh;

    private CardAdapter mCardAdapter;
    private CardGenerator mCardGeneratorAction; //FirstAddCardFragment acts as CardGenerator

    /**
     * gets a singleton object about this class
     * should be called before other methods
     */
    public static FirstHomeFragment newInstance() {
        Bundle args = new Bundle();
        FirstHomeFragment fragment = new FirstHomeFragment();
        fragment.setArguments(args);
        instance = fragment;
        return fragment;
    }

    public CardAdapter getCardAdapter() {
        return mCardAdapter;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        mView = inflater.inflate(R.layout.app_first_child_home, container, false);
        EventBusActivityScope.getDefault(_mActivity).register(this);
        initHandler();
        initView();
        initCardAdapter();
        if (mCardAdapter != null) mCardAdapter.attach(this);
        initCardRecyclerView();
        return mView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        super.onActivityCreated(savedInstanceState);
        pullCards();
    }

    @Override
    public void onPause() {
        Logger.d(">>>");
        super.onPause();
        //Logger.e("!!! amo test 1HomePause: going to SupportHelper.getActiveFragment(getFragmentManager()) that includes child fragment manager");
        //SupportHelper.getActiveFragment(getFragmentManager());
    }

    @Override
    public void onResume() {
        //Logger.d(">>>");
        super.onResume();
        //pullCards();
        //Logger.e("!!! amo test 1HomeResume: going to SupportHelper.getActiveFragment(getFragmentManager()) that includes child fragment manager");
        //SupportHelper.getActiveFragment(getFragmentManager());
    }

    @Override
    public void onDestroyView() {
        Logger.d(">>>");
        super.onDestroyView();
        EventBusActivityScope.getDefault(_mActivity).unregister(this);
        if (mCardAdapter != null) mCardAdapter.detach(this);
        mCardRCV.clearOnScrollListeners();
        //Logger.e("!!! amo test 1HomeDestroyView: going to SupportHelper.getActiveFragment(getFragmentManager()) that includes child fragment manager");
        //SupportHelper.getActiveFragment(getFragmentManager());
    }

    @Override
    public boolean onBackPressedSupport() {
        Logger.d(">>>");
        //Logger.e("!!! amo test 1HomeBackPress: going to SupportHelper.getActiveFragment(getFragmentManager()) that includes child fragment manager");
        //SupportHelper.getActiveFragment(getFragmentManager());
        return super.onBackPressedSupport();
    }

    @Override
    public void onRefresh() {
        Logger.d(">>>");
        pullCards();
    }

    /**
     * called when user repeats to select this FIRST Tab button
     */
    @Subscribe
    public void onTabSelectedEvent(TabSelectedEvent event) {
        Logger.d(">>>");
        if (event.getPosition() != MainActivity.FIRST) {
            Logger.e("!!! event.getPosition() != MainActivity.FIRST");
            return;
        }
        if (!mRefresh.isRefreshing()) mRefresh.setRefreshing(true);
        Logger.d("... going to refresh and scroll to first");
        onRefresh();
        mCardRCV.smoothScrollToPosition(BaseModelAdapter.POSITION_AT_FIRST);
    }

    private void initView() {
        mAddCardBtn = mView.findViewById(R.id.add_card_floating_btn);
        mAddCardBtn.setOnClickListener(new OnAddCardClickListener());
        mRefresh = mView.findViewById(R.id.refresh_layoutOfFirstHomeFragment);
        mRefresh.setColorSchemeResources(R.color.colorPrimary);
        mRefresh.setOnRefreshListener(this);
    }

    private void initHandler() {
        mHandler = new UIHandler();
    }

    private void pullCards() {
        Logger.d(">>>");
        if (mCardAdapter == null) return;
        mCardAdapter.clearCards();
        syncCardData();
    }

    private void moreCards() {
        Logger.d(">>>");
        if (mCardAdapter == null) return;
        moreCardData();
    }

    /**
     * just to synchronize the present data set of adapter with the display on UI
     * this method triggers CardAdapter#onBindViewHolder()
     * which leads the recycler view to carries out its updating on its own
     * therefore, don't call this method in CardAdapter#onBindViewHolder() for avoiding endless loops
     */
    private void bindCardData() {
        if (mCardAdapter == null) {
            Logger.e("!!! adapter is null");
            return;
        }
        mCardAdapter.notifyDataSetChanged();
    }

    /**
     * synchronizes the data set of adapter in a specific period (defined in presenter) with firebase database and display on UI
     */
    private void syncCardData() {
        if (mCardAdapter == null) {
            Logger.e("!!! adapter is null");
            return;
        }
        mCardAdapter.load();
    }

    /**
     * synchronizes the data set of adapter in a early period (defined in presenter) with firebase database and display on UI
     */
    private void moreCardData() {
        if (mCardAdapter == null) {
            Logger.e("!!! adapter is null");
            return;
        }
        mCardAdapter.fetch();
    }

    private void initCardAdapter() {
        mCardAdapter = new CardAdapter(_mActivity, this);
        mCardAdapter.setCardClickListener(new CardClickListener());
        mCardAdapter.setMoreClickListener(new MoreClickListener());
        mCardAdapter.setCardLoadListener(new CardLoader());
        readCardsFromMocks(mCardAdapter.getItemsOfDataSet());
    }

    private void initCardRecyclerView() {
        mCardRCV = mView.findViewById(R.id.recy);
        //mLayoutManager = new LinearLayoutManager(_mActivity);
        LinearLayoutManager mLayoutManager = new CardsLayoutManager(_mActivity);
        mLayoutManager.setAutoMeasureEnabled(false);
        mCardRCV.setHasFixedSize(false);
        mCardRCV.setLayoutManager(mLayoutManager);
        mCardRCV.setAdapter(mCardAdapter);
        mCardRCV.addOnScrollListener(new OnRCVScroll());
        //mCardRCV.setNestedScrollingEnabled(false);
        //mCardRCV.addItemDecoration(new DividerItemDecoration(_mActivity, mLayoutManager.getOrientation()));
        //mCardRCV.getRecycledViewPool().setMaxRecycledViews(0, 0);
    }

    /**
     * for testing and fixing the issue
     * that RecyclerView.Adapter#onBindViewHolder() won't be invoked if RecyclerView.Adapter#getItemCount() return 0
     */
    private void readCardsFromMocks(List<Card> cards) {
        Card mock;
        String cardId;
        String profilePhoto;
        String profileTitle;
        String profileName;
        String profileID;
        String articleTitle;
        String articleContent;
        int articleCount;
        int distance;
        long date;
        for (int i = 0; i < 1; i++) {
            cardId          = "CARD_ID" + i;
            profilePhoto    = "https://png.icons8.com/cotton/40/000000/toolbox.png";
            profileTitle    = "離線帳號";
            profileName     = "貓雕像" + i;
            profileID       = "USER_ID" + i;
            articleTitle    = "沒有網路的喔";
            articleContent  = "一種離線測試的存在";
            articleCount    = -1;
            distance        = 100 + i * 100;
            date            = 0;
            mock = new Card(cardId, profilePhoto, profileTitle, profileName, profileID
                    , articleTitle, articleContent, articleCount, distance, date);
            //CardsDao.getInstance(_mActivity).add(mock);
            cards.add(mock);
        }
        //CardsDao.getInstance(_mActivity).find(0, cards);
    }

    public void showSnackBar(String msg) {
        showSnackBar(mAddCardBtn, msg);
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
     * refers to a well-implemented generator representing an action of generating a card which is called by home fragment
     * <p>
     * since user creates a card on another fragment which acts as a generator
     * so we should pass generator fragment a reference to home fragment to call this method outside
     */
    public void enqueueAction(CardGenerator action) {
        mCardGeneratorAction = action;
    }

    /**
     * executes action done by main thread to have generator add a new card for this home fragment
     * <p>
     * since user creates a card on another fragment which acts as a generator
     * so we should pass generator fragment a reference to home fragment to call this method outside
     */
    public void executeAction() {
        Runnable mExecCommit = new Runnable() {
            @Override
            public void run() {
                mCardGeneratorAction.generateCards(mCardAdapter.getItemsOfDataSet());
            }
        };
        mHandler.post(mExecCommit);
    }

    /**
     * executes generator immediately to add a new card for this home fragment
     * <p>
     * since user creates a card on another fragment which acts as a generator
     * so we should pass generator fragment a reference to home fragment to call this method outside
     */
    public void generateCardImmediate(CardGenerator cardGenerator) {
        cardGenerator.generateCards(mCardAdapter.getItemsOfDataSet());
        bindCardData();
    }

    @Override
    public void feed(OurModel model, OurPresenter presenter) {

    }

    @Override
    public void onNotice(int result, String message) {
        if (result == CardAdapter.PROGRESS_ON && message != null) showProgress(message);
        else if (result == CardAdapter.PROGRESS_OFF) hideProgress();
    }

    /**
     * passed from CardAdapter, pojo means the count of card just read from Firebase in the early period
     */
    @Override
    public void onFetch(int result, Object pojo) {
        switch (result) {
            case BaseModelAdapter.RESULT_SUCCESS:
                Logger.d("... got BaseModelAdapter.RESULT_SUCCESS and let RCV scroll to " + (mCardRCV.getAdapter().getItemCount()-((int)pojo)));
                if (mRefresh.isRefreshing()) mRefresh.setRefreshing(false);
                mCardRCV.smoothScrollToPosition(mCardRCV.getAdapter().getItemCount()-((int)pojo));
                break;
            case BaseModelAdapter.RESULT_FAILURE:
                Logger.e("!!! got BaseModelAdapter.RESULT_FAILURE");
                if (mRefresh.isRefreshing()) mRefresh.setRefreshing(false);
                break;
            default:
                break;
        }
    }

    /**
     * passed from CardAdapter, pojo is barely null
     */
    @Override
    public void onLoad(int result, Object pojo) {
        switch (result) {
            case BaseModelAdapter.RESULT_SUCCESS:
                Logger.d("... got BaseModelAdapter.RESULT_SUCCESS and let RCV scroll to 0");
                if (mRefresh.isRefreshing()) mRefresh.setRefreshing(false);
                mCardRCV.smoothScrollToPosition(BaseModelAdapter.POSITION_AT_FIRST);
                break;
            case BaseModelAdapter.RESULT_FAILURE:
                Logger.e("!!! got BaseModelAdapter.RESULT_FAILURE");
                if (mRefresh.isRefreshing()) mRefresh.setRefreshing(false);
                break;
            /*
            case CardAdapter.HANDLER_GO_SUCCESS:
                Message msg1 = new Message();
                msg1.what = HANDLER_MSG_WHAT_SYNC_CARD_SUCCESS;
                mHandler.sendMessage(msg1);
                break;
            case CardAdapter.HANDLER_GO_FAILURE:
                Message msg2 = new Message();
                msg2.what = HANDLER_MSG_WHAT_SYNC_CARD_FAILURE;
                mHandler.sendMessage(msg2);
                break;
            */
            default:
                break;
        }
    }

    @Override
    public void onRemove(int result, int position, Object model) {

    }

    @Override
    public void onRecover(int result, int position,Object model) {

    }

    /**
     * a generator implemented in order to add a new card for the recycler adapter of home fragment
     * home fragment calls generator when to add a new card into its adapter
     */
    interface CardGenerator {
        void generateCards(List<Card> cards);
    }

    class OnAddCardClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            FirstAddCardFragment addCardFragment = FirstAddCardFragment.newInstance(instance);
            if (addCardFragment == null) return;
            addCardFragment.setOnPop(new AddCardFragmentOnPop());
            start(addCardFragment);
        }
    }

    /**
     * callback for card adapter to start card detail fragment
     */
    class CardClickListener implements CardAdapter.CardClickListener {
        @Override
        public void onCardClick(Card card, View item_view, int item_index) {
            Logger.d("... clicked card id:" + card.getCardId() + " and going to start card detail fragment");
            if (item_view.isClickable()) {
                FirstCardDetailFragment cardDetailFragment = FirstCardDetailFragment.newInstance(card.getProfileID(), card.getCardId());
                cardDetailFragment.setHomeFragment(instance);
                cardDetailFragment.setIndexOfAdapter(item_index);
                if (cardDetailFragment == null) return;
                cardDetailFragment.setOnPop(new CardDetailFragmentOnPop());
                cardDetailFragment.setOnShow(new CardDetailFragmentOnShow());
                start(cardDetailFragment);
            }
        }
    }

    /**
     * callback for card adapter to invoke it to load more cards in a specific period
     */
    class MoreClickListener implements CardAdapter.MoreClickListener {
        @Override
        public void onMoreClick(Card card, View item_view) {
            Logger.d("... clicked under the cut date:" + new Date(card.getDate()).toString());
            if (!mRefresh.isRefreshing()) mRefresh.setRefreshing(true);
            moreCards();
        }
    }

    /**
     * callback for card adapter to call FirstHomeFragment#readCardsFromFirebase() outside
     */
    class CardLoader implements CardAdapter.CardLoader {
        @Override
        public void onNeedLoad(List<Card> cards) {
            Logger.d(">>> do nothing here");
        }
    }

    /**
     * callback for pop() on add card fragment
     */
    class AddCardFragmentOnPop implements FirstAddCardFragment.OnPop {
        @Override
        public void onPop(int resultCode) {
            //pullCards();
            switch (resultCode) {
                case FirstAddCardFragment.POP_RESULT_POST_SUCCESS:
                    mCardRCV.smoothScrollToPosition(BaseModelAdapter.POSITION_AT_FIRST);
                    showSnackBar("廢文優文都是文");
                    break;
                case FirstAddCardFragment.POP_RESULT_UPLOAD_FAILURE:
                    Logger.e("... 上傳圖片失敗耶");
                    showSnackBar("上傳圖片失敗耶");
                    break;
                case FirstAddCardFragment.POP_RESULT_POST_FAILURE:
                    Logger.e("... 推文失敗囉");
                    showSnackBar("推文失敗囉");
                    break;
                case FirstAddCardFragment.POP_RESULT_CANCEL:
                    Logger.e("... 有空再來推文呀");
                    showSnackBar("有空再來推文呀");
                    break;
            }
        }
    }

    /**
     * callback for pop() on card detail fragment
     */
    class CardDetailFragmentOnPop implements FirstCardDetailFragment.OnPop {
        @Override
        public void onPop(int resultCode) {
            //pullCards();
            switch (resultCode) {
                case FirstCardDetailFragment.POP_RESULT_INIT_CARD_FAILURE:
                    showSnackBar("剛剛讀取卡片失敗惹");
                    break;
                case FirstCardDetailFragment.POP_RESULT_INIT_COMMENT_FAILURE:
                    showSnackBar("剛剛讀取留言失敗惹");
                    break;
                case FirstCardDetailFragment.POP_RESULT_REMOVE_CARD_SUCCESS_COMMENT_SUCCESS:
                    showSnackBar("已經成功刪掉卡片惹");
                    break;
                case FirstCardDetailFragment.POP_RESULT_REMOVE_CARD_SUCCESS_COMMENT_FAILURE:
                    showSnackBar("剛剛刪除卡片成功但刪除推文失敗囧");
                    break;
                case FirstCardDetailFragment.POP_RESULT_REMOVE_CARD_FAILURE_COMMENT_NO_TRY:
                    showSnackBar("剛剛刪除卡片沒成功囧");
                    break;
                case FirstCardDetailFragment.POP_RESULT_CANCEL:
                    //showSnackBar("");
                    break;
            }
        }
    }

    /**
     * callback for showing details of a card
     */
    class CardDetailFragmentOnShow implements FirstCardDetailFragment.OnShow {
        @Override
        public void onShow(int number_of_comments) {

        }
    }

    @SuppressLint("HandlerLeak")
    private class UIHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                default:

                    break;
            }
        }
    }

    class CardsLayoutManager extends LinearLayoutManager {
        private CardsLayoutManager(Context context) {
            super(context);
        }

        @Override
        public void onMeasure(RecyclerView.Recycler recycler, RecyclerView.State state, int widthSpec, int heightSpec) {
            super.onMeasure(recycler, state, widthSpec, heightSpec);
            if (true) return;
            View child_of_rv = recycler.getViewForPosition(0);
            if (child_of_rv == null) {
                Logger.e("!!! child_of_rv is null");
                super.onMeasure(recycler, state, widthSpec, heightSpec);
                return;
            }
            measureChild(child_of_rv, widthSpec, heightSpec);
            Logger.d("... View.MeasureSpec.getSize(widthSpec):" + View.MeasureSpec.getSize(widthSpec));
            Logger.d("... View.MeasureSpec.getSize(heightSpec):" + View.MeasureSpec.getSize(heightSpec));
            Logger.d("... recycler.getViewForPosition(0).getMeasuredWidth():" + recycler.getViewForPosition(0).getMeasuredWidth());
            Logger.d("... recycler.getViewForPosition(0).getMeasuredHeight():" + recycler.getViewForPosition(0).getMeasuredHeight());
            int measuredWidth = View.MeasureSpec.getSize(widthSpec);
            //int measuredWidth = child_of_rv.getMeasuredWidth();
            //int measureHeight = View.MeasureSpec.getSize(heightSpec);
            int measureHeight = child_of_rv.getMeasuredHeight();
            setMeasuredDimension(measuredWidth, measureHeight);
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
            int items_count = recyclerView.getAdapter().getItemCount();
            if (destination == RecyclerView.NO_POSITION) {
                Logger.e("!!! no position to scroll");
                return;
            }
            if (first_visible == first_complete) {
                Logger.d("... meet the first item of data set of adapter, last item position:"
                        + last_visible
                        + ", item count:" + items_count
                );
            } else if (last_complete == recyclerView.getAdapter().getItemCount()-1) {
                Logger.d("... meet the last item of data set of adapter, last item position:"
                        + last_visible
                        + ", item count:" + items_count
                );
            }
            //orientateRecyclerView(recyclerView, destination); //bug happens when
        }

        /**
         * this happens many times a second during a scroll, so be wary of the code placed here.
         */
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            /*
            if (dy < 0) {
                Logger.d("... RCV scrolling up:" + dy);
            } else if (dy > 0) {
                Logger.d("... RCV scrolling down:" + dy);
            }
            int visible_item_count = recyclerView.getChildCount();
            int items_count = recyclerView.getAdapter().getItemCount();
            int invisible_item_count = (items_count >= visible_item_count) ? (items_count - visible_item_count) : 0;
            if (((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition() == invisible_item_count) {
                Logger.d("... meet the last item of data set of adapter, last item position:"
                        + ((LinearLayoutManager)recyclerView.getLayoutManager()).findLastVisibleItemPosition()
                        + ", item count:" + items_count
                        );
            } else if (((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition() < invisible_item_count){
                Logger.d("... before the last item of data set of adapter, last item position:"
                        + ((LinearLayoutManager)recyclerView.getLayoutManager()).findLastVisibleItemPosition()
                        + ", item count:" + items_count
                        );
            } else if (((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition() > invisible_item_count){
                Logger.d("... after(data set shrunk or un-synchronized!) the last item of data set of adapter, last item position:"
                        + ((LinearLayoutManager)recyclerView.getLayoutManager()).findLastVisibleItemPosition()
                        + ", item count:" + items_count
                );
            }
            */
        }
    }
}
