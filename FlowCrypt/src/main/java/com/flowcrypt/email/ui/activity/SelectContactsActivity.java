/*
 * © 2016-2018 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.flowcrypt.email.R;
import com.flowcrypt.email.database.dao.source.ContactsDaoSource;
import com.flowcrypt.email.js.PgpContact;
import com.flowcrypt.email.ui.activity.base.BaseBackStackActivity;
import com.flowcrypt.email.ui.adapter.ContactsListCursorAdapter;
import com.flowcrypt.email.util.GeneralUtil;
import com.flowcrypt.email.util.UIUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

/**
 * This activity can be used for select single or multiply contacts (not implemented yet) from the local database. The
 * activity returns {@link PgpContact} as a result.
 *
 * @author Denis Bondarenko
 * Date: 14.11.2017
 * Time: 17:23
 * E-mail: DenBond7@gmail.com
 */

public class SelectContactsActivity extends BaseBackStackActivity implements LoaderManager.LoaderCallbacks<Cursor>,
    AdapterView.OnItemClickListener, SearchView.OnQueryTextListener {
  public static final String KEY_EXTRA_PGP_CONTACT =
      GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_PGP_CONTACT", SelectContactsActivity.class);

  public static final String KEY_EXTRA_PGP_CONTACTS =
      GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_PGP_CONTACTS", SelectContactsActivity.class);

  private static final String KEY_EXTRA_TITLE =
      GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_TITLE", SelectContactsActivity.class);
  private static final String KEY_EXTRA_IS_MULTIPLY =
      GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_IS_MULTIPLY", SelectContactsActivity.class);

  private View progressBar;
  private ListView listView;
  private View emptyView;
  private ContactsListCursorAdapter adapter;
  private String searchPattern;

  public static Intent newIntent(Context context, String title, boolean isMultiply) {
    Intent intent = new Intent(context, SelectContactsActivity.class);
    intent.putExtra(KEY_EXTRA_TITLE, title);
    intent.putExtra(KEY_EXTRA_IS_MULTIPLY, isMultiply);
    return intent;
  }

  @Override
  public int getContentViewResourceId() {
    return R.layout.activity_contacts_settings;
  }

  @Override
  public View getRootView() {
    return null;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final boolean isMultiply = getIntent().getBooleanExtra(KEY_EXTRA_IS_MULTIPLY, false);
    final String title = getIntent().getStringExtra(KEY_EXTRA_TITLE);

    this.adapter = new ContactsListCursorAdapter(this, null, false, null, false);

    this.progressBar = findViewById(R.id.progressBar);
    this.emptyView = findViewById(R.id.emptyView);
    this.listView = findViewById(R.id.listViewContacts);
    this.listView.setAdapter(adapter);
    this.listView.setChoiceMode(isMultiply ? ListView.CHOICE_MODE_MULTIPLE : ListView.CHOICE_MODE_SINGLE);
    if (!isMultiply) {
      this.listView.setOnItemClickListener(this);
    }

    if (!TextUtils.isEmpty(title) && getSupportActionBar() != null) {
      getSupportActionBar().setTitle(title);
    }

    LoaderManager.getInstance(this).initLoader(R.id.loader_id_load_contacts_with_has_pgp_true, null, this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.activity_select_contact, menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem searchItem = menu.findItem(R.id.menuSearch);
    SearchView searchView = (SearchView) searchItem.getActionView();
    if (!TextUtils.isEmpty(searchPattern)) {
      searchItem.expandActionView();
    }
    searchView.setQuery(searchPattern, true);
    searchView.setQueryHint(getString(R.string.search));
    searchView.setOnQueryTextListener(this);
    searchView.clearFocus();
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  @NonNull
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    switch (id) {
      case R.id.loader_id_load_contacts_with_has_pgp_true:
        String selection = ContactsDaoSource.COL_HAS_PGP + " = ?";
        String[] selectionArgs = new String[]{"1"};

        if (!TextUtils.isEmpty(searchPattern)) {
          selection = ContactsDaoSource.COL_HAS_PGP + " = ? AND ( " + ContactsDaoSource.COL_EMAIL + " " +
              "LIKE ? OR " + ContactsDaoSource.COL_NAME + " " + " LIKE ? )";
          selectionArgs = new String[]{"1", "%" + searchPattern + "%", "%" + searchPattern + "%"};
        }

        Uri uri = new ContactsDaoSource().getBaseContentUri();

        return new CursorLoader(this, uri, null, selection, selectionArgs, null);

      default:
        return new Loader<>(this);
    }
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
    switch (loader.getId()) {
      case R.id.loader_id_load_contacts_with_has_pgp_true:
        UIUtil.exchangeViewVisibility(this, false, progressBar, listView);

        if (data != null && data.getCount() > 0) {
          emptyView.setVisibility(View.GONE);
          adapter.swapCursor(data);
        } else {
          UIUtil.exchangeViewVisibility(this, true, emptyView, listView);
        }
        break;
    }
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    switch (loader.getId()) {
      case R.id.loader_id_load_contacts_with_has_pgp_true:
        adapter.swapCursor(null);
        break;
    }
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    Cursor cursor = (Cursor) parent.getAdapter().getItem(position);
    PgpContact pgpContact = new ContactsDaoSource().getCurrentPgpContact(cursor);

    Intent intentResult = new Intent();
    intentResult.putExtra(KEY_EXTRA_PGP_CONTACT, pgpContact);
    setResult(RESULT_OK, intentResult);
    finish();
  }

  @Override
  public boolean onQueryTextSubmit(String query) {
    this.searchPattern = query;
    LoaderManager.getInstance(this).restartLoader(R.id.loader_id_load_contacts_with_has_pgp_true, null, this);
    return true;
  }

  @Override
  public boolean onQueryTextChange(String newText) {
    this.searchPattern = newText;
    LoaderManager.getInstance(this).restartLoader(R.id.loader_id_load_contacts_with_has_pgp_true, null, this);
    return true;
  }
}
