package com.ours.yours.app.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.base.BaseChildFragment;
import com.ours.yours.app.entity.Card;
import com.ours.yours.app.entity.CardList;
import com.ours.yours.app.manager.CardManager;
import com.ours.yours.app.ui.fragment.first.child.FirstHomeFragment;
import com.ours.yours.app.ui.mvp.OurPresenter;
import com.ours.yours.app.ui.mvp.OurView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * adapter provides a binding from data set synchronized with firebase to views displayed within a RecyclerView
 */
public class CardAdapter extends BaseModelAdapter<Card>  implements OurPresenter<OurView> {
    private BaseChildFragment mFragment;
    private LayoutInflater mInflater;
    private CardClickListener mCardClickListener;
    private MoreClickListener mMoreClickListener;
    private CardLoader mCardLoader;
    private final static int ITEM_TYPE_BASE = 10;
    private final static int ITEM_TYPE_UNKNOWN = ITEM_TYPE_BASE + 1;
    private final static int ITEM_TYPE_NORMAL = ITEM_TYPE_BASE + 2;
    private final static int ITEM_TYPE_UNDER_THE_CUT = ITEM_TYPE_BASE + 3;
    public final static String CARD_ID_UNDER_THE_CUT = "UNDER_THE_CUT";

    /*
    / **
     * result passed to our view on the OurView#onLoad()
     * /
    private final static int RESULT_BASE    = 20;
    public final static int RESULT_SUCCESS = RESULT_BASE + 1;
    public final static int RESULT_FAILURE = RESULT_BASE + 2;
    */

    /**
     * notification passed to our view on the OurView#onNotice()
     */
    public final static int PROGRESS_ON  = 0;
    public final static int PROGRESS_OFF = 1;
    public final static int HANDLER_GO_SUCCESS = 2;
    public final static int HANDLER_GO_FAILURE = 3;

    /**
     * start and end of a specific period
     */
    //public final static long DEFAULT_PERIOD_START = System.currentTimeMillis() - 14*24*60*60*1000;
    //public final static long DEFAULT_PERIOD_START = 0; //only Date works...
    //public final static long DEFAULT_PERIOD_END = System.currentTimeMillis();
    private final static long DEFAULT_PERIOD = (long) (7.0 * 24.0 * 3600.0 * 1000.0); //only Date works...
    private final static int DEFAULT_TIME_COST = 500; //500 ms for investigating how many cards to be read this time
    private final static int DEFAULT_TIME_WAIT = 2500; //2000 ms for waiting to start counting since an invocation

    private OurView mOurView; //FirstHomeFragment
    private Context mContext;
    private Thread mMVPThread;
    private final Object lock = new Lock();
    private Map<Long, Worker> mWorkers;
    private int mResult = BaseModelAdapter.RESULT_FAILURE;

    /**
     * earliest date of cards in a specific period
     */
    private long earliest_date = CardList.DEFAULT_LAST_DATE;
    private long start_date;
    private long end_date;
    
    /**
     * for inner class
     */
    private CardAdapter instance;
    private Map<String, Long> mapUserIdAndCardCount; //map recording the count of cards of each user
    private Map<String, Integer> mapUserIdAndCardCountRead = new HashMap<>(); //count of cards read from Firebase of each user
    private int all_card_count = 0; //count of cards of all users in a specific period
    private int all_card_count_read = 0; // //count of cards read of all users from Firebase in a specific period

    public CardAdapter(Context context, BaseChildFragment fragment) {
        super(context);
        mContext = context;
        mFragment = fragment;
        mInflater = LayoutInflater.from(context);
        instance = this;
    }

