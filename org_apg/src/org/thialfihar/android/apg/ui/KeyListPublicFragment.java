/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2010 Thialfihar <thi@thialfihar.org>
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

package org.thialfihar.android.apg.ui;

import org.spongycastle.openpgp.PGPPublicKeyRing;
import org.thialfihar.android.apg.Id;
import org.thialfihar.android.apg.R;
import org.thialfihar.android.apg.helper.PGPHelper;
import org.thialfihar.android.apg.provider.ProviderHelper;
import org.thialfihar.android.apg.provider.ApgContract.KeyRings;
import org.thialfihar.android.apg.provider.ApgContract.UserIds;
import org.thialfihar.android.apg.ui.widget.KeyListAdapter;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.app.LoaderManager;
import android.view.ContextMenu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;

public class KeyListPublicFragment extends KeyListFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private KeyListPublicActivity mKeyListPublicActivity;

    private KeyListAdapter mAdapter;

    /**
     * Define Adapter and Loader on create of Activity
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mKeyListPublicActivity = (KeyListPublicActivity) getActivity();

        mAdapter = new KeyListAdapter(mKeyListPublicActivity, null, Id.type.public_key);
        setListAdapter(mAdapter);

        // Start out with a progress indicator.
        setListShown(false);

        // Prepare the loader. Either re-connect with an existing one,
        // or start a new one.
        // id is -1 as the child cursors are numbered 0,...,n
        getLoaderManager().initLoader(-1, null, this);
    }

    /**
     * Context Menu on Long Click
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        // TODO: better inflate menu from xml
        // android.view.MenuInflater inflater = (android.view.MenuInflater) mActivity
        // .getMenuInflater();
        // menu.setHeaderTitle(R.string.checkbox_list_context_title);
        // inflater.inflate(R.menu.checkbox_list_context, menu);

        menu.add(0, Id.menu.update, 1, R.string.menu_updateKey);
        menu.add(0, Id.menu.exportToServer, 1, R.string.menu_exportKeyToServer);
        menu.add(0, Id.menu.signKey, 1, R.string.menu_signKey);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        ExpandableListContextMenuInfo expInfo = (ExpandableListContextMenuInfo) item.getMenuInfo();

        // expInfo.id would also return row id of childs, but we always want to get the row id of
        // the group item, thus we are using the following way
        int groupPosition = ExpandableListView.getPackedPositionGroup(expInfo.packedPosition);
        long keyRingRowId = getExpandableListAdapter().getGroupId(groupPosition);

        switch (item.getItemId()) {
        case Id.menu.update:
            long updateKeyId = 0;
            PGPPublicKeyRing updateKeyRing = ProviderHelper.getPGPPublicKeyRing(mKeyListActivity,
                    keyRingRowId);
            if (updateKeyRing != null) {
                updateKeyId = PGPHelper.getMasterKey(updateKeyRing).getKeyID();
            }
            if (updateKeyId == 0) {
                // this shouldn't happen
                return true;
            }

            Intent queryIntent = new Intent(mKeyListActivity, KeyServerQueryActivity.class);
            queryIntent.setAction(KeyServerQueryActivity.ACTION_LOOK_UP_KEY_ID_AND_RETURN);
            queryIntent.putExtra(KeyServerQueryActivity.EXTRA_KEY_ID, updateKeyId);

            // TODO: lookup??
            startActivityForResult(queryIntent, Id.request.look_up_key_id);

            return true;

        case Id.menu.exportToServer:
            // TODO: do it better directly with keyRingRowId?
            long masterKeyId = ProviderHelper.getPublicMasterKeyId(mKeyListActivity, keyRingRowId);

            Intent uploadIntent = new Intent(mKeyListActivity, KeyServerUploadActivity.class);
            uploadIntent.setAction(KeyServerUploadActivity.ACTION_EXPORT_KEY_TO_SERVER);
            uploadIntent.putExtra(KeyServerUploadActivity.EXTRA_KEY_ID, masterKeyId);
            startActivityForResult(uploadIntent, Id.request.export_to_server);

            return true;

        case Id.menu.signKey:
            long keyId = 0;
            PGPPublicKeyRing signKeyRing = ProviderHelper.getPGPPublicKeyRing(mKeyListActivity,
                    keyRingRowId);
            if (signKeyRing != null) {
                keyId = PGPHelper.getMasterKey(signKeyRing).getKeyID();
            }
            if (keyId == 0) {
                // this shouldn't happen
                return true;
            }

            Intent signIntent = new Intent(mKeyListActivity, SignKeyActivity.class);
            signIntent.putExtra(SignKeyActivity.EXTRA_KEY_ID, keyId);
            startActivity(signIntent);

            return true;

        default:
            return super.onContextItemSelected(item);

        }
    }

    // These are the rows that we will retrieve.
    static final String[] PROJECTION = new String[] { KeyRings._ID, KeyRings.MASTER_KEY_ID,
            UserIds.USER_ID };

    static final String SORT_ORDER = UserIds.USER_ID + " ASC";

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created. This
        // sample only has one Loader, so we don't care about the ID.
        Uri baseUri = KeyRings.buildPublicKeyRingsUri();

        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        return new CursorLoader(getActivity(), baseUri, PROJECTION, null, null, SORT_ORDER);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in. (The framework will take care of closing the
        // old cursor once we return.)
        mAdapter.setGroupCursor(data);

        // The list should now be shown.
        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no
        // longer using it.
        mAdapter.setGroupCursor(null);
    }

}
