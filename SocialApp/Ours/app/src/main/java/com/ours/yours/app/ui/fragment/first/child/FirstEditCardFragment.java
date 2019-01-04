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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.adapter.BaseModelAdapter;
import com.ours.yours.app.adapter.PhotoAdapter;
import com.ours.yours.app.base.PickImageFragment;
import com.ours.yours.app.dao.FileHelper;
import com.ours.yours.app.dao.UriHelper;
import com.ours.yours.app.entity.Card;
import com.ours.yours.app.entity.Photo;
import com.ours.yours.app.manager.CardManager;
import com.ours.yours.app.ui.mvp.OurModel;
import com.ours.yours.app.ui.mvp.OurPresenter;
import com.ours.yours.app.ui.mvp.OurView;
import com.ours.yours.app.utils.BitmapHelper;
import com.ours.yours.app.worker.ExecutorHelper;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FirstEditCardFragment extends PickImageFragment implements OurView {
    private final static String ARG_EDIT_CARD = "ARG_EDIT_CARD";
    private final static int INTENT_REQUEST_CODE_IMAGE       = 10;
    private final static int HANDLER_MSG_WHAT_BASE           = 20;
    private final static int HANDLER_MSG_WHAT_UPLOAD_SUCCESS = HANDLER_MSG_WHAT_BASE + 0;
    private final static int HANDLER_MSG_WHAT_UPLOAD_FAILURE = HANDLER_MSG_WHAT_BASE + 1;
    private final static int HANDLER_MSG_WHAT_POST_SUCCESS   = HANDLER_MSG_WHAT_BASE + 2;
    private final static int HANDLER_MSG_WHAT_POST_FAILURE   = HANDLER_MSG_WHAT_BASE + 3;
    private final static int HANDLER_MSG_WHAT_PREVIEW_PHOTO  = HANDLER_MSG_WHAT_BASE + 4;
    private final static int HANDLER_MSG_WHAT_CANCEL         = HANDLER_MSG_WHAT_BASE + 5;

    private final static int POP_RESULT_BASE           = 30;
    public  final static int POP_RESULT_UPLOAD_FAILURE = POP_RESULT_BASE + 1;
    public  final static int POP_RESULT_POST_SUCCESS   = POP_RESULT_BASE + 2;
    public  final static int POP_RESULT_POST_FAILURE   = POP_RESULT_BASE + 3;
    public  final static int POP_RESULT_CANCEL         = POP_RESULT_BASE + 4;

    private final static int EDIT_STATE_BASE      = 40;
    public  final static int EDIT_STATE_ORIGINAL  = EDIT_STATE_BASE + 1;
    public  final static int EDIT_STATE_RECOVER   = EDIT_STATE_BASE + 2;
    public  final static int EDIT_STATE_UPLOADING = EDIT_STATE_BASE + 3;
    public  final static int EDIT_STATE_UPLOADED  = EDIT_STATE_BASE + 4;
    public  final static int EDIT_STATE_POSTING  = EDIT_STATE_BASE + 5;


    private final static String HANDLER_MSG_BUNDLE_KEY_BASE  = "HANDLER_MSG_BUNDLE_KEY_";
    private final static String HANDLER_MSG_BUNDLE_KEY_PHOTO_URI = HANDLER_MSG_BUNDLE_KEY_BASE + "PHOTO_URI";
    private final static String HANDLER_MSG_BUNDLE_KEY_PHOTO_NAME = HANDLER_MSG_BUNDLE_KEY_BASE + "PHOTO_NAME";

    private Handler mHandler;
    private View mView;
    private ImageButton mPostBtn, mAddImageBtn;
    private Toolbar mToolbar;
    private EditText mTitle, mContent;
    private EditCardTextWatcher mEditCardTextWatcher = new EditCardTextWatcher();
    private Card mCard;
    private PhotoAdapter mPhotoAdapter;
    private RecyclerView mPhotoRCV;
    private LinearLayoutManager mPhotoLayoutManager;
    private Button mSnackBtn;

    private OnPop mOnPop;
    private int mResultCode;

    private final Object lock = new Lock();

    /**
     * meta about local photos waiting to be uploaded
     * UI                        A B C O --remove B--> A C O --recover B--> A B C O --remove O--> A B C --recover B--> A B C O
     * data set of adapter       A B C                 A C O                A B C O               A B C                A B C O
     * photo_uris                A B C                 A C O                A B C O               A B C                A B C O
     * photo_file_names          A B C                 A C O                A B C O               A B C                A B C O
     * photo_uri_cache                                 B
     * photo_file_name_cache                           B
     * photos_original_remove                                                                     O
     * photo_urls_original       O
     * photo_file_names_original O
     * upload_photo_count        3                     2                    3                     3                    3
     */
    private List<Uri> photo_uris = new LinkedList<>(); //uris of local photos waiting to be uploaded
    private List<String> photo_file_names = new LinkedList<>(); //file names of local photos waiting to be uploaded
    private int upload_photo_count = 0; //> 0 if one or more photos stored in photo_uris waiting to be uploaded
    private List<Uri> compress_uris_remove = new LinkedList<>(); //uris of temporary compress files waiting to be removed after if exist
    private Uri photo_uri_cache; //cache about uri used between remove and recover on snack bar
    private String photo_file_name_cache; //cache about file name used between remove and recover on snack bar
    /**
     * meta about original remote photos waiting to be removed
     */
    private Map<String, Photo> photos_original_remove = new HashMap<>(); //photos originally bolonging to card waiting to be removed
    /**
     * backup of original card photos including of urls and file names
     */
    private List<String> photo_urls_original = new LinkedList<>();
    private List<String> photo_file_names_original = new LinkedList<>();
    /**
     * state of edit card
     */
    private int mEditState = EDIT_STATE_BASE;
    
    public FirstEditCardFragment() {
        super();
    }

    public static FirstEditCardFragment newInstance() {
        Bundle args = new Bundle();
        args.putParcelable(ARG_EDIT_CARD, null);
        FirstEditCardFragment fragment = new FirstEditCardFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public void setCard(Card card) {
        this.mCard = card;
    }

    public void setOnPop(OnPop onPop) {
        this.mOnPop = onPop;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        mView = inflater.inflate(R.layout.app_first_child_edit_card, container, false);
        initView();
        initToolbar();
        initHandler();
        bindCardData();
        initPhotoRecyclerView();
        initPhotoAdapter();
        initCard();
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
        removeTemporaryCompressFiles();
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
            /**
             * the uri of the last compress file is always added to the tail of photo_uris
             * which stores uris of photos added and waiting to be uploaded
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
                upload_photo_count = 0;
                photo_uris.clear();
                photo_file_names.clear();
            }
        } else {
            Logger.e("!!! unknown request code:" + requestCode + ", result code" + resultCode);
        }
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
        removeTemporaryCompressFiles();
        if (mResultCode == POP_RESULT_CANCEL || mResultCode == POP_RESULT_UPLOAD_FAILURE || mResultCode == POP_RESULT_POST_FAILURE) {
            /**
             * before pop for cancel or failure, recovers the card instance which is the same one
             * being accessed by both of FirstEditCardFragment and FirstAddCardFragment
             */
            if (upload_photo_count > 0) {
                recoverCardPhotoMeta(EDIT_STATE_RECOVER, mCard);
            } else {
                recoverCardPhotoMeta(EDIT_STATE_ORIGINAL, mCard);
            }
        }
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
        mPostBtn = mView.findViewById(R.id.firstEditCardToolbarPostBtn);
        mPostBtn.setOnClickListener(new PostBtnOnClickListener());
        mAddImageBtn = mView.findViewById(R.id.firstEditCardToolbarAddBtn);
        mAddImageBtn.setOnClickListener(new AddPhotoBtnOnClickListener());
        mTitle = mView.findViewById(R.id.firstEditCardPostTitle);
        mTitle.addTextChangedListener(mEditCardTextWatcher);
        mContent = mView.findViewById(R.id.firstEditCardPostContent);
        mContent.addTextChangedListener(mEditCardTextWatcher);
        mSnackBtn = mView.findViewById(R.id.firstEditCardSnackBarBtn);
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
                            prepareCardPhotoMeta(EDIT_STATE_UPLOADED, mCard);
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
                        Logger.d("... got post success msg and going to pop");
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
        mToolbar = mView.findViewById(R.id.firstEditCardToolbar);
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
        mTitle.setText(mCard.getArticleTitle());
        mContent.setText(mCard.getArticleContent());
        if (mCard.getArticlePhoto().size() != mCard.getArticleCount()) Logger.e("!!! record of count error");
        /**
         * no need to notify PhotoRCV item changes here because lately calling to initCard() wound invoke
         * feed() which runs setItemsAsDataSet() that would trigger RCV UI updates
         * while completing reading data set from Firebase
         */
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
        if (upload_photo_count > 0) { //if there're local photos stored in photo_uris waiting to be uploaded
            prepareCardPhotoMeta(EDIT_STATE_UPLOADING, mCard);
        } else {
            prepareCardPhotoMeta(EDIT_STATE_POSTING, mCard); //if no photo waiting to be uploaded
        }
        switch (mEditState) {
            case EDIT_STATE_UPLOADING:
                Logger.d("... EDIT_STATE_UPLOADING");
                mCard.printCardData();
                CardManager.getInstance(_mActivity).uploadManyPhotosOnFirebase(card, photo_uris, new CardUploader());
                break;
            case EDIT_STATE_POSTING:
                Logger.d("... EDIT_STATE_POSTTING");
                mCard.printCardData();
                CardManager.getInstance(_mActivity).postupCardOnFirebase(card, new CardPoster());
                break;
            default:
                Logger.e("!!! irrelevant state");
                break;
        }
    }

    private void removeOriginalPhotos(Card card, Map<String, Photo> original_photos_remove, final CardManager.Remover remover) {
        List<String> targets = new ArrayList();
        for (final Map.Entry<String, Photo> entry : original_photos_remove.entrySet()) {
            targets.add(entry.getKey()); //copies file names of original photos wanted to be removed
        }
        try {
            CardManager.getInstance(_mActivity).removeManyPhotosFromFirebase(card, targets, remover);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
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

    private void initPhotoAdapter() {
        mPhotoAdapter = new PhotoAdapter(_mActivity);
        mPhotoAdapter.setClickedAuthorId(null); //here view feeds model to presenter directly
        mPhotoAdapter.setClickedCardId(null); //here view feeds model to presenter directly
        //mPhotoAdapter.setClickedAuthorId(mCard.getProfileID());
        //mPhotoAdapter.setClickedCardId(mCard.getCardId());
        mPhotoAdapter.setRemoveBtnVisible(true);
        mPhotoAdapter.setAddPhotoImageOnClickListener(new AddPhotoImageOnClickListener());
        mPhotoAdapter.attach(this);
        mPhotoRCV.setAdapter(mPhotoAdapter);
        //mPhotoAdapter.setMaxHeight(400);
        //PhotoAdapter.setMinHeight(400);
        //mPhotoAdapter.setScaleType(ImageView.ScaleType.FIT_CENTER);
        readPhotosFromMocks(mPhotoAdapter);
    }

    private void initPhotoRecyclerView() {
        mPhotoRCV = mView.findViewById(R.id.firstEditCardPostImageRCV);
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
     * firstly sync. card with firebase through presenter (PhotoAdapter), secondly sync. comment
     *
     * clears the mock data set used to initial the adapter before
     *
     * no need to notify RCV item changes here due to
     * RecyclerView.Adapter#addItemToDataSet() would trigger RCV UI updates
     * while feeding photos to populate data into items of RCV through adapter
     */
    private void initCard() {
        if (mCard == null) {
            Logger.e("!!! mCard is null");
            return;
        }
        mCard.printCardData();
        List<Photo> photos_init = new LinkedList<>();
        if (mCard.getArticleCount() <= 0) return;
        for (int i=0; i<mCard.getArticleCount(); i++) {
            String url = mCard.getArticlePhoto().get(i);
            String file_name = mCard.getArticlePhotoFileName().get(i);
            photos_init.add(new Photo(url, file_name));
        }
        mPhotoAdapter.removeItemsFromDataSet(BaseModelAdapter.REMOVE_POSITION_AT_FIRST, BaseModelAdapter.REMOVE_POSITION_AT_LAST);
        feed(photos_init, mPhotoAdapter);
        //mPhotoAdapter.load();
    }

    private void backupCardPhotoMeta(int state, Card card) {
        if (card == null) return;
        switch (state) {
            case EDIT_STATE_UPLOADING:
                photo_urls_original.clear();
                photo_file_names_original.clear();
                photo_urls_original.addAll(card.getArticlePhoto());
                photo_file_names_original.addAll(card.getArticlePhotoFileName());
                StringBuilder stringBuilder = new StringBuilder();
                for (String url : photo_urls_original) stringBuilder.append("... original url:" + url + "\n");
                for (String name : photo_file_names_original) stringBuilder.append("... original file name:" + name + "\n");
                Logger.d(stringBuilder.toString());
                break;
            default:
                Logger.e("!!! irrelevant state");
                break;
        }
    }

    private void recoverCardPhotoMeta(int state, Card card) {
        if (card == null) return;
        switch (state) {
            case EDIT_STATE_ORIGINAL:
                mEditState = EDIT_STATE_ORIGINAL;
                break;
            case EDIT_STATE_RECOVER:
                mEditState = EDIT_STATE_RECOVER;
                if (!photo_urls_original.isEmpty()) {
                    card.getArticlePhoto().clear();
                    card.getArticlePhoto().addAll(photo_urls_original);
                    card.setArticleCount(photo_urls_original.size());
                }
                if (!photo_file_names_original.isEmpty()) {
                    card.getArticlePhotoFileName().clear();
                    card.setArticlePhotoFileName(photo_file_names_original);
                }
                break;
            default:
                Logger.e("!!! irrelevant state");
                break;
        }
    }

    private void prepareCardPhotoMeta(int state, Card card) {
        if (card == null) return;
        switch (state) {
            case EDIT_STATE_POSTING:
                mEditState = EDIT_STATE_POSTING;
                break;
            case EDIT_STATE_UPLOADING:
                /**
                 * puts original photo urls and file names aside that empties the space
                 * in according to apply to CardManager#uploadManyPhotosOnFirebase()
                 * which only works for card holding empty article photo urls and file names waiting to be assigned
                 *
                 * the count of how many article photo belonged card is FIRSTLY assigned to the count of photos added so far
                 */
                mEditState = EDIT_STATE_UPLOADING;
                backupCardPhotoMeta(state, card);
                card.getArticlePhoto().clear();
                card.getArticlePhotoFileName().clear();
                card.setArticlePhotoFileName(photo_file_names);
                card.setArticleCount(upload_photo_count);
                break;
            case EDIT_STATE_UPLOADED:
                /**
                 * after CardManager#uploadManyPhotosOnFirebase() uploading completes, reconnects the originals at tail
                 *
                 * the count of how many article photo belonged card is SECONDLY added the count of photos originally belonged to card
                 */
                mEditState = EDIT_STATE_UPLOADED;
                card.getArticlePhoto().addAll(photo_urls_original);
                card.getArticlePhotoFileName().addAll(photo_file_names_original);
                card.setArticleCount(card.getArticleCount() + photo_urls_original.size());
                break;
            default:
                Logger.e("!!! irrelevant state");
                break;
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
            adapter.addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_FIRST, mock);
        }
    }

    /**
     * after all of temporary compress image files has been uploaded successfully
     * removes them throw photo_uris which are referring to those files
     */
    private void removeTemporaryCompressFiles() {
        Logger.d(">>>");
        boolean isRemoveBeforePop = false;
        if (compress_uris_remove.isEmpty()) {
            isRemoveBeforePop = true;
            /**
             * shallow copy when even no invocation to upload but some local compress files exist
             */
            compress_uris_remove.addAll(photo_uris);
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

    public void feed(List<Photo> models, OurPresenter presenter) {
        if (presenter instanceof PhotoAdapter) {
            Logger.d(">>> (self one)");
            ((PhotoAdapter) presenter).addItemsToDataSet(models);
            mPhotoRCV.smoothScrollToPosition(BaseModelAdapter.POSITION_AT_FIRST);
        }
    }

    @Override
    public void feed(OurModel model, OurPresenter presenter) {
        if (model instanceof Photo && presenter instanceof PhotoAdapter) {
            Logger.d(">>> (override) photo file name:" + ((Photo) model).getName());
            ((PhotoAdapter)presenter).addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_FIRST, (Photo) model);
            mPhotoRCV.smoothScrollToPosition(BaseModelAdapter.POSITION_AT_FIRST);
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
        Logger.d(">>> position of adapter:" + position);
        if (!(model instanceof Photo)) return;
        Photo photo_removed = (Photo) model;
        final String scheme = Uri.parse(photo_removed.getUrl()).getScheme();
        switch (result) {
            case BaseModelAdapter.RESULT_SUCCESS:
                /**
                 *  if try to remove an original photo at URL or a local photo waiting to be uploaded at URI
                 */
                if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("ftp")) { //if URL original
                    Logger.d("... got BaseModelAdapter.RESULT_SUCCESS for URL");
                    photos_original_remove.put(photo_removed.getName(), photo_removed);

                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("... check meta and data set after remove the original" + "\n");
                    if (mPhotoAdapter.getItemCount() <= 0) {
                        stringBuilder.append("adapter has no data" + "\n");
                        int i = 0;
                        for (Map.Entry entry : photos_original_remove.entrySet()) {
                            stringBuilder.append("photos_original_remove at " + i + " has file name:");
                            stringBuilder.append(((Photo)entry.getValue()).getName() + "\n");
                            i++;
                        }
                        Logger.d(stringBuilder.toString());
                    } else {
                        for (int i=0; i<mPhotoAdapter.getItemCount(); i++) {
                            stringBuilder.append("adapter at " + i + " has uri:");
                            stringBuilder.append(mPhotoAdapter.getItemOfDataSet(i).getUrl() + "\n");
                            stringBuilder.append("adapter at " + i + " has file name:");
                            stringBuilder.append(mPhotoAdapter.getItemOfDataSet(i).getName() + "\n");
                        }
                        int i = 0;
                        for (Map.Entry entry : photos_original_remove.entrySet()) {
                            stringBuilder.append("photos_original_remove at " + i + " has file name:");
                            stringBuilder.append(((Photo)entry.getValue()).getName() + "\n");
                            i++;
                        }
                        Logger.d(stringBuilder.toString());
                    }

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
                } else { //if URI local
                    Logger.d("... got BaseModelAdapter.RESULT_SUCCESS for URI");
                    Uri photo_uri_cache_test = null;
                    String photo_file_name_cache_test = null;
                    /**
                     * adapter of edit card fragment displays both of local and original photos
                     * being such circumstance, position of adapter doesn't not match cache's
                     * it's necessary to iterate and find the one of cache corresponding to the one of adapter removed before
                     */
                    for (int i=0; i<photo_uris.size(); i++) {
                        if (photo_uris.get(i).toString().equals(photo_removed.getUrl())) {
                            photo_uri_cache_test = photo_uris.get(i);
                        }
                    }
                    for (int i=0; i<photo_file_names.size(); i++) {
                        if (photo_file_names.get(i).equals(photo_removed.getName())) {
                            photo_file_name_cache_test = photo_file_names.get(i);
                        }
                    }
                    if (photo_uri_cache_test != null && photo_file_name_cache_test != null
                            && photo_uri_cache_test.toString().equals(photo_removed.getUrl())
                            && photo_file_name_cache_test.equals(photo_removed.getName())) { //if position fine
                        /**
                         * adapter of edit card fragment displays both of local and original photos
                         * being such circumstance, position of adapter doesn't not match cache's
                         * it's necessary to iterate and find the one of cache corresponding to the one of adapter removed before
                         */
                        for (int i=0; i<photo_uris.size(); i++) {
                            if (photo_uris.get(i).toString().equals(photo_removed.getUrl())) {
                                photo_uri_cache = photo_uris.remove(i);
                                Logger.d("... now photo_uri_cache:" + photo_uri_cache);
                            }

                        }
                        for (int i=0; i<photo_file_names.size(); i++) {
                            if (photo_file_names.get(i).equals(photo_removed.getName())) {
                                photo_file_name_cache = photo_file_names.remove(i);
                                Logger.d("... now photo_file_name_cache:" + photo_file_name_cache);
                            }
                        }
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
                                + "photo_uri_cache:" + ((photo_uri_cache_test!=null)?photo_uri_cache_test.toString():"null") + "\n"
                                + "photo_file_name_cache:" + ((photo_file_name_cache_test!=null)?photo_file_name_cache_test:"null") + "\n"
                                + "uri of adapter removed:" + photo_removed.getUrl() + "\n"
                                + "file name of adapter removed:" + photo_removed.getName());
                        Snackbar.make(mSnackBtn, "快取抓錯人惹", Snackbar.LENGTH_SHORT).show();
                        mPhotoAdapter.addItemToDataSet(position, photo_removed);
                        return;
                    }

                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("... check meta and data set after remove the local" + "\n");
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
                        }
                        if (photo_uris.size()>0 && photo_file_names.size()>0) {
                            for (int i=0; i<photo_uris.size(); i++) {
                                stringBuilder.append("photo_uris at " + i + " has uri:");
                                stringBuilder.append(photo_uris.get(i).toString() + "\n");
                                stringBuilder.append("photo_file_names at " + i + " has uri:");
                                stringBuilder.append(photo_file_names.get(i) + "\n");
                            }
                        } else {
                            if (photo_uris.size()<=0) stringBuilder.append("photo_uris has no data" + "\n");
                            if (photo_file_names.size()<=0) stringBuilder.append("photo_file_names has no data" + "\n");
                        }
                        Logger.d(stringBuilder.toString());
                    }
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
        Logger.d(">>> position of adapter:" + position);
        if (!(model instanceof Photo)) return;
        Photo photo_recovered = (Photo) model;
        String scheme = Uri.parse(photo_recovered.getUrl()).getScheme();
        switch (result) {
            case BaseModelAdapter.RESULT_SUCCESS:
                if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("ftp")) { //if URL
                    Logger.d("... got BaseModelAdapter.RESULT_SUCCESS for URL");
                    if (photos_original_remove.containsKey(photo_recovered.getName())) {
                        photos_original_remove.remove(photo_recovered.getName());

                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("... check meta and data set after recover the original" + "\n");
                        if (mPhotoAdapter.getItemCount() <= 0) {
                            stringBuilder.append("adapter has no data" + "\n");
                            int i = 0;
                            for (Map.Entry entry : photos_original_remove.entrySet()) {
                                stringBuilder.append("photos_original_remove at " + i + " has file name:");
                                stringBuilder.append(((Photo)entry.getValue()).getName() + "\n");
                                i++;
                            }
                            Logger.d(stringBuilder.toString());
                        } else {
                            for (int i=0; i<mPhotoAdapter.getItemCount(); i++) {
                                stringBuilder.append("adapter at " + i + " has uri:");
                                stringBuilder.append(mPhotoAdapter.getItemOfDataSet(i).getUrl() + "\n");
                                stringBuilder.append("adapter at " + i + " has file name:");
                                stringBuilder.append(mPhotoAdapter.getItemOfDataSet(i).getName() + "\n");
                            }
                            int i = 0;
                            for (Map.Entry entry : photos_original_remove.entrySet()) {
                                stringBuilder.append("photos_original_remove at " + i + " has file name:");
                                stringBuilder.append(((Photo)entry.getValue()).getName() + "\n");
                                i++;
                            }
                            Logger.d(stringBuilder.toString());
                        }

                        Snackbar.make(mSnackBtn, "已還", Snackbar.LENGTH_SHORT).show();
                    } else {
                        Snackbar.make(mSnackBtn, "沒這張圖吧", Snackbar.LENGTH_SHORT).show();
                    }
                } else { //if URI
                    Logger.d("... got BaseModelAdapter.RESULT_SUCCESS for URI");
                    /**
                     * adapter of edit card fragment displays both of local and original photos
                     * being such circumstance, even position of adapter doesn't not match cache's
                     * , it doesn't matter for recovering if cache is correct
                     * cache refers to a specific position same as item user removed then recovered on adapter before
                     */
                    if (photo_uri_cache.toString().equals(photo_recovered.getUrl())
                            && photo_file_name_cache.equals(photo_recovered.getName())) { //if position fine
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
                    stringBuilder.append("... check meta and data set after recover the local" + "\n");
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
                        for (int i = 0; i < mPhotoAdapter.getItemCount(); i++) {
                            stringBuilder.append("adapter at " + i + " has uri:");
                            stringBuilder.append(mPhotoAdapter.getItemOfDataSet(i).getUrl() + "\n");
                            stringBuilder.append("adapter at " + i + " has file name:");
                            stringBuilder.append(mPhotoAdapter.getItemOfDataSet(i).getName() + "\n");
                        }
                        if (photo_uris.size() > 0 && photo_file_names.size() > 0) {
                            for (int i = 0; i < photo_uris.size(); i++) {
                                stringBuilder.append("photo_uris at " + i + " has uri:");
                                stringBuilder.append(photo_uris.get(i).toString() + "\n");
                                stringBuilder.append("photo_file_names at " + i + " has uri:");
                                stringBuilder.append(photo_file_names.get(i) + "\n");
                            }
                        } else {
                            if (photo_uris.size() <= 0)
                                stringBuilder.append("photo_uris has no data" + "\n");
                            if (photo_file_names.size() <= 0)
                                stringBuilder.append("photo_file_names has no data" + "\n");
                        }
                        Logger.d(stringBuilder.toString());

                    }
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

    private static final class Lock {}

    class EditCardTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            mPostBtn.setEnabled(true);
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s.toString().trim().length() > 0) {
                mPostBtn.setEnabled(true);
            } else {
                mPostBtn.setEnabled(false);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            mPostBtn.setEnabled(true);
        }
    }

    /**
     * cardId is going to be assigned by CardManager with key provided by Firebase
     * articlePhoto is going to be assigned by CardManager with download urls
     * distance is going to be assigned by Google Map
     */
    class PostBtnOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Logger.d(">>>");
            long date    = (new Date()).getTime();
            String articleTitle     = mCard.getArticleTitle();
            String articleContent   = mCard.getArticleContent();
            v.setClickable(false); //blocks immediate repeating
            showProgress("努力上傳中...");
            if (!mTitle.getText().toString().equals("")) articleTitle = mTitle.getText().toString();
            if (!mContent.getText().toString().equals("")) articleContent = mContent.getText().toString();
            if (photo_uris.size() != photo_file_names.size()) {
                Logger.e("!!! photo_uris.size() != photo_file_names.size()");
                hideProgress();
                return;
            }
            if (upload_photo_count != photo_uris.size()) {
                Logger.e("!!! upload_photo_count != photo_uris.size()");
                hideProgress();
                return;
            }
            if (upload_photo_count != photo_file_names.size()) {
                Logger.e("!!! upload_photo_count != photo_file_names.size()");
                hideProgress();
                return;
            }
            if (photos_original_remove.size() > 0) { //if there're original photos recorded in photos_original_remove waiting to be removed
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("... going to remove original photo files:");
                for (Map.Entry entry : photos_original_remove.entrySet()) {
                    stringBuilder.append(((Photo)entry.getValue()).getName() + "\n");
                }
                Logger.d(stringBuilder.toString());
            }
            mCard.setArticleTitle(articleTitle);
            mCard.setArticleContent(articleContent);
            mCard.setDate(date);
            /**
             * removes original photos if need be
             */
            if (!photos_original_remove.isEmpty()) { //if there're original photos waiting to be removed
                /**
                 * flag used to make sure calling to start post task once and for all
                 */
                final boolean is_post_task_start[] = {false};
                /**
                 * runnable lives in thread POST
                 */
                final Runnable post_task = new Runnable() {
                    @Override
                    public void run() {
                        synchronized (lock) { //becomes the owner of the object's monitor
                            Logger.d("... post task is going to wait");
                            try {
                                lock.wait(); //relinquishes the ownership of the object's monitor
                            } catch (InterruptedException e) {
                                Logger.d("... got InterruptedException and proceed");
                            }
                            Logger.d("... post task proceed");
                            /**
                             * updates model and uploads local photo if need be
                             */
                            try {
                                post(mCard);
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                };
                /**
                 * remover lives in thread REMOVE
                 */
                final CardRemover remover = new CardRemover() {
                    @Override
                    public void onFind(boolean is_find) {
                        super.onFind(is_find);
                        if (is_find) {
                            /**
                             * starts thread POST
                             */
                            if (!(is_post_task_start[0])) {
                                Logger.d("... found and starts post task");
                                ExecutorHelper.getInstance().execute(post_task, ExecutorHelper.TYPE_MAIN);
                                is_post_task_start[0] = true;
                            } else {
                                Logger.e("!!! found but no more post task");
                            }
                        } else {
                            Logger.e("!!! can't find original photo wanted to remove within card and return");
                            hideProgress();
                        }
                    }

                    @Override
                    public void onSuccess(int process, int success_index) {
                        if (process == CardManager.CARD_REMOVE_PROCESS_DONE) {
                            hideProgress();
                            showProgress("移除圖片完成，準備更新貼文");
                            synchronized (lock) { //becomes the owner of the object's monitor
                                lock.notify(); //relinquishes the ownership of the object's monitor
                            }
                        } else {
                            hideProgress();
                            showProgress("正在移除圖片:" + process + "%");
                        }
                    }

                    @Override
                    public void onFailure(int fail_index) {
                        Logger.e("!!! fail index:" + fail_index);
                        synchronized (lock) { //becomes the owner of the object's monitor
                            lock.notify(); //relinquishes the ownership of the object's monitor
                        }
                        hideProgress();
                    }
                };
                /**
                 * starts thread REMOVE
                 *
                 * CardRemover#onFind() starts post task with wait() if CardManager#removeManyPhotosFromFirebase found matches
                 * and notifies post task to proceed to post if remove task is completed
                 * therefore, remover has to run in another thread different from where post task is living and waiting
                 */
                ExecutorHelper.getInstance().execute(new Runnable() {
                    @Override
                    public void run() {
                        removeOriginalPhotos(mCard, photos_original_remove, remover);
                    }}, ExecutorHelper.TYPE_MAIN);
            } else { //if no original photos waiting to be removed
                /**
                 * updates model and uploads local photo if need be
                 */
                try {
                    post(mCard);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class AddPhotoBtnOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            pickImage();
        }
    }

    class AddPhotoImageOnClickListener implements PhotoAdapter.AddPhotoImageOnClickListener {
        @Override
        public void onClick(View v) {
            pickImage();
        }
    }

    class CancleBtnOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Message msg = new Message();
            msg.what = HANDLER_MSG_WHAT_CANCEL;
            mHandler.sendMessage(msg);
            upload_photo_count = 0;
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
            } else {
                hideProgress();
                showProgress("正在上傳圖片:" + process + "%");
            }
        }

        @Override
        public void onFailure(int fail_index) {
            Message msg = new Message();
            msg.what = HANDLER_MSG_WHAT_UPLOAD_FAILURE;
            mHandler.sendMessage(msg);
        }
    }

    class CardRemover implements CardManager.Remover {
        @Override
        public void onFind(boolean isFound) {

        }

        @Override
        public void onSuccess(int process, int success_index) {

        }

        @Override
        public void onSuccess() {
            Logger.e("!!! no process and success_index one is not recommended here");
        }

        @Override
        public void onFailure(int fail_index) {
            Logger.e("!!! no success_index one is not recommended here");
        }

        @Override
        public void onFailure() {

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
