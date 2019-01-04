package com.ours.yours.app.manager;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.storage.UploadTask;
import com.orhanobut.logger.Logger;

import com.ours.yours.app.entity.Comment;
import com.ours.yours.app.entity.CommentList;
import com.ours.yours.app.firebase.FirebaseDatabaseHelper;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CommentManager extends BaseManager {
    private static CommentManager mInstance;
    private Context mContext;

    public CommentManager(Context context) throws ClassNotFoundException {
        mContext = context;
        initFirebase();
    }

    /**
     * gets a singleton object managing all methods on comment to use firebase
     * should be called before other methods
     */
    public static CommentManager getInstance(Context context) throws ClassNotFoundException {
        //Logger.d(">>>");
        if (mInstance == null ) {
            Logger.d("... going to create the singleton instance");
            mInstance = new CommentManager(context);
        }
        return mInstance;
    }

    private void initFirebase() throws ClassNotFoundException {
        FirebaseDatabaseHelper.getInstance().pushDatabaseAndGetAuoGeneratedKey(Class.forName("com.ours.yours.app.entity.Comment"));
    }

    /**
     * writes contents of comment to firebase database which is already known what card id it's belonging to
     */
    public void postupCommentOnFirebase(final Comment comment, final Poster poster) {
        Logger.d(">>>");
        if (comment == null) {
            Logger.e("!!! comment is null");
            return;
        }
        if (comment.getCardId() == null) {
            Logger.e("!!! card id of this comment is null");
            return;
        }
        comment.setCommentId(FirebaseDatabaseHelper.getInstance().pushDatabaseAndGetAuoGeneratedKey(Comment.class, comment));
        Logger.d("... going to post a comment up at card id:" + comment.getCardId() + " and comment id:" + comment.getCommentId());
        FirebaseDatabaseHelper.getInstance().writeModelToDatabase(comment, new FirebaseDatabaseHelper.OnWriteDatabaseCompleteListener() {
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
     * deletes values about specified comment storing in firebase database which is belonging to current user
     */
    public void tearoffCommentFromFirebase(final Comment comment, final Remover remover) {
        Logger.d(">>>");
        if (comment == null) {
            Logger.e("!!! comment is null");
            return;
        }
        if (!comment.getProfileID().equals(FirebaseDatabaseHelper.getInstance().getUserId())) {
            Logger.e("!!! user id not equal to card author's");
            return;
        }
        FirebaseDatabaseHelper.getInstance().deleteModelFromDatabase(comment.getClass(), comment.getCardId()
                , comment.getCommentId(), new FirebaseDatabaseHelper.OnDeleteDatabaseCompleteListener() {
                    @Override
                    public void onComplete(DatabaseError error, DatabaseReference ref) {
                        if (error == null) {
                            remover.onSuccess();
                        } else {
                            Logger.e("!!! error:" + error.getMessage());
                            remover.onFailure();
                        }
                    }});
    }

    /**
     * deletes values about specified card's comments storing in firebase database which is belonging to current user
     */
    public void tearoffCommentsOfCardFromFirebase(final String card_id, final String card_author_id,final Remover remover) {
        Logger.d(">>>");
        if (card_id == null) {
            Logger.e("!!! card_id is null");
            return;
        }
        if (!card_author_id.equals(FirebaseDatabaseHelper.getInstance().getUserId())) {
            Logger.e("!!! user id not equal to card author's");
            return;
        }
        FirebaseDatabaseHelper.getInstance().deleteListFromDatabase(Comment.class
                , card_id, new FirebaseDatabaseHelper.OnDeleteDatabaseCompleteListener() {
                    @Override
                    public void onComplete(DatabaseError error, DatabaseReference ref) {
                        if (error == null) {
                            remover.onSuccess();
                        } else {
                            Logger.e("!!! error:" + error.getMessage());
                            remover.onFailure();
                        }
                    }});
    }

    /**
     * attaches value event listener to obtain values of specified comment
     * to obtain value through attaching listener is asynchronous to life cycle of this method
     * a subscriber implemented by UI class is called by listener for reacting simultaneously on UI when listener is triggered by firebase
     */
    public void subscribeCommentInFirebase(final String card_id, final String comment_id, final Subscriber subscriber) {
        Logger.d(">>>");
        if (card_id == null || card_id.equals("")) {
            Logger.e("!!! card is null or empty");
            return;
        }
        if (comment_id == null || comment_id.equals("")) {
            Logger.e("!!! comment_id is null or empty");
            return;
        }
        FirebaseDatabaseHelper.OnReadDatabaseValueEventListener listener = new FirebaseDatabaseHelper.OnReadDatabaseValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                /**
                 * $ROOT/catalog/card_id/CommentId/CardId
                 * $ROOT/catalog/card_id/CommentId/CommentId
                 * $ROOT/catalog/card_id/CommentId/ProfilePhoto
                 * $ROOT/catalog/card_id/CommentId/ProfileName
                 * $ROOT/catalog/card_id/CommentId/ProfileID
                 * $ROOT/catalog/card_id/CommentId/Comment
                 * $ROOT/catalog/card_id/CommentId/Distance
                 * $ROOT/catalog/card_id/CommentId/Date
                 *                      ^[key]    :[value]
                 */
                Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue(); //map represents a pojo living in JSON tree
                if (!isComment(map)) {
                    Logger.e("!!! dataSnapshot is not comment");
                    return;
                }
                Comment comment = dataSnapshot.getValue(Comment.class);
                if (comment == null) {
                    Logger.e("!!! comment is null");
                    return;
                }
                else printCommentData(comment);
                subscriber.onUpdate(comment);
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                subscriber.onError(databaseError.getMessage());
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }
        };
        registerValueEventListeners(mContext, listener);
        FirebaseDatabaseHelper.getInstance().readModelFromDatabase(Comment.class, card_id, comment_id, listener);
    }

    /**
     * attaches value event listener to obtain values of comments of specified card id
     * to obtain value through attaching listener is asynchronous to life cycle of this method
     * a subscriber implemented by UI class is called by listener for reacting simultaneously on UI when listener is triggered by firebase
     */
    public void subscribeCommentsOfCardInFirebase(final String card_id, final Subscriber subscriber) {
        Logger.d(">>> card id:" + card_id);
        if (card_id == null || card_id.equals("")) {
            Logger.e("!!! card is null or empty");
            return;
        }
        FirebaseDatabaseHelper.OnReadDatabaseValueEventListener listener = new FirebaseDatabaseHelper.OnReadDatabaseValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Logger.d("... comment count:" + dataSnapshot.getChildrenCount());
                CommentList comments = new CommentList();
                List<Comment> comments_entity = new LinkedList<>();
                /**
                 * $ROOT/catalog/card_id/CommentId/CardId
                 * $ROOT/catalog/card_id/CommentId/CommentId
                 * $ROOT/catalog/card_id/CommentId/ProfilePhoto
                 * $ROOT/catalog/card_id/CommentId/ProfileName
                 * $ROOT/catalog/card_id/CommentId/ProfileID
                 * $ROOT/catalog/card_id/CommentId/Comment
                 * $ROOT/catalog/card_id/CommentId/Distance
                 * $ROOT/catalog/card_id/CommentId/Date
                 *              ^[key]  :[value]
                 * after get children:  ^[key]    :[value]
                 */
                for (DataSnapshot snapShotOfCommentId : dataSnapshot.getChildren()) {
                    Map<String, Object> map = (Map<String, Object>) snapShotOfCommentId.getValue();
                    if (!isComment(map)) {
                        Logger.e("!!! dataSnapshot is not comment");
                        continue;
                    }
                    Comment comment = snapShotOfCommentId.getValue(Comment.class);
                    if (comment == null) {
                        Logger.e("!!! comment is null");
                        continue;
                    }
                    else printCommentData(comment);
                    if (comments.getLastDateOfComment() <= comment.getDate()) {
                        comments.setLastDateOfComment(comment.getDate());
                        Logger.d("... recorded last date:" + new Date(comments.getLastDateOfComment()).toString());
                    }
                    comments_entity.add(comment);
                }
                Collections.sort(comments_entity, new Comparator<Comment>() {
                    @Override
                    public int compare(Comment o1, Comment o2) {
                        return ((Long)o2.getDate()).compareTo(o1.getDate());
                    }
                });
                comments.setComments(comments_entity);
                subscriber.onClone(comments);
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                subscriber.onError(databaseError.getMessage());
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }
        };
        registerValueEventListeners(mContext, listener);
        FirebaseDatabaseHelper.getInstance().readListFromDatabase(Comment.class, card_id, listener);
    }

    /**
     * attaches value event listener to obtain values of comments of specified catalog
     * to obtain value through attaching listener is asynchronous to life cycle of this method
     * a subscriber implemented by UI class is called by listener for reacting simultaneously on UI when listener is triggered by firebase
     */
    public void subscribeAllCommentsInFirebase(final Subscriber subscriber) {
        Logger.d(">>>");
        FirebaseDatabaseHelper.OnReadDatabaseValueEventListener listener = new FirebaseDatabaseHelper.OnReadDatabaseValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Logger.d("... card count:" + dataSnapshot.getChildrenCount());
                CommentList comments = new CommentList();
                List<Comment> comments_entity = new LinkedList<>();
                /**
                 * $ROOT/catalog/card_id/CommentId/CardId
                 * $ROOT/catalog/card_id/CommentId/CommentId
                 * $ROOT/catalog/card_id/CommentId/ProfilePhoto
                 * $ROOT/catalog/card_id/CommentId/ProfileName
                 * $ROOT/catalog/card_id/CommentId/ProfileID
                 * $ROOT/catalog/card_id/CommentId/Comment
                 * $ROOT/catalog/card_id/CommentId/Distance
                 * $ROOT/catalog/card_id/CommentId/Date
                 *      ^[key]  :[value]
                 *              ^[key]  :[value]
                 *                      ^[key]    :[value]
                 */
                for (DataSnapshot snapShotOfCardId : dataSnapshot.getChildren()) {
                    Logger.d("... card id(" + snapShotOfCardId.getKey() + ") comment count:" + snapShotOfCardId.getChildrenCount());
                    for (DataSnapshot snapShotOfCommentId : snapShotOfCardId.getChildren()) {
                        Map<String, Object> map = (Map<String, Object>) snapShotOfCommentId.getValue();
                        if (!isComment(map)) {
                            Logger.e("!!! dataSnapshot is not comment");
                            continue;
                        }
                        Comment comment = snapShotOfCommentId.getValue(Comment.class);
                        if (comment == null) {
                            Logger.e("!!! comment is null");
                            continue;
                        }
                        else printCommentData(comment);
                        if (comments.getLastDateOfComment() <= comment.getDate()) {
                            comments.setLastDateOfComment(comment.getDate());
                            Logger.d("... recorded last date:" + new Date(comments.getLastDateOfComment()).toString());
                        }
                        comments_entity.add(comment);
                    }
                }
                Collections.sort(comments_entity, new Comparator<Comment>() {
                    @Override
                    public int compare(Comment o1, Comment o2) {
                        return ((Long)o2.getDate()).compareTo(o1.getDate());
                    }
                });
                comments.setComments(comments_entity);
                subscriber.onClone(comments);
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                subscriber.onError(databaseError.getMessage());
                FirebaseDatabaseHelper.getInstance().detachValueEventListener(this);
            }
        };
        registerValueEventListeners(mContext, listener);
        FirebaseDatabaseHelper.getInstance().readCatalogFromDatabase(Comment.class, listener);
    }

    public void unsubscribe() {
        unregisterValueEventListeners(mContext);
    }

    /**
     * true if map represents a comment as a java object stored and mapped to child locations in a nested fashion of JSON tree
     * otherwise false
     */
    private boolean isComment(Map<String, Object> map) {
        if (map == null) {
            Logger.e("!!! map is null");
            return false;
        }
        if (!map.containsKey("CardId")) Logger.e("!!! no key of CardId");
        if (!map.containsKey("CommentId")) Logger.e("!!! no key of CommentId");
        if (!map.containsKey("ProfilePhoto")) Logger.e("!!! no key of ProfilePhoto");
        if (!map.containsKey("ProfileName")) Logger.e("!!! no key of ProfileName");
        if (!map.containsKey("ProfileID")) Logger.e("!!! no key of ProfileID");
        if (!map.containsKey("Comment")) Logger.e("!!! no key of Comment");
        if (!map.containsKey("Distance")) Logger.e("!!! no key of Distance");
        if (!map.containsKey("Date")) Logger.e("!!! no key of Date");

        boolean is_comment =
                map.containsKey("CardId") &&
                map.containsKey("CommentId") &&
                map.containsKey("ProfilePhoto") &&
                map.containsKey("ProfileName") &&
                map.containsKey("ProfileID") &&
                map.containsKey("Comment") &&
                map.containsKey("Distance") &&
                map.containsKey("Date");
        return is_comment;
    }

    private void printCommentData(Comment c){
        c.printCommentData();
    }

    /**
     * callback implemented and used to notified other UI class(V of MVP) when ValueEventListener#onDataChange() is invoked
     * subscriber for reacting to event about value changes of firebase database on UI
     */
    public interface Subscriber {
        void onUpdate(Comment card);
        void onClone(CommentList cards);
        void onError(String error);
    }

    /**
     * poster for reacting to event about posting task on UI
     */
    public interface Poster {
        void onSuccess();
        void onFailure();
    }

    /**
     * remover for reacting to event about removing task on UI
     */
    public interface Remover {
        void onSuccess();
        void onFailure();
    }
}