    /**
     * involves inflating a layout from XML and then returning it to the holder
     * when RecyclerView needs a new ViewHolder to represent item
     *
     * a new ViewHolder will be constructed with an inflated View which represents the item
     * by either creating a new View manually or inflating it from an XML layout file.
     */
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder view_holder = null;
        View item_view;
        switch (viewType) {
            case ITEM_TYPE_UNKNOWN:
                break;
            case ITEM_TYPE_NORMAL:
                item_view = mInflater.inflate(R.layout.item_first_home_card, parent, false);
                view_holder = new CardViewHolder(item_view);
                break;
            case ITEM_TYPE_UNDER_THE_CUT:
                item_view = mInflater.inflate(R.layout.item_first_home_more, parent, false);
                view_holder = new UnderTheCutViewHolder(item_view);
                break;
        }
        return view_holder;
    }

    /**
     * involves populating data into item through holder at the position (which is positioned by layout manager)
     * when RecyclerView displays of updates the UI with the data through the holder referring to the given position
     *
     * position is the index of one of items within the adapter's data set
     *
     * this method will synchronize data set with firebase database if the wanted position is beyond the length of data set
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Logger.d(">>> about the position:" + position);
        if (position >= getItemCount()) {
            Logger.d("... position(" + position + ")" + " >= size of data set(" + getItemCount()+  ")" + " and going to load");
            if (mFragment.isInternetConnectedOrConnecting()) {
                Logger.d("... going to onNeedLoad()");
                mCardLoader.onNeedLoad(getItemsOfDataSet());
            } else {
                ((FirstHomeFragment) mFragment).showSnackBar("沒網路耶...");
            }
        }
        switch (holder.getItemViewType()) {
            case ITEM_TYPE_UNKNOWN:
                break;
            case ITEM_TYPE_NORMAL:
                ((CardViewHolder) holder).bindData(getItemOfDataSet(position));
                break;
            case ITEM_TYPE_UNDER_THE_CUT:
                ((UnderTheCutViewHolder) holder).bindData(getItemOfDataSet(position));
                break;
        }

    }

    @Override
    public int getItemCount() {
        return super.getItemCount();
    }

    @Override
    public int getItemViewType(int position) {
        Card card = getItemOfDataSet(position);
        if (card == null) return ITEM_TYPE_UNKNOWN;
        if (card.getCardId().equals(CARD_ID_UNDER_THE_CUT)) return ITEM_TYPE_UNDER_THE_CUT;
        else return ITEM_TYPE_NORMAL;
    }

    public void setCardClickListener(CardClickListener listener) {
        mCardClickListener = listener;
    }

    public void setMoreClickListener(MoreClickListener listener) {
        mMoreClickListener = listener;
    }

    public void setCardLoadListener(CardLoader listener) {
        mCardLoader = listener;
    }

    @Override
    public void attach(OurView view) {
        Logger.d(">>>");
        if (view == null) {
            Logger.e("!!! view is null");
            return;
        }
        mOurView = view;
    }

    @Override
    public void detach(OurView view) {
        Logger.d(">>>");
        if (mOurView != view) {
            Logger.e("!!! mOurView doesn't refer to same one as view");
            return;
        }
        if (mOurView == null) {
            return;
        }
        mOurView = null;
    }

    @Override
    public void load() {
        Logger.d(">>>");
        if (mMVPThread != null) {
            if (mMVPThread.isAlive()) {
                Logger.d("... previous loading task is still running but going to interrupt it");
                mMVPThread.interrupt();
            } else {
                mMVPThread = null;
            }
        }
        mMVPThread = new Thread(new LoadTask());
        mMVPThread.start();
    }

    @Override
    public void fetch() {
        Logger.d(">>>");
        if (mMVPThread != null) {
            if (mMVPThread.isAlive()) {
                Logger.d("... previous loading task is still running but going to interrupt it");
                mMVPThread.interrupt();
            } else {
                mMVPThread = null;
            }
        }
        mMVPThread = new Thread(new FetchTask());
        mMVPThread.start();
    }

    @Override
    public void remove(int position) {

    }

    @Override
    public void recover(int position) {

    }

    @Override
    public void cancel(boolean cancel) {
        if (mWorkers == null) {
            Logger.e("!!! mWorkers is null");
            return;
        }
        if (!cancel) {
            Logger.e("!!! do nothing when cancel goes with false");
            return;
        }
        for (Map.Entry<Long, Worker> entry : mWorkers.entrySet()) {
            Worker worker = entry.getValue();
            if (worker.getThread() != null) {
                if (worker.getThread().isAlive()) {
                    Logger.d("... going to cancel on the worker and others will break:" + worker.getStamp());
                    worker.cancel();
                    break;
                } else {
                    Logger.d("... worker has completed its task:" + worker.getStamp());
                }
            } else {
                Logger.e("!!! work get thread returned null:" + worker.getStamp());
            }
        }
        try {
            CardManager.getInstance(mContext).unsubscribe();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * makes a card used to create an item of under the cut type
     */
    private Card makeCardAsUnderTheCut() {
        Card card = new Card();
        card.setCardId(CardAdapter.CARD_ID_UNDER_THE_CUT);
        card.setDate(start_date);
        return card;
    }

    /**
     * get the map holding ids of authors and how many cards belonging to in a specific period stored in firebase
     *
     * CardSubscriber#onGetParentIdsAndCounts() would be called many times by ChildEventListener#onChildAdded()
     * since a invocation of FirebaseDatabaseHelper#readIdsAndChildrenCounts()
     */
    private void readAuthorIdsAndCardCountsPeriod(final long start, final long end, final CountListener listener) throws ClassNotFoundException {
        if (mapUserIdAndCardCount != null) mapUserIdAndCardCount.clear();
        CardManager.getInstance(mContext).subscribeAuthorIdsAndCardCounts(new CardSubscriber() {
            @Override
            public void onGetParentIdsAndCounts(Map<String, Long> parentIdsAndSiblingCounts) {
                all_card_count = 0;
                StringBuilder stringBuilder = new StringBuilder();
                for (Map.Entry<String, Long> entry : parentIdsAndSiblingCounts.entrySet()) {
                    stringBuilder.append("... counting and user id " + entry.getKey() + " has " + entry.getValue() +  " cards" + "\n");
                    all_card_count = (int) (all_card_count + entry.getValue());
                    mapUserIdAndCardCount = parentIdsAndSiblingCounts;
                    if (all_card_count > 0) listener.onCounting(parentIdsAndSiblingCounts);
                }
                Logger.d(stringBuilder.toString());
            }
        }, start, end);
    }

    /*
     * synchronizes the data set of somewhere with firebase
     *
     * no occasion to call this yet...
     *
    private void readCardsFromFirebase(final List<Card> dest) throws ClassNotFoundException {
        if (dest == null) return;
        CardManager.getInstance(mContext).subscribeAllCardsInFirebase(new CardSubscriber() {
            @Override
            public void onClone(CardList cards) {
                Logger.d(">>>");
                dest.clear();
                dest.addAll(cards.getCards());
                Message msg = new Message();
                mResult = RESULT_SUCCESS;
                mOurView.onNotice(HANDLER_GO_SUCCESS, null);
                if (mMVPThread != null && mMVPThread.isAlive()) {
                    Logger.d("... going to interrupt mvp thread");
                    mMVPThread.interrupt();
                }
            }

            @Override
            public void onError(String error) {
                Logger.e("!!! error:" + error);
                mOurView.onNotice(HANDLER_GO_FAILURE, null);
                mResult = RESULT_FAILURE;
                if (mMVPThread != null && mMVPThread.isAlive()) {
                    Logger.d("... going to interrupt mvp thread");
                    mMVPThread.interrupt();
                }
            }
        });
    }
    */

    /**
     * synchronizes the data set of adapter all the time with firebase and notifies recycler view of changes
     */
    private void readCardsFromFirebase(final BaseModelAdapter adapter) throws ClassNotFoundException {
        if (adapter == null) return;
        mOurView.onNotice(PROGRESS_ON, "努力下載中...");
        CardManager.getInstance(mContext).subscribeAllCardsInFirebase(new CardSubscriber() {
            @Override
            public void onClone(CardList cards) {
                Logger.d(">>>");
                mOurView.onNotice(PROGRESS_OFF, null);
                if (adapter instanceof CardAdapter) {
                    ((CardAdapter) adapter).setItemsAsDataSet(cards.getCards());
                } else {
                    Logger.d("!!! adapter is not card adapter");
                }
                mResult = RESULT_SUCCESS;
                if (mMVPThread != null && mMVPThread.isAlive()) {
                    Logger.d("... going to interrupt mvp thread");
                    mMVPThread.interrupt();
                }
            }

            @Override
            public void onError(String error) {
                Logger.e("!!! error:" + error);
                mResult = RESULT_FAILURE;
                if (mMVPThread != null && mMVPThread.isAlive()) {
                    Logger.d("... going to interrupt mvp thread");
                    mMVPThread.interrupt();
                }
            }
        });
    }

    /**
     * reads the data set of adapter in a specific period with firebase the way stacking at the head
     * and notifies recycler view of changes to update EVERY TIME getting a new one model
     * this method need not to send handler msg to ask UI update
     * because base model adapter notifies RCV while item being inserted and triggers UI update
     */
    private void readCardsFromFirebasePeriodPullHead(final BaseModelAdapter adapter, final long start, final long end) throws ClassNotFoundException {
        if (adapter == null) return;
        mOurView.onNotice(PROGRESS_ON, "努力下載中...");
        CardManager.getInstance(mContext).subscribeAllCardsInFirebasePeriod(new CardSubscriber() {
            /**
             * this would be called many times while getting a child after querying
             */
            @Override
            public void onUpdate(Card card) {
                Logger.d(">>> card date:" + card.getDate()); //got only one card and hide progress well...
                mOurView.onNotice(PROGRESS_OFF, null);
                if (adapter instanceof CardAdapter) {
                    ((CardAdapter) adapter).addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_FIRST, card);
                    if (earliest_date > card.getDate() || earliest_date == CardList.DEFAULT_LAST_DATE) {
                        earliest_date = card.getDate();
                        Logger.d("... now earliest date:" + new Date(earliest_date).toString());
                    } else Logger.d("... unchanged earliest date:" + new Date(earliest_date).toString());
                } else {
                    Logger.d("!!! adapter is not card adapter");
                }
                if (getItemCount() > 0) { //this method doesn't care if completes reading all cards or not, got a card and callback to our view
                    Logger.d("... got a card in this period");
                    mResult = RESULT_SUCCESS;
                }
                if (mMVPThread != null && mMVPThread.isAlive()) {
                    Logger.d("... going to interrupt mvp thread");
                    mMVPThread.interrupt();
                }
                /* no timing to unsubscribe child event listeners...
                try {
                    CardManager.getInstance(mContext).unsubscribe();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                */
            }

            @Override
            public void onError(String error) {
                Logger.e("!!! error:" + error);
                mResult = RESULT_FAILURE;
                if (mMVPThread != null && mMVPThread.isAlive()) {
                    Logger.d("... going to interrupt mvp thread");
                    mMVPThread.interrupt();
                }
            }
        }, start, end);
    }

    /**
     * reads the data set of adapter in a specific period with firebase the way collecting, sorting and stacking at the tail
     * and notifies recycler view of changes to update EVERY TIME getting a list of cards belonging to a certain user
     * this method need not to send handler msg to ask UI update
     * because base model adapter notifies RCV while item being inserted and triggers UI update
     *
     * this method should be called after CardAdapter#readAuthorIdsAndCardCountsPeriod() to make sure how many cards should be read this time
     *
     * invokes OurView#onNotice() and OurView#onLoad() on the our view to interact with UI
     */
    private void readCardsFromFirebasePeriodPullSort(final BaseModelAdapter adapter, final long start, final long end) throws ClassNotFoundException {
        Logger.d(">>> global all card count:" + all_card_count);
        if (adapter == null) return;
        if (mapUserIdAndCardCount == null) {
            Logger.e("!!!mapUserIdAndCardCount is null");
            return;
        }
        final HashMap<String, List<Card>> cards = new HashMap<>();
        if (mapUserIdAndCardCountRead != null) mapUserIdAndCardCountRead.clear();
        for (Map.Entry<String, Long> entry : mapUserIdAndCardCount.entrySet()) {
            Logger.d("... in this period, user id " + entry.getKey() + " has " + entry.getValue() +  " cards");
            mapUserIdAndCardCountRead.put(entry.getKey(), 0);
            cards.put(entry.getKey(), new ArrayList<Card>()); // malloc for cards of each author
        }
        all_card_count_read = 0;
        mOurView.onNotice(PROGRESS_ON, "努力下載中...");
        CardManager.getInstance(mContext).subscribeAllCardsInFirebasePeriod(new CardSubscriber() {
            /**
             * this would be called many times while getting a child after querying
             */
            @Override
            public void onUpdate(Card card) {
                Logger.d(">>> card date:" + card.getDate()); //got only one card and hide progress well...
                String author_id = card.getProfileID();
                mOurView.onNotice(PROGRESS_OFF, null);
                if (adapter instanceof CardAdapter) {
                    if (!cards.containsKey(author_id)) {
                        Logger.e("!!! no such author id recorded before:" + author_id);
                        return;
                    }
                    int previous_author_card_read_count = mapUserIdAndCardCountRead.get(author_id);
                    int updated_author_card_read_count = previous_author_card_read_count + 1;
                    int author_card_count = Math.toIntExact(mapUserIdAndCardCount.get(author_id));
                    mapUserIdAndCardCountRead.put(author_id, updated_author_card_read_count);
                    cards.get(author_id).add(card);
                    all_card_count_read += 1;
                    Logger.d("... for author:" + author_id + "\n"
                            + ", has " + author_card_count + " cards" + "\n"
                            + ", updated read count:" + mapUserIdAndCardCountRead.get(author_id) + "\n"
                            + ", date of card just added to list:" + new Date(cards.get(author_id).get(cards.get(author_id).size()-1).getDate()).toString()
                    );
                    if (updated_author_card_read_count == author_card_count) {
                        Logger.d("... GOT THE LAST of card in this period OF AUTHOR:" + author_id);
                        Collections.sort(cards.get(author_id), new Comparator<Card>() {
                            @Override
                            public int compare(Card o1, Card o2) {
                                return ((Long)o2.getDate()).compareTo(o1.getDate());
                            }
                        });
                        Logger.d("... going to add items to data set with " + cards.get(author_id).size() + " cards");
                        ((CardAdapter) adapter).addItemsToDataSet(cards.get(author_id));
                        if (getItemCount() > 0 && all_card_count_read == all_card_count) { //if completes reading all cards
                            Logger.d("... GOT THE LAST card in this period OF ALL");
                            mResult = RESULT_SUCCESS;
                            /**
                             * adds the new under the cut
                             */
                            Logger.d("... going to add a new under the cut");
                            ((CardAdapter) adapter).addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_LAST, makeCardAsUnderTheCut());
                            if (mMVPThread != null && mMVPThread.isAlive()) {
                                Logger.d("... going to interrupt mvp thread");
                                mMVPThread.interrupt();
                            }
                            try {
                                CardManager.getInstance(mContext).unsubscribe();
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        Logger.d("... for author:" + author_id
                                + ", un-read card count:" + (author_card_count - mapUserIdAndCardCountRead.get(author_id))
                        );
                    }
                    if (earliest_date > card.getDate() || earliest_date == CardList.DEFAULT_LAST_DATE) {
                        earliest_date = card.getDate();
                        Logger.d("... now earliest date:" + new Date(earliest_date).toString());
                    } else Logger.d("... unchanged earliest date:" + new Date(earliest_date).toString());
                } else {
                    Logger.d("!!! adapter is not card adapter");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e("!!! error:" + error);
                mOurView.onNotice(PROGRESS_OFF, null);
                mResult = RESULT_FAILURE;
                if (mMVPThread != null && mMVPThread.isAlive()) {
                    Logger.d("... going to interrupt mvp thread");
                    mMVPThread.interrupt();
                }
            }
        }, start, end);
    }

    /**
     * reads the data set of adapter in a specific period with firebase the way collecting, sorting and stacking at the tail
     * and notifies recycler view of changes to update EVERY TIME getting a list of cards belonging to a certain user
     * this method need not to send handler msg to ask UI update
     * because base model adapter notifies RCV while item being inserted and triggers UI update
     *
     * this method should be called after CardAdapter#readAuthorIdsAndCardCountsPeriod() to make sure how many cards should be read this time
     *
     * invokes OurView#onNotice() and OurView#onFetch() on the our view to interact with UI
     */
    private void readCardsFromFirebasePeriodMoreSort(final BaseModelAdapter adapter, final long start, final long end) throws ClassNotFoundException {
        Logger.d(">>> global all card count:" + all_card_count);
        if (adapter == null) return;
        if (mapUserIdAndCardCount == null) {
            Logger.e("!!!mapUserIdAndCardCount is null");
            return;
        }
        final HashMap<String, List<Card>> cards = new HashMap<>();
        if (mapUserIdAndCardCountRead != null) mapUserIdAndCardCountRead.clear();
        for (Map.Entry<String, Long> entry : mapUserIdAndCardCount.entrySet()) {
            Logger.d("... in this period, user id " + entry.getKey() + " has " + entry.getValue() +  " cards");
            mapUserIdAndCardCountRead.put(entry.getKey(), 0);
            cards.put(entry.getKey(), new ArrayList<Card>()); // malloc for cards of each author
        }
        all_card_count_read = 0;
        mOurView.onNotice(PROGRESS_ON, "努力下載中...");
        CardManager.getInstance(mContext).subscribeAllCardsInFirebasePeriod(new CardSubscriber() {
            /**
             * this would be called many times while getting a child after querying
             */
            @Override
            public void onUpdate(Card card) {
                Logger.d(">>> card date:" + card.getDate()); //got only one card and hide progress well...
                String author_id = card.getProfileID();
                mOurView.onNotice(PROGRESS_OFF, null);
                if (adapter instanceof CardAdapter) {
                    if (!cards.containsKey(author_id)) {
                        Logger.e("!!! no such author id recorded before:" + author_id);
                        return;
                    }
                    int previous_author_card_read_count = mapUserIdAndCardCountRead.get(author_id);
                    int updated_author_card_read_count = previous_author_card_read_count + 1;
                    int author_card_count = Math.toIntExact(mapUserIdAndCardCount.get(author_id));
                    mapUserIdAndCardCountRead.put(author_id, updated_author_card_read_count);
                    cards.get(author_id).add(card);
                    all_card_count_read += 1;
                    Logger.d("... for author:" + author_id + "\n"
                            + ", has " + author_card_count + " cards" + "\n"
                            + ", updated read count:" + mapUserIdAndCardCountRead.get(author_id) + "\n"
                            + ", date of card just added to list:" + new Date(cards.get(author_id).get(cards.get(author_id).size()-1).getDate()).toString()
                    );
                    if (updated_author_card_read_count == author_card_count) {
                        Logger.d("... GOT THE LAST of card in this period OF AUTHOR:" + author_id);
                        Collections.sort(cards.get(author_id), new Comparator<Card>() {
                            @Override
                            public int compare(Card o1, Card o2) {
                                return ((Long)o2.getDate()).compareTo(o1.getDate());
                            }
                        });
                        Logger.d("... going to add items to data set with " + cards.get(author_id).size() + " cards");
                        ((CardAdapter) adapter).addItemsToDataSet(cards.get(author_id));
                        if (getItemCount() > 0 && all_card_count_read == all_card_count) { //if completes reading all cards
                            Logger.d("... GOT THE LAST card in this period OF ALL");
                            mResult = RESULT_SUCCESS;
                            /**
                             * adds the new under the cut
                             */
                            Logger.d("... going to add a new under the cut");
                            ((CardAdapter) adapter).addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_LAST, makeCardAsUnderTheCut());
                            if (mMVPThread != null && mMVPThread.isAlive()) {
                                Logger.d("... going to interrupt mvp thread");
                                mMVPThread.interrupt();
                            }
                            try {
                                CardManager.getInstance(mContext).unsubscribe();
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        Logger.d("... for author:" + author_id
                                + ", un-read card count:" + (author_card_count - mapUserIdAndCardCountRead.get(author_id))
                        );
                    }
                    if (earliest_date > card.getDate() || earliest_date == CardList.DEFAULT_LAST_DATE) {
                        earliest_date = card.getDate();
                        Logger.d("... now earliest date:" + new Date(earliest_date).toString());
                    } else Logger.d("... unchanged earliest date:" + new Date(earliest_date).toString());
                } else {
                    Logger.d("!!! adapter is not card adapter");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e("!!! error:" + error);
                mOurView.onNotice(PROGRESS_OFF, null);
                mResult = RESULT_FAILURE;
                if (mMVPThread != null && mMVPThread.isAlive()) {
                    Logger.d("... going to interrupt mvp thread");
                    mMVPThread.interrupt();
                }
            }
        }, start, end);
    }

    public void clearCards() {
        removeItemsFromDataSet(BaseModelAdapter.REMOVE_POSITION_AT_FIRST, BaseModelAdapter.REMOVE_POSITION_AT_LAST);
    }

    /**
     * callback for triggering responses to UI that a card item within RecyclerView is clicked
     */
    public interface CardClickListener {
        void onCardClick(Card card, View item_view, int item_index);
    }

    /**
     * callback for triggering responses to UI that the under the cut item within RecyclerView is clicked
     */
    public interface MoreClickListener {
        void onMoreClick(Card card, View item_view);
    }

    /**
     * callback for triggering responses to UI that adapter needs to synchronize data set with firebase
     */
    public interface CardLoader {
        void onNeedLoad(List<Card> cards);
    }

    /**
     * callback for card manager in response to user and update the data set of adapter
     */
    class CardSubscriber implements CardManager.Subscriber {
        @Override
        public void onUpdate(Card card) {
        }

        @Override
        public void onClone(CardList cards) {
        }

        @Override
        public void onError(String error) {
        }

        @Override
        public void onAuthors(List<String> author_ids) {
        }

        @Override
        public void onGetParentIdsAndCounts(Map<String, Long> parentIdsAndSiblingCounts) {
        }
    }

    private interface CountListener {
        void onCounting(Map<String, Long> parentIdsAndSiblingCounts);
    }

    /**
     * a ViewHolder describes an item view and metadata about its place within the RecyclerView
     */
    private class CardViewHolder extends RecyclerView.ViewHolder {
        private TextView mProfileTitleView, mProfileNameView;
        private TextView mArticleTitleView, mArticleContentView;
        private ImageView mProfilePhotoView;
        private ImageView mArticlePhotoView;
        private TextView mMapDistanceView;
        private TextView mDateView;

        private CardViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(new OnItemViewClick());
            mProfilePhotoView = itemView.findViewById(R.id.firstHomeCardProfilePhotoImageView);
            mProfileTitleView = itemView.findViewById(R.id.firstHomeCardProfileTitleTextView);
            mProfileNameView = itemView.findViewById(R.id.firstHomeCardProfileNameTextView);
            mArticleTitleView = itemView.findViewById(R.id.firstHomeCardArticleTitleTextView);
            mArticleContentView = itemView.findViewById(R.id.firstHomeCardArticleContentTextView);
            mArticlePhotoView = itemView.findViewById(R.id.firstHomeCardArticlePhotoImageView);
            mMapDistanceView = itemView.findViewById(R.id.firstHomeCardArticleDistanceTextView);
            mDateView = itemView.findViewById(R.id.firstHomeCardArticleDateTextView);
        }

        /**
         *  configures the individual RecyclerView.ViewHolder object and loads it with actual data that need to be displayed
         */
        private void bindData(Card card) {
            ViewCompat.setTransitionName(mProfilePhotoView, String.valueOf(getAdapterPosition()) + "_image");
            ViewCompat.setTransitionName(mProfileTitleView, String.valueOf(getAdapterPosition()) + "_tv");
            ViewCompat.setTransitionName(mProfileNameView, String.valueOf(getAdapterPosition()) + "_tv");
            ViewCompat.setTransitionName(mArticleTitleView, String.valueOf(getAdapterPosition()) + "_tv");
            ViewCompat.setTransitionName(mArticleContentView, String.valueOf(getAdapterPosition()) + "_tv");
            ViewCompat.setTransitionName(mArticlePhotoView, String.valueOf(getAdapterPosition()) + "_image");
            ViewCompat.setTransitionName(mMapDistanceView, String.valueOf(getAdapterPosition()) + "_tv");
            ViewCompat.setTransitionName(mDateView, String.valueOf(getAdapterPosition()) + "_tv");

            String profile_photo = card.getProfilePhoto();
            Glide.with(getContext())
                    .load(profile_photo)
                    .centerCrop()
                    //.override(getSize(mContext).x, getSize(mContext).y)
                    .override(60, 60)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .crossFade()
                    .error(R.drawable.ic_stub)
                    .into(mProfilePhotoView);
            mProfileTitleView.setText(card.getProfileTitle());
            mProfileNameView.setText(card.getProfileName());
            mArticleTitleView.setText(card.getArticleTitle());
            mArticleContentView.setText(card.getArticleContent());

            if (card.getArticlePhoto().size() != card.getArticleCount()) Logger.e("!!! record of count error");
            if (card.getArticlePhoto() != null && card.getArticlePhoto().size() > 0) {
                String article_photo = card.getArticlePhoto().get(0);
                mArticlePhotoView.setVisibility(View.VISIBLE);
                Glide.with(getContext())
                        .load(article_photo)
                        .fitCenter()
                        .override(getSize(getContext()).x, getSize(getContext()).y)
                        //.override(300, 300)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .crossFade()
                        .error(R.drawable.ic_stub)
                        .into(mArticlePhotoView);
            } else {
                mArticlePhotoView.setVisibility(View.GONE);
                ((ViewGroup)itemView).removeView(mArticlePhotoView);
            }

            mMapDistanceView.setText(String.valueOf(card.getDistance()) + "km");
            Date now = new Date();
            //Logger.e("... now:" + now.getTime());
            //Logger.e("... card time:" + card.getDate());
            long interval_millisec  = now.getTime() - card.getDate();
            long interval_sec       = interval_millisec/1000;
            long interval_min       = interval_sec/(60);
            long interval_hr        = interval_min/(60);
            long interval_day       = interval_hr/(24);
            //Logger.e("... interval_millisec:" + interval_millisec);
            //Logger.e("... interval_sec:" + interval_sec);
            //Logger.e("... interval_hr:" + interval_hr);
            //Logger.e("... interval_day   time:" + interval_day);
            StringBuffer buffer = new StringBuffer();
            do {
                if (interval_day >= 1) {
                    buffer.append(interval_day);
                    buffer.append("天");
                    buffer.append("前");
                    break;
                }
                if (interval_hr >= 1) {
                    buffer.append(interval_hr);
                    buffer.append("小時");
                    buffer.append("前");
                    break;
                }
                if (interval_min >= 1) {
                    buffer.append(interval_min);
                    buffer.append("分鐘");
                    buffer.append("前");
                    break;
                }
                buffer.append("剛剛");
                break;
            } while (false);
            mDateView.setText(buffer.toString());
        }

        private Point getSize(Context context) {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            return size;
        }

        class OnItemViewClick implements View.OnClickListener {
            @Override
            public void onClick(View v) {
                if(getAdapterPosition() != RecyclerView.NO_POSITION) {
                    Logger.d(">>> click position:" + getAdapterPosition()); //gets item position
                } else {
                    Logger.e("!!! no position");
                }
                if(mCardClickListener != null) {
                    Card card_clicked = getItemOfDataSet(getAdapterPosition());
                    mCardClickListener.onCardClick(card_clicked, v, getAdapterPosition());
                }
            }
        }
    }

    /**
     * a ViewHolder describes an item view and metadata about its place within the RecyclerView
     */
    private class UnderTheCutViewHolder extends RecyclerView.ViewHolder {
        private TextView mTextView;
        private ImageView mImageView;

        private UnderTheCutViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(new OnItemViewClick());
            mTextView = itemView.findViewById(R.id.firstHomeUnderTheCutTextView);
            mImageView = itemView.findViewById(R.id.firstHomeUnderTheCutImageView);
        }

        /**
         *  configures the individual RecyclerView.ViewHolder object and loads it with actual data that need to be displayed
         */
        @SuppressLint("SetTextI18n")
        private void bindData(Card card) {
            ViewCompat.setTransitionName(mTextView, String.valueOf(getAdapterPosition()) + "_tv");
            ViewCompat.setTransitionName(mImageView, String.valueOf(getAdapterPosition()) + "_image");
            mTextView.setVisibility(View.VISIBLE);
            //Logger.e("... interval_day   time:" + interval_day);
            long interval_millisec  = new Date().getTime() - card.getDate();
            long interval_sec       = interval_millisec/1000;
            long interval_min       = interval_sec/(60);
            long interval_hr        = interval_min/(60);
            long interval_day       = interval_hr/(24);
            StringBuffer buffer = new StringBuffer();
            do {
                if (interval_day >= 1) {
                    buffer.append(interval_day/7);
                    buffer.append("週內的");
                    break;
                }
                if (interval_hr >= 1) {
                    buffer.append(interval_hr);
                    buffer.append("小時內的");
                    break;
                }
                if (interval_min >= 1) {
                    buffer.append(interval_min);
                    buffer.append("分鐘內的");
                    break;
                }
                buffer.append("剛剛");
                break;
            } while (false);
            mTextView.setText("按一下看更多，目前爬到" + buffer.toString());
            mImageView.setVisibility(View.VISIBLE);
        }

        class OnItemViewClick implements View.OnClickListener {
            @Override
            public void onClick(View v) {
                if(getAdapterPosition() != RecyclerView.NO_POSITION) {
                    Logger.d(">>> click position:" + getAdapterPosition()); //gets item position
                } else {
                    Logger.e("!!! no position");
                }
                if(mMoreClickListener != null) {
                    Card card_clicked = getItemOfDataSet(getAdapterPosition());
                    mMoreClickListener.onMoreClick(card_clicked, v);
                }
            }
        }
    }

    private static final class Lock {}

    /**
     * reads card from Firebase real time database in the default period and display on UI
     */
    private class FetchTask implements Runnable {
        @Override
        public void run() {
            try {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        /**
                         * isGoToRead is a flag used to make sure calling once and for all
                         * to readCardsFromFirebasePeriodPullSort()
                         */
                        final boolean[] isGoToRead = {true};
                        final boolean[] isGoToDisplayNoMoreCard = {true};
                        try {
                            start_date -= DEFAULT_PERIOD;
                            end_date -= DEFAULT_PERIOD;
                            readAuthorIdsAndCardCountsPeriod(start_date, end_date, new CountListener() {
                                @Override
                                public void onCounting(Map<String, Long> parentIdsAndSiblingCounts) {
                                    if (isGoToRead[0]) {
                                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    /**
                                                     * removes the under the cut created before
                                                     */
                                                    if (getItemOfDataSet(getItemCount()-1).getCardId().equals(CARD_ID_UNDER_THE_CUT)) {
                                                        Logger.d("... counting and going to remove the past under the cut");
                                                        removeItemFromDataSet(getItemCount()-1);
                                                    }
                                                    readCardsFromFirebasePeriodMoreSort(instance, start_date, end_date);
                                                } catch (ClassNotFoundException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }, DEFAULT_TIME_COST);
                                        isGoToRead[0] = false;
                                        isGoToDisplayNoMoreCard[0] = false;
                                    }
                                }
                            });
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isGoToDisplayNoMoreCard[0]) {
                                        Logger.d("... counting so not to display no more card");
                                        return;
                                    }
                                    /**
                                     * too long to investigate the counts, so abort to read cards this time
                                     */
                                    isGoToRead[0] = false;
                                    cancel(true);
                                    /**
                                     * removes the under the cut created before
                                     */
                                    if (getItemOfDataSet(getItemCount()-1).getCardId().equals(CARD_ID_UNDER_THE_CUT)) {
                                        Logger.d("... no more card and going to remove the past under the cut");
                                        removeItemFromDataSet(getItemCount()-1);
                                    }
                                    /**
                                     * adds the new under the cut
                                     */
                                    Logger.d("... no more card and going to add a new under the cut");
                                    addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_LAST, makeCardAsUnderTheCut());
                                    mResult = RESULT_FAILURE;
                                    if (mMVPThread != null && mMVPThread.isAlive()) {
                                        Logger.d("... going to interrupt mvp thread");
                                        mMVPThread.interrupt();
                                    }
                                }
                            }, DEFAULT_TIME_COST + DEFAULT_TIME_WAIT); //if no more cards in reading for ms, updates the under the cut
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
                Logger.d("... going to wait");
                synchronized (lock) { //becomes the owner of the object's monitor
                    lock.wait(); //relinquishes the ownership of the object's monitor
                }
            } catch (InterruptedException e) {
                Logger.d("... got InterruptedException and proceed");
                if (mOurView != null) {
                    Logger.d("... going to call onFetch(" + mResult + ", " + all_card_count +") on the our view (FirstHomeFragment)");
                    mOurView.onFetch(mResult, all_card_count_read);
                }
            }
            Logger.d("... task done and now count of items:" + getItemCount());
        }
    }

    /**
     * reads card from Firebase real time database in the default period and display on UI
     */
    private class LoadTask implements Runnable {
        @Override
        public void run() {
            try {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        /**
                         * isGoToRead is a flag used to make sure the calling once and for all
                         * to readCardsFromFirebasePeriodPullSort()
                         */
                        final boolean[] isGoToRead = {true};
                        final boolean[] isGoToDisplayNoMoreCard = {true};
                        try {
                            end_date = (new Date()).getTime();
                            start_date = end_date - DEFAULT_PERIOD;
                            readAuthorIdsAndCardCountsPeriod(start_date, end_date, new CountListener() {
                                @Override
                                public void onCounting(Map<String, Long> parentIdsAndSiblingCounts) {
                                    if (isGoToRead[0]) {
                                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    /**
                                                     * no under the cut created before so no one need to be removed
                                                     */
                                                    readCardsFromFirebasePeriodPullSort(instance, start_date, end_date);
                                                } catch (ClassNotFoundException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }, DEFAULT_TIME_COST);
                                        isGoToRead[0] = false;
                                        isGoToDisplayNoMoreCard[0] = false;
                                    }
                                }
                            });
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isGoToDisplayNoMoreCard[0]) {
                                        Logger.d("... counting so not to display no more card");
                                        return;
                                    }
                                    /**
                                     * too long to investigate the counts, so abort to read cards this time
                                     */
                                    isGoToRead[0] = false;
                                    cancel(true);
                                    /**
                                     * no under the cut created before so no one need to be removed
                                     * and adds a new under the cut as
                                     * the first item shown on UI
                                     */
                                    Logger.e("!!! no more card and going to add a new under the cut");
                                    addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_LAST, makeCardAsUnderTheCut());
                                    mResult = RESULT_FAILURE;
                                    if (mMVPThread != null && mMVPThread.isAlive()) {
                                        Logger.d("... going to interrupt mvp thread");
                                        mMVPThread.interrupt();
                                    }
                                }
                            }, DEFAULT_TIME_COST + DEFAULT_TIME_WAIT); //if no more cards in reading for ms, updates the under the cut
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
                Logger.d("... going to wait");
                synchronized (lock) { //becomes the owner of the object's monitor
                    lock.wait(); //relinquishes the ownership of the object's monitor
                }
            } catch (InterruptedException e) {
                Logger.d("... got InterruptedException and proceed");
                if (mOurView != null) {
                    Logger.d("... going to call onLoad(" + mResult + ", " + all_card_count +") on the our view (FirstHomeFragment)");
                    mOurView.onLoad(mResult, all_card_count_read);
                }
            }
            Logger.d("... task done and now count of items:" + getItemCount());
        }
    }
    
    private class Worker {
        Thread thread;
        long stamp;

        private Worker(Thread thread, long time_stamp) {
            this.thread = thread;
            this.stamp = time_stamp;
        }

        private long getStamp() {
            return stamp;
        }

        private Thread getThread() {
            return thread;
        }

        private void start() {
            if (thread != null) {
                Logger.d("... going to worker start:" + stamp);
                thread.start();
            }
        }

        private void cancel() {
        }
    }
}
