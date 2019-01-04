package com.ours.yours.app.firebase;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.orhanobut.logger.Logger;
import com.ours.yours.app.entity.Card;
import com.ours.yours.app.entity.Comment;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FirebaseDatabaseHelper {
    private static final int MAX_UPLOAD_RETRY_MILLIS = 60000; //1 minute
    private static final int COUNT_ON_PAGE = 20;
    private static FirebaseDatabaseHelper mInstance;
    
    private static final boolean isLog = true;

    /**
     * Card   :$ROOT/catalog/user_id/card_id(auto-generated)
     * Comment:$ROOT/catalog/card_id/comment_id(auto-generated)
     */
    private DatabaseReference mDatabaseReferenceRoot;
    /**
     * Card:$ROOT/catalog/user_id/card_id/uri_path_last_segment
     */
    private StorageReference mStorageReferenceRoot;
    /**
     * active value event listeners mapping to each database reference
     */
    private Map<ValueEventListener, DatabaseReference> mValueEventListeners = new HashMap<>();
    /**
     * active child event listeners mapping to each database reference
     */
    private Map<ChildEventListener, DatabaseReference> mChildEventListeners = new HashMap<>();

    private FirebaseDatabaseHelper() {
        initDatabaseAndStorage();
    }

    /**
     * gets a singleton object managing all methods to firebase database
     * should be called before other methods
     */
    public static FirebaseDatabaseHelper getInstance() {
        //if (isLog) Logger.d(">>>");
        if (mInstance == null ) {
            if (isLog) Logger.d("... going to create the singleton instance");
            mInstance = new FirebaseDatabaseHelper();
        }
        return mInstance;
    }

    private void initDatabaseAndStorage() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Logger.e("!!! user is null");
            return;
        }
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.setPersistenceEnabled(true);
        mDatabaseReferenceRoot = database.getReference();
        if (mDatabaseReferenceRoot != null) if (isLog) Logger.d("... root key of Realtime Database:" + mDatabaseReferenceRoot.getKey());
        FirebaseStorage storage = FirebaseStorage.getInstance();
        storage.setMaxUploadRetryTimeMillis(MAX_UPLOAD_RETRY_MILLIS);
        mStorageReferenceRoot = storage.getReference();
        if (mStorageReferenceRoot != null) if (isLog) Logger.d("... root path of Firebase Storage:" + mStorageReferenceRoot.getPath());
    }

    /**
     * returns user id being loggin-in
     */
    public String getUserId() {
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    /**
     * returns user name being loggin-in
     */
    public String getUserName() {
        return FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
    }

    /**
     * calls push() on firebase realtime database and retrieves an auto-generate child location headed with catalog/id/ as prefixes
     * returns key(ex: card id)
     *
     * id:
     *  user id for card
     *  card id for comment
     * key:
     *  card id for card
     *  comment id for comment
     */
    public String pushDatabaseAndGetAuoGeneratedKey(Class<?> clazz) {
        if (isLog) Logger.d(">>>");
        String key;
        DatabaseReference reference;
        String catalog = null;
        String user_id = null;
        if (mDatabaseReferenceRoot == null) {
            Logger.e("!!! mDatabaseReferenceRoot is null");
            return null;
        }
        if (clazz != null) {
            catalog = clazz.getSimpleName();
            if (isLog) Logger.d("... catalog:" + catalog);
        }
        if (clazz.getName().equals(Card.class.getName())) {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                user_id = FirebaseAuth.getInstance().getCurrentUser().getUid();
                if (isLog) Logger.d("... user id:" + user_id);
            } else {
                Logger.e("!!! get user fail");
                return null;
            }
            reference = createDatabaseChild(catalog, user_id);
            key = reference.getKey();
            if (isLog) Logger.d("... key:" + key);
            return key;
        } else if (clazz.getName().equals(Comment.class.getName())) {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                if (isLog) Logger.d("... user id:" + user_id);
            } else {
                Logger.e("!!! get user fail");
                return null;
            }
            reference = createDatabaseChild(catalog, null);
            key = reference.getKey();
            if (isLog) Logger.d("... key:" + key);
            return key;
        } else {
            return null;
        }
    }

    /**
     * calls push() on firebase realtime database to auto generate a child location headed with model info. and catalog as prefixes
     * returns key(ex: comment id)
     */
    public String pushDatabaseAndGetAuoGeneratedKey(Class<?> clazz, Object model) {
        if (isLog) Logger.d(">>>");
        String key;
        DatabaseReference reference;
        String catalog = null;
        if (mDatabaseReferenceRoot == null) {
            Logger.e("!!! mDatabaseReferenceRoot is null");
            return null;
        }
        if (clazz != null) {
            catalog = clazz.getSimpleName();
            if (isLog) Logger.d("... catalog:" + catalog);
        }
        if (model instanceof Card) {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                reference = createDatabaseChild(catalog, FirebaseAuth.getInstance().getCurrentUser().getUid());
                key = reference.getKey();
                if (isLog) Logger.d("... push and get key:" + key);
                return key;
            } else {
                Logger.e("!!! failed to get current user");
                return null;
            }
        } else if (model instanceof Comment) {
            reference = createDatabaseChild(catalog, ((Comment) model).getCardId());
            key = reference.getKey();
            if (isLog) Logger.d("... push and get key:" + key);
            return key;
        } else {
            return null;
        }
    }

    /**
     * attaches listener to obtain value at the location storing a model in JSON tree
     * detaching the listener which's been attached before should be excuted by the model managers after completing the reading task
     *
     * id:
     *  user id for card
     *  card id for comment
     * key:
     *  card id for card
     *  comment id for comment
     */
    public void readModelFromDatabase(Class<?> clazz, String id, String key, OnReadDatabaseValueEventListener listener) {
        if (isLog) Logger.d(">>>");
        DatabaseReference referenceCatalogIdModelId;
        String catalog;
        if (clazz.getName().equals(Card.class.getName())) {
            catalog = Card.class.getSimpleName();
            referenceCatalogIdModelId = getDatabaseChild(catalog, id, key);
            //referenceCatalogIdModelId.addValueEventListener(listener);
            referenceCatalogIdModelId.addListenerForSingleValueEvent(listener);
            mValueEventListeners.put(listener, referenceCatalogIdModelId);
        } else if (clazz.getName().equals(Comment.class.getName())) {
            catalog = Comment.class.getSimpleName();
            referenceCatalogIdModelId = getDatabaseChild(catalog, id, key);
            referenceCatalogIdModelId.addListenerForSingleValueEvent(listener);
            mValueEventListeners.put(listener, referenceCatalogIdModelId);
        }
    }

    /**
     * attaches listener to obtain values at child locations of "catalog/id/" storing list of models in JSON tree
     * reads all values ordered by key
     *
     * id: user id for card
     *     card id for comment
     */
    public void readListFromDatabase(Class<?> clazz, String id, OnReadDatabaseValueEventListener listener) {
        if (isLog) Logger.d(">>>");
        DatabaseReference referenceCatalogId;
        Query query;
        String catalog;
        if (clazz.getName().equals(Card.class.getName())) {
            String user_id = id;
            catalog = Card.class.getSimpleName();
            referenceCatalogId = getDatabaseChild(catalog, user_id, null);
            query = referenceCatalogId.orderByKey().limitToLast(COUNT_ON_PAGE);
            //query.addValueEventListener(listener);
            query.addListenerForSingleValueEvent(listener);
            mValueEventListeners.put(listener, referenceCatalogId);
        } else if (clazz.getName().equals(Comment.class.getName())) {
            String card_id = id;
            catalog = Comment.class.getSimpleName();
            referenceCatalogId = getDatabaseChild(catalog, card_id, null);
            query = referenceCatalogId.orderByKey();
            query.addListenerForSingleValueEvent(listener);
            mValueEventListeners.put(listener, referenceCatalogId);
        }
    }

    /**
     * attaches listener to obtain values at child locations of "catalog/id/" storing list of models in JSON tree
     * reads values created in a specific period and ordered by date
     *
     * id: user id for card
     *     card id for comment
     *
     * query can be ordered by deep nested ONE more level down children
     */
    public void readListFromDatabase(Class<?> clazz, String id, OnReadDatabaseChildEventListener listener, long start, long end) {
        //if (isLog) Logger.d(">>> end at date before:" + (end/(1*24*60*60*1000) - start/(1*24*60*60*1000)));
        if (isLog) Logger.d(">>> start:" + start + ", end:" + end + ", id:" + id);
        DatabaseReference referenceCatalogId;
        Query query;
        String catalog;
        if (clazz.getName().equals(Card.class.getName())) {
            catalog = Card.class.getSimpleName();
            referenceCatalogId = getDatabaseChild(catalog, id, null);
            query = referenceCatalogId.orderByChild("Date").startAt(start).endAt(end).limitToLast(COUNT_ON_PAGE);
            query.addChildEventListener(listener);
            mChildEventListeners.put(listener, referenceCatalogId);
        } else if (clazz.getName().equals(Comment.class.getName())) {
            catalog = Comment.class.getSimpleName();
            referenceCatalogId = getDatabaseChild(catalog, id, null);
            query = referenceCatalogId.orderByChild("Date").startAt(start).endAt(end).limitToLast(COUNT_ON_PAGE);
            query.addChildEventListener(listener);
            mChildEventListeners.put(listener, referenceCatalogId);
        }
    }

    /**
     * attaches listener to obtain values at child locations of "catalog/" storing lots of models in JSON tree
     * reads all values ordered by key
     *
     * catalog: "Card" for card
     *          "Comment" for comment
     */
    public void readCatalogFromDatabase(Class<?> clazz, OnReadDatabaseValueEventListener listener) {
        DatabaseReference referenceCatalog;
        String catalog;
        if (clazz.getName().equals(Card.class.getName())) {
            catalog = Card.class.getSimpleName();
            referenceCatalog = getDatabaseChild(catalog, null, null);
            referenceCatalog.orderByKey().limitToLast(COUNT_ON_PAGE).addListenerForSingleValueEvent(listener);
            mValueEventListeners.put(listener, referenceCatalog);
        } else if (clazz.getName().equals(Comment.class.getName())) {
            catalog = Comment.class.getSimpleName();
            referenceCatalog = getDatabaseChild(catalog, null, null);
            referenceCatalog.orderByChild("Date").limitToLast(COUNT_ON_PAGE).addListenerForSingleValueEvent(listener);
            mValueEventListeners.put(listener, referenceCatalog);
        }
    }

    /**
     * attaches listener to obtain values at child locations of "catalog/" storing lots of models in JSON tree
     * reads values created in a specific period and ordered by date
     *
     * catalog: "Card" for card
     *          "Comment" for comment
     *
     * It's surprised to found out even query can be ordered by deep nested children, but children ONE more level down only
     * Being such circumstance, go for node one more level down first and do query for a specific period on it
     */
    public void readCatalogFromDatabase(final Class<?> clazz, final OnReadDatabaseChildEventListener child_listener, final long start, final long end) {
        if (isLog) Logger.d(">>> start:" + start + ", end:" + end);
        DatabaseReference referenceCatalog;
        OnReadDatabaseValueEventListener ids_listener;
        String catalog;
        if (clazz.getName().equals(Card.class.getName())) {
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
             */
            ids_listener = new OnReadDatabaseValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    List<String> parent_ids = new LinkedList<>(); //user ids
                    if (isLog) Logger.d("... user count:" + dataSnapshot.getChildrenCount());
                    for (DataSnapshot snapShotOfUserId : dataSnapshot.getChildren()) {
                        if (isLog) Logger.d("... user(" + snapShotOfUserId.getKey()
                                + " has" + snapShotOfUserId.getChildrenCount()
                                + " cards (all the time)");
                        parent_ids.add(snapShotOfUserId.getKey());
                    }
                    for (String id : parent_ids) {
                        readListFromDatabase(clazz, id, child_listener, start, end);
                    }
                    FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    child_listener.onCancelled(databaseError);
                    FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
                }
            };
            catalog = Card.class.getSimpleName();
            referenceCatalog = getDatabaseChild(catalog, null, null);
            referenceCatalog.orderByKey().limitToLast(COUNT_ON_PAGE).addListenerForSingleValueEvent(ids_listener);
            mValueEventListeners.put(ids_listener, referenceCatalog);
        } else if (clazz.getName().equals(Comment.class.getName())) {
            /**
             * $ROOT/catalog/card_id/CommentId/CardId
             * $ROOT/catalog/card_id/CommentId/CommentId
             * $ROOT/catalog/card_id/CommentId/ProfilePhoto
             * $ROOT/catalog/card_id/CommentId/ProfileName
             * $ROOT/catalog/card_id/CommentId/ProfileID
             * $ROOT/catalog/card_id/CommentId/Comment
             * $ROOT/catalog/card_id/CommentId/Distance
             * $ROOT/catalog/card_id/CommentId/Date
             *      ^[key]  :[value]                    -> initial
             *              ^[key]  :[value]            -> 1th for-loop
             */
            ids_listener = new OnReadDatabaseValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    List<String> parent_ids = new LinkedList<>(); //card ids
                    if (isLog) Logger.d("... card count:" + dataSnapshot.getChildrenCount());
                    for (DataSnapshot snapShotOfCardId : dataSnapshot.getChildren()) {
                        if (isLog) Logger.d("... card(" + snapShotOfCardId.getKey()
                                + " has" + snapShotOfCardId.getChildrenCount()
                                + " comments (all the time)");
                        parent_ids.add(snapShotOfCardId.getKey());
                    }
                    for (String id : parent_ids) {
                        readListFromDatabase(clazz, id, child_listener, start, end);
                    }
                    FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    child_listener.onCancelled(databaseError);
                    FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
                }
            };
            catalog = Comment.class.getSimpleName();
            referenceCatalog = getDatabaseChild(catalog, null, null);
            referenceCatalog.orderByKey().limitToLast(COUNT_ON_PAGE).addListenerForSingleValueEvent(ids_listener);
            mValueEventListeners.put(ids_listener, referenceCatalog);
        }
    }

    /**
     * attaches listener to obtain parent ids and count of sibling at child locations of "catalog/" storing lots of models in JSON tree
     * reads values created in a specific period and ordered by date
     *
     * catalog: "Card" for card
     *          "Comment" for comment
     *
     * id: "user id" for card
     *     "card id" for comment
     */
    public void readIdsAndChildrenCounts(final Class<?> clazz, final OnReadDatabaseChildEventListener child_listener, final long start, final long end) {
        if (isLog) Logger.d(">>> start:" + start + ", end:" + end);
        DatabaseReference referenceCatalog;
        OnReadDatabaseValueEventListener ids_listener;
        List<String> parent_ids; //user ids
        final Map<String, Long> parentIdsAndSiblingCounts = new HashMap<>(); //pairs holding user ids and counts of user cards
        String catalog;
        if (clazz.getName().equals(Card.class.getName())) {
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
             */
            //listener querying how many cards has been created by each user in a specific period
            final ChildEventListener countsListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
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
                    String user_id = dataSnapshot.getRef().getParent().getKey(); //parent id
                    long previous_count = parentIdsAndSiblingCounts.get(user_id);
                    long updated_count = previous_count + 1;
                    parentIdsAndSiblingCounts.put(user_id, updated_count);
                    //if (isLog) Logger.d("... now user:" + user_id + " has updated card count(period):" + updated_count);
                    child_listener.onGetParentIdsAndCounts(parentIdsAndSiblingCounts);
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
            };
            ids_listener = new OnReadDatabaseValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    List<String> parent_ids = new LinkedList<>(); //user ids
                    Query query;
                    String catalog;
                    if (isLog) Logger.d("... user count:" + dataSnapshot.getChildrenCount());
                    for (DataSnapshot snapShotOfUserId : dataSnapshot.getChildren()) {
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
                         *               ^[key] :[value]
                         */
                        if (isLog) Logger.d("... user:" + snapShotOfUserId.getKey()
                                + " has" + snapShotOfUserId.getChildrenCount()
                                + " cards (all the time)");
                        parent_ids.add(snapShotOfUserId.getKey());
                        if (!parentIdsAndSiblingCounts.containsKey(snapShotOfUserId.getKey())) {
                            if (isLog) Logger.d("... going to put " + snapShotOfUserId.getKey() + " into map");
                            parentIdsAndSiblingCounts.put(snapShotOfUserId.getKey(), (long) 0);
                        }
                    }
                    for (String id : parent_ids) {
                        if (clazz.getName().equals(Card.class.getName())) {
                            catalog = Card.class.getSimpleName();
                            DatabaseReference referenceId = getDatabaseChild(catalog, id, null);
                            query = referenceId.orderByChild("Date").startAt(start).endAt(end).limitToLast(COUNT_ON_PAGE);
                            query.addChildEventListener(countsListener);
                            mChildEventListeners.put(child_listener, referenceId);
                        }
                    }
                    FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    child_listener.onCancelled(databaseError);
                    FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
                }
            };
            catalog = Card.class.getSimpleName();
            referenceCatalog = getDatabaseChild(catalog, null, null);
            referenceCatalog.orderByKey().limitToLast(COUNT_ON_PAGE).addListenerForSingleValueEvent(ids_listener);
            mValueEventListeners.put(ids_listener, referenceCatalog);
        } else if (clazz.getName().equals(Comment.class.getName())) {
            /**
             * $ROOT/catalog/card_id/CommentId/CardId
             * $ROOT/catalog/card_id/CommentId/CommentId
             * $ROOT/catalog/card_id/CommentId/ProfilePhoto
             * $ROOT/catalog/card_id/CommentId/ProfileName
             * $ROOT/catalog/card_id/CommentId/ProfileID
             * $ROOT/catalog/card_id/CommentId/Comment
             * $ROOT/catalog/card_id/CommentId/Distance
             * $ROOT/catalog/card_id/CommentId/Date
             *      ^[key]  :[value]                    -> initial
             *              ^[key]  :[value]            -> 1th for-loop
             */
            //listener querying how many comments has been made belonging to each card in a specific period
            final ChildEventListener countsListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    /**
                     * $ROOT/catalog/card_id/CommentId/CardId
                     * $ROOT/catalog/card_id/CommentId/CommentId
                     * $ROOT/catalog/card_id/CommentId/ProfilePhoto
                     * $ROOT/catalog/card_id/CommentId/ProfileName
                     * $ROOT/catalog/card_id/CommentId/ProfileID
                     * $ROOT/catalog/card_id/CommentId/Comment
                     * $ROOT/catalog/card_id/CommentId/Distance
                     * $ROOT/catalog/card_id/CommentId/Date
                     *                       ^[key]   :[value]
                     */
                    String card_id = dataSnapshot.getRef().getParent().getKey(); //parent id
                    long previous_count = parentIdsAndSiblingCounts.get(card_id);
                    long updated_count = previous_count + 1;
                    parentIdsAndSiblingCounts.put(card_id, updated_count);
                    //if (isLog) Logger.d("... now card:" + card_id + " has updated comment count(period):" + updated_count);
                    child_listener.onGetParentIdsAndCounts(parentIdsAndSiblingCounts);
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
            };
            ids_listener = new OnReadDatabaseValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    List<String> parent_ids = new LinkedList<>(); //user ids
                    Query query;
                    String catalog;
                    if (isLog) Logger.d("... card count:" + dataSnapshot.getChildrenCount());
                    for (DataSnapshot snapShotOfCardId : dataSnapshot.getChildren()) {
                        if (isLog) Logger.d("... card:" + snapShotOfCardId.getKey()
                                + " has" + snapShotOfCardId.getChildrenCount()
                                + " comments (all the time)");
                        parent_ids.add(snapShotOfCardId.getKey());
                        if (!parentIdsAndSiblingCounts.containsKey(snapShotOfCardId.getKey())) {
                            if (isLog) Logger.d("... going to put " + snapShotOfCardId.getKey() + " into map");
                            parentIdsAndSiblingCounts.put(snapShotOfCardId.getKey(), (long) 0);
                        }
                    }
                    for (String id : parent_ids) {
                        if (clazz.getName().equals(Comment.class.getName())) {
                            catalog = Comment.class.getSimpleName();
                            DatabaseReference referenceId = getDatabaseChild(catalog, id, null);
                            query = referenceId.orderByChild("Date").startAt(start).endAt(end).limitToLast(COUNT_ON_PAGE);
                            query.addChildEventListener(countsListener);
                            mChildEventListeners.put(child_listener, referenceId);
                        }
                    }
                    FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    child_listener.onCancelled(databaseError);
                    FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
                }
            };
            catalog = Comment.class.getSimpleName();
            referenceCatalog = getDatabaseChild(catalog, null, null);
            referenceCatalog.orderByKey().limitToLast(COUNT_ON_PAGE).addListenerForSingleValueEvent(ids_listener);
            mValueEventListeners.put(ids_listener, referenceCatalog);
        }
    }

    /**
     * performs simultaneous updating to the location storing model in JSON tree with a single call
     * model as a java object is automatically mapped to child locations in a nested fashion
     *
     * write a pojo to $ROOT/catalog/id/key/POJO_to_JSON_TREE
     * id:
     *  user id for card that users can edit only the cards posted by themselves
     *  card id for comment
     * key:
     *  card id for card
     *  comment id for comment
     */
    public void writeModelToDatabase(Object model, OnWriteDatabaseCompleteListener listener) {
        if (isLog) Logger.d(">>>");
        DatabaseReference referenceCatalogId;
        if (model instanceof Card) {
            String catalog = Card.class.getSimpleName();
            String user_id = FirebaseAuth.getInstance().getCurrentUser().getUid();
            String card_id = ((Card) model).getCardId();
            Map<String, Object> modelValue = ((Card) model).toMap();
            Map<String, Object> updates = new HashMap<>();
            updates.put("/" + card_id + "/", modelValue);
            referenceCatalogId = getDatabaseChild(catalog, user_id, null);
            referenceCatalogId.updateChildren(updates, listener);
        } else if (model instanceof Comment) {
            String catalog = Comment.class.getSimpleName();
            String card_id = ((Comment) model).getCardId();
            String comment_id = ((Comment) model).getCommentId();
            Map<String, Object> modelValue = ((Comment) model).toMap();
            Map<String, Object> updates = new HashMap<>();
            updates.put("/" + comment_id + "/", modelValue);
            referenceCatalogId = getDatabaseChild(catalog, card_id, null);
            referenceCatalogId.updateChildren(updates, listener);
        }
    }

    /**
     * deletes value at the location at the specified "catalog/id/key" in JSON tree
     *
     * id:
     *  user id for card
     *  card id for comment
     * key:
     *  card id for card
     *  comment id for comment
     */
    public void deleteModelFromDatabase(Class<?> clazz, String id, String key, OnDeleteDatabaseCompleteListener listener) {
        if (isLog) Logger.d(">>>");
        DatabaseReference referenceCatalogIdModelId;
        if (clazz.getName().equals(Card.class.getName())) {
            String catalog = Card.class.getSimpleName();
            String user_id = id;
            String card_id = key;
            referenceCatalogIdModelId = getDatabaseChild(catalog, user_id, card_id);
            referenceCatalogIdModelId.removeValue(listener);
        } else if (clazz.getName().equals(Comment.class.getName())) {
            String catalog = Comment.class.getSimpleName();
            String card_id = id;
            String comment_id = key;
            referenceCatalogIdModelId = getDatabaseChild(catalog, card_id, comment_id);
            referenceCatalogIdModelId.removeValue(listener);
        }
    }

    /**
     * deletes all values associated with specific id
     *
     * id: user id for card
     *     card id for comment
     */
    public void deleteListFromDatabase(Class<?> clazz, String id, OnDeleteDatabaseCompleteListener listener) {
        if (isLog) Logger.d(">>>");
        DatabaseReference referenceCatalogId;
        if (clazz.getName().equals(Card.class.getName())) {
            String catalog = Card.class.getSimpleName();
            String user_id = id;
            referenceCatalogId = getDatabaseChild(catalog, user_id, null);
            referenceCatalogId.removeValue(listener);
        } else if (clazz.getName().equals(Comment.class.getName())) {
            String catalog = Comment.class.getSimpleName();
            String card_id = id;
            referenceCatalogId = getDatabaseChild(catalog, card_id, null);
            referenceCatalogId.removeValue(listener);
        }
    }

    /**
     * asynchronously uploads a file from a local uri to a specified StorageReference to /catalog/user_id/id/last_path_segment_of_uri
     * attaches listener to obtain download URL by getDownloadUrl() at the moment onSuccess() on the OnSuccessListener is triggered
     */
    public void uploadUriToStorage(Uri uri, Object model, OnUploadStorageSuccess listener_success, OnUploadStorageFailure listener_failure) {
        if (isLog) Logger.d(">>>");
        if (model instanceof Card) {
            StorageReference refCatalogUserIdCardIdUriLastPathSegment;
            String catalog;
            String user_id;
            String card_id;
            catalog = Card.class.getSimpleName();
            user_id = FirebaseAuth.getInstance().getCurrentUser().getUid();
            card_id = ((Card) model).getCardId();
            if (isLog) Logger.d("... user id:" + user_id + " card id:" + card_id);
            refCatalogUserIdCardIdUriLastPathSegment = createStorageChild(catalog, user_id, card_id).child(uri.getLastPathSegment());
            if (listener_success == null) {
                if (listener_failure == null) {
                    refCatalogUserIdCardIdUriLastPathSegment.putFile(uri);
                } else {
                    refCatalogUserIdCardIdUriLastPathSegment.putFile(uri).addOnFailureListener(listener_failure);
                }
            } else {
                if (listener_failure == null) {
                    refCatalogUserIdCardIdUriLastPathSegment.putFile(uri).addOnSuccessListener(listener_success);
                } else {
                    refCatalogUserIdCardIdUriLastPathSegment.putFile(uri).addOnSuccessListener(listener_success).addOnFailureListener(listener_failure);
                }
            }
        }
    }

    /**
     * asynchronously downloads a file from a remote StorageReference to /catalog/user_id/id/last_path_segment_of_uri(file_name)
     * attaches listener to obtain file at the moment onSuccess() on the OnSuccessListener is triggered
     */
    public void downloadFileFromStorage(File file, String file_name, Object model, OnDownloadStorageSuccess listener_success, OnDownloadStorageFailure listener_failure) {
        if (isLog) Logger.d(">>>");
        String download_url;
        if (model instanceof Card) {
            StorageReference refCatalogUserIdCardIdUriLastPathSegment;
            String catalog;
            String user_id;
            String card_id;
            catalog = Card.class.getSimpleName();
            user_id = FirebaseAuth.getInstance().getCurrentUser().getUid();
            card_id = ((Card) model).getCardId();
            refCatalogUserIdCardIdUriLastPathSegment = createStorageChild(catalog, user_id, card_id).child(file_name);
            if (listener_success == null) {
                if (listener_failure == null) {
                    refCatalogUserIdCardIdUriLastPathSegment.getFile(file);
                } else {
                    refCatalogUserIdCardIdUriLastPathSegment.getFile(file).addOnFailureListener(listener_failure);
                }
            } else {
                if (listener_failure == null) {
                    refCatalogUserIdCardIdUriLastPathSegment.getFile(file).addOnSuccessListener(listener_success);
                } else {
                    refCatalogUserIdCardIdUriLastPathSegment.getFile(file).addOnSuccessListener(listener_success).addOnFailureListener(listener_failure);
                }
            }
        }
    }

    /**
     * asynchronously delete a file from a remote StorageReference to /catalog/id/key/last_path_segment_of_uri(file_name)
     * attaches listener to know if task to delete is succeeded
     */
    public void deleteOneFileFromStorage(Object model, String file_name, OnDeleteStorageSuccess listener_success, OnDeleteStorageFailure listener_failure) {
        if (isLog) Logger.d(">>>");
        if (model instanceof Card) {
            StorageReference refCatalogUserIdCardIdUriLastPathSegment;
            String catalog;
            String user_id;
            String card_id;
            String photo_file_name;
            catalog = Card.class.getSimpleName();
            user_id = FirebaseAuth.getInstance().getCurrentUser().getUid();
            card_id = ((Card) model).getCardId();
            photo_file_name = file_name;
            refCatalogUserIdCardIdUriLastPathSegment = createStorageChild(catalog, user_id, card_id).child(photo_file_name);
            if (listener_success == null) {
                if (listener_failure == null) {
                    refCatalogUserIdCardIdUriLastPathSegment.delete();
                } else {
                    refCatalogUserIdCardIdUriLastPathSegment.delete().addOnFailureListener(listener_failure);
                }
            } else {
                if (listener_failure == null) {
                    refCatalogUserIdCardIdUriLastPathSegment.delete().addOnSuccessListener(listener_success);
                } else {
                    refCatalogUserIdCardIdUriLastPathSegment.delete().addOnSuccessListener(listener_success).addOnFailureListener(listener_failure);
                }
            }
        }
    }

    /**
     * asynchronously delete a file from a remote StorageReference to /catalog/id/key/last_path_segment_of_uris(file_names)
     * attaches listener to know if task to delete is succeeded
     */
    public void deleteManyFilesFromStorage(Object model, OnDeleteStorageSuccess listener_success, OnDeleteStorageFailure listener_failure) {
        if (isLog) Logger.d(">>>");
        if (model instanceof Card) {
            StorageReference refCatalogUserIdCardIdUriLastPathSegment;
            String catalog;
            String user_id;
            String card_id;
            List<String> article_photo_file_names;
            catalog = Card.class.getSimpleName();
            user_id = FirebaseAuth.getInstance().getCurrentUser().getUid();
            card_id = ((Card) model).getCardId();
            article_photo_file_names = ((Card) model).getArticlePhotoFileName();
            for (int i=0; i<article_photo_file_names.size(); i++) {
                if (isLog) Logger.d("... going to remove url:" + article_photo_file_names.get(i));
                refCatalogUserIdCardIdUriLastPathSegment = createStorageChild(catalog, user_id, card_id).child(article_photo_file_names.get(i));
                if (listener_success == null) {
                    if (listener_failure == null) {
                        refCatalogUserIdCardIdUriLastPathSegment.delete();
                    } else {
                        refCatalogUserIdCardIdUriLastPathSegment.delete().addOnFailureListener(listener_failure);
                    }
                } else {
                    if (listener_failure == null) {
                        refCatalogUserIdCardIdUriLastPathSegment.delete().addOnSuccessListener(listener_success);
                    } else {
                        refCatalogUserIdCardIdUriLastPathSegment.delete().addOnSuccessListener(listener_success).addOnFailureListener(listener_failure);
                    }
                }
            }
        }
    }

    /**
     * deletes objects located at the specified id
     *
     * key: card id for card
     */
    public void deleteAllAboutModelFromStorage(Class<?> clazz, String key) {
        if (isLog) Logger.d(">>>");
        if (clazz.getName().equals(Card.class.getName())) {
            StorageReference refCatalogUserIdCardId;
            String catalog;
            String user_id;
            String card_id;
            catalog = Card.class.getSimpleName();
            user_id = FirebaseAuth.getInstance().getCurrentUser().getUid();
            card_id = key;
            refCatalogUserIdCardId = createStorageChild(catalog, user_id, card_id);
            refCatalogUserIdCardId.delete();
        }
    }

    /**
     * get a URL to download the file after uploading a file
     */
    public Uri getDownloadUrl(Uri uri, Class<?> clazz, String id, OnGetDownloadUrlUploadStorageSuccess listener_success, OnGetDownloadUrlUploadStorageFailure listener_failure) {
        if (isLog) Logger.d(">>>");
        Uri download_url = null;
        if (clazz.getName().equals(Card.class.getName())) {
            StorageReference refCatalogUserIdCardIdUriLastPathSegment;
            String catalog;
            String user_id;
            String card_id;
            catalog = Card.class.getSimpleName();
            user_id = FirebaseAuth.getInstance().getCurrentUser().getUid();
            card_id = id;
            if (isLog) Logger.d("... user id:" + user_id + " card id:" + card_id);
            refCatalogUserIdCardIdUriLastPathSegment = createStorageChild(catalog, user_id, card_id).child(uri.getLastPathSegment());
            if (listener_success == null) {
                if (listener_failure == null) {
                    refCatalogUserIdCardIdUriLastPathSegment.getDownloadUrl();
                } else {
                    refCatalogUserIdCardIdUriLastPathSegment.getDownloadUrl().addOnFailureListener(listener_failure);
                }
            } else {
                if (listener_failure == null) {
                    refCatalogUserIdCardIdUriLastPathSegment.getDownloadUrl().addOnSuccessListener(listener_success);
                } else {
                    refCatalogUserIdCardIdUriLastPathSegment.getDownloadUrl().addOnSuccessListener(listener_success).addOnFailureListener(listener_failure);
                }
            }
        }
        return download_url;
    }

    /**
     * creates a reference to an auto-generated child location with "/catalog/" and "/id/" as prefixes
     * no prefix of "/catalog/" or "/id/" if null user id or catalog
     * returns reference representing a particular location in Firebase Database
     *
     * id: user id for card
     *     card id for comment
     *
     * auto-create: card id for card
     *              comment id for comment
     */
    private DatabaseReference createDatabaseChild(String catalog, String id) {
        if (isLog) Logger.d(">>>");
        if (mDatabaseReferenceRoot == null) {
            Logger.e("!!! mDatabaseReferenceRoot is null");
            return null;
        }
        DatabaseReference reference;
        if (catalog == null) {
            if(id == null) {
                if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id);
                reference = mDatabaseReferenceRoot.push();
            } else {
                if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id);
                reference = mDatabaseReferenceRoot.child(id).push();
            }
        } else {
            if(id == null) {
                if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id);
                reference = mDatabaseReferenceRoot.child(catalog).push();
            } else {
                if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id);
                reference = mDatabaseReferenceRoot.child(catalog).child(id).push();
            }
        }
        return reference;
    }

    /**
     * gets a database reference to location headed with "/catalog/", "/id/" and "/key/" as prefixes(to a child location in JSON tree)
     *
     * id: user id for card
     *     card id for comment
     * key:
     *     card id for card
     *     comment id for comment
     */
    private DatabaseReference getDatabaseChild(String catalog, String id, String key) {
        //if (isLog) Logger.d(">>>");
        DatabaseReference reference;
        if (catalog == null) {
            if (id == null) {
                if (key == null) {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = FirebaseDatabase.getInstance().getReference();
                } else {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = FirebaseDatabase.getInstance().getReference(key);
                }
            } else {
                if (key == null) {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = FirebaseDatabase.getInstance().getReference(id);
                } else {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = FirebaseDatabase.getInstance().getReference(id + "/" + key);
                }
            }
        } else {
            if (id == null) {
                if (key == null) {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = FirebaseDatabase.getInstance().getReference(catalog);
                } else {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = FirebaseDatabase.getInstance().getReference(catalog + "/" + key);
                }
            } else {
                if (key == null) {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = FirebaseDatabase.getInstance().getReference(catalog + "/" + id);
                } else {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = FirebaseDatabase.getInstance().getReference(catalog + "/" + id + "/" + key);
                }
            }
        }
        return reference;
    }

    /**
     * creates a storage reference to location headed with "/catalog/", "/id/" and "/key/" as prefixes(to a child location in JSON tree)
     *
     * id:
     *  user id for card
     * key:
     *  card id for card
     */
    private StorageReference createStorageChild(String catalog, String id, String key) {
        //if (isLog) Logger.d(">>>");
        if (mStorageReferenceRoot == null) {
            Logger.e("!!! mDatabaseReferenceRoot is null");
            return null;
        }
        StorageReference reference;
        if (catalog == null) {
            if (id == null) {
                if (key == null) {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = mStorageReferenceRoot;
                } else {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = mStorageReferenceRoot.child(key);
                }
            } else {
                if (key == null) {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = mStorageReferenceRoot.child(id);
                } else {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = mStorageReferenceRoot.child(id).child(key);
                }
            }
        } else {
            if (id == null) {
                if (key == null) {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = mStorageReferenceRoot.child(catalog);
                } else {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = mStorageReferenceRoot.child(catalog).child(key);
                }
            } else {
                if (key == null) {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = mStorageReferenceRoot.child(catalog).child(id);
                } else {
                    if (isLog) Logger.d("... catalog:" + catalog + ", id:" + id + ", key:" + key);
                    reference = mStorageReferenceRoot.child(catalog).child(id).child(key);
                }
            }
        }
        return reference;
    }

    public void detachValueEventListener(ValueEventListener listener) {
        if (mValueEventListeners.containsKey(listener)) {
            DatabaseReference reference = mValueEventListeners.get(listener);
            if (isLog) Logger.d("... going to remove value event listener of child key:" + reference.getKey());
            reference.removeEventListener(listener);
            mValueEventListeners.remove(listener);
        } else {
            Logger.e("!!! mValueEventListeners doesn't contain this listener");
        }
    }

    public void detachChildEventListener(ChildEventListener listener) {
        if (mChildEventListeners.containsKey(listener)) {
            DatabaseReference reference = mChildEventListeners.get(listener);
            if (isLog) Logger.d("... going to remove child event listener of reference key:" + reference.getKey());
            reference.removeEventListener(listener);
            mChildEventListeners.remove(listener);
        } else {
            Logger.e("!!! mChildEventListeners doesn't contain this listener");
        }
    }

    /**
     * listener for task to firebase realtime database
     */
    public interface OnReadDatabaseValueEventListener extends ValueEventListener {
        @Override
        void onDataChange(DataSnapshot dataSnapshot);

        @Override
        void onCancelled(DatabaseError databaseError);
    }

    public interface OnReadDatabaseChildEventListener extends ChildEventListener {
        @Override
        void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s);

        @Override
        void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s);

        @Override
        void onChildRemoved(@NonNull DataSnapshot dataSnapshot);

        @Override
        void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s);

        @Override
        void onCancelled(@NonNull DatabaseError databaseError);

        void onGetParentIdsAndCounts(Map<String, Long> parentIdsAndSiblingCounts);
    }

    public interface OnWriteDatabaseCompleteListener extends DatabaseReference.CompletionListener {
        @Override
        public abstract void onComplete (DatabaseError error, DatabaseReference ref);
    }

    public interface OnDeleteDatabaseCompleteListener extends DatabaseReference.CompletionListener {
        @Override
        public abstract void onComplete (DatabaseError error, DatabaseReference ref);
    }

    /**
     * listener for task to firebase storage
     */
    public interface OnUploadStorageSuccess extends OnSuccessListener<UploadTask.TaskSnapshot> {
        @Override
        void onSuccess(UploadTask.TaskSnapshot taskSnapshot);
    }

    public interface OnUploadStorageFailure extends OnFailureListener {
        @Override
        void onFailure(@NonNull Exception e);
    }

    public interface OnGetDownloadUrlUploadStorageSuccess extends OnSuccessListener<Uri> {
        @Override
        void onSuccess(Uri uri);
    }

    public interface OnGetDownloadUrlUploadStorageFailure extends OnFailureListener {
        @Override
        void onFailure(@NonNull Exception e);
    }

    public interface OnDownloadStorageSuccess extends OnSuccessListener<FileDownloadTask.TaskSnapshot> {
        @Override
        public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot);
    }

    public interface OnDownloadStorageFailure extends OnFailureListener {
        @Override
        void onFailure(@NonNull Exception e);
    }

    public interface OnDeleteStorageSuccess extends OnSuccessListener<Void> {
        @Override
        public void onSuccess(Void aVoid);
    }

    public interface OnDeleteStorageFailure extends OnFailureListener {
        @Override
        void onFailure(@NonNull Exception e);
    }
}
