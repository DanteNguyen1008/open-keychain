/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thialfihar.android.apg.ui.widget;

import org.thialfihar.android.apg.Constants;
import org.thialfihar.android.apg.Id;
import org.thialfihar.android.apg.R;
import org.thialfihar.android.apg.helper.OtherHelper;
import org.thialfihar.android.apg.helper.PGPHelper;
import org.thialfihar.android.apg.provider.ApgContract.Keys;
import org.thialfihar.android.apg.provider.ApgContract.UserIds;
import org.thialfihar.android.apg.util.Log;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MergeCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorTreeAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class KeyListAdapter extends CursorTreeAdapter {
    private Context mContext;
    private LayoutInflater mInflater;

    protected int mKeyType;

    private static final int CHILD_KEY = 0;
    private static final int CHILD_USER_ID = 1;
    private static final int CHILD_FINGERPRINT = 2;

    public KeyListAdapter(Context context, Cursor groupCursor, int keyType) {
        super(groupCursor, context);
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mKeyType = keyType;
    }

    /**
     * Inflate new view for group items
     */
    @Override
    public View newGroupView(Context context, Cursor cursor, boolean isExpanded, ViewGroup parent) {
        return mInflater.inflate(R.layout.key_list_group_item, null);
    }

    /**
     * Binds TextViews from group view to results from database group cursor.
     */
    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
        int userIdIndex = cursor.getColumnIndex(UserIds.USER_ID);

        TextView mainUserId = (TextView) view.findViewById(R.id.mainUserId);
        mainUserId.setText(R.string.unknownUserId);
        TextView mainUserIdRest = (TextView) view.findViewById(R.id.mainUserIdRest);
        mainUserIdRest.setText("");

        String userId = cursor.getString(userIdIndex);
        if (userId != null) {
            String[] userIdSplit = OtherHelper.splitUserId(userId);

            if (userIdSplit[1] != null) {
                mainUserIdRest.setText(userIdSplit[1]);
            }
            mainUserId.setText(userIdSplit[0]);
        }

        if (mainUserId.getText().length() == 0) {
            mainUserId.setText(R.string.unknownUserId);
        }

        if (mainUserIdRest.getText().length() == 0) {
            mainUserIdRest.setVisibility(View.GONE);
        }
    }

    /**
     * Inflate new view for child items
     */
    @Override
    public View newChildView(Context context, Cursor cursor, boolean isLastChild, ViewGroup parent) {
        // first entry is fingerprint
        if (cursor.getPosition() == 0) {
            return mInflater.inflate(R.layout.key_list_child_item_user_id, null);
        } else {
            // differentiate between keys and userIds in MergeCursor
            if (cursor.getColumnIndex(Keys.KEY_ID) != -1) {
                // other layout for master key
                int masterKeyIndex = cursor.getColumnIndex(Keys.IS_MASTER_KEY);
                if (cursor.getInt(masterKeyIndex) == 1) {
                    return mInflater.inflate(R.layout.key_list_child_item_master_key, null);
                } else {
                    return mInflater.inflate(R.layout.key_list_child_item_sub_key, null);
                }
            } else {
                return mInflater.inflate(R.layout.key_list_child_item_user_id, null);
            }
        }
    }

    /**
     * Bind TextViews from view of childs to query results
     */
    @Override
    protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {
        // first entry is fingerprint
        if (cursor.getPosition() == 0) {
            String fingerprint = PGPHelper.getFingerPrint(context,
                    cursor.getLong(cursor.getColumnIndex(Keys.KEY_ID)));
            fingerprint = fingerprint.replace("  ", "\n");

            TextView userId = (TextView) view.findViewById(R.id.userId);
            userId.setText(context.getString(R.string.fingerprint) + ":\n" + fingerprint);
        } else {
            // differentiate between keys and userIds in MergeCursor
            if (cursor.getColumnIndex(Keys.KEY_ID) != -1) {

                String keyIdStr = PGPHelper.getSmallFingerPrint(cursor.getLong(cursor
                        .getColumnIndex(Keys.KEY_ID)));
                String algorithmStr = PGPHelper.getAlgorithmInfo(
                        cursor.getInt(cursor.getColumnIndex(Keys.ALGORITHM)),
                        cursor.getInt(cursor.getColumnIndex(Keys.KEY_SIZE)));

                TextView keyId = (TextView) view.findViewById(R.id.keyId);
                keyId.setText(keyIdStr);

                TextView keyDetails = (TextView) view.findViewById(R.id.keyDetails);
                keyDetails.setText("(" + algorithmStr + ")");

                ImageView encryptIcon = (ImageView) view.findViewById(R.id.ic_encryptKey);
                if (cursor.getInt(cursor.getColumnIndex(Keys.CAN_ENCRYPT)) != 1) {
                    encryptIcon.setVisibility(View.GONE);
                }

                ImageView signIcon = (ImageView) view.findViewById(R.id.ic_signKey);
                if (cursor.getInt(cursor.getColumnIndex(Keys.CAN_SIGN)) != 1) {
                    signIcon.setVisibility(View.GONE);
                }
            } else {
                String userIdStr = cursor.getString(cursor.getColumnIndex(UserIds.USER_ID));

                TextView userId = (TextView) view.findViewById(R.id.userId);
                userId.setText(userIdStr);
            }
        }
    }

    /**
     * Given the group cursor, we start cursors for a fingerprint, keys, and userIds, which are
     * merged together and build the child cursor
     */
    @Override
    protected Cursor getChildrenCursor(Cursor groupCursor) {
        final long keyRingRowId = groupCursor.getLong(groupCursor.getColumnIndex(BaseColumns._ID));

        Cursor fingerprintCursor = getChildCursor(keyRingRowId, CHILD_FINGERPRINT);
        Cursor keyCursor = getChildCursor(keyRingRowId, CHILD_KEY);
        Cursor userIdCursor = getChildCursor(keyRingRowId, CHILD_USER_ID);

        MergeCursor mergeCursor = new MergeCursor(new Cursor[] { fingerprintCursor, keyCursor,
                userIdCursor });
        Log.d(Constants.TAG, "mergeCursor:" + DatabaseUtils.dumpCursorToString(mergeCursor));

        return mergeCursor;
    }

    /**
     * This builds a cursor for a specific type of children
     * 
     * @param keyRingRowId
     *            foreign row id of the keyRing
     * @param type
     * @return
     */
    private Cursor getChildCursor(long keyRingRowId, int type) {
        Uri uri = null;
        String[] projection = null;
        String sortOrder = null;
        String selection = null;

        switch (type) {
        case CHILD_FINGERPRINT:
            projection = new String[] { Keys._ID, Keys.KEY_ID, Keys.IS_MASTER_KEY, Keys.ALGORITHM,
                    Keys.KEY_SIZE, Keys.CAN_SIGN, Keys.CAN_ENCRYPT, };
            sortOrder = Keys.RANK + " ASC";

            // use only master key for fingerprint
            selection = Keys.IS_MASTER_KEY + " = 1 ";

            if (mKeyType == Id.type.public_key) {
                uri = Keys.buildPublicKeysUri(String.valueOf(keyRingRowId));
            } else {
                uri = Keys.buildSecretKeysUri(String.valueOf(keyRingRowId));
            }
            break;

        case CHILD_KEY:
            projection = new String[] { Keys._ID, Keys.KEY_ID, Keys.IS_MASTER_KEY, Keys.ALGORITHM,
                    Keys.KEY_SIZE, Keys.CAN_SIGN, Keys.CAN_ENCRYPT, };
            sortOrder = Keys.RANK + " ASC";

            if (mKeyType == Id.type.public_key) {
                uri = Keys.buildPublicKeysUri(String.valueOf(keyRingRowId));
            } else {
                uri = Keys.buildSecretKeysUri(String.valueOf(keyRingRowId));
            }

            break;

        case CHILD_USER_ID:
            projection = new String[] { UserIds._ID, UserIds.USER_ID, UserIds.RANK, };
            sortOrder = UserIds.RANK + " ASC";

            // not the main user id
            selection = UserIds.RANK + " > 0 ";

            if (mKeyType == Id.type.public_key) {
                uri = UserIds.buildPublicUserIdsUri(String.valueOf(keyRingRowId));
            } else {
                uri = UserIds.buildSecretUserIdsUri(String.valueOf(keyRingRowId));
            }

            break;

        default:
            return null;

        }

        return mContext.getContentResolver().query(uri, projection, selection, null, sortOrder);
    }

}