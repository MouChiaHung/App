package com.ours.yours.app.ui.fragment.first.child;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import com.google.firebase.auth.FirebaseAuth;
import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.adapter.BaseModelAdapter;
import com.ours.yours.app.adapter.PhotoAdapter;
import com.ours.yours.app.base.PickImageFragment;
import com.ours.yours.app.dao.FileHelper;
import com.ours.yours.app.dao.UriHelper;
import com.ours.yours.app.entity.Card;
import com.ours.yours.app.entity.Photo;
import com.ours.yours.app.firebase.FirebaseDatabaseHelper;
import com.ours.yours.app.manager.CardManager;
import com.ours.yours.app.ui.mvp.OurModel;
import com.ours.yours.app.ui.mvp.OurPresenter;
import com.ours.yours.app.ui.mvp.OurView;
import com.ours.yours.app.utils.BitmapHelper;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class FirstAddCardFragment extends PickImageFragment implements FirstHomeFragment.CardGenerator, OurView {
    private final static String ARG_ADD_CARD = "ARG_ADD_CARD";
    private final static int INTENT_REQUEST_CODE_IMAGE       = 10;
    private final static int HANDLER_MSG_WHAT_BASE           = 20;
    private  final static int HANDLER_MSG_WHAT_UPLOAD_SUCCESS   = HANDLER_MSG_WHAT_BASE + 0;
    private  final static int HANDLER_MSG_WHAT_UPLOAD_FAILURE   = HANDLER_MSG_WHAT_BASE + 1;
    private  final static int HANDLER_MSG_WHAT_POST_SUCCESS     = HANDLER_MSG_WHAT_BASE + 2;
    private  final static int HANDLER_MSG_WHAT_POST_FAILURE     = HANDLER_MSG_WHAT_BASE + 3;
    private final static int HANDLER_MSG_WHAT_PREVIEW_PHOTO     = HANDLER_MSG_WHAT_BASE + 4;
    private  final static int HANDLER_MSG_WHAT_CANCEL           = HANDLER_MSG_WHAT_BASE + 5;

    private final static int POP_RESULT_BASE             = 30;
    public  final static int POP_RESULT_UPLOAD_FAILURE   = POP_RESULT_BASE + 1;
    public  final static int POP_RESULT_POST_SUCCESS     = POP_RESULT_BASE + 2;
    public  final static int POP_RESULT_POST_FAILURE     = POP_RESULT_BASE + 3;
    public  final static int POP_RESULT_CANCEL           = POP_RESULT_BASE + 4;

    private final static String HANDLER_MSG_BUNDLE_KEY_BASE  = "HANDLER_MSG_BUNDLE_KEY_";
    private final static String HANDLER_MSG_BUNDLE_KEY_PHOTO_URI = HANDLER_MSG_BUNDLE_KEY_BASE + "PHOTO_URI";
    private final static String HANDLER_MSG_BUNDLE_KEY_PHOTO_NAME = HANDLER_MSG_BUNDLE_KEY_BASE + "PHOTO_NAME";

    private FirstHomeFragment mFirstHomeFragment;
    private Handler mHandler;
    private View mView;
    private ImageButton mPostBtn, mAddImageBtn;
    private Toolbar mToolbar;
    private EditText mTitle, mContent;
    private AddCardTextWatcher mAddCardTextWatcher = new AddCardTextWatcher();
    private Card mCard;
    private PhotoAdapter mPhotoAdapter;
    private RecyclerView mPhotoRCV;
    private LinearLayoutManager mPhotoLayoutManager;
    private Button mSnackBtn;

    private OnPop mOnPop;
    private int mResultCode;

    /**
     * meta about local photos waiting to be uploaded
     * UI                   A B C  --remove B--> A C --recover B--> A B C
     * data set of adapter  A B C                A C                A B C
     * photo_uris           A B C                A C                A B C
     * photo_file_names     A B C                A C                A B C
     * photo_uri_cache                            B
     * photo_file_name_cache                      B
     */
    private List<Uri> photo_uris = new LinkedList<>(); //uris of local photos waiting to be uploaded
    private List<String> photo_file_names = new LinkedList<>(); //file names of local photos waiting to be uploaded
    private int upload_photo_count = Card.DEFAULT_ARTICLE_COUNT; //> 0 if one or more photos stored in photo_uris waiting to be uploaded
    private List<Uri> compress_uris_remove = new LinkedList<>(); //uris of temporary compress files waiting to be removed after if exist
    private Uri photo_uri_cache; //cache about uri used between remove and recover on snack bar
    private String photo_file_name_cache; //cache about file name used between remove and recover on snack bar

    public FirstAddCardFragment() {
        super();
    }

    public static FirstAddCardFragment newInstance(FirstHomeFragment parent_fragment) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_ADD_CARD, null);
        FirstAddCardFragment fragment = new FirstAddCardFragment();
        fragment.setHomeFragment(parent_fragment);
        fragment.setArguments(args);
        return fragment;
    }

    public void setHomeFragment(FirstHomeFragment fragment) {
        this.mFirstHomeFragment = fragment;
    }

    public void setOnPop(OnPop onPop) {
        this.mOnPop = onPop;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        mView = inflater.inflate(R.layout.app_first_child_add_card, container, false);
        initView();
        initToolbar();
        initHandler();
        initPhotoRecyclerView();
        initPhotoAdapter();
        return mView;
    }

    @Override
    public void onAttach(Context context) {
        Logger.d(">>>");
        super.onAttach(context);
    }

    @Override
    public void onResume() {
        //Logger.d(">>>");
        super.onResume();
    }

    @Override
    public void onDestroyView() {
        Logger.d(">>>");
        super.onDestroyView();
        mPhotoRCV.clearOnScrollListeners();
        mPhotoAdapter.detach(this);
    }

    @Override
    protected void onPick(Uri image) {
        if (image != null) Snackbar.make(mSnackBtn, "已選了個圖", Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE && resultCode == Activity.RESULT_OK && getUriOfPickImage() != null) {
            Logger.d("... crop image request code:" + requestCode + ", result code" + resultCode);
            if (mPhotoAdapter.getItemCount() == 1 //if a mock implying to add photo
                    && mPhotoAdapter.getItemOfDataSet(0).getUrl().equals(String.valueOf(R.drawable.add_photo))
                    && mPhotoAdapter.getItemOfDataSet(0).getName().equals(Photo.DEFAULT_NAME) ){
                Logger.d("... going to remove mock photo");
                mPhotoAdapter.removeItemsFromDataSet(BaseModelAdapter.REMOVE_POSITION_AT_FIRST, BaseModelAdapter.REMOVE_POSITION_AT_LAST);
            }
            if (getUriOfPickImage().toString().equals(CropImage.getCaptureImageOutputUri(_mActivity).toString())) { //if data got from camera
                File compress_bitmap_file = BitmapHelper.getInstance().compressToExternalFile(null, null
                        , BitmapHelper.getInstance().decodeFromUri(getUriOfPickImage(), _mActivity), _mActivity);
                if (compress_bitmap_file ==  null) {
                    Logger.e("!!! file_dest is null");
                    return;
                }
                Logger.d("... got an camera image and going to compress and save to an external file:" + compress_bitmap_file.getAbsolutePath());
                setUriOfPickImage(UriHelper.getInstance().toUri(compress_bitmap_file));
            } else { //if data got from folders
                File compress_bitmap_file = BitmapHelper.getInstance().compressToExternalFile(null, null
                        , BitmapHelper.getInstance().decodeFromUri(getUriOfPickImage(), _mActivity), _mActivity);
                if (compress_bitmap_file ==  null) {
                    Logger.e("!!! file_dest is null");
                    return;
                }
                Logger.d("... got an folder image and going to compress and save to an external file:" + compress_bitmap_file.getAbsolutePath());
                setUriOfPickImage(UriHelper.getInstance().toUri(compress_bitmap_file));
            }
            /*
            FileHelper.getInstance().copy(UriHelper.getInstance().toFile(mUriPickImage)
                    , FileHelper.getInstance().createExternalFile(null, UriHelper.getInstance().generateFileNameBasedOnTimeStamp()
                            + UriHelper.getInstance().getFileName(mUriPickImage, _mActivity), _mActivity)
            );
            */
            photo_uris.add(getUriOfPickImage());
            photo_file_names.add(getUriOfPickImage().getLastPathSegment());
            compress_uris_remove.add(getUriOfPickImage());
            Logger.d("... one more upload photo of local uri:" + photo_uris.get(photo_uris.size()-1).toString()
                    + " with file name:" + photo_file_names.get(photo_file_names.size()-1));
            if (photo_uris.size() == photo_file_names.size()) {
                upload_photo_count = photo_uris.size();
                Logger.d("... now photo count:" + upload_photo_count);
                previewImageFile(photo_uris.get(photo_uris.size()-1), photo_file_names.get(photo_file_names.size()-1));
            } else {
                Logger.e("!!! count of urls is not equal to count of file names");
                upload_photo_count = Card.DEFAULT_ARTICLE_COUNT;
                photo_uris.clear();
                photo_file_names.clear();
            }
        }
        /*
        else if (requestCode == INTENT_REQUEST_CODE_IMAGE && resultCode == RESULT_OK && data != null) {
            Logger.d("... intent request code:" + requestCode + ", result code" + resultCode);
            if (mPhotoAdapter.getItemCount() == 1
                    && mPhotoAdapter.getItemOfDataSet(0).getUrl().equals(String.valueOf(R.drawable.add_photo)) //as readPhotosFromMocks()
                    && mPhotoAdapter.getItemOfDataSet(0).getName().equals(Photo.DEFAULT_NAME) ){ //as readPhotosFromMocks()
                Logger.d("... going to remove mock photo");
                mPhotoAdapter.removeItemsFromDataSet(BaseModelAdapter.REMOVE_POSITION_AT_FIRST, BaseModelAdapter.REMOVE_POSITION_AT_LAST);
            }
            photo_uris.add(data.getData());
            photo_file_names.add(data.getData().getLastPathSegment());
            Logger.d("... going to upload photo of local uri  :" + photo_uris.get(photo_uris.size()-1).toString());
            Logger.d("... going to upload photo with file name:" + photo_file_names.get(photo_file_names.size()-1));
            if (photo_uris.size() == photo_file_names.size()) {
                upload_photo_count = photo_uris.size();
                Logger.d("... now photo count:" + upload_photo_count);
                previewImageFile(photo_uris.get(photo_uris.size()-1), photo_file_names.get(photo_file_names.size()-1));
            } else {
                Logger.e("!!! count of urls is not equal to count of file names");
                upload_photo_count = Card.DEFAULT_ARTICLE_COUNT;
                photo_uris.clear();
                photo_file_names.clear();
            }
        }
        */
        else {
            Logger.e("!!! unknown request code:" + requestCode + ", result code" + resultCode);
        }

    }

    @Override
    public void generateCards(List<Card> cards) {
        cards.add(BaseModelAdapter.POSITION_AT_FIRST, mCard);
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mResultCode == POP_RESULT_CANCEL) {
                    removeTemporaryCompressFiles();
                }
            }
        }).start();
        super.pop();
        if (mOnPop != null) mOnPop.onPop(mResultCode);
    }

    /**
     * consumes this back press event at TransactionDelegate#dispatchBackPressedEvent()
     * and let handler trigger pop() which has been overridden by this class
     * instead of ending in popChild() on the parent fragment "FirstFragment"
     */
    @Override
    public boolean onBackPressedSupport() {
        Message msg = new Message();
        msg.what = HANDLER_MSG_WHAT_CANCEL;
        mHandler.sendMessage(msg);
        return true;
    }

    private void initView() {
        mPostBtn = mView.findViewById(R.id.firstAddCardToolbarPostBtn);
        mPostBtn.setOnClickListener(new PostBtnOnClickListener());
        mAddImageBtn = mView.findViewById(R.id.firstAddCardToolbarAddBtn);
        mAddImageBtn.setOnClickListener(new AddPhotoBtnOnClickListener());
        mTitle = mView.findViewById(R.id.firstAddCardPostTitle);
        mTitle.addTextChangedListener(mAddCardTextWatcher);
        mContent = mView.findViewById(R.id.firstAddCardPostContent);
        mContent.addTextChangedListener(mAddCardTextWatcher);
        mSnackBtn = mView.findViewById(R.id.firstAddCardSnackBarBtn);
    }

    @SuppressLint("HandlerLeak")
    private void initHandler() {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case HANDLER_MSG_WHAT_UPLOAD_SUCCESS:
                        try {
                            hideProgress();
                            showProgress("快好惹，二檔上傳中...");
                            /**
                             * CardManager#uploadManyPhotosOnFirebase() stored urls of uris on ArticlePhoto the way LIFO
                             * so reverses the file names to arrange elements of ArticlePhotoFileName to indices relative to the correct urls
                             * uri       01 02 03 ----LIFO--> ArticlePhoto         03 02 01
                             * file name 01 02 03 --REVERSE-> ArticlePhotoFileName 03 02 01
                             */
                             Logger.d("... got upload success msg and going to reverse ArticlePhotoFileName");
                            Collections.reverse(mCard.getArticlePhotoFileName());
                            mCard.printCardData();
                            CardManager.getInstance(_mActivity).postupCardOnFirebase(mCard, new CardPoster());
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                        break;
                    case HANDLER_MSG_WHAT_UPLOAD_FAILURE:
                        mResultCode = POP_RESULT_UPLOAD_FAILURE;
                        hideProgress();
                        pop();
                        break;
                    case HANDLER_MSG_WHAT_POST_SUCCESS:
                        //commit();
                        commitImmediate();
                        mResultCode = POP_RESULT_POST_SUCCESS;
                        //mPostBtn.setClickable(true); //unblocks click
                        hideProgress();
                        hideKeyboard();
                        pop();
                        break;
                    case HANDLER_MSG_WHAT_POST_FAILURE:
                        mResultCode = POP_RESULT_POST_FAILURE;
                        //mPostBtn.setClickable(true); //unblocks click
                        hideProgress();
                        pop();
                        break;
                    case HANDLER_MSG_WHAT_CANCEL:
                        mResultCode = POP_RESULT_CANCEL;
                        //mPostBtn.setClickable(true); //unblocks click
                        pop();
                        break;
                    case HANDLER_MSG_WHAT_PREVIEW_PHOTO:
                        Bundle bundle = msg.getData();
                        String uri = bundle.getString(HANDLER_MSG_BUNDLE_KEY_PHOTO_URI);
                        String name = bundle.getString(HANDLER_MSG_BUNDLE_KEY_PHOTO_NAME);
                        Photo photo = new Photo(uri, name);
                        feed(photo, mPhotoAdapter);
                        break;
                }
            }
        };
    }

    private void initToolbar() {
        mToolbar = mView.findViewById(R.id.firstAddCardToolbar);
    }

    /**
     * posts a new card which has un-specified card id and article photo url, both of them will be assigned by card manager later
     * which means this method is going to trigger an asynchronous posting task
     *
     * a poster implemented and used to respond to user on UI when card manager gets the event
     * about result of task of upload image and write pojo of card to firebase real time database
     *
     * with photo   : uploads files to Firebase storage then writes POJO to Firebase real time database when succeeding of upload task
     * without photo: writes POJO to Firebase real time database directly
     */
    private void post(Card card) throws ClassNotFoundException {
        if (CardManager.getInstance(_mActivity) == null) {
            Logger.e("!!! card manager is null");
            return;
        }
        /**
         * CaraManager#uploadManyPhotosOnFirebase() invokes List#remove() losing referring to elements of photo_uris
         */
        if (card.getArticleCount() > 0) CardManager.getInstance(_mActivity).uploadManyPhotosOnFirebase(card, photo_uris, new CardUploader());
        else CardManager.getInstance(_mActivity).postupCardOnFirebase(card, new CardPoster());
    }

    private void previewImageFile(Uri file_uri, String file_name) {
        Message msg = new Message();
        Bundle bundle = new Bundle();
        bundle.putString(HANDLER_MSG_BUNDLE_KEY_PHOTO_URI, String.valueOf(file_uri));
        bundle.putString(HANDLER_MSG_BUNDLE_KEY_PHOTO_NAME, String.valueOf(file_name));
        msg.what = HANDLER_MSG_WHAT_PREVIEW_PHOTO;
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private void commit() {
        if (mFirstHomeFragment == null) return;
        mFirstHomeFragment.enqueueAction(this);
        mFirstHomeFragment.executeAction();
    }

    private void commitImmediate() {
        if (mFirstHomeFragment == null) return;
        mFirstHomeFragment.generateCardImmediate(this);
    }

    private void initPhotoAdapter() {
        mPhotoAdapter = new PhotoAdapter(_mActivity);
        mPhotoAdapter.setClickedAuthorId(null); //here view feeds model to presenter directly
        mPhotoAdapter.setClickedCardId(null); //here view feeds model to presenter directly
        mPhotoAdapter.setRemoveBtnVisible(true);
        mPhotoAdapter.setAddPhotoImageOnClickListener(new AddPhotoImageOnClickListener());
        mPhotoAdapter.attach(this);
        mPhotoRCV.setAdapter(mPhotoAdapter);
        readPhotosFromMocks(mPhotoAdapter);
    }

    private void initPhotoRecyclerView() {
        mPhotoRCV = mView.findViewById(R.id.firstAddCardPostImageRCV);
        mPhotoLayoutManager = new LinearLayoutManager(_mActivity, LinearLayoutManager.HORIZONTAL, false);
        //StaggeredGridLayoutManager gridLayoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        //mPhotoLayoutManager.setAutoMeasureEnabled(true);
        //sets layout manager to position the items
        mPhotoRCV.setLayoutManager(mPhotoLayoutManager);
        //mPhotoRCV.setLayoutManager(gridLayoutManager);
        mPhotoRCV.setHasFixedSize(false);

        //mPhotoRCV.addOnScrollListener(new OnRCVScroll());
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
     * for testing and fixing the issue
     * that RecyclerView.Adapter#onBindViewHolder() won't be invoked if RecyclerView.Adapter#getItemCount() return 0
     */
    private void readPhotosFromMocks(PhotoAdapter adapter) {
        Logger.d(">>>");
        Photo mock;
        String url = String.valueOf(R.drawable.add_photo);
        String name = Photo.DEFAULT_NAME;
        for (int i = 0; i < 1; i++) {
            mock = new Photo(url, name);
            adapter.addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_LAST, mock);
        }
    }

    /**
     * after all of temporary compress image files has been uploaded successfully
     * removes them throw photo_uris which are referring to those files
     */
    private void removeTemporaryCompressFiles() {
        boolean isRemoveBeforePop = false;
        if (compress_uris_remove.isEmpty()) {
            isRemoveBeforePop = true;
            for (Uri uri : photo_uris) compress_uris_remove.add(uri); //shallow copy when even no invocation to upload but some local compress files exist
        }
        Logger.d("... count of temporary compress image files:" + compress_uris_remove.size());
        if (compress_uris_remove.size() <= 0) {
            Logger.d("... no temporary compress image file");
            return;
        }
        for (Uri uri : compress_uris_remove) {
            File file_to_be_removed = UriHelper.getInstance().toFile(uri);
            if (isRemoveBeforePop) Logger.d("... going to remove temporary compress image file before pop:" + file_to_be_removed.getAbsolutePath());
            else Logger.d("... going to remove temporary compress image file:" + file_to_be_removed.getAbsolutePath());
            FileHelper.getInstance().remove(file_to_be_removed.getAbsolutePath(), FileHelper.DEFAULT_REMOVE_ANY);
        }
    }

    @Override
    public void feed(OurModel model, OurPresenter presenter) {
        if (model instanceof Photo && presenter instanceof PhotoAdapter) {
            ((PhotoAdapter)presenter).addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_LAST, (Photo) model);
            mPhotoRCV.scrollToPosition(mPhotoRCV.getAdapter().getItemCount()-1);
        }
    }

    @Override
    public void onNotice(int result, String message) {

    }

    @Override
    public void onFetch(int result, Object pojo) {

    }

    @Override
    public void onLoad(int result, Object pojo) {

    }

    @Override
    public void onRemove(int result, final int position, Object model) {
        Logger.d(">>> position:" + position);
        if (!(model instanceof Photo)) return;
        Photo photo_removed = (Photo) model;
        switch (result) {
            case BaseModelAdapter.RESULT_SUCCESS:
                Logger.d("... got BaseModelAdapter.RESULT_SUCCESS:");
                if (photo_uris.get(position).toString().equals(photo_removed.getUrl())
                        && photo_file_names.get(position).equals(photo_removed.getName())) { //if position fine
                    photo_uri_cache = photo_uris.remove(position);
                    photo_file_name_cache = photo_file_names.remove(position);
                    upload_photo_count = photo_uris.size();
                    Logger.d("cached a correct one after remove" + "\n"
                            + "photo_uri_cache:" + photo_uri_cache + "\n"
                            + "photo_file_name_cache:" + photo_file_name_cache + "\n"
                            + "upload_photo_count:" + upload_photo_count);
                    final Snackbar snackbar = Snackbar.make(mSnackBtn, "確定不要了?", Snackbar.LENGTH_LONG)
                            .setAction("還我啊", new View.OnClickListener(){
                                @Override
                                public void onClick(View v) {
                                    mPhotoAdapter.recover(position);
                                }
                            });
                    snackbar.setDuration(1500).show();
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mPhotoAdapter.getItemCount() == 0 && !snackbar.isShown()) readPhotosFromMocks(mPhotoAdapter);
                        }
                    }, 2000);
                } else { //if position chaos between data set of adapter and cache
                    Logger.e("!!! cached an incorrect one after remove" + "\n"
                            + "photo_uri_cache:" + photo_uris.get(position).toString() + "\n"
                            + "photo_file_name_cache:" + photo_file_names.get(position) + "\n"
                            + "uri of adapter removed:" + photo_removed.getUrl() + "\n"
                            + "file name of adapter removed:" + photo_removed.getName());
                    Snackbar.make(mSnackBtn, "快取抓錯人惹", Snackbar.LENGTH_SHORT).show();
                    mPhotoAdapter.addItemToDataSet(position, photo_removed);
                    return;
                }

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("... check meta and data set after remove" + "\n");
                if (mPhotoAdapter.getItemCount() <= 0) {
                    stringBuilder.append("adapter has no data" + "\n");
                    for (int i=0; i<photo_uris.size(); i++) {
                        stringBuilder.append("photo_uris at " + i + " has uri:");
                        stringBuilder.append(photo_uris.get(i).toString() + "\n");
                        stringBuilder.append("photo_file_names at " + i + " has uri:");
                        stringBuilder.append(photo_file_names.get(i) + "\n");
                    }
                    Logger.d(stringBuilder.toString());
                } else {
                    for (int i=0; i<mPhotoAdapter.getItemCount(); i++) {
                        stringBuilder.append("adapter at " + i + " has uri:");
                        stringBuilder.append(mPhotoAdapter.getItemOfDataSet(i).getUrl() + "\n");
                        stringBuilder.append("adapter at " + i + " has file name:");
                        stringBuilder.append(mPhotoAdapter.getItemOfDataSet(i).getName() + "\n");
                        stringBuilder.append("photo_uris at " + i + " has uri:");
                        stringBuilder.append(photo_uris.get(i).toString() + "\n");
                        stringBuilder.append("photo_file_names at " + i + " has uri:");
                        stringBuilder.append(photo_file_names.get(i) + "\n");
                    }
                    Logger.d(stringBuilder.toString());
                }

                break;
            case BaseModelAdapter.RESULT_FAILURE:
                Logger.e("!!! got BaseModelAdapter.RESULT_FAILURE");
                Snackbar.make(mSnackBtn, "沒刪掉耶", Snackbar.LENGTH_LONG).show();
                break;
            default:
                break;
        }
    }

    @Override
    public void onRecover(int result, int position, Object model) {
        Logger.d(">>> position:" + position);
        if (!(model instanceof Photo)) return;
        Photo photo_recovered = (Photo) model;
        switch (result) {
            case BaseModelAdapter.RESULT_SUCCESS:
                Logger.d("... got BaseModelAdapter.RESULT_SUCCESS:");
                if (photo_uri_cache.toString().equals(photo_recovered.getUrl()) && photo_file_name_cache.equals(photo_recovered.getName())) { //if position fine
                    photo_uris.add(position, photo_uri_cache);
                    photo_file_names.add(position, photo_file_name_cache);
                    upload_photo_count = photo_uris.size();
                    Logger.d("cached a correct one after recover" + "\n"
                            + "photo_uri_cache:" + photo_uri_cache + "\n"
                            + "photo_file_name_cache:" + photo_file_name_cache + "\n"
                            + "upload_photo_count:" + upload_photo_count);
                    Snackbar.make(mSnackBtn, "已還", Snackbar.LENGTH_SHORT).show();
                } else { //if position chaos between data set of adapter and cache
                    Logger.e("!!! cached an incorrect one after recover" + "\n"
                            + "photo_uri_cache:" + photo_uri_cache + "\n"
                            + "photo_file_name_cache:" + photo_file_name_cache + "\n"
                            + "uri of adapter removed:" + photo_recovered.getUrl() + "\n"
                            + "file name of adapter removed:" + photo_recovered.getName());
                    Snackbar.make(mSnackBtn, "快取抓錯人惹", Snackbar.LENGTH_SHORT).show();
                    mPhotoAdapter.removeItemFromDataSet(position);
                    return;
                }

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("... check meta and data set after recover" + "\n");
                if (mPhotoAdapter.getItemCount() <= 0) {
                    stringBuilder.append("adapter has no data" + "\n");
                    for (int i=0; i<photo_uris.size(); i++) {
                        stringBuilder.append("photo_uris at " + i + " has uri:");
                        stringBuilder.append(photo_uris.get(i).toString() + "\n");
                        stringBuilder.append("photo_file_names at " + i + " has uri:");
                        stringBuilder.append(photo_file_names.get(i) + "\n");
                    }
                    Logger.d(stringBuilder.toString());
                } else {
                    for (int i=0; i<mPhotoAdapter.getItemCount(); i++) {
                        stringBuilder.append("adapter at " + i + " has uri:");
                        stringBuilder.append(mPhotoAdapter.getItemOfDataSet(i).getUrl() + "\n");
                        stringBuilder.append("adapter at " + i + " has file name:");
                        stringBuilder.append(mPhotoAdapter.getItemOfDataSet(i).getName() + "\n");
                        stringBuilder.append("photo_uris at " + i + " has uri:");
                        stringBuilder.append(photo_uris.get(i).toString() + "\n");
                        stringBuilder.append("photo_file_names at " + i + " has uri:");
                        stringBuilder.append(photo_file_names.get(i) + "\n");
                    }
                    Logger.d(stringBuilder.toString());
                }

                break;
            case BaseModelAdapter.RESULT_FAILURE:
                Logger.e("!!! got BaseModelAdapter.RESULT_FAILURE");
                Snackbar.make(mSnackBtn, "來不及了", Snackbar.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
    }

    /**
     * callback for popping this fragment from back back
     */
    public interface OnPop {
        void onPop(int resultCode);
    }

    class AddCardTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s.toString().trim().length() > 0) {
                mPostBtn.setEnabled(true);
            } else {
                mPostBtn.setEnabled(false);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {}
    }

    /**
     * cardId is going to be assigned by CardManager with key provided by Firebase
     * articlePhoto is going to be assigned by CardManager with download urls
     * distance is going to be assigned by Google Map
     */
    class PostBtnOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            String cardId           = Card.DEFAULT_CARD_ID;
            String profilePhoto     = Card.DEFAULT_PROFILE_PHOTO_URL;
            String profileTitle     = Card.DEFAULT_PROFILE_TITLE;
            String profileName      = Card.DEFAULT_PROFILE_NAME;
            String profileID        = Card.DEFAULT_PROFILE_ID;
            String articleTitle     = Card.DEFAULT_ARTICLE_TITLE;
            String articleContent   = Card.DEFAULT_ARTICLE_CONTENT;
            int    articleCount     = Card.DEFAULT_ARTICLE_COUNT;
            int distance = Card.DEFAULT_DISTANCE;
            long date    = (new Date()).getTime();

            v.setClickable(false); //blocks immediate repeating
            showProgress("努力上傳中...");
            if (Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getPhotoUrl() != null) {
                profilePhoto   = Objects.requireNonNull(Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getPhotoUrl()).toString();
            }
            if (FirebaseDatabaseHelper.getInstance().getUserName() != null) profileName = FirebaseDatabaseHelper.getInstance().getUserName();
            if (FirebaseDatabaseHelper.getInstance().getUserId() != null) profileID = FirebaseDatabaseHelper.getInstance().getUserId();
            if (!mTitle.getText().toString().equals("")) articleTitle = mTitle.getText().toString();
            if (!mContent.getText().toString().equals("")) articleContent = mContent.getText().toString();
            if (photo_uris.size() != photo_file_names.size()) {
                Logger.e("!!! photo_uris.size() != photo_file_names.size()");
                return;
            }
            if (upload_photo_count != photo_uris.size()) {
                Logger.e("!!! upload_photo_count != photo_uris.size()");
                return;
            }
            if (upload_photo_count != photo_file_names.size()) {
                Logger.e("!!! upload_photo_count != photo_file_names.size()");
                return;
            }
            if (upload_photo_count > Card.DEFAULT_ARTICLE_COUNT) {
                articleCount = upload_photo_count;
            }
            mCard = new Card(cardId, profilePhoto, profileTitle, profileName, profileID, articleTitle, articleContent, articleCount, distance, date);
            if (mCard.getArticleCount() > Card.DEFAULT_ARTICLE_COUNT && photo_file_names.size() > 0) {
                mCard.setArticlePhotoFileName(photo_file_names);
            }
            try {
                if (!CardManager.getInstance(_mActivity).pushAndGetAuoGeneratedCardId(mCard)) {
                    Message msg = new Message();
                    msg.what = HANDLER_MSG_WHAT_POST_FAILURE;
                    mHandler.sendMessage(msg);
                    return;
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            try {
                post(mCard);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    class AddPhotoBtnOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            /*
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, INTENT_REQUEST_CODE_IMAGE);
            */
            pickImage();
        }
    }

    class AddPhotoImageOnClickListener implements PhotoAdapter.AddPhotoImageOnClickListener {
        @Override
        public void onClick(View v) {
            /*
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, INTENT_REQUEST_CODE_IMAGE);
            */
            pickImage();
        }
    }

    class CancleBtnOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Message msg = new Message();
            msg.what = HANDLER_MSG_WHAT_CANCEL;
            mHandler.sendMessage(msg);
            upload_photo_count = Card.DEFAULT_ARTICLE_COUNT;
            photo_uris.clear();
            photo_file_names.clear();
        }
    }

    class CardPoster implements CardManager.Poster {
        @Override
        public void onSuccess() {
            Message msg = new Message();
            msg.what = HANDLER_MSG_WHAT_POST_SUCCESS;
            mHandler.sendMessage(msg);
        }

        @Override
        public void onFailure() {
            Message msg = new Message();
            msg.what = HANDLER_MSG_WHAT_POST_FAILURE;
            mHandler.sendMessage(msg);
        }
    }

    class CardUploader implements CardManager.Uploader {
        @Override
        public void onSuccess(int process, int success_index) {
            if (process == CardManager.CARD_UPLOAD_PROCESS_DONE) {
                Message msg = new Message();
                msg.what = HANDLER_MSG_WHAT_UPLOAD_SUCCESS;
                mHandler.sendMessage(msg);
                removeTemporaryCompressFiles();
            } else {
                hideProgress();
                showProgress("上傳進度:" + process + "%");
            }
        }

        @Override
        public void onFailure(int fail_index) {
            Message msg = new Message();
            msg.what = HANDLER_MSG_WHAT_UPLOAD_FAILURE;
            mHandler.sendMessage(msg);
            removeTemporaryCompressFiles();
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
            orientateRecyclerView(recyclerView, destination);
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
        }
    }
}
