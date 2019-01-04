package com.ours.yours.app.manager;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.UploadTask;
import com.orhanobut.logger.Logger;
import com.ours.yours.app.entity.Card;
import com.ours.yours.app.entity.CardList;
import com.ours.yours.app.firebase.FirebaseDatabaseHelper;
import com.ours.yours.app.firebase.FirebaseDatabaseHelper.OnDeleteStorageFailure;
import com.ours.yours.app.worker.ExecutorHelper;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CardManager extends BaseManager {
    public final static int CARD_UPLOAD_PROCESS_DONE = 100;
    public final static int CARD_REMOVE_PROCESS_DONE = 100;
    public final static int ERROR_BASE = -10;
    public final static int UPLOAD_PHOTO_ERROR = ERROR_BASE - 1;
    public final static int REMOVE_PHOTO_ERROR = ERROR_BASE - 2;
    public final static int INDEX_NO_SUCH_THING = -1;
    private static CardManager mInstance;
    private Context mContext;

    private final Object lock = new Lock();

    /**
     * inner class access
     */
    private static int count_of_success_upload = 0;
    private static int count_of_delete_photo = 0;

    private CardManager(Context context) throws ClassNotFoundException {
        mContext = context;
        initFirebase();
    }

    /**
     * gets a singleton object managing all methods on card to use firebase
     * should be called before other methods
     */
    public static CardManager getInstance(Context context) throws ClassNotFoundException {
        //Logger.d(">>>");
        if (mInstance == null ) {
            Logger.d("... going to create the singleton instance");
            mInstance = new CardManager(context);
        }
        count_of_success_upload = 0;
        count_of_delete_photo = 0;
        return mInstance;
    }

    private void initFirebase() throws ClassNotFoundException {
        FirebaseDatabaseHelper.getInstance().pushDatabaseAndGetAuoGeneratedKey(Class.forName("com.ours.yours.app.entity.Card"));
    }

    /**
     * retrieves an auto-generate child location
     */
    public boolean pushAndGetAuoGeneratedCardId(Card card) {
        Logger.d(">>>");
        if (card == null) {
            Logger.e("!!! card is null");
            return false;
        }
        card.setCardId(FirebaseDatabaseHelper.getInstance().pushDatabaseAndGetAuoGeneratedKey(Card.class));
        if (!card.getCardId().equals(Card.DEFAULT_CARD_ID)) {
            Logger.d("... got card id:" + card.getCardId());
            return true;
        } else {
            Logger.e("!! failed to get card id");
            return false;
        }
    }

    /**
     * uploads an image to firebase storage to obtain url of an image through attaching listener
     * which is asynchronous to this method
     *
     * we should post a card up after upload listener has been triggered by firebase which offers a well done url of the image
     * , then let uploader do what it's been designed in response to user/UI that completes such asynchronous posting task
     * which means this method should be called before postupCardOnFirebase()
     */
    private void uploadOnePhotoOnFirebase(final Card card, final Uri image, final Uploader uploader, final int index) {
        Logger.d(">>> index:" + index);
        if (image == null) {
            Logger.e("!!! image is null");
            uploader.onFailure(UPLOAD_PHOTO_ERROR);
            return;
        }
        if (card == null) {
            Logger.e("!!! card is null");
            uploader.onFailure(UPLOAD_PHOTO_ERROR);
            return;
        }
        if (card.getCardId() == null) {
            Logger.e("!!! card id is null");
            uploader.onFailure(UPLOAD_PHOTO_ERROR);
            return;
        }
        FirebaseDatabaseHelper.getInstance().uploadUriToStorage(image, card, new FirebaseDatabaseHelper.OnUploadStorageSuccess() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                FirebaseDatabaseHelper.getInstance().getDownloadUrl(image, Card.class, card.getCardId()
                    , new FirebaseDatabaseHelper.OnGetDownloadUrlUploadStorageSuccess() {
                        @Override
                        public void onSuccess(Uri uri) {
                            count_of_success_upload++;
                            Logger.d("... succeeded to upload regarding index:" + index + " and got download url" + String.valueOf(uri));
                            card.addArticlePhoto(String.valueOf(uri));
                            Logger.d("... now ArticlePhoto[" +  (card.getArticlePhoto().size()-1) + "]:"
                                    + card.getArticlePhoto().get(card.getArticlePhoto().size()-1) + " regarding file name:"
                                    + card.getArticlePhotoFileName().get(index));
                            int process = (int) (((float)CARD_UPLOAD_PROCESS_DONE)*((float)(count_of_success_upload)/card.getArticleCount()));
                            Logger.d("... going to notice uploader process(" + process + ") and index(" + index + ")");
                            uploader.onSuccess(process, index);
                            synchronized (lock) { //becomes the owner of the object's monitor
                                lock.notify(); //relinquishes the ownership of the object's monitor
                            }
                        }
                    }
                    , new FirebaseDatabaseHelper.OnGetDownloadUrlUploadStorageFailure() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Logger.e("!!! exception of getting download url:" + e.getMessage());
                            FirebaseDatabaseHelper.getInstance().deleteOneFileFromStorage(card
                                    , card.getArticlePhotoFileName().get(index)
                                    , new FirebaseDatabaseHelper.OnDeleteStorageSuccess() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            Logger.d("... deleted file:" + card.getArticlePhotoFileName().get(index));
                                        }
                                    }
                                    , new FirebaseDatabaseHelper.OnDeleteStorageFailure() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Logger.e("!!! failed to delete file:" + card.getArticlePhotoFileName().get(index));
                                        }
                                    });
                            uploader.onFailure(index);
                            synchronized (lock) { //becomes the owner of the object's monitor
                                lock.notifyAll(); //relinquishes the ownership of the object's monitor
                            }
                        }
                    });
            }
        }, new FirebaseDatabaseHelper.OnUploadStorageFailure() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Logger.e("!!! exception of uploading:" + e.getMessage());
                uploader.onFailure(index);
                synchronized (lock) { //becomes the owner of the object's monitor
                    lock.notifyAll(); //relinquishes the ownership of the object's monitor
                }
            }
        });
    }

    /**
     * uploads each image to firebase storage to obtain url of each image through attaching listener
     * which is asynchronous to this method
     *
     * we should post a card up after upload listener has been triggered by firebase which offers a well done url of the image
     * , then let uploader do what it's been designed in response to user/UI that completes such asynchronous posting task
     * which means this method should be called before postupCardOnFirebase()
     *
     * doing factorial way, LIFO from uri images into article photo list
     * uri       01 02 03 ----LIFO--> ArticlePhoto         03 02 01
     * file name 01 02 03 --REVERSE-> ArticlePhotoFileName 03 02 01
     */
    public void uploadManyPhotosOnFirebase(final Card card, final List<Uri> images, final Uploader uploader) {
        if (images == null) {
            Logger.e("!!! images is null");
            uploader.onFailure(UPLOAD_PHOTO_ERROR);
            return;
        }
        Logger.d(">>> size of images:" + images.size());
        if (card == null) {
            Logger.e("!!! card is null");
            uploader.onFailure(UPLOAD_PHOTO_ERROR);
            return;
        }
        if (card.getCardId() == null) {
            Logger.e("!!! card id is null");
            uploader.onFailure(UPLOAD_PHOTO_ERROR);
            return;
        }
        final int index_of_tail = images.size() - 1;
        final Uri tail = images.remove(index_of_tail);
        Logger.d("... going to upload the index " + index_of_tail +" of uris regarding file name:" + card.getArticlePhotoFileName().get(index_of_tail));
        ExecutorHelper.getInstance()
            .execute(new Runnable() {
                 @Override
                 public void run() {
                     uploadOnePhotoOnFirebase(card, tail, uploader, index_of_tail);
                 }
             }, ExecutorHelper.TYPE_MAIN);
        ExecutorHelper.getInstance()
            .execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (lock) { //becomes the owner of the object's monitor
                            Logger.d("... going to wait");
                            lock.wait(); //relinquishes the ownership of the object's monitor
                        }
                    } catch (InterruptedException e) {
                        Logger.d("... got InterruptedException and proceed");
                    }
                    Logger.d("... proceed");
                    if (!images.isEmpty()) {
                        Logger.d("... going to uploadPhotosOnFirebase() again");
                        uploadManyPhotosOnFirebase(card, images, uploader);
                    }
                }
            }, ExecutorHelper.TYPE_MAIN);
    }

    /**
     * writes contents of card to firebase database which is asynchronous to this method
     *
     * we should post a card up after upload listener has been triggered by firebase which offers a well done url of the image
     * , then let poster do what it's been designed in response to user/UI that completes such asynchronous posting task
     * which means this method should be called before uploadPhotosOnFirebase()
     */
    public void postupCardOnFirebase(final Card card, final Poster poster) {
        Logger.d(">>> card id" + card.getCardId());
        if (card == null) {
            Logger.e("!!! card is null");
            return;
        }
        if (card.getCardId() == null) {
            Logger.e("!!! card id is null");
            return;
        }
        if (card.getArticleCount() != card.getArticlePhotoFileName().size()) {
            Logger.e("!!! card count is not equal to length of image file names");
            poster.onFailure();
            return;
        }
        FirebaseDatabaseHelper.getInstance().writeModelToDatabase(card, new FirebaseDatabaseHelper.OnWriteDatabaseCompleteListener() {
            @Override
            public void onComplete(DatabaseError error, DatabaseReference ref) {
                if (error == null) {
                    poster.onSuccess();
                } else {
                    Logger.e("!!! error:" + error.getMessage());
                    poster.onFailure();
                }
            }
        });
    }

    /**
     * deletes values about specified card storing in firebase database and storage
     *
     * we should remove a card from firebase realtime database after delete listener has been triggered by firebase storage
     * , then let remover do what it's been designed in response to user/UI that completes such asynchronous removing task
     */
    public void tearoffCardFromFirebase(final Card card, final Remover remover) {
        Logger.d(">>>");
        if (card == null) {
            Logger.e("!!! card is null");
            return;
        }
        if (card.getCardId() == null) {
            Logger.e("!!! card id is null");
            return;
        }
        if (card.getArticleCount() > 0) {
            FirebaseDatabaseHelper.getInstance().deleteManyFilesFromStorage(card, new FirebaseDatabaseHelper.OnDeleteStorageSuccess() {
                @Override
                public void onSuccess(Void aVoid) {
                    count_of_delete_photo++;
                    Logger.d("... tried to delete " + count_of_delete_photo + " files from storage");
                    if (count_of_delete_photo == card.getArticleCount()) {
                        FirebaseDatabaseHelper.getInstance().deleteModelFromDatabase(Card.class, FirebaseDatabaseHelper.getInstance().getUserId()
                            , card.getCardId(), new FirebaseDatabaseHelper.OnDeleteDatabaseCompleteListener() {
                                @Override
                                public void onComplete(DatabaseError error, DatabaseReference ref) {
                                    if (error == null) {
                                        remover.onSuccess();
                                    } else {
                                        Logger.e("!!! delete a model from database error:" + error.getMessage());
                                        remover.onFailure();
                                    }
                                }
                            });
                    } else {
                        Logger.d("... do nothing until firebase delete all of photos belonging to this card");
                    }
                }
            }, new OnDeleteStorageFailure() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Logger.e("!!! delete many files from storage fail:" + e.getMessage());
                    if (((StorageException) e).getErrorCode() == StorageException.ERROR_OBJECT_NOT_FOUND) {
                        count_of_delete_photo++;
                        Logger.e("!!! no object to delete for the " + count_of_delete_photo + "th file but go ahead to try to delete other");
                        if (count_of_delete_photo == card.getArticleCount()) {
                            FirebaseDatabaseHelper.getInstance().deleteModelFromDatabase(Card.class, FirebaseDatabaseHelper.getInstance().getUserId()
                                    , card.getCardId(), new FirebaseDatabaseHelper.OnDeleteDatabaseCompleteListener() {
                                        @Override
                                        public void onComplete(DatabaseError error, DatabaseReference ref) {
                                            if (error == null) {
                                                remover.onSuccess();
                                            } else {
                                                Logger.e("!!! delete a model from database error:" + error.getMessage());
                                                remover.onFailure();
                                            }
                                        }
                                    });
                        }
                    } else {
                        remover.onFailure();
                    }
                }
            });
        } else {
            FirebaseDatabaseHelper.getInstance().deleteModelFromDatabase(Card.class, FirebaseDatabaseHelper.getInstance().getUserId()
                , card.getCardId(), new FirebaseDatabaseHelper.OnDeleteDatabaseCompleteListener() {
                    @Override
                    public void onComplete(DatabaseError error, DatabaseReference ref) {
                        if (error == null) {
                            remover.onSuccess();
                        } else {
                            Logger.e("!!! error:" + error.getMessage());
                            remover.onFailure();
                        }
                    }
                });
        }

    }

    /**
     * removes each image from firebase storage through attaching listener which is asynchronous to this method
     *
     * Before posting a card being edited up, manager removes photos intent to be deleted from storage
     * and updates the card referring to the edited one
     * , then let remover do what it's been designed in response to user/UI that completes such asynchronous posting task
     * which means this method should be called before postupCardOnFirebase() if need be
     */
    private void removeOnePhotoFromFirebase(final Card card, final int index_of_card, final String target, final int count_of_targets, final Remover remover) {
        Logger.d(">>> card index:" + index_of_card + ", count_of_targets:" + count_of_targets);
        if (target == null) {
            Logger.e("!!! target is null");
            remover.onFailure(REMOVE_PHOTO_ERROR);
            return;
        }
        if (card == null) {
            Logger.e("!!! card is null");
            remover.onFailure(REMOVE_PHOTO_ERROR);
            return;
        }
        if (card.getCardId() == null) {
            Logger.e("!!! card id is null");
            remover.onFailure(REMOVE_PHOTO_ERROR);
            return;
        }
        FirebaseDatabaseHelper.getInstance().deleteOneFileFromStorage(card, target, new FirebaseDatabaseHelper.OnDeleteStorageSuccess() {
            @Override
            public void onSuccess(Void aVoid) {
                int process = (int) (((float)CARD_REMOVE_PROCESS_DONE)*((float)(1)/count_of_targets));
                String photo_name_removed = card.getArticlePhotoFileName().remove(index_of_card);
                String photo_url_removed = card.getArticlePhoto().remove(index_of_card);
                card.setArticleCount(card.getArticleCount()-1);
                Logger.d("... succeeded to remove file name:" + photo_name_removed + " and url:" + photo_url_removed);
                remover.onSuccess(process, index_of_card);
                synchronized (lock) { //becomes the owner of the object's monitor, mutex lock and unlock
                    lock.notify(); //relinquishes the ownership of the object's monitor, mutex cond signal
                }
            }
        }, new FirebaseDatabaseHelper.OnDeleteStorageFailure() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Logger.e("!!! exception of getting download url:" + e.getMessage());
                remover.onFailure(index_of_card);
                synchronized (lock) { //becomes the owner of the object's monitor
                    lock.notifyAll(); //relinquishes the ownership of the object's monitor
                }
            }
        });
    }

    /**
     * removes each image from firebase storage through attaching listener which is asynchronous to this method
     *
     * Before posting a card being edited up, manager removes photos intent to be deleted from storage
     * and updates the card referring to the edited one
     * , then let remover do what it's been designed in response to user/UI that completes such asynchronous posting task
     * which means this method should be called before postupCardOnFirebase() if need be
     *
     * doing factorial way, LIFO for files got out of article photo list
     */
    public void removeManyPhotosFromFirebase(final Card card, final List<String> targets, final Remover remover) {
        if (targets == null) {
            Logger.e("!!! targets is null");
            remover.onFailure();
            return;
        }
        Logger.d(">>> counts of files to be removed:" + targets.size());
        if (card == null) {
            Logger.e("!!! card is null");
            remover.onFailure(REMOVE_PHOTO_ERROR);
            return;
        }
        if (card.getCardId() == null) {
            Logger.e("!!! card id is null");
            remover.onFailure(REMOVE_PHOTO_ERROR);
            return;
        }
        int index_of_card = INDEX_NO_SUCH_THING;
        final int index_of_tail = targets.size()-1;
        final String target = targets.remove(index_of_tail);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("for target:" + target + " at index of targets:" + index_of_tail + "\n");
        for (int i=0; i<card.getArticleCount(); i++) { //finding match
            String file_name = card.getArticlePhotoFileName().get(i);
            stringBuilder.append("card at " + i + ":" + file_name);
            if (target.equalsIgnoreCase(file_name)) {
                index_of_card = i;
                stringBuilder.append(" match" + "\n");
            } else {
                stringBuilder.append(" not match" + "\n");
            }
        }
        if (index_of_card != INDEX_NO_SUCH_THING) {
            stringBuilder.append("... index_of_card:" + index_of_card + " matches target at index:" + index_of_tail + "\n");
        }
        Logger.d(stringBuilder.toString());

        if (index_of_card != INDEX_NO_SUCH_THING) {
            final int index = index_of_card;
            remover.onFind(true);
            ExecutorHelper.getInstance().execute(new Runnable() {
                 @Override
                 public void run() {
                     removeOnePhotoFromFirebase(card, index, target, index_of_tail+1, remover);
                     try {
                         synchronized (lock) { //becomes the owner of the object's monitor, mutex lock and unlock
                             Logger.d("... going to wait");
                             lock.wait(); //relinquishes the ownership of the object's monitor, mutex cond wait
                         }
                     } catch (InterruptedException e) {
                         Logger.d("... got InterruptedException and proceed");
                     }
                     Logger.d("... proceed");
                     if (!targets.isEmpty()) {
                         Logger.d("... going to removeManyPhotosFromFirebase() again");
                         removeManyPhotosFromFirebase(card, targets, remover);
                     }
                 }
                }, ExecutorHelper.TYPE_MAIN);
        } else {
            Logger.e("!!! found nothing matching targets");
            remover.onFind(false);
        }
    }

    /**
     * attaches value event listener to obtain values of specified card
     * to obtain value through attaching listener is asynchronous to this method
     * a subscriber implemented by UI class is called by listener for reacting simultaneously on UI when listener is triggered by firebase
     */
    public void subscribeCardInFirebase(final String author_id, final String card_id, final Subscriber subscriber) {
        Logger.d(">>>");
        if (author_id == null || author_id.equals("")) {
            Logger.e("!!! author id is null or empty");
            return;
        }
        if (card_id == null || card_id.equals("")) {
            Logger.e("!!! card is null or empty");
            return;
        }
        FirebaseDatabaseHelper.OnReadDatabaseValueEventListener listener = new FirebaseDatabaseHelper.OnReadDatabaseValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                /**
                 * $ROOT/catalog/user_id/CardId/CardId
                 * $ROOT/catalog/user_id/CardId/ProfilePhoto
                 * $ROOT/catalog/user_id/CardId/ProfileTitle
                 * $ROOT/catalog/user_id/CardId/ProfileName
                 * $ROOT/catalog/user_id/CardId/ProfileID
                 * $ROOT/catalog/user_id/CardId/ArticleTitle
                 * $ROOT/catalog/user_id/CardId/ArticleContent
                 * $ROOT/catalog/user_id/CardId/ArticleCount
                 * $ROOT/catalog/user_id/CardId/ArticlePhoto
                 * $ROOT/catalog/user_id/CardId/ArticlePhotoFileName
                 * $ROOT/catalog/user_id/CardId/Distance
                 * $ROOT/catalog/user_id/CardId/Date
                 *                      ^[key] :[value]
                 */
                Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue(); //map represents a pojo living in JSON tree
                if (!isCard(map)) {
                    Logger.e("!!! dataSnapshot is not card");
                    return;
                }
                Card card = dataSnapshot.getValue(Card.class);
                if (card == null) {
                    Logger.e("!!! card is null");
                    return;
                }
                else card.printCardData();
                subscriber.onUpdate(card);
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                subscriber.onError(databaseError.getMessage());
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }
        };
        registerValueEventListeners(mContext, listener);
        FirebaseDatabaseHelper.getInstance().readModelFromDatabase(Card.class, author_id, card_id, listener);
    }

    /**
     * attaches value event listener to obtain values of cards of specified user
     * to obtain value through attaching listener is asynchronous to this method
     * a subscriber implemented by UI class is called by listener for reacting simultaneously on UI when listener is triggered by firebase
     */
    public void subscribeCardsOfUserInFirebase(final String author_id, final Subscriber subscriber) {
        Logger.d(">>>");
        if (author_id == null || author_id.equals("")) {
            Logger.e("!!! author id is null or empty");
            return;
        }
        FirebaseDatabaseHelper.OnReadDatabaseValueEventListener listener = new FirebaseDatabaseHelper.OnReadDatabaseValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                CardList cards = new CardList();
                List<Card> cards_entity = new LinkedList<>();
                /**
                 * $ROOT/catalog/user_id/CardId/CardId
                 * $ROOT/catalog/user_id/CardId/ProfilePhoto
                 * $ROOT/catalog/user_id/CardId/ProfileTitle
                 * $ROOT/catalog/user_id/CardId/ProfileName
                 * $ROOT/catalog/user_id/CardId/ProfileID
                 * $ROOT/catalog/user_id/CardId/ArticleTitle
                 * $ROOT/catalog/user_id/CardId/ArticleContent
                 * $ROOT/catalog/user_id/CardId/ArticleCount
                 * $ROOT/catalog/user_id/CardId/ArticlePhoto
                 * $ROOT/catalog/user_id/CardId/ArticlePhotoFileName
                 * $ROOT/catalog/user_id/CardId/Distance
                 * $ROOT/catalog/user_id/CardId/Date
                 *              ^[key]  :[value]
                 * after get children   ^[key] :[value]
                 */
                for (DataSnapshot snapShotOfCardId : dataSnapshot.getChildren()) {
                    Map<String, Object> map = (Map<String, Object>) snapShotOfCardId.getValue();
                    if (!isCard(map)) {
                        Logger.e("!!! dataSnapshot is not card");
                        continue;
                    }
                    Card card = snapShotOfCardId.getValue(Card.class);
                    if (card == null) {
                        Logger.e("!!! card is null");
                        continue;
                    }
                    else card.printCardData();
                    if (cards.getLastDate() <= card.getDate() || cards.getLastDate() == CardList.DEFAULT_LAST_DATE) {
                        cards.setLastDate(card.getDate());
                        Logger.d("... now earliest date:" + new Date(cards.getLastDate()).toString());
                    }
                    cards_entity.add(card);
                }
                Collections.sort(cards_entity, new Comparator<Card>() {
                    @Override
                    public int compare(Card o1, Card o2) {
                        return ((Long)o2.getDate()).compareTo(o1.getDate());
                    }
                });
                cards.setCards(cards_entity);
                subscriber.onClone(cards);
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                subscriber.onError(databaseError.getMessage());
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }
        };
        registerValueEventListeners(mContext, listener);
        FirebaseDatabaseHelper.getInstance().readListFromDatabase(Card.class, author_id, listener);
    }

    /**
     * attaches value event listener to obtain values of cards in a specific period
     * to obtain value through attaching listener is asynchronous to this method
     * a subscriber implemented by UI class is called by listener for reacting simultaneously on UI when listener is triggered by firebase
     */
    public void subscribeCardsOfUserInFirebasePeriod(String author_id, final Subscriber subscriber, long start, long end) {
        FirebaseDatabaseHelper.OnReadDatabaseChildEventListener listener = new FirebaseDatabaseHelper.OnReadDatabaseChildEventListener() {
            /**
             * this would be called many times while getting a child after querying
             */
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                Logger.d(">>> sibling location:" + s + ", and value:" + dataSnapshot.toString());
                /**
                 * $ROOT/catalog/user_id/CardId/CardId
                 * $ROOT/catalog/user_id/CardId/ProfilePhoto
                 * $ROOT/catalog/user_id/CardId/ProfileTitle
                 * $ROOT/catalog/user_id/CardId/ProfileName
                 * $ROOT/catalog/user_id/CardId/ProfileID
                 * $ROOT/catalog/user_id/CardId/ArticleTitle
                 * $ROOT/catalog/user_id/CardId/ArticleContent
                 * $ROOT/catalog/user_id/CardId/ArticleCount
                 * $ROOT/catalog/user_id/CardId/ArticlePhoto
                 * $ROOT/catalog/user_id/CardId/ArticlePhotoFileName
                 * $ROOT/catalog/user_id/CardId/Distance
                 * $ROOT/catalog/user_id/CardId/Date
                 *                      ^[key] :[value]
                 */
                Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                if (!isCard(map)) {
                    Logger.e("!!! dataSnapshot is not card");
                    return;
                }
                Card card = dataSnapshot.getValue(Card.class);
                if (card == null) {
                    Logger.e("!!! card is null");
                    return;
                }
                else card.printCardData();
                subscriber.onUpdate(card);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                Logger.d(">>> sibling location:" + s);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                Logger.d(">>>");
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                Logger.d(">>> sibling location:" + s);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Logger.e("!!! database error:" + databaseError.getMessage());
            }

            @Override
            public void onGetParentIdsAndCounts(Map<String, Long> parentIdsAndSiblingCounts) {

            }
        };
        registerChildEventListeners(mContext, listener);
        FirebaseDatabaseHelper.getInstance().readListFromDatabase(Card.class, author_id, listener, start, end);
    }

    /**
     * attaches value event listener to obtain values of cards
     * to obtain value through attaching listener is asynchronous to this method
     * a subscriber implemented by UI class is called by listener for reacting simultaneously on UI when listener is triggered by firebase
     */
    public void subscribeAllCardsInFirebase(final Subscriber subscriber) {
        Logger.d(">>>");
        FirebaseDatabaseHelper.OnReadDatabaseValueEventListener listener = new FirebaseDatabaseHelper.OnReadDatabaseValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Logger.d("... user count:" + dataSnapshot.getChildrenCount());
                CardList cards = new CardList();
                List<Card> cards_entity = new LinkedList<>();
                /**
                 * $ROOT/catalog/user_id/CardId/CardId
                 * $ROOT/catalog/user_id/CardId/ProfilePhoto
                 * $ROOT/catalog/user_id/CardId/ProfileTitle
                 * $ROOT/catalog/user_id/CardId/ProfileName
                 * $ROOT/catalog/user_id/CardId/ProfileID
                 * $ROOT/catalog/user_id/CardId/ArticleTitle
                 * $ROOT/catalog/user_id/CardId/ArticleContent
                 * $ROOT/catalog/user_id/CardId/ArticleCount
                 * $ROOT/catalog/user_id/CardId/ArticlePhoto
                 * $ROOT/catalog/user_id/CardId/ArticlePhotoFileName
                 * $ROOT/catalog/user_id/CardId/Distance
                 * $ROOT/catalog/user_id/CardId/Date
                 *      ^[key]  :[value]
                 *              ^[key]  :[value]
                 *                      ^[key] :[value]
                 */
                for (DataSnapshot snapShotOfUserId : dataSnapshot.getChildren()) {
                    Logger.d("... user(" + snapShotOfUserId.getKey() + ") post count:" + snapShotOfUserId.getChildrenCount());
                    for (DataSnapshot snapShotOfCardId : snapShotOfUserId.getChildren()) {
                        Map<String, Object> map = (Map<String, Object>) snapShotOfCardId.getValue();
                        if (!isCard(map)) {
                            Logger.e("!!! dataSnapshot is not card");
                            continue;
                        }
                        Card card = snapShotOfCardId.getValue(Card.class);
                        if (card == null) {
                            Logger.e("!!! card is null");
                            continue;
                        }
                        else card.printCardData();
                        if (cards.getLastDate() > card.getDate() || cards.getLastDate() == CardList.DEFAULT_LAST_DATE) {
                            cards.setLastDate(card.getDate());
                            Logger.d("... now earliest date of card list:" + new Date(cards.getLastDate()).toString());
                        }
                        cards_entity.add(card);
                    }
                }
                Collections.sort(cards_entity, new Comparator<Card>() {
                    @Override
                    public int compare(Card o1, Card o2) {
                        return ((Long)o2.getDate()).compareTo(o1.getDate());
                    }
                });
                cards.setCards(cards_entity);
                subscriber.onClone(cards);
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                subscriber.onError(databaseError.getMessage());
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }
        };
        registerValueEventListeners(mContext, listener);
        FirebaseDatabaseHelper.getInstance().readCatalogFromDatabase(Card.class, listener);
    }

    /**
     * attaches value event listener to obtain values of cards in a specific period
     * to obtain value through attaching listener is asynchronous to this method
     * a subscriber implemented by UI class is called by listener for reacting simultaneously on UI when listener is triggered by firebase
     *
     * this method should be called after CardManager#subscribeAuthorIdsAndCardCounts() to make sure how many cards should be read this time
     */
    public void subscribeAllCardsInFirebasePeriod(final Subscriber subscriber, long start, long end) {
        Logger.d(">>>");
        FirebaseDatabaseHelper.OnReadDatabaseChildEventListener listener = new FirebaseDatabaseHelper.OnReadDatabaseChildEventListener() {
            /**
             * this would be called many times while getting a child after querying
             */
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                //Logger.d(">>> sibling location:" + s + ", and value:" + dataSnapshot.toString());
                /**
                 * $ROOT/catalog/user_id/CardId/CardId
                 * $ROOT/catalog/user_id/CardId/ProfilePhoto
                 * $ROOT/catalog/user_id/CardId/ProfileTitle
                 * $ROOT/catalog/user_id/CardId/ProfileName
                 * $ROOT/catalog/user_id/CardId/ProfileID
                 * $ROOT/catalog/user_id/CardId/ArticleTitle
                 * $ROOT/catalog/user_id/CardId/ArticleContent
                 * $ROOT/catalog/user_id/CardId/ArticleCount
                 * $ROOT/catalog/user_id/CardId/ArticlePhoto
                 * $ROOT/catalog/user_id/CardId/ArticlePhotoFileName
                 * $ROOT/catalog/user_id/CardId/Distance
                 * $ROOT/catalog/user_id/CardId/Date
                 *                      ^[key] :[value]
                 */
                Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                if (!isCard(map)) {
                    Logger.e("!!! dataSnapshot is not card");
                    return;
                }
                Card card = dataSnapshot.getValue(Card.class);
                if (card == null) {
                    Logger.e("!!! card is null");
                    return;
                }
                else card.printCardData();
                subscriber.onUpdate(card);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                Logger.d(">>> sibling location:" + s);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                Logger.d(">>>");
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                Logger.d(">>> sibling location:" + s);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Logger.e("!!! database error:" + databaseError.getMessage());
            }

            @Override
            public void onGetParentIdsAndCounts(Map<String, Long> parentIdsAndSiblingCounts) {

            }
        };
        registerChildEventListeners(mContext, listener);
        FirebaseDatabaseHelper.getInstance().readCatalogFromDatabase(Card.class, listener, start, end);
    }

    public void unsubscribe() {
        unregisterValueEventListeners(mContext);
        unregisterChildEventListeners(mContext);
    }

    /**
     * attaches value event listener to obtain ids of authors
     * to obtain value through attaching listener is asynchronous to this method
     * a subscriber implemented by UI class is called by listener for reacting simultaneously on UI when listener is triggered by firebase
     */
    public void subscribeAuthorIds(final Subscriber subscriber) {
        Logger.d(">>>");
        FirebaseDatabaseHelper.OnReadDatabaseValueEventListener listener = new FirebaseDatabaseHelper.OnReadDatabaseValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<String> ids = new LinkedList<>();
                Logger.d("... user count:" + dataSnapshot.getChildrenCount());
                /**
                 * $ROOT/catalog/user_id/CardId/CardId
                 * $ROOT/catalog/user_id/CardId/ProfilePhoto
                 * $ROOT/catalog/user_id/CardId/ProfileTitle
                 * $ROOT/catalog/user_id/CardId/ProfileName
                 * $ROOT/catalog/user_id/CardId/ProfileID
                 * $ROOT/catalog/user_id/CardId/ArticleTitle
                 * $ROOT/catalog/user_id/CardId/ArticleContent
                 * $ROOT/catalog/user_id/CardId/ArticleCount
                 * $ROOT/catalog/user_id/CardId/ArticlePhoto
                 * $ROOT/catalog/user_id/CardId/ArticlePhotoFileName
                 * $ROOT/catalog/user_id/CardId/Distance
                 * $ROOT/catalog/user_id/CardId/Date
                 *      ^[key]  :[value]                -> initial
                 *              ^[key]  :[value]        -> 1th for-loop
                 *                      ^[key] :[value] -> 2th for-loop
                 */
                for (DataSnapshot snapShotOfUserId : dataSnapshot.getChildren()) {
                    Logger.d("... user(" + snapShotOfUserId.getKey() + ") post count:" + snapShotOfUserId.getChildrenCount());
                    ids.add(snapShotOfUserId.getKey());
                }
                subscriber.onAuthors(ids);
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                subscriber.onError(databaseError.getMessage());
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }
        };
        registerValueEventListeners(mContext, listener);
        FirebaseDatabaseHelper.getInstance().readCatalogFromDatabase(Card.class, listener);
    }

    /**
     * attaches value event listener to obtain counts of cards belonging to each author in a specified period
     * to obtain value through attaching listener is asynchronous to this method
     * a subscriber implemented by UI class is called by listener for reacting simultaneously on UI when listener is triggered by firebase
     */
    public void subscribeAuthorIdsAndCardCounts(final Subscriber subscriber, final long start, final long end) {
        Logger.d(">>>");
        FirebaseDatabaseHelper.OnReadDatabaseChildEventListener listener = new FirebaseDatabaseHelper.OnReadDatabaseChildEventListener() {
            /**
             * this would be called many times while getting a child after querying
             */
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }

            /**
             * this would be called many times by ChildEventListener#onChildAdded()
             * since a invocation of FirebaseDatabaseHelper#readIdsAndChildrenCounts()
             */
            @Override
            public void onGetParentIdsAndCounts(Map<String, Long> parentIdsAndSiblingCounts) {
                subscriber.onGetParentIdsAndCounts(parentIdsAndSiblingCounts);
            }
        };
        registerChildEventListeners(mContext, listener);
        FirebaseDatabaseHelper.getInstance().readIdsAndChildrenCounts(Card.class, listener, start, end);
    }

    /**
     * true if map represents a card as a java object stored and mapped to child locations in a nested fashion of JSON tree
     * otherwise false
     */
    private boolean isCard(Map<String, Object> map) {
        if (map == null) {
            Logger.e("!!! map is null");
            return false;
        }
        boolean is_card =
                map.containsKey("CardId") &&
                map.containsKey("ProfilePhoto") &&
                map.containsKey("ProfileTitle") &&
                map.containsKey("ProfileName") &&
                map.containsKey("ProfileID") &&
                map.containsKey("ArticleTitle") &&
                map.containsKey("ArticleContent") &&
                map.containsKey("ArticleCount") &&
                //map.containsKey("ArticlePhoto") &&
                //map.containsKey("ArticlePhotoFileName") &&
                map.containsKey("Distance") &&
                map.containsKey("Date");
        return is_card;
    }

    private static final class Lock {}

    /**
     * callback implemented and used to notified other UI class(V of MVP) when ValueEventListener#onDataChange() is invoked
     * subscriber for reacting to event about value changes of firebase database on UI
     */
    public interface Subscriber {
        void onUpdate(Card card);
        void onClone(CardList cards);
        void onError(String error);
        void onAuthors(List<String> author_ids);
        void onGetParentIdsAndCounts(Map<String, Long> parentIdsAndSiblingCounts);
    }

    /**
     * poster for reacting to event about posting task on UI
     */
    public interface Poster {
        void onSuccess();
        void onFailure();
    }

    /**
     * poster for reacting to event about posting task on UI
     */
    public interface Uploader {
        void onSuccess(int process, int success_index);
        void onFailure(int fail_index);
    }

    /**
     * remover for reacting to event about removing task on UI
     */
    public interface Remover {
        void onFind(boolean isFound);
        void onSuccess(int process, int success_index); //for delete one of photos belonging to a card
        void onSuccess(); //for delete all of photos belonging to a card
        void onFailure(int fail_index); //for delete one of photos belonging to a card
        void onFailure(); //for delete all of photos belonging to a card
    }
}
