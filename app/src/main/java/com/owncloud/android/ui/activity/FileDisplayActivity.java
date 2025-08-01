/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2023-2024 TSI-mc <surinder.kumar@t-systems.com>
 * SPDX-FileCopyrightText: 2023 Archontis E. Kostis <arxontisk02@gmail.com>
 * SPDX-FileCopyrightText: 2019 Chris Narkiewicz <hello@ezaquarii.com>
 * SPDX-FileCopyrightText: 2018-2022 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2018-2020 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2016 ownCloud Inc.
 * SPDX-FileCopyrightText: 2012-2013 David A. Velasco <dvelasco@solidgear.es>
 * SPDX-FileCopyrightText: 2011 Bartosz Przybylski <bart.p.pl@gmail.com>
 * SPDX-License-Identifier: GPL-2.0-only AND (AGPL-3.0-or-later OR GPL-2.0-only)
 */
package com.owncloud.android.ui.activity;

import android.accounts.Account;
import android.accounts.AuthenticatorException;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources.NotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nextcloud.appReview.InAppReviewHelper;
import com.nextcloud.client.account.User;
import com.nextcloud.client.appinfo.AppInfo;
import com.nextcloud.client.core.AsyncRunner;
import com.nextcloud.client.core.Clock;
import com.nextcloud.client.di.Injectable;
import com.nextcloud.client.editimage.EditImageActivity;
import com.nextcloud.client.files.DeepLinkHandler;
import com.nextcloud.client.jobs.download.FileDownloadHelper;
import com.nextcloud.client.jobs.download.FileDownloadWorker;
import com.nextcloud.client.jobs.upload.FileUploadHelper;
import com.nextcloud.client.jobs.upload.FileUploadWorker;
import com.nextcloud.client.media.PlayerServiceConnection;
import com.nextcloud.client.network.ClientFactory;
import com.nextcloud.client.preferences.AppPreferences;
import com.nextcloud.client.utils.IntentUtil;
import com.nextcloud.model.WorkerState;
import com.nextcloud.model.WorkerStateLiveData;
import com.nextcloud.utils.extensions.ActivityExtensionsKt;
import com.nextcloud.utils.extensions.BundleExtensionsKt;
import com.nextcloud.utils.extensions.FileExtensionsKt;
import com.nextcloud.utils.extensions.IntentExtensionsKt;
import com.nextcloud.utils.extensions.ViewExtensionsKt;
import com.nextcloud.utils.fileNameValidator.FileNameValidator;
import com.nextcloud.utils.view.FastScrollUtils;
import com.owncloud.android.MainApp;
import com.owncloud.android.R;
import com.owncloud.android.databinding.FilesBinding;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.datamodel.SyncedFolderProvider;
import com.owncloud.android.datamodel.VirtualFolderType;
import com.owncloud.android.files.services.NameCollisionPolicy;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.files.RestoreFileVersionRemoteOperation;
import com.owncloud.android.lib.resources.files.SearchRemoteOperation;
import com.owncloud.android.lib.resources.notifications.GetNotificationsRemoteOperation;
import com.owncloud.android.lib.resources.notifications.models.Notification;
import com.owncloud.android.operations.CopyFileOperation;
import com.owncloud.android.operations.CreateFolderOperation;
import com.owncloud.android.operations.DownloadType;
import com.owncloud.android.operations.MoveFileOperation;
import com.owncloud.android.operations.RefreshFolderOperation;
import com.owncloud.android.operations.RemoveFileOperation;
import com.owncloud.android.operations.RenameFileOperation;
import com.owncloud.android.operations.SynchronizeFileOperation;
import com.owncloud.android.operations.UploadFileOperation;
import com.owncloud.android.syncadapter.FileSyncAdapter;
import com.owncloud.android.ui.CompletionCallback;
import com.owncloud.android.ui.asynctasks.CheckAvailableSpaceTask;
import com.owncloud.android.ui.asynctasks.FetchRemoteFileTask;
import com.owncloud.android.ui.asynctasks.GetRemoteFileTask;
import com.owncloud.android.ui.dialog.SendShareDialog;
import com.owncloud.android.ui.dialog.SortingOrderDialogFragment;
import com.owncloud.android.ui.dialog.StoragePermissionDialogFragment;
import com.owncloud.android.ui.dialog.TermsOfServiceDialog;
import com.owncloud.android.ui.events.SearchEvent;
import com.owncloud.android.ui.events.SyncEventFinished;
import com.owncloud.android.ui.events.TokenPushEvent;
import com.owncloud.android.ui.fragment.FileDetailFragment;
import com.owncloud.android.ui.fragment.FileFragment;
import com.owncloud.android.ui.fragment.GalleryFragment;
import com.owncloud.android.ui.fragment.GroupfolderListFragment;
import com.owncloud.android.ui.fragment.OCFileListFragment;
import com.owncloud.android.ui.fragment.SearchType;
import com.owncloud.android.ui.fragment.SharedListFragment;
import com.owncloud.android.ui.fragment.TaskRetainerFragment;
import com.owncloud.android.ui.fragment.UnifiedSearchFragment;
import com.owncloud.android.ui.helpers.FileOperationsHelper;
import com.owncloud.android.ui.helpers.UriUploader;
import com.owncloud.android.ui.interfaces.TransactionInterface;
import com.owncloud.android.ui.preview.PreviewImageActivity;
import com.owncloud.android.ui.preview.PreviewImageFragment;
import com.owncloud.android.ui.preview.PreviewMediaActivity;
import com.owncloud.android.ui.preview.PreviewMediaFragment;
import com.owncloud.android.ui.preview.PreviewTextFileFragment;
import com.owncloud.android.ui.preview.PreviewTextFragment;
import com.owncloud.android.ui.preview.PreviewTextStringFragment;
import com.owncloud.android.ui.preview.pdf.PreviewPdfFragment;
import com.owncloud.android.utils.DataHolderUtil;
import com.owncloud.android.utils.DisplayUtils;
import com.owncloud.android.utils.ErrorMessageAdapter;
import com.owncloud.android.utils.FileSortOrder;
import com.owncloud.android.utils.MimeTypeUtil;
import com.owncloud.android.utils.PermissionUtil;
import com.owncloud.android.utils.PushUtils;
import com.owncloud.android.utils.StringUtils;
import com.owncloud.android.utils.theme.CapabilityUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import kotlin.Unit;

import static com.owncloud.android.datamodel.OCFile.PATH_SEPARATOR;
import static com.owncloud.android.utils.PermissionUtil.PERMISSION_CHOICE_DIALOG_TAG;

/**
 * Displays, what files the user has available in his Nextcloud. This is the main view.
 */
public class FileDisplayActivity extends FileActivity
    implements FileFragment.ContainerActivity,
    OnEnforceableRefreshListener, SortingOrderDialogFragment.OnSortingOrderListener,
    SendShareDialog.SendShareDialogDownloader, Injectable {

    public static final String RESTART = "RESTART";
    public static final String ALL_FILES = "ALL_FILES";
    public static final String LIST_GROUPFOLDERS = "LIST_GROUPFOLDERS";
    public static final int SINGLE_USER_SIZE = 1;
    public static final String OPEN_FILE = "NC_OPEN_FILE";

    private FilesBinding binding;

    private SyncBroadcastReceiver mSyncBroadcastReceiver;
    private UploadFinishReceiver mUploadFinishReceiver;
    private DownloadFinishReceiver mDownloadFinishReceiver;
    private RemoteOperationResult mLastSslUntrustedServerResult;
    @Inject LocalBroadcastManager localBroadcastManager;

    public static final String TAG_PUBLIC_LINK = "PUBLIC_LINK";
    public static final String FTAG_CHOOSER_DIALOG = "CHOOSER_DIALOG";
    public static final String KEY_FILE_ID = "KEY_FILE_ID";
    public static final String KEY_FILE_PATH = "KEY_FILE_PATH";
    public static final String KEY_ACCOUNT = "KEY_ACCOUNT";
    public static final String KEY_IS_SORT_GROUP_VISIBLE = "KEY_IS_SORT_GROUP_VISIBLE";

    private static final String KEY_WAITING_TO_PREVIEW = "WAITING_TO_PREVIEW";
    private static final String KEY_SYNC_IN_PROGRESS = "SYNC_IN_PROGRESS";
    private static final String KEY_WAITING_TO_SEND = "WAITING_TO_SEND";
    private static final String DIALOG_TAG_SHOW_TOS = "DIALOG_TAG_SHOW_TOS";

    public static final String ACTION_DETAILS = "com.owncloud.android.ui.activity.action.DETAILS";

    public static final int REQUEST_CODE__SELECT_CONTENT_FROM_APPS = REQUEST_CODE__LAST_SHARED + 1;
    public static final int REQUEST_CODE__SELECT_FILES_FROM_FILE_SYSTEM = REQUEST_CODE__LAST_SHARED + 2;
    public static final int REQUEST_CODE__MOVE_OR_COPY_FILES = REQUEST_CODE__LAST_SHARED + 3;
    public static final int REQUEST_CODE__UPLOAD_FROM_CAMERA = REQUEST_CODE__LAST_SHARED + 5;
    public static final int REQUEST_CODE__UPLOAD_FROM_VIDEO_CAMERA = REQUEST_CODE__LAST_SHARED + 6;

    protected static final long DELAY_TO_REQUEST_REFRESH_OPERATION_LATER = DELAY_TO_REQUEST_OPERATIONS_LATER + 350;

    private static final String TAG = FileDisplayActivity.class.getSimpleName();

    public static final String TAG_LIST_OF_FILES = "LIST_OF_FILES";

    public static final String TEXT_PREVIEW = "TEXT_PREVIEW";

    private OCFile mWaitingToPreview;

    private boolean mSyncInProgress;

    private OCFile mWaitingToSend;

    private Collection<MenuItem> mDrawerMenuItemstoShowHideList;

    public static final String KEY_IS_SEARCH_OPEN = "IS_SEARCH_OPEN";
    public static final String KEY_SEARCH_QUERY = "SEARCH_QUERY";

    public static final String REFRESH_FOLDER_EVENT_RECEIVER = "REFRESH_FOLDER_EVENT";

    private String searchQuery = "";
    private boolean searchOpen;

    private SearchView searchView;
    private PlayerServiceConnection mPlayerConnection;
    private User lastDisplayedUser = null;

    @Inject AppPreferences preferences;

    @Inject AppInfo appInfo;

    @Inject InAppReviewHelper inAppReviewHelper;

    @Inject FastScrollUtils fastScrollUtils;
    @Inject AsyncRunner asyncRunner;
    @Inject Clock clock;
    @Inject SyncedFolderProvider syncedFolderProvider;

    /**
     * Indicates whether the downloaded file should be previewed immediately. Since `FileDownloadWorker` can be
     * triggered from multiple sources, this helps determine if an automatic preview is needed after download.
     */
    private long fileIDForImmediatePreview = -1;

    public void setFileIDForImmediatePreview(long fileIDForImmediatePreview) {
        this.fileIDForImmediatePreview = fileIDForImmediatePreview;
    }

    public static Intent openFileIntent(Context context, User user, OCFile file) {
        final Intent intent = new Intent(context, PreviewImageActivity.class);
        intent.putExtra(FileActivity.EXTRA_FILE, file);
        intent.putExtra(FileActivity.EXTRA_USER, user);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log_OC.v(TAG, "onCreate() start");
        // Set the default theme to replace the launch screen theme.
        setTheme(R.style.Theme_ownCloud_Toolbar_Drawer);

        super.onCreate(savedInstanceState);
        loadSavedInstanceState(savedInstanceState);

        /// USER INTERFACE
        initLayout();
        initUI();
        initTaskRetainerFragment();

        // Restoring after UI has been inflated.
        if (savedInstanceState != null) {
            showSortListGroup(savedInstanceState.getBoolean(KEY_IS_SORT_GROUP_VISIBLE));
        }

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            handleOpenFileViaIntent(getIntent());
        }

        mPlayerConnection = new PlayerServiceConnection(this);

        checkStoragePath();

        initSyncBroadcastReceiver();
        observeWorkerState();
        registerRefreshFolderEventReceiver();
    }

    @SuppressWarnings("unchecked")
    private void loadSavedInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mWaitingToPreview = BundleExtensionsKt.getParcelableArgument(savedInstanceState, KEY_WAITING_TO_PREVIEW, OCFile.class);
            mSyncInProgress = savedInstanceState.getBoolean(KEY_SYNC_IN_PROGRESS);
            mWaitingToSend = BundleExtensionsKt.getParcelableArgument(savedInstanceState, KEY_WAITING_TO_SEND, OCFile.class);
            searchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
            searchOpen = savedInstanceState.getBoolean(FileDisplayActivity.KEY_IS_SEARCH_OPEN, false);
        } else {
            mWaitingToPreview = null;
            mSyncInProgress = false;
            mWaitingToSend = null;
        }
    }

    private void initLayout() {
        // Inflate and set the layout view
        binding = FilesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }

    private void initUI() {
        setupHomeSearchToolbarWithSortAndListButtons();
        mMenuButton.setOnClickListener(v -> openDrawer());
        mSwitchAccountButton.setOnClickListener(v -> showManageAccountsDialog());
        mNotificationButton.setOnClickListener(v -> startActivity(NotificationsActivity.class));
        fastScrollUtils.fixAppBarForFastScroll(binding.appbar.appbar, binding.rootLayout);
    }

    private void initTaskRetainerFragment() {
        // Init Fragment without UI to retain AsyncTask across configuration changes
        FragmentManager fm = getSupportFragmentManager();
        TaskRetainerFragment taskRetainerFragment = (TaskRetainerFragment) fm.findFragmentByTag(TaskRetainerFragment.FTAG_TASK_RETAINER_FRAGMENT);
        if (taskRetainerFragment == null) {
            taskRetainerFragment = new TaskRetainerFragment();
            fm.beginTransaction().add(taskRetainerFragment, TaskRetainerFragment.FTAG_TASK_RETAINER_FRAGMENT).commit();
        }   // else, Fragment already created and retained across configuration change
    }

    private void checkStoragePath() {
        String newStorage = Environment.getExternalStorageDirectory().getAbsolutePath();
        String storagePath = preferences.getStoragePath(newStorage);
        if (!preferences.isStoragePathValid() && !new File(storagePath).exists()) {
            // falling back to default
            preferences.setStoragePath(newStorage);
            preferences.setStoragePathValid();
            MainApp.setStoragePath(newStorage);

            try {
                final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.Theme_ownCloud_Dialog)
                    .setTitle(R.string.wrong_storage_path)
                    .setMessage(R.string.wrong_storage_path_desc)
                    .setPositiveButton(R.string.dialog_close, (dialog, which) -> dialog.dismiss())
                    .setIcon(R.drawable.ic_settings);

                viewThemeUtils.dialog.colorMaterialAlertDialogBackground(getApplicationContext(), builder);

                builder.create().show();
            } catch (WindowManager.BadTokenException e) {
                Log_OC.e(TAG, "Error showing wrong storage info, so skipping it: " + e.getMessage());
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            StoragePermissionDialogFragment fragment = (StoragePermissionDialogFragment) getSupportFragmentManager().findFragmentByTag(PERMISSION_CHOICE_DIALOG_TAG);
            if (fragment != null) {
                Dialog dialog = fragment.getDialog();

                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                    getSupportFragmentManager().beginTransaction().remove(fragment).commitNowAllowingStateLoss();
                    PermissionUtil.requestExternalStoragePermission(this, viewThemeUtils);
                }
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // handle notification permission on API level >= 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // request notification permission first and then prompt for storage permissions
            // storage permissions handled in onRequestPermissionsResult
            PermissionUtil.requestNotificationPermission(this);
        } else {
            PermissionUtil.requestExternalStoragePermission(this, viewThemeUtils);
        }

        if (IntentExtensionsKt.getParcelableArgument(getIntent(), OCFileListFragment.SEARCH_EVENT, SearchEvent.class) != null) {
            switchToSearchFragment(savedInstanceState);
            setupDrawer();
        } else {
            createMinFragments(savedInstanceState);
            syncAndUpdateFolder(true);
        }

        if (OPEN_FILE.equals(getIntent().getAction())) {
            getSupportFragmentManager().executePendingTransactions();
            onOpenFileIntent(getIntent());
        } else if (RESTART.equals(getIntent().getAction())) {
            // most likely switched to different account
            DisplayUtils.showSnackMessage(this, String.format(getString(R.string.logged_in_as), accountManager.getUser().getAccountName()));
        }

        upgradeNotificationForInstantUpload();
        checkOutdatedServer();
        checkNotifications();
    }

    private Activity getActivity() {
        return this;
    }

    /**
     * For Android 7+. Opens a pop up info for the new instant upload and disabled the old instant upload.
     */
    private void upgradeNotificationForInstantUpload() {
        // check for Android 6+ if legacy instant upload is activated --> disable + show info
        if (preferences.instantPictureUploadEnabled() || preferences.instantVideoUploadEnabled()) {
            preferences.removeLegacyPreferences();
            // show info pop-up
            new MaterialAlertDialogBuilder(this, R.style.Theme_ownCloud_Dialog).setTitle(R.string.drawer_synced_folders).setMessage(R.string.synced_folders_new_info).setPositiveButton(R.string.drawer_open, (dialog, which) -> {
                // show instant upload
                Intent syncedFoldersIntent = new Intent(getApplicationContext(), SyncedFoldersActivity.class);
                dialog.dismiss();
                startActivity(syncedFoldersIntent);
            }).setNegativeButton(R.string.drawer_close, (dialog, which) -> dialog.dismiss()).setIcon(R.drawable.nav_synced_folders).show();
        }
    }

    private void checkOutdatedServer() {
        Optional<User> user = getUser();
        // show outdated warning
        if (user.isPresent() && CapabilityUtils.checkOutdatedWarning(getResources(), user.get().getServer().getVersion(), getCapabilities().getExtendedSupport().isTrue())) {
            DisplayUtils.showServerOutdatedSnackbar(this, Snackbar.LENGTH_LONG);
        }
    }
    
    private void checkNotifications() {
        new Thread(() -> {
            try {
                RemoteOperationResult<List<Notification>> result = new GetNotificationsRemoteOperation()
                    .execute(clientFactory.createNextcloudClient(accountManager.getUser()));
                
                if (result.isSuccess() && !result.getResultData().isEmpty()) {
                    runOnUiThread(() -> mNotificationButton.setVisibility(View.VISIBLE));
                } else {
                    runOnUiThread(() -> mNotificationButton.setVisibility(View.GONE));
                }
                
            } catch (ClientFactory.CreationException e) {
                Log_OC.e(TAG, "Could not fetch notifications!");
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PermissionUtil.PERMISSIONS_POST_NOTIFICATIONS:
                // handle notification permission on API level >= 33
                // dialogue was dismissed -> prompt for storage permissions
                PermissionUtil.requestExternalStoragePermission(this, viewThemeUtils);
                break;
            case PermissionUtil.PERMISSIONS_EXTERNAL_STORAGE:
                // If request is cancelled, result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted
                    EventBus.getDefault().post(new TokenPushEvent());
                    syncAndUpdateFolder(true);
                    // toggle on is save since this is the only scenario this code gets accessed
                }
                break;
            case PermissionUtil.PERMISSIONS_CAMERA:
                // If request is cancelled, result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted
                    getOCFileListFragmentFromFile(OCFileListFragment::directCameraUpload);
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void switchToSearchFragment(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            OCFileListFragment listOfFiles = new OCFileListFragment();
            Bundle args = new Bundle();

            args.putParcelable(OCFileListFragment.SEARCH_EVENT,
                               IntentExtensionsKt.getParcelableArgument(getIntent(),
                                                                        OCFileListFragment.SEARCH_EVENT,
                                                                        SearchEvent.class));
            args.putBoolean(OCFileListFragment.ARG_ALLOW_CONTEXTUAL_ACTIONS, true);

            listOfFiles.setArguments(args);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.left_fragment_container, listOfFiles, TAG_LIST_OF_FILES);
            transaction.commit();
        } else {
            getSupportFragmentManager().findFragmentByTag(TAG_LIST_OF_FILES);
        }
    }

    private void createMinFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            OCFileListFragment listOfFiles = new OCFileListFragment();
            Bundle args = new Bundle();
            args.putBoolean(OCFileListFragment.ARG_ALLOW_CONTEXTUAL_ACTIONS, true);
            listOfFiles.setArguments(args);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.left_fragment_container, listOfFiles, TAG_LIST_OF_FILES);
            transaction.commit();
        } else {
            getSupportFragmentManager().findFragmentByTag(TAG_LIST_OF_FILES);
        }
    }

    private void initFragments() {
        /// First fragment
        OCFileListFragment listOfFiles = getListOfFilesFragment();
        if (listOfFiles != null && TextUtils.isEmpty(searchQuery)) {
            listOfFiles.listDirectory(getCurrentDir(), getFile(), MainApp.isOnlyOnDevice(), false);
        } else {
            Log_OC.e(TAG, "Still have a chance to lose the initialization of list fragment >(");
        }

        /// reset views
        resetTitleBarAndScrolling();
    }

    // Is called with the flag FLAG_ACTIVITY_SINGLE_TOP and set the new file and intent
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (ACTION_DETAILS.equalsIgnoreCase(intent.getAction())) {
            OCFile file = IntentExtensionsKt.getParcelableArgument(intent, EXTRA_FILE, OCFile.class);
            setFile(file);
            setIntent(intent);
            showDetails(file);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            handleOpenFileViaIntent(intent);
        } else if (OPEN_FILE.equals(intent.getAction())) {
            onOpenFileIntent(intent);
        } else if (RESTART.equals(intent.getAction())) {
            finish();
            startActivity(intent);
        } else {
            // Verify the action and get the query
            if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
                setIntent(intent);

                SearchEvent searchEvent = IntentExtensionsKt.getParcelableArgument(intent, OCFileListFragment.SEARCH_EVENT, SearchEvent.class);
                if (searchEvent != null) {
                    if (SearchRemoteOperation.SearchType.PHOTO_SEARCH == searchEvent.getSearchType()) {
                        Log_OC.d(this, "Switch to photo search fragment");

                        GalleryFragment photoFragment = new GalleryFragment();
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(OCFileListFragment.SEARCH_EVENT, searchEvent);
                        photoFragment.setArguments(bundle);
                        setLeftFragment(photoFragment);
                    } else if (searchEvent.getSearchType() == SearchRemoteOperation.SearchType.SHARED_FILTER) {
                        Log_OC.d(this, "Switch to shared fragment");
                        SharedListFragment sharedListFragment = new SharedListFragment();
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(OCFileListFragment.SEARCH_EVENT, searchEvent);
                        sharedListFragment.setArguments(bundle);
                        setLeftFragment(sharedListFragment);
                    } else {
                        Log_OC.d(this, "Switch to oc file search fragment");

                        OCFileListFragment photoFragment = new OCFileListFragment();
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(OCFileListFragment.SEARCH_EVENT, searchEvent);
                        photoFragment.setArguments(bundle);
                        setLeftFragment(photoFragment);
                    }
                }
            } else if (ALL_FILES.equals(intent.getAction())) {
                Log_OC.d(this, "Switch to oc file fragment");
                DrawerActivity.menuItemId = R.id.nav_all_files;
                setLeftFragment(new OCFileListFragment());
                getSupportFragmentManager().executePendingTransactions();
                browseToRoot();
            } else if (LIST_GROUPFOLDERS.equals(intent.getAction())) {
                Log_OC.d(this, "Switch to list groupfolders fragment");
                DrawerActivity.menuItemId = R.id.nav_groupfolders;
                setLeftFragment(new GroupfolderListFragment());
                getSupportFragmentManager().executePendingTransactions();
            }
        }
    }

    private void onOpenFileIntent(Intent intent) {
        String extra = intent.getStringExtra(EXTRA_FILE);
        OCFile file = getStorageManager().getFileByDecryptedRemotePath(extra);
        if (file != null) {
            OCFileListFragment fileFragment;
            final Fragment leftFragment = getLeftFragment();
            if (leftFragment instanceof OCFileListFragment) {
                fileFragment = (OCFileListFragment) leftFragment;
            } else {
                fileFragment = new OCFileListFragment();
                setLeftFragment(fileFragment);
            }
            fileFragment.onItemClicked(file);
        }
    }

    /**
     * Replaces the first fragment managed by the activity with the received as a parameter.
     *
     * @param fragment New Fragment to set.
     */
    private void setLeftFragment(Fragment fragment) {
        setLeftFragment(fragment, true);
    }

    private void setLeftFragment(Fragment fragment, boolean showSortListGroup) {
        prepareFragmentBeforeCommit(showSortListGroup);
        commitFragment(fragment, isFragmentCommitted -> Log_OC.d(TAG, "Left fragment committed: " + isFragmentCommitted));
    }

    private void prepareFragmentBeforeCommit(boolean showSortListGroup) {
        if (searchView != null) {
            searchView.post(() -> searchView.setQuery(searchQuery, true));
        }
        setDrawerIndicatorEnabled(false);

        //clear the subtitle while navigating to any other screen from Media screen
        clearToolbarSubtitle();

        showSortListGroup(showSortListGroup);
    }

    private void commitFragment(@NonNull Fragment fragment, CompletionCallback callback) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (ActivityExtensionsKt.isActive(this) && !fragmentManager.isDestroyed()) {
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.addToBackStack(null);
            transaction.replace(R.id.left_fragment_container, fragment, TAG_LIST_OF_FILES);
            transaction.commit();
            callback.onComplete(true);
        } else {
            callback.onComplete(false);
        }
    }

    private void getOCFileListFragmentFromFile(TransactionInterface transaction) {
        final Fragment leftFragment = getLeftFragment();

        if (leftFragment instanceof OCFileListFragment result) {
            transaction.onOCFileListFragmentComplete(result);
            return;
        }

        OCFileListFragment listOfFiles = new OCFileListFragment();
        Bundle args = new Bundle();
        args.putBoolean(OCFileListFragment.ARG_ALLOW_CONTEXTUAL_ACTIONS, true);
        listOfFiles.setArguments(args);

        runOnUiThread(() -> {
            FragmentManager fm = getSupportFragmentManager();
            if (!fm.isStateSaved() && !fm.isDestroyed()) {
                prepareFragmentBeforeCommit(true);
                commitFragment(listOfFiles, isFragmentCommitted -> {
                    if (isFragmentCommitted) {
                        Log_OC.d(TAG, "OCFileListFragment committed, executing pending transaction");
                        fm.executePendingTransactions();
                        transaction.onOCFileListFragmentComplete(listOfFiles);
                    } else {
                        Log_OC.d(TAG, "OCFileListFragment not committed, skipping executing pending transaction");
                    }
                });
            }
        });
    }

    public void showFileActions(OCFile file) {
        dismissLoadingDialog();
        getOCFileListFragmentFromFile(listOfFiles -> {
            browseUp(listOfFiles);
            listOfFiles.onOverflowIconClicked(file, null);
        });
    }

    public @Nullable Fragment getLeftFragment() {
        return getSupportFragmentManager().findFragmentByTag(FileDisplayActivity.TAG_LIST_OF_FILES);
    }

    public @Nullable
    @Deprecated OCFileListFragment getListOfFilesFragment() {
        Fragment listOfFiles = getSupportFragmentManager().findFragmentByTag(FileDisplayActivity.TAG_LIST_OF_FILES);
        if (listOfFiles instanceof OCFileListFragment) {
            return (OCFileListFragment) listOfFiles;
        }
        Log_OC.e(TAG, "Access to unexisting list of files fragment!!");
        return null;
    }

    protected void resetTitleBarAndScrolling() {
        updateActionBarTitleAndHomeButton(null);
        resetScrolling(true);
    }

    public void updateListOfFilesFragment(boolean fromSearch) {
        OCFileListFragment fileListFragment = getListOfFilesFragment();
        if (fileListFragment != null) {
            fileListFragment.listDirectory(MainApp.isOnlyOnDevice(), fromSearch);
        }
    }

    public void resetSearchView() {
        OCFileListFragment fileListFragment = getListOfFilesFragment();

        if (fileListFragment != null) {
            fileListFragment.setSearchFragment(false);
        }
    }

    protected void refreshDetailsFragmentIfVisible(String downloadEvent, String downloadedRemotePath, boolean success) {
        Fragment leftFragment = getLeftFragment();
        if (leftFragment instanceof FileDetailFragment detailsFragment) {
            boolean waitedPreview = mWaitingToPreview != null && mWaitingToPreview.getRemotePath().equals(downloadedRemotePath);
            OCFile fileInFragment = detailsFragment.getFile();
            if (fileInFragment != null && !downloadedRemotePath.equals(fileInFragment.getRemotePath())) {
                // the user browsed to other file ; forget the automatic preview
                mWaitingToPreview = null;

            } else if (downloadEvent.equals(FileDownloadWorker.Companion.getDownloadAddedMessage())) {
                // grant that the details fragment updates the progress bar
                detailsFragment.listenForTransferProgress();
                detailsFragment.updateFileDetails(true, false);

            } else if (downloadEvent.equals(FileDownloadWorker.Companion.getDownloadFinishMessage())) {
                //  update the details panel
                boolean detailsFragmentChanged = false;
                if (waitedPreview) {
                    if (success) {
                        // update the file from database, for the local storage path
                        mWaitingToPreview = getStorageManager().getFileById(mWaitingToPreview.getFileId());

                        if (PreviewMediaActivity.Companion.canBePreviewed(mWaitingToPreview)) {
                            startMediaPreview(mWaitingToPreview, 0, true, true, true, true);
                            detailsFragmentChanged = true;
                        } else if (MimeTypeUtil.isVCard(mWaitingToPreview.getMimeType())) {
                            startContactListFragment(mWaitingToPreview);
                            detailsFragmentChanged = true;
                        } else if (PreviewTextFileFragment.canBePreviewed(mWaitingToPreview)) {
                            startTextPreview(mWaitingToPreview, true);
                            detailsFragmentChanged = true;
                        } else if (MimeTypeUtil.isPDF(mWaitingToPreview)) {
                            startPdfPreview(mWaitingToPreview);
                            detailsFragmentChanged = true;
                        } else {
                            getFileOperationsHelper().openFile(mWaitingToPreview);
                        }
                    }
                    mWaitingToPreview = null;
                }
                if (!detailsFragmentChanged) {
                    detailsFragment.updateFileDetails(false, success);
                }
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean drawerOpen = isDrawerOpen();

        for (MenuItem menuItem : mDrawerMenuItemstoShowHideList) {
            menuItem.setVisible(!drawerOpen);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_file_display, menu);

        menu.findItem(R.id.action_select_all).setVisible(false);
        MenuItem searchMenuItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) MenuItemCompat.getActionView(searchMenuItem);
        searchMenuItem.setVisible(false);
        mSearchText.setOnClickListener(v -> {
            showSearchView();
            searchView.setIconified(false);
        });

        viewThemeUtils.androidx.themeToolbarSearchView(searchView);

        // populate list of menu items to show/hide when drawer is opened/closed
        mDrawerMenuItemstoShowHideList = new ArrayList<>(1);
        mDrawerMenuItemstoShowHideList.add(searchMenuItem);

        //focus the SearchView
        if (!TextUtils.isEmpty(searchQuery)) {
            searchView.post(() -> {
                searchView.setIconified(false);
                searchView.setQuery(searchQuery, true);
                searchView.clearFocus();
            });
        }

        final View mSearchEditFrame = searchView.findViewById(androidx.appcompat.R.id.search_edit_frame);

        searchView.setOnCloseListener(() -> {
            if (TextUtils.isEmpty(searchView.getQuery().toString())) {
                searchView.onActionViewCollapsed();
                setDrawerIndicatorEnabled(isDrawerIndicatorAvailable()); // order matters
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                mDrawerToggle.syncState();

                OCFileListFragment ocFileListFragment = getListOfFilesFragment();
                if (ocFileListFragment != null) {
                    ocFileListFragment.setSearchFragment(false);
                    ocFileListFragment.refreshDirectory();
                }
            } else {
                searchView.post(() -> searchView.setQuery("", true));
            }
            return true;
        });

        ViewTreeObserver vto = mSearchEditFrame.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            int oldVisibility = -1;

            @Override
            public void onGlobalLayout() {

                int currentVisibility = mSearchEditFrame.getVisibility();

                if (currentVisibility != oldVisibility) {
                    if (currentVisibility == View.VISIBLE) {
                        setDrawerIndicatorEnabled(false);
                    }

                    oldVisibility = currentVisibility;
                }

            }
        });

        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean retval = true;

        int itemId = item.getItemId();

        if (itemId == android.R.id.home) {
            if (!isDrawerOpen() && !isSearchOpen() && isRoot(getCurrentDir()) && getLeftFragment() instanceof OCFileListFragment) {
                openDrawer();
            } else {
                onBackPressed();
            }
        } else if (itemId == R.id.action_select_all) {
            OCFileListFragment fragment = getListOfFilesFragment();

            if (fragment != null) {
                fragment.selectAllFiles(true);
            }
        } else {
            retval = super.onOptionsItemSelected(item);
        }

        return retval;
    }

    /**
     * Called, when the user selected something for uploading
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE__SELECT_CONTENT_FROM_APPS && (resultCode == RESULT_OK || resultCode == UploadFilesActivity.RESULT_OK_AND_MOVE)) {

            requestUploadOfContentFromApps(data, resultCode);

        } else if (requestCode == REQUEST_CODE__SELECT_FILES_FROM_FILE_SYSTEM && (resultCode == RESULT_OK || resultCode == UploadFilesActivity.RESULT_OK_AND_MOVE || resultCode == UploadFilesActivity.RESULT_OK_AND_DO_NOTHING || resultCode == UploadFilesActivity.RESULT_OK_AND_DELETE)) {

            requestUploadOfFilesFromFileSystem(data, resultCode);

        } else if ((requestCode == REQUEST_CODE__UPLOAD_FROM_CAMERA || requestCode == REQUEST_CODE__UPLOAD_FROM_VIDEO_CAMERA) && (resultCode == RESULT_OK || resultCode == UploadFilesActivity.RESULT_OK_AND_DELETE)) {

            new CheckAvailableSpaceTask(new CheckAvailableSpaceTask.CheckAvailableSpaceListener() {
                @Override
                public void onCheckAvailableSpaceStart() {
                    Log_OC.d(this, "onCheckAvailableSpaceStart");
                }

                @Override
                public void onCheckAvailableSpaceFinish(boolean hasEnoughSpaceAvailable, String... filesToUpload) {
                    Log_OC.d(this, "onCheckAvailableSpaceFinish");

                    if (hasEnoughSpaceAvailable) {
                        File file = new File(filesToUpload[0]);
                        File renamedFile;
                        if (requestCode == REQUEST_CODE__UPLOAD_FROM_CAMERA) {
                            renamedFile = new File(file.getParent() + PATH_SEPARATOR + FileOperationsHelper.getCapturedImageName());
                        } else {
                            renamedFile = new File(file.getParent() + PATH_SEPARATOR + FileOperationsHelper.getCapturedVideoName());
                        }

                        if (!file.renameTo(renamedFile)) {
                            DisplayUtils.showSnackMessage(getActivity(), R.string.error_uploading_direct_camera_upload);
                            return;
                        }

                        requestUploadOfFilesFromFileSystem(renamedFile.getParentFile().getAbsolutePath(), new String[]{renamedFile.getAbsolutePath()}, FileUploadWorker.LOCAL_BEHAVIOUR_DELETE);
                    }
                }
            }, new String[]{FileOperationsHelper.createCameraFile(getActivity(), requestCode == REQUEST_CODE__UPLOAD_FROM_VIDEO_CAMERA).getAbsolutePath()}).execute();
        } else if (requestCode == REQUEST_CODE__MOVE_OR_COPY_FILES && resultCode == RESULT_OK) {
            exitSelectionMode();
        } else if (requestCode == PermissionUtil.REQUEST_CODE_MANAGE_ALL_FILES) {
            syncAndUpdateFolder(true);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void exitSelectionMode() {
        OCFileListFragment ocFileListFragment = getListOfFilesFragment();
        if (ocFileListFragment != null) {
            ocFileListFragment.exitSelectionMode();
        }
    }

    private void requestUploadOfFilesFromFileSystem(Intent data, int resultCode) {
        String[] filePaths = data.getStringArrayExtra(UploadFilesActivity.EXTRA_CHOSEN_FILES);
        String basePath = data.getStringExtra(UploadFilesActivity.LOCAL_BASE_PATH);
        requestUploadOfFilesFromFileSystem(basePath, filePaths, resultCode);
    }

    private String[] getRemotePaths(String directory, String[] filePaths, String localBasePath) {
        String[] remotePaths = new String[filePaths.length];
        for (int j = 0; j < remotePaths.length; j++) {
            String relativePath = StringUtils.removePrefix(filePaths[j], localBasePath);
            remotePaths[j] = directory + relativePath;
        }

        return remotePaths;
    }

    private void requestUploadOfFilesFromFileSystem(String localBasePath, String[] filePaths, int resultCode) {
        if (localBasePath != null && filePaths != null) {
            if (!localBasePath.endsWith("/")) {
                localBasePath = localBasePath + "/";
            }

            String remotePathBase = getCurrentDir().getRemotePath();
            String[] decryptedRemotePaths = getRemotePaths(remotePathBase, filePaths, localBasePath);

            int behaviour = switch (resultCode) {
                case UploadFilesActivity.RESULT_OK_AND_MOVE -> FileUploadWorker.LOCAL_BEHAVIOUR_MOVE;
                case UploadFilesActivity.RESULT_OK_AND_DELETE -> FileUploadWorker.LOCAL_BEHAVIOUR_DELETE;
                default -> FileUploadWorker.LOCAL_BEHAVIOUR_FORGET;
            };

            connectivityService.isNetworkAndServerAvailable(result -> {
                if (result) {
                    boolean isValidFolderPath = FileNameValidator.INSTANCE.checkFolderPath(remotePathBase,getCapabilities(),this);
                    if (!isValidFolderPath) {
                        DisplayUtils.showSnackMessage(this, R.string.file_name_validator_error_contains_reserved_names_or_invalid_characters);
                        return;
                    }

                    FileUploadHelper.Companion.instance().uploadNewFiles(getUser().orElseThrow(RuntimeException::new),
                                                                         filePaths,
                                                                         decryptedRemotePaths,
                                                                         behaviour,
                                                                         true,
                                                                         UploadFileOperation.CREATED_BY_USER,
                                                                         false,
                                                                         false,
                                                                         NameCollisionPolicy.ASK_USER);
                } else {
                    fileDataStorageManager.addCreateFileOfflineOperation(filePaths, decryptedRemotePaths);
                }
            });
        } else {
            Log_OC.d(TAG, "User clicked on 'Update' with no selection");
            DisplayUtils.showSnackMessage(this, R.string.filedisplay_no_file_selected);
        }
    }

    private void requestUploadOfContentFromApps(Intent contentIntent, int resultCode) {

        ArrayList<Parcelable> streamsToUpload = new ArrayList<>();

        if (contentIntent.getClipData() != null && contentIntent.getClipData().getItemCount() > 0) {

            for (int i = 0; i < contentIntent.getClipData().getItemCount(); i++) {
                streamsToUpload.add(contentIntent.getClipData().getItemAt(i).getUri());
            }

        } else {
            streamsToUpload.add(contentIntent.getData());
        }

        int behaviour = (resultCode == UploadFilesActivity.RESULT_OK_AND_MOVE) ? FileUploadWorker.LOCAL_BEHAVIOUR_MOVE : FileUploadWorker.LOCAL_BEHAVIOUR_COPY;

        OCFile currentDir = getCurrentDir();
        String remotePath = (currentDir != null) ? currentDir.getRemotePath() : OCFile.ROOT_PATH;

        UriUploader uploader = new UriUploader(this, streamsToUpload, remotePath, getUser().orElseThrow(RuntimeException::new), behaviour, false, // Not show waiting dialog while file is being copied from private storage
                                               null  // Not needed copy temp task listener
        );

        uploader.uploadUris();

    }

    private boolean isSearchOpen() {
        if (searchView == null) {
            return false;
        } else {
            View mSearchEditFrame = searchView.findViewById(androidx.appcompat.R.id.search_edit_frame);
            return mSearchEditFrame != null && mSearchEditFrame.getVisibility() == View.VISIBLE;
        }
    }

    private Boolean isRootDirectory() {
        OCFile currentDir = getCurrentDir();
        return (currentDir == null || currentDir.getParentId() == FileDataStorageManager.ROOT_PARENT_ID);
    }

    /*
     * BackPressed priority/hierarchy:
     *    1. close search view if opened
     *    2. close drawer if opened
     *    3. if it is OCFileListFragment and it's in Root -> (finish Activity) or it's not Root -> (browse up)
     *    4. otherwise pop up the fragment and sortGroup view visibility and call super.onBackPressed()
     */
    @SuppressFBWarnings("ITC_INHERITANCE_TYPE_CHECKING")
    @Override
    // TODO Apply fail fast principle
    public void onBackPressed() {
        if (isSearchOpen()) {
            resetSearchAction();
            return;
        }

        if (isDrawerOpen()) {
            super.onBackPressed();
            return;
        }

        if (getLeftFragment() instanceof OCFileListFragment listOfFiles) {
            if (isRoot(getCurrentDir())) {
                finish();
            } else {
                browseUp(listOfFiles);
            }
        } else {
            popBack();
        }
    }

    private void browseUp(OCFileListFragment listOfFiles) {
        listOfFiles.onBrowseUp();
        setFile(listOfFiles.getCurrentFile());
        listOfFiles.setFabVisible(true);
        listOfFiles.registerFabListener();
        resetTitleBarAndScrolling();
        setDrawerAllFiles();
    }

    private void resetSearchAction() {
        Fragment leftFragment = getLeftFragment();
        if (!isSearchOpen() || searchView == null) {
            return;
        }

        searchView.setQuery("", true);
        searchView.onActionViewCollapsed();
        searchView.clearFocus();

        if (isRoot(getCurrentDir()) && leftFragment instanceof OCFileListFragment listOfFiles) {

            // Remove the list to the original state
            ArrayList<String> listOfHiddenFiles = listOfFiles.getAdapter().listOfHiddenFiles;
            listOfFiles.performSearch("", listOfHiddenFiles, true);

            hideSearchView(getCurrentDir());
            setDrawerIndicatorEnabled(isDrawerIndicatorAvailable());
        }

        if (leftFragment instanceof UnifiedSearchFragment) {
            showSortListGroup(false);
            super.onBackPressed();
        }
    }

    /**
     * Use this method when want to pop the fragment on back press. It resets Scrolling (See
     * {@link #resetScrolling(boolean) with true} and pop the visibility for sortListGroup (See
     * {@link #showSortListGroup(boolean) with false}. At last call to super.onBackPressed()
     */
    private void popBack() {
        binding.fabMain.setImageResource(R.drawable.ic_plus);
        resetScrolling(true);
        showSortListGroup(false);
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // responsibility of restore is preferred in onCreate() before than in
        // onRestoreInstanceState when there are Fragments involved
        super.onSaveInstanceState(outState);
        FileExtensionsKt.logFileSize(mWaitingToPreview, TAG);
        outState.putParcelable(FileDisplayActivity.KEY_WAITING_TO_PREVIEW, mWaitingToPreview);
        outState.putBoolean(FileDisplayActivity.KEY_SYNC_IN_PROGRESS, mSyncInProgress);
        // outState.putBoolean(FileDisplayActivity.KEY_REFRESH_SHARES_IN_PROGRESS,
        // mRefreshSharesInProgress);
        outState.putParcelable(FileDisplayActivity.KEY_WAITING_TO_SEND, mWaitingToSend);
        if (searchView != null) {
            outState.putBoolean(KEY_IS_SEARCH_OPEN, !searchView.isIconified());
        }
        outState.putString(KEY_SEARCH_QUERY, searchQuery);
        outState.putBoolean(KEY_IS_SORT_GROUP_VISIBLE, sortListGroupVisibility());
        Log_OC.v(TAG, "onSaveInstanceState() end");
    }

    @Override
    protected void onResume() {
        Log_OC.v(TAG, "onResume() start");
        super.onResume();
        // Instead of onPostCreate, starting the loading in onResume for children fragments
        Fragment leftFragment = getLeftFragment();

        // Listen for sync messages
        if (!(leftFragment instanceof OCFileListFragment) || !((OCFileListFragment) leftFragment).isSearchFragment()) {
            initSyncBroadcastReceiver();
        }

        if (!(leftFragment instanceof OCFileListFragment)) {
            if (leftFragment instanceof FileFragment) {
                super.updateActionBarTitleAndHomeButton(((FileFragment) leftFragment).getFile());
            }
            return;
        }

        OCFileListFragment ocFileListFragment = (OCFileListFragment) leftFragment;

        ocFileListFragment.setLoading(mSyncInProgress);
        syncAndUpdateFolder(false, true);

        OCFile startFile = null;
        if (getIntent() != null) {
            OCFile fileArgs = IntentExtensionsKt.getParcelableArgument(getIntent(), EXTRA_FILE, OCFile.class);
            if (fileArgs != null) {
                startFile = fileArgs;
                setFile(startFile);
            }
        }

        // refresh list of files
        if (searchView != null && !TextUtils.isEmpty(searchQuery)) {
            searchView.setQuery(searchQuery, false);
        } else if (!ocFileListFragment.isSearchFragment() && startFile == null) {
            updateListOfFilesFragment(false);
            ocFileListFragment.registerFabListener();
        } else {
            ocFileListFragment.listDirectory(startFile, false, false);
            updateActionBarTitleAndHomeButton(startFile);
        }

        // Listen for upload messages
        IntentFilter uploadIntentFilter = new IntentFilter(FileUploadWorker.Companion.getUploadFinishMessage());
        mUploadFinishReceiver = new UploadFinishReceiver();
        localBroadcastManager.registerReceiver(mUploadFinishReceiver, uploadIntentFilter);

        // Listen for download messages
        IntentFilter downloadIntentFilter = new IntentFilter(FileDownloadWorker.Companion.getDownloadAddedMessage());
        downloadIntentFilter.addAction(FileDownloadWorker.Companion.getDownloadFinishMessage());
        mDownloadFinishReceiver = new DownloadFinishReceiver();
        localBroadcastManager.registerReceiver(mDownloadFinishReceiver, downloadIntentFilter);

        checkAndSetMenuItemId();

        if (menuItemId == Menu.NONE) {
            setDrawerAllFiles();
        } else {
            configureToolbar();
        }

        //show in-app review dialog to user
        inAppReviewHelper.showInAppReview(this);

        checkNotifications();

        Log_OC.v(TAG, "onResume() end");
    }

    private void checkAndSetMenuItemId() {
        if (MainApp.isOnlyPersonFiles()) {
            menuItemId = R.id.nav_personal_files;
        } else if (MainApp.isOnlyOnDevice()) {
            menuItemId = R.id.nav_on_device;
        } else if (menuItemId == Menu.NONE) {
            menuItemId = R.id.nav_all_files;
        }
    }

    private void configureToolbar() {
        // Other activities menuItemIds must be excluded to show correct toolbar.
        final var excludedMenuItemIds = new ArrayList<>();
        excludedMenuItemIds.add(R.id.nav_community);
        excludedMenuItemIds.add(R.id.nav_trashbin);
        excludedMenuItemIds.add(R.id.nav_uploads);
        excludedMenuItemIds.add(R.id.nav_activity);
        excludedMenuItemIds.add(R.id.nav_settings);
        excludedMenuItemIds.add(R.id.nav_assistant);

        boolean isSearchEventExists = false;
        if (getLeftFragment() instanceof OCFileListFragment fileListFragment) {
            isSearchEventExists = (fileListFragment.getSearchEvent() != null);
        }

        if (!(getLeftFragment() instanceof GalleryFragment) &&
            (!isSearchEventExists ||
                menuItemId == R.id.nav_all_files ||
                menuItemId == R.id.nav_personal_files ||
                excludedMenuItemIds.contains(menuItemId))) {
            setupHomeSearchToolbarWithSortAndListButtons();
        } else {
            setupToolbar();
        }
    }

    private void setDrawerAllFiles() {
        checkAndSetMenuItemId();
        setNavigationViewItemChecked();

        if (MainApp.isOnlyOnDevice()) {
            setupToolbar();
        } else {
            setupHomeSearchToolbarWithSortAndListButtons();
        }
    }

    public void initSyncBroadcastReceiver() {
        if (mSyncBroadcastReceiver == null) {
            IntentFilter syncIntentFilter = new IntentFilter(FileSyncAdapter.EVENT_FULL_SYNC_START);
            syncIntentFilter.addAction(FileSyncAdapter.EVENT_FULL_SYNC_END);
            syncIntentFilter.addAction(FileSyncAdapter.EVENT_FULL_SYNC_FOLDER_CONTENTS_SYNCED);
            syncIntentFilter.addAction(RefreshFolderOperation.EVENT_SINGLE_FOLDER_CONTENTS_SYNCED);
            syncIntentFilter.addAction(RefreshFolderOperation.EVENT_SINGLE_FOLDER_SHARES_SYNCED);
            mSyncBroadcastReceiver = new SyncBroadcastReceiver();
            localBroadcastManager.registerReceiver(mSyncBroadcastReceiver, syncIntentFilter);
        }
    }

    @Override
    protected void onPause() {
        Log_OC.v(TAG, "onPause() start");
        if (mSyncBroadcastReceiver != null) {
            localBroadcastManager.unregisterReceiver(mSyncBroadcastReceiver);
            mSyncBroadcastReceiver = null;
        }
        if (mUploadFinishReceiver != null) {
            localBroadcastManager.unregisterReceiver(mUploadFinishReceiver);
            mUploadFinishReceiver = null;
        }
        if (mDownloadFinishReceiver != null) {
            localBroadcastManager.unregisterReceiver(mDownloadFinishReceiver);
            mDownloadFinishReceiver = null;
        }

        super.onPause();
        Log_OC.v(TAG, "onPause() end");
    }

    @Override
    public void onSortingOrderChosen(FileSortOrder selection) {
        OCFileListFragment ocFileListFragment = getListOfFilesFragment();
        if (ocFileListFragment != null) {
            ocFileListFragment.sortFiles(selection);
        }
    }

    @Override
    public void downloadFile(OCFile file, String packageName, String activityName) {
        startDownloadForSending(file, OCFileListFragment.DOWNLOAD_SEND, packageName, activityName);
    }

    // region SyncBroadcastReceiver
    private class SyncBroadcastReceiver extends BroadcastReceiver {
        @SuppressLint("VisibleForTests")
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                final String event = intent.getAction();
                Log_OC.d(TAG, "Received broadcast " + event);

                // region EventData
                final String accountName = intent.getStringExtra(FileSyncAdapter.EXTRA_ACCOUNT_NAME);
                final String syncFolderRemotePath = intent.getStringExtra(FileSyncAdapter.EXTRA_FOLDER_PATH);
                final String id = intent.getStringExtra(FileSyncAdapter.EXTRA_RESULT);
                final Object syncResult = DataHolderUtil.getInstance().retrieve(id);
                final boolean sameAccount = getAccount() != null && accountName != null && accountName.equals(getAccount().name) && getStorageManager() != null;
                final OCFileListFragment fileListFragment = getListOfFilesFragment();
                // endregion

                if (sameAccount) {
                    handleSyncEvent(event, syncFolderRemotePath, id, fileListFragment, syncResult);
                }

                if (syncResult instanceof RemoteOperationResult<?> operation && operation.getCode() == ResultCode.SSL_RECOVERABLE_PEER_UNVERIFIED) {
                    mLastSslUntrustedServerResult = operation;
                }
            } catch (RuntimeException e) {
                safelyDeleteResult(intent);
            }
        }
    }

    // avoid app crashes after changing the serial id of RemoteOperationResult in owncloud library with broadcast notifications pending to process
    private void safelyDeleteResult(Intent intent) {
        try {
            DataHolderUtil.getInstance().delete(intent.getStringExtra(FileSyncAdapter.EXTRA_RESULT));
        } catch (RuntimeException re) {
            Log_OC.i(TAG, "Ignoring error deleting data");
        }
    }

    private void handleSyncEvent(String event, String syncFolderRemotePath, String id, OCFileListFragment fileListFragment, Object syncResult) {
        if (FileSyncAdapter.EVENT_FULL_SYNC_START.equals(event)) {
            mSyncInProgress = true;
            return;
        }

        OCFile currentFile = (getFile() == null) ? null : getStorageManager().getFileByPath(getFile().getRemotePath());
        final OCFile currentDir = (getCurrentDir() == null) ? null : getStorageManager().getFileByPath(getCurrentDir().getRemotePath());
        final boolean isSyncFolderRemotePathRoot = OCFile.ROOT_PATH.equals(syncFolderRemotePath);

        if (currentDir == null && !isSyncFolderRemotePathRoot) {
            handleRemovedFolder(syncFolderRemotePath);
        } else if (currentDir != null) {
            currentFile = handleRemovedFileFromServer(currentFile, currentDir);
            updateFileList(fileListFragment, currentDir, syncFolderRemotePath);
            setFile(currentFile);
        }

        handleSyncResult(event, syncResult);

        DataHolderUtil.getInstance().delete(id);

        mSyncInProgress = !FileSyncAdapter.EVENT_FULL_SYNC_END.equals(event) && !RefreshFolderOperation.EVENT_SINGLE_FOLDER_SHARES_SYNCED.equals(event);
        Log_OC.d(TAG, "Setting progress visibility to " + mSyncInProgress);

        handleScrollBehaviour(fileListFragment);
        setBackgroundText();
    }

    private OCFile handleRemovedFileFromServer(OCFile currentFile, OCFile currentDir) {
        if (currentFile == null && !getFile().isFolder()) {
            resetTitleBarAndScrolling();
            return currentDir;
        }

        return currentFile;
    }

    private void handleRemovedFolder(String syncFolderRemotePath) {
        DisplayUtils.showSnackMessage(getActivity(), R.string.sync_current_folder_was_removed, syncFolderRemotePath);
        browseToRoot();
    }

    private void updateFileList(OCFileListFragment ocFileListFragment, OCFile currentDir, String syncFolderRemotePath) {
        if (!currentDir.getRemotePath().equals(syncFolderRemotePath)) {
            return;
        }

        if (ocFileListFragment == null) {
            return;
        }

        ocFileListFragment.listDirectory(currentDir, MainApp.isOnlyOnDevice(), false);
    }

    private void handleScrollBehaviour(OCFileListFragment ocFileListFragment) {
        if (ocFileListFragment == null) {
            return;
        }

        ocFileListFragment.setLoading(mSyncInProgress);
        if (mSyncInProgress || ocFileListFragment.isLoading()) {
            return;
        }

        if (ocFileListFragment.isEmpty()) {
            lockScrolling();
            return;
        }

        resetScrolling(false);
    }

    private void handleSyncResult(String event, Object syncResult) {
        if (!RefreshFolderOperation.EVENT_SINGLE_FOLDER_CONTENTS_SYNCED.equals(event) || syncResult == null) {
            return;
        }

        if (syncResult instanceof RemoteOperationResult<?> operation && operation.isSuccess()) {
            hideInfoBox();
            return;
        }

        handleFailedSyncResult(syncResult);
    }

    private void handleFailedSyncResult(Object syncResult) {
        if (checkForRemoteOperationError(syncResult)) {
            requestCredentialsUpdate();
        } else {
            handleNonCredentialSyncErrors(syncResult);
        }
    }

    private void handleNonCredentialSyncErrors(Object syncResult) {
        if (!(syncResult instanceof RemoteOperationResult<?> operation)) {
            return;
        }

        switch (operation.getCode()) {
            case SSL_RECOVERABLE_PEER_UNVERIFIED:
                showUntrustedCertDialog(operation);
                break;

            case MAINTENANCE_MODE:
                showInfoBox(R.string.maintenance_mode);
                break;

            case NO_NETWORK_CONNECTION:
                showInfoBox(R.string.offline_mode);
                break;

            case HOST_NOT_AVAILABLE:
                showInfoBox(R.string.host_not_available);
                break;

            case SIGNING_TOS_NEEDED:
                showTermsOfServiceDialog();
                break;

            default:
                break;
        }
    }

    private void showTermsOfServiceDialog() {
        if (getSupportFragmentManager().findFragmentByTag(DIALOG_TAG_SHOW_TOS) == null) {
            new TermsOfServiceDialog().show(getSupportFragmentManager(), DIALOG_TAG_SHOW_TOS);
        }
    }

    private boolean checkForRemoteOperationError(Object syncResult) {
        if (!(syncResult instanceof RemoteOperationResult<?> operation)) {
            return false;
        }

        return ResultCode.UNAUTHORIZED == operation.getCode() || (operation.isException() && operation.getException() instanceof AuthenticatorException);
    }

    /**
     * Show a text message on screen view for notifying user if content is loading or folder is empty
     */
    private void setBackgroundText() {
        final OCFileListFragment ocFileListFragment = getListOfFilesFragment();
        if (ocFileListFragment != null) {
            if (mSyncInProgress || getFile().getFileLength() > 0 && getStorageManager().getFolderContent(getFile(), false).isEmpty()) {
                ocFileListFragment.setEmptyListLoadingMessage();
            } else {
                if (MainApp.isOnlyOnDevice()) {
                    ocFileListFragment.setMessageForEmptyList(R.string.file_list_empty_headline, R.string.file_list_empty_on_device, R.drawable.ic_list_empty_folder, true);
                } else {
                    connectivityService.isNetworkAndServerAvailable(result -> {
                        if (result) {
                            ocFileListFragment.setEmptyListMessage(SearchType.NO_SEARCH);
                        } else {
                            ocFileListFragment.setEmptyListMessage(SearchType.OFFLINE_MODE);
                        }
                    });
                }
            }
        } else {
            Log_OC.e(TAG, "OCFileListFragment is null");
        }
    }
    // endregion

    /**
     * Once the file upload has finished -> update view
     */
    private class UploadFinishReceiver extends BroadcastReceiver {
        /**
         * Once the file upload has finished -> update view
         * <p>
         * {@link BroadcastReceiver} to enable upload feedback in UI
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String uploadedRemotePath = intent.getStringExtra(FileUploadWorker.EXTRA_REMOTE_PATH);
            String accountName = intent.getStringExtra(FileUploadWorker.ACCOUNT_NAME);
            Account account = getAccount();
            boolean sameAccount = accountName != null && account != null && accountName.equals(account.name);
            OCFile currentDir = getCurrentDir();
            boolean isDescendant = currentDir != null && uploadedRemotePath != null && uploadedRemotePath.startsWith(currentDir.getRemotePath());

            if (sameAccount && isDescendant) {
                String linkedToRemotePath = intent.getStringExtra(FileUploadWorker.EXTRA_LINKED_TO_PATH);
                if (linkedToRemotePath == null || isAscendant(linkedToRemotePath)) {
                    updateListOfFilesFragment(false);
                }
            }

            boolean uploadWasFine = intent.getBooleanExtra(FileUploadWorker.EXTRA_UPLOAD_RESULT, false);

            boolean renamedInUpload = false;
            boolean sameFile = false;
            if (getFile() != null) {
                renamedInUpload = getFile().getRemotePath().equals(intent.getStringExtra(FileUploadWorker.EXTRA_OLD_REMOTE_PATH));
                sameFile = getFile().getRemotePath().equals(uploadedRemotePath) || renamedInUpload;
            }

            if (sameAccount && sameFile && getLeftFragment() instanceof FileDetailFragment fileDetailFragment) {
                if (uploadWasFine) {
                    setFile(getStorageManager().getFileByPath(uploadedRemotePath));
                } else {
                    //TODO remove upload progress bar after upload failed.
                    Log_OC.d(TAG, "Remove upload progress bar after upload failed");
                }
                if (renamedInUpload && !TextUtils.isEmpty(uploadedRemotePath)) {
                    String newName = new File(uploadedRemotePath).getName();
                    DisplayUtils.showSnackMessage(getActivity(), R.string.filedetails_renamed_in_upload_msg, newName);
                }

                if (uploadWasFine || getFile() != null && getFile().fileExists()) {
                    fileDetailFragment.updateFileDetails(false, true);
                } else {
                    onBackPressed();
                }

                // Force the preview if the file is an image or text file
                if (uploadWasFine) {
                    OCFile ocFile = getFile();
                    if (PreviewImageFragment.canBePreviewed(ocFile)) {
                        startImagePreview(getFile(), true);
                    } else if (PreviewTextFileFragment.canBePreviewed(ocFile)) {
                        startTextPreview(ocFile, true);
                    }
                    // TODO what about other kind of previews?
                }
            }

            OCFileListFragment ocFileListFragment = getListOfFilesFragment();
            if (ocFileListFragment != null) {
                ocFileListFragment.setLoading(false);
            }
        }

        // TODO refactor this receiver, and maybe DownloadFinishReceiver; this method is duplicated :S
        private boolean isAscendant(String linkedToRemotePath) {
            OCFile currentDir = getCurrentDir();
            return currentDir != null && currentDir.getRemotePath().startsWith(linkedToRemotePath);
        }
    }


    /**
     * Class waiting for broadcast events from the {@link FileDownloadWorker} service.
     * <p>
     * Updates the UI when a download is started or finished, provided that it is relevant for the current folder.
     */
    private class DownloadFinishReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            boolean sameAccount = isSameAccount(intent);
            String downloadedRemotePath = intent.getStringExtra(FileDownloadWorker.EXTRA_REMOTE_PATH);
            String downloadBehaviour = intent.getStringExtra(OCFileListFragment.DOWNLOAD_BEHAVIOUR);
            boolean isDescendant = isDescendant(downloadedRemotePath);

            if (sameAccount && isDescendant) {
                String linkedToRemotePath = intent.getStringExtra(FileDownloadWorker.EXTRA_LINKED_TO_PATH);
                if (linkedToRemotePath == null || isAscendant(linkedToRemotePath)) {
                    updateListOfFilesFragment(false);
                }
                refreshDetailsFragmentIfVisible(intent.getAction(), downloadedRemotePath, intent.getBooleanExtra(FileDownloadWorker.EXTRA_DOWNLOAD_RESULT, false));
            }

            if (mWaitingToSend != null) {
                // update file after downloading
                mWaitingToSend = getStorageManager().getFileByRemoteId(mWaitingToSend.getRemoteId());
                if (mWaitingToSend != null && mWaitingToSend.isDown() && OCFileListFragment.DOWNLOAD_SEND.equals(downloadBehaviour)) {
                    String packageName = intent.getStringExtra(SendShareDialog.PACKAGE_NAME);
                    String activityName = intent.getStringExtra(SendShareDialog.ACTIVITY_NAME);

                    sendDownloadedFile(packageName, activityName);
                }
            }

            if (mWaitingToPreview != null) {
                mWaitingToPreview = getStorageManager().getFileByRemoteId(mWaitingToPreview.getRemoteId());
                if (mWaitingToPreview != null && mWaitingToPreview.isDown() && EditImageActivity.OPEN_IMAGE_EDITOR.equals(downloadBehaviour)) {
                    startImageEditor(mWaitingToPreview);
                }
            }
        }

        private boolean isDescendant(String downloadedRemotePath) {
            OCFile currentDir = getCurrentDir();
            return currentDir != null && downloadedRemotePath != null && downloadedRemotePath.startsWith(currentDir.getRemotePath());
        }

        private boolean isAscendant(String linkedToRemotePath) {
            OCFile currentDir = getCurrentDir();
            return currentDir != null && currentDir.getRemotePath().startsWith(linkedToRemotePath);
        }

        private boolean isSameAccount(Intent intent) {
            String accountName = intent.getStringExtra(FileDownloadWorker.EXTRA_ACCOUNT_NAME);
            return accountName != null && getAccount() != null && accountName.equals(getAccount().name);
        }
    }


    public void browseToRoot() {
        OCFileListFragment listOfFiles = getListOfFilesFragment();
        if (listOfFiles != null) {  // should never be null, indeed
            OCFile root = getStorageManager().getFileByPath(OCFile.ROOT_PATH);
            listOfFiles.listDirectory(root, MainApp.isOnlyOnDevice(), false);
            setFile(listOfFiles.getCurrentFile());
            startSyncFolderOperation(root, false);
        }
        binding.fabMain.setImageResource(R.drawable.ic_plus);
        resetTitleBarAndScrolling();
    }


    @Override
    public void onBrowsedDownTo(OCFile directory) {
        setFile(directory);
        resetTitleBarAndScrolling();
        // Sync Folder
        startSyncFolderOperation(directory, false);
    }

    /**
     * Shows the information of the {@link OCFile} received as a parameter.
     *
     * @param file {@link OCFile} whose details will be shown
     */
    @Override
    public void showDetails(OCFile file) {
        showDetails(file, 0);
    }

    /**
     * Shows the information of the {@link OCFile} received as a parameter.
     *
     * @param file      {@link OCFile} whose details will be shown
     * @param activeTab the active tab in the details view
     */
    public void showDetails(OCFile file, int activeTab) {
        User currentUser = getUser().orElseThrow(RuntimeException::new);

        resetScrolling(true);

        Fragment detailFragment = FileDetailFragment.newInstance(file, currentUser, activeTab);
        setLeftFragment(detailFragment, false);
        configureToolbarForPreview(file);
    }

    /**
     * Prevents content scrolling and toolbar collapse
     */
    @VisibleForTesting
    public void lockScrolling() {
        binding.appbar.appbar.setExpanded(true, false);
        final AppBarLayout.LayoutParams appbarParams = (AppBarLayout.LayoutParams) binding.appbar.toolbarFrame.getLayoutParams();
        appbarParams.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL);
        binding.appbar.toolbarFrame.setLayoutParams(appbarParams);
    }

    /**
     * Resets content scrolling and toolbar collapse
     */
    @VisibleForTesting
    public void resetScrolling(boolean expandAppBar) {
        AppBarLayout.LayoutParams appbarParams = (AppBarLayout.LayoutParams) binding.appbar.toolbarFrame.getLayoutParams();
        appbarParams.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
        binding.appbar.toolbarFrame.setLayoutParams(appbarParams);
        if (expandAppBar) {
            binding.appbar.appbar.setExpanded(true, false);
        }
    }

    @Override
    public void updateActionBarTitleAndHomeButton(OCFile chosenFile) {
        if (chosenFile == null) {
            chosenFile = getFile();     // if no file is passed, current file decides
        }
        super.updateActionBarTitleAndHomeButton(chosenFile);
    }

    @Override
    public boolean isDrawerIndicatorAvailable() {
        return isRoot(getCurrentDir());
    }

    private void observeWorkerState() {
        WorkerStateLiveData.Companion.instance().observe(this, state -> {
            if (state instanceof WorkerState.DownloadStarted) {
                Log_OC.d(TAG, "Download worker started");
                handleDownloadWorkerState();
            } else if (state instanceof WorkerState.DownloadFinished finishedState) {
                fileDownloadProgressListener = null;
                previewFile(finishedState);
            } else if (state instanceof WorkerState.UploadFinished) {
                refreshList();
            } else if (state instanceof  WorkerState.OfflineOperationsCompleted) {
                refreshCurrentDirectory();
            }
        });
    }

    private void previewFile(WorkerState.DownloadFinished finishedState) {
        if (fileIDForImmediatePreview == -1) {
            return;
        }

        final var currentFile = finishedState.getCurrentFile();
        if (currentFile == null) {
            return;
        }

        if (fileIDForImmediatePreview != currentFile.getFileId() || !currentFile.isDown()) {
            return;
        }

        fileIDForImmediatePreview = -1;
        if (PreviewImageFragment.canBePreviewed(currentFile)) {
            startImagePreview(currentFile, currentFile.isDown());
        } else {
            previewFile(currentFile, null);
        }
    }

    public void previewImageWithSearchContext(OCFile file, boolean searchFragment, SearchType currentSearchType) {
        // preview image - it handles the download, if needed
        if (searchFragment) {
            VirtualFolderType type = switch (currentSearchType) {
                case FAVORITE_SEARCH -> VirtualFolderType.FAVORITE;
                case GALLERY_SEARCH -> VirtualFolderType.GALLERY;
                default -> VirtualFolderType.NONE;
            };

            startImagePreview(file, type, file.isDown());
        } else {
            startImagePreview(file, file.isDown());
        }
    }

    public void previewFile(OCFile file, @Nullable CompletionCallback setFabVisible) {
        if (!file.isDown()) {
            Log_OC.d(TAG,"File is not downloaded, cannot be previewed");
            return;
        }

        if (MimeTypeUtil.isVCard(file)) {
            startContactListFragment(file);
        } else if (MimeTypeUtil.isPDF(file)) {
            startPdfPreview(file);
        } else if (PreviewTextFileFragment.canBePreviewed(file)) {
            if (setFabVisible != null) {
                setFabVisible.onComplete(false);
            }

            startTextPreview(file, false);
        } else if (PreviewMediaActivity.Companion.canBePreviewed(file)) {
            if (setFabVisible != null) {
                setFabVisible.onComplete(false);
            }

            startMediaPreview(file, 0, true, true, false, true);
        } else {
            getFileOperationsHelper().openFile(file);
        }
    }

    public void refreshCurrentDirectory() {
        OCFile currentDir = (getCurrentDir() != null) ?
            getStorageManager().getFileByDecryptedRemotePath(getCurrentDir().getRemotePath()) : null;

        OCFileListFragment fileListFragment =
            (ActivityExtensionsKt.lastFragment(this) instanceof OCFileListFragment fragment) ? fragment : getListOfFilesFragment();

        if (fileListFragment != null) {
            fileListFragment.listDirectory(currentDir, MainApp.isOnlyOnDevice(), false);
        }
    }

    private void handleDownloadWorkerState() {
        if (mWaitingToPreview != null && getStorageManager() != null) {
            mWaitingToPreview = getStorageManager().getFileById(mWaitingToPreview.getFileId());
            if (mWaitingToPreview != null && !mWaitingToPreview.isDown()) {
                requestForDownload();
            }
        }
    }

    @Override
    protected ServiceConnection newTransferenceServiceConnection() {
        return new ListServiceConnection();
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     * TODO: Check if this can be removed since download and uploads uses work manager now.
     */
    private class ListServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
        }

        @Override
        public void onServiceDisconnected(ComponentName component) {
            if (component.equals(new ComponentName(FileDisplayActivity.this, FileDownloadWorker.class))) {
                Log_OC.d(TAG, "Download service disconnected");
                fileDownloadProgressListener = null;
            }
        }
    }

    /**
     * Updates the view associated to the activity after the finish of some operation over files in the current
     * account.
     *
     * @param operation Removal operation performed.
     * @param result    Result of the removal.
     */
    @Override
    public void onRemoteOperationFinish(RemoteOperation operation, RemoteOperationResult result) {
        super.onRemoteOperationFinish(operation, result);

        if (operation instanceof RemoveFileOperation removeFileOperation) {
            onRemoveFileOperationFinish(removeFileOperation, result);
        } else if (operation instanceof RenameFileOperation renameFileOperation) {
            onRenameFileOperationFinish(renameFileOperation, result);
        } else if (operation instanceof SynchronizeFileOperation synchronizeFileOperation) {
            onSynchronizeFileOperationFinish(synchronizeFileOperation, result);
        } else if (operation instanceof CreateFolderOperation createFolderOperation) {
            onCreateFolderOperationFinish(createFolderOperation, result);
        } else if (operation instanceof MoveFileOperation moveFileOperation) {
            onMoveFileOperationFinish(moveFileOperation, result);
        } else if (operation instanceof CopyFileOperation copyFileOperation) {
            onCopyFileOperationFinish(copyFileOperation, result);
        } else if (operation instanceof RestoreFileVersionRemoteOperation) {
            onRestoreFileVersionOperationFinish(result);
        }
    }

    private OCFileListFragment getFileListFragment() {
        return (ActivityExtensionsKt.lastFragment(this) instanceof OCFileListFragment fragment) ? fragment : getListOfFilesFragment();
    }

    private void refreshGalleryFragmentIfNeeded() {
        final var fileListFragment = getFileListFragment();
        if (fileListFragment instanceof GalleryFragment) {
            startPhotoSearch(R.id.nav_gallery);
        }
    }

    private void fetchRecommendedFilesIfNeeded() {
        final var fileListFragment = getFileListFragment();
        if (fileListFragment != null && !(fileListFragment instanceof GalleryFragment)) {
            fileListFragment.fetchRecommendedFiles();
        }
    }

    private void refreshShowDetails() {
        Fragment details = getLeftFragment();
        if (details instanceof FileFragment) {
            OCFile file = ((FileFragment) details).getFile();
            if (file != null) {
                file = getStorageManager().getFileByPath(file.getRemotePath());
                if (details instanceof PreviewTextFragment) {
                    // Refresh  OCFile of the fragment
                    ((PreviewTextFileFragment) details).updateFile(file);
                } else {
                    showDetails(file);
                }
            }
            supportInvalidateOptionsMenu();
        }
    }

    /**
     * Updates the view associated to the activity after the finish of an operation trying to remove a file.
     *
     * @param operation Removal operation performed.
     * @param result    Result of the removal.
     */
    private void onRemoveFileOperationFinish(RemoveFileOperation operation, RemoteOperationResult result) {

        if (!operation.isInBackground()) {
            DisplayUtils.showSnackMessage(this, ErrorMessageAdapter.getErrorCauseMessage(result, operation, getResources()));
        }

        if (result.isSuccess()) {
            OCFile removedFile = operation.getFile();
            tryStopPlaying(removedFile);
            Fragment leftFragment = getLeftFragment();

            // check if file is still available, if so do nothing
            boolean fileAvailable = getStorageManager().fileExists(removedFile.getFileId());
            if (leftFragment instanceof FileFragment && !fileAvailable && removedFile.equals(((FileFragment) leftFragment).getFile())) {
                setFile(getStorageManager().getFileById(removedFile.getParentId()));
                resetTitleBarAndScrolling();
            }
            OCFile parentFile = getStorageManager().getFileById(removedFile.getParentId());
            if (parentFile != null && parentFile.equals(getCurrentDir())) {
                updateListOfFilesFragment(false);
            } else if (getLeftFragment() instanceof GalleryFragment galleryFragment) {
                galleryFragment.onRefresh();
            }
            supportInvalidateOptionsMenu();
            refreshGalleryFragmentIfNeeded();
            fetchRecommendedFilesIfNeeded();
        } else {
            if (result.isSslRecoverableException()) {
                mLastSslUntrustedServerResult = result;
                showUntrustedCertDialog(mLastSslUntrustedServerResult);
            }
        }
    }

    private void onRestoreFileVersionOperationFinish(RemoteOperationResult result) {
        if (result.isSuccess()) {
            OCFile file = getFile();

            // delete old local copy
            if (file.isDown()) {
                List<OCFile> list = new ArrayList<>();
                list.add(file);
                getFileOperationsHelper().removeFiles(list, true, true);

                // download new version, only if file was previously download
                getFileOperationsHelper().syncFile(file);
            }

            OCFile parent = getStorageManager().getFileById(file.getParentId());
            startSyncFolderOperation(parent, true, true);

            Fragment leftFragment = getLeftFragment();
            if (leftFragment instanceof FileDetailFragment fileDetailFragment) {
                fileDetailFragment.getFileDetailActivitiesFragment().reload();
            }

            DisplayUtils.showSnackMessage(this, R.string.file_version_restored_successfully);
        } else {
            DisplayUtils.showSnackMessage(this, R.string.file_version_restored_error);
        }
    }

    private void tryStopPlaying(OCFile file) {
        // placeholder for stop-on-delete future code
        if (mPlayerConnection != null && MimeTypeUtil.isAudio(file) && mPlayerConnection.isPlaying()) {
            mPlayerConnection.stop(file);
        }
    }

    /**
     * Updates the view associated to the activity after the finish of an operation trying to move a file.
     *
     * @param operation Move operation performed.
     * @param result    Result of the move operation.
     */
    private void onMoveFileOperationFinish(MoveFileOperation operation, RemoteOperationResult result) {
        if (result.isSuccess()) {
            syncAndUpdateFolder(true);
        } else {
            try {
                DisplayUtils.showSnackMessage(this, ErrorMessageAdapter.getErrorCauseMessage(result, operation, getResources()));

            } catch (NotFoundException e) {
                Log_OC.e(TAG, "Error while trying to show fail message ", e);
            }
        }
    }

    /**
     * Updates the view associated to the activity after the finish of an operation trying to copy a file.
     *
     * @param operation Copy operation performed.
     * @param result    Result of the copy operation.
     */
    private void onCopyFileOperationFinish(CopyFileOperation operation, RemoteOperationResult result) {
        if (result.isSuccess()) {
            updateListOfFilesFragment(false);
            refreshGalleryFragmentIfNeeded();
        } else {
            try {
                DisplayUtils.showSnackMessage(this, ErrorMessageAdapter.getErrorCauseMessage(result, operation, getResources()));

            } catch (NotFoundException e) {
                Log_OC.e(TAG, "Error while trying to show fail message ", e);
            }
        }
    }

    /**
     * Updates the view associated to the activity after the finish of an operation trying to rename a file.
     *
     * @param operation Renaming operation performed.
     * @param result    Result of the renaming.
     */
    private void onRenameFileOperationFinish(RenameFileOperation operation, RemoteOperationResult result) {
        Optional<User> optionalUser = getUser();
        OCFile renamedFile = operation.getFile();
        if (result.isSuccess() && optionalUser.isPresent()) {
            final User currentUser = optionalUser.get();
            Fragment leftFragment = getLeftFragment();
            if (leftFragment instanceof FileFragment fileFragment) {
                if (fileFragment instanceof FileDetailFragment fileDetailFragment && renamedFile.equals(fileFragment.getFile())) {
                    fileDetailFragment.updateFileDetails(renamedFile, currentUser);
                    showDetails(renamedFile);

                } else if (fileFragment instanceof PreviewMediaFragment previewMediaFragment && renamedFile.equals(fileFragment.getFile())) {
                    previewMediaFragment.updateFile(renamedFile);
                    if (PreviewMediaFragment.canBePreviewed(renamedFile)) {
                        long position = ((PreviewMediaFragment) fileFragment).getPosition();
                        startMediaPreview(renamedFile, position, true, true, true, false);
                    } else {
                        getFileOperationsHelper().openFile(renamedFile);
                    }
                } else if (fileFragment instanceof PreviewTextFragment && renamedFile.equals(fileFragment.getFile())) {
                    ((PreviewTextFileFragment) fileFragment).updateFile(renamedFile);
                    if (PreviewTextFileFragment.canBePreviewed(renamedFile)) {
                        startTextPreview(renamedFile, true);
                    } else {
                        getFileOperationsHelper().openFile(renamedFile);
                    }
                }
            }

            OCFile file = getStorageManager().getFileById(renamedFile.getParentId());
            if (file != null && file.equals(getCurrentDir())) {
                updateListOfFilesFragment(false);
            }
            refreshGalleryFragmentIfNeeded();
            fetchRecommendedFilesIfNeeded();
        } else {
            DisplayUtils.showSnackMessage(this, ErrorMessageAdapter.getErrorCauseMessage(result, operation, getResources()));

            if (result.isSslRecoverableException()) {
                mLastSslUntrustedServerResult = result;
                showUntrustedCertDialog(mLastSslUntrustedServerResult);
            }
        }
    }


    private void onSynchronizeFileOperationFinish(SynchronizeFileOperation operation, RemoteOperationResult result) {
        if (result.isSuccess() && operation.transferWasRequested()) {
            OCFile syncedFile = operation.getLocalFile();
            onTransferStateChanged(syncedFile, true, true);
            supportInvalidateOptionsMenu();
            refreshShowDetails();
        }
    }

    /**
     * Updates the view associated to the activity after the finish of an operation trying create a new folder
     *
     * @param operation Creation operation performed.
     * @param result    Result of the creation.
     */
    private void onCreateFolderOperationFinish(CreateFolderOperation operation, RemoteOperationResult result) {
        if (result.isSuccess()) {
            OCFileListFragment fileListFragment = getListOfFilesFragment();
            if (fileListFragment != null) {
                fileListFragment.onItemClicked(getStorageManager().getFileByDecryptedRemotePath(operation.getRemotePath()));
            }
        } else {
            try {
                if (ResultCode.FOLDER_ALREADY_EXISTS == result.getCode()) {
                    DisplayUtils.showSnackMessage(this, R.string.folder_already_exists);
                } else {
                    DisplayUtils.showSnackMessage(this, ErrorMessageAdapter.getErrorCauseMessage(result, operation, getResources()));
                }
            } catch (NotFoundException e) {
                Log_OC.e(TAG, "Error while trying to show fail message ", e);
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void onTransferStateChanged(OCFile file, boolean downloading, boolean uploading) {
        updateListOfFilesFragment(false);
        Fragment leftFragment = getLeftFragment();
        Optional<User> optionalUser = getUser();
        if (leftFragment instanceof FileDetailFragment && file.equals(((FileDetailFragment) leftFragment).getFile()) && optionalUser.isPresent()) {
            final User currentUser = optionalUser.get();
            if (downloading || uploading) {
                ((FileDetailFragment) leftFragment).updateFileDetails(file, currentUser);
            } else {
                if (!file.fileExists()) {
                    resetTitleBarAndScrolling();
                } else {
                    ((FileDetailFragment) leftFragment).updateFileDetails(false, true);
                }
            }
        }

    }


    private void requestForDownload() {
        User user = getUser().orElseThrow(RuntimeException::new);
        FileDownloadHelper.Companion.instance().downloadFileIfNotStartedBefore(user, mWaitingToPreview);
    }

    @Override
    public void onSavedCertificate() {
        startSyncFolderOperation(getCurrentDir(), false);
    }

    /**
     * Starts an operation to refresh the requested folder.
     * <p>
     * The operation is run in a new background thread created on the fly.
     * <p>
     * The refresh updates is a "light sync": properties of regular files in folder are updated (including associated
     * shares), but not their contents. Only the contents of files marked to be kept-in-sync are synchronized too.
     *
     * @param folder     Folder to refresh.
     * @param ignoreETag If 'true', the data from the server will be fetched and sync'ed even if the eTag didn't
     *                   change.
     */
    public void startSyncFolderOperation(OCFile folder, boolean ignoreETag) {
        startSyncFolderOperation(folder, ignoreETag, false);
    }

    /**
     * Starts an operation to refresh the requested folder.
     * <p>
     * The operation is run in a new background thread created on the fly.
     * <p>
     * The refresh updates is a "light sync": properties of regular files in folder are updated (including associated
     * shares), but not their contents. Only the contents of files marked to be kept-in-sync are synchronized too.
     *
     * @param folder      Folder to refresh.
     * @param ignoreETag  If 'true', the data from the server will be fetched and sync'ed even if the eTag didn't
     *                    change.
     * @param ignoreFocus reloads file list even without focus, e.g. on tablet mode, focus can still be in detail view
     */
    public void startSyncFolderOperation(final OCFile folder, final boolean ignoreETag, boolean ignoreFocus) {

        // the execution is slightly delayed to allow the activity get the window focus if it's being started
        // or if the method is called from a dialog that is being dismissed
        if (TextUtils.isEmpty(searchQuery) && getUser().isPresent()) {
            getHandler().postDelayed(() -> {
                Optional<User> user = getUser();

                if (!ignoreFocus && !hasWindowFocus() || !user.isPresent()) {
                    // do not refresh if the user rotates the device while another window has focus
                    // or if the current user is no longer valid
                    return;
                }

                long currentSyncTime = System.currentTimeMillis();
                mSyncInProgress = true;

                // perform folder synchronization
                RemoteOperation refreshFolderOperation = new RefreshFolderOperation(folder, currentSyncTime, false, ignoreETag, getStorageManager(), user.get(), getApplicationContext());
                refreshFolderOperation.execute(getAccount(), MainApp.getAppContext(), FileDisplayActivity.this, null, null);

                OCFileListFragment fragment = getListOfFilesFragment();

                if (fragment != null && !(fragment instanceof GalleryFragment)) {
                    fragment.setLoading(true);
                }

                setBackgroundText();
            }, DELAY_TO_REQUEST_REFRESH_OPERATION_LATER);
        }
    }

    private void requestForDownload(OCFile file, String downloadBehaviour, String packageName, String activityName) {
        final User currentUser = getUser().orElseThrow(RuntimeException::new);
        if (!FileDownloadHelper.Companion.instance().isDownloading(currentUser, file)) {
            FileDownloadHelper.Companion.instance().downloadFile(currentUser, file, downloadBehaviour, DownloadType.DOWNLOAD, activityName, packageName, null);
        }
    }

    private void sendDownloadedFile(String packageName, String activityName) {
        if (mWaitingToSend != null) {

            Intent sendIntent = IntentUtil.createSendIntent(this, mWaitingToSend);
            sendIntent.setComponent(new ComponentName(packageName, activityName));

            // Show dialog
            String sendTitle = getString(R.string.activity_chooser_send_file_title);
            startActivity(Intent.createChooser(sendIntent, sendTitle));
        } else {
            Log_OC.e(TAG, "Trying to send a NULL OCFile");
        }

        mWaitingToSend = null;
    }

    /**
     * Requests the download of the received {@link OCFile} , updates the UI to monitor the download progress and
     * prepares the activity to send the file when the download finishes.
     *
     * @param file         {@link OCFile} to download and preview.
     * @param packageName
     * @param activityName
     */
    public void startDownloadForSending(OCFile file, String downloadBehaviour, String packageName, String activityName) {
        mWaitingToSend = file;
        requestForDownload(mWaitingToSend, downloadBehaviour, packageName, activityName);
    }

    public void startImagePreview(OCFile file, boolean showPreview) {
        Intent showDetailsIntent = new Intent(this, PreviewImageActivity.class);
        showDetailsIntent.putExtra(EXTRA_FILE, file);
        showDetailsIntent.putExtra(EXTRA_LIVE_PHOTO_FILE, file.livePhotoVideo);
        showDetailsIntent.putExtra(EXTRA_USER, getUser().orElseThrow(RuntimeException::new));
        if (showPreview) {
            startActivity(showDetailsIntent);
        } else {
            FileOperationsHelper fileOperationsHelper = new FileOperationsHelper(this, getUserAccountManager(), connectivityService, editorUtils);
            fileOperationsHelper.startSyncForFileAndIntent(file, showDetailsIntent);
        }
    }

    public void startImagePreview(OCFile file, VirtualFolderType type, boolean showPreview) {
        Intent showDetailsIntent = new Intent(this, PreviewImageActivity.class);
        showDetailsIntent.putExtra(PreviewImageActivity.EXTRA_FILE, file);
        showDetailsIntent.putExtra(EXTRA_LIVE_PHOTO_FILE, file.livePhotoVideo);
        showDetailsIntent.putExtra(EXTRA_USER, getUser().orElseThrow(RuntimeException::new));
        showDetailsIntent.putExtra(PreviewImageActivity.EXTRA_VIRTUAL_TYPE, type);

        if (showPreview) {
            startActivity(showDetailsIntent);
        } else {
            FileOperationsHelper fileOperationsHelper = new FileOperationsHelper(this,
                                                                                 getUserAccountManager(),
                                                                                 connectivityService,
                                                                                 editorUtils);
            fileOperationsHelper.startSyncForFileAndIntent(file, showDetailsIntent);
        }
    }

    /**
     * Stars the preview of an already down media {@link OCFile}.
     *
     * @param file                  Media {@link OCFile} to preview.
     * @param startPlaybackPosition Media position where the playback will be started, in milliseconds.
     * @param autoplay              When 'true', the playback will start without user interactions.
     */
    public void startMediaPreview(OCFile file, long startPlaybackPosition, boolean autoplay, boolean showPreview, boolean streamMedia, boolean showInActivity) {
        Optional<User> user = getUser();
        if (!user.isPresent()) {
            return; // not reachable under normal conditions
        }
        User actualUser = user.get();
        if (showPreview && file.isDown() && !file.isDownloading() || streamMedia) {
            if (showInActivity) {
                startMediaActivity(file, startPlaybackPosition, autoplay, actualUser);
            } else {
                configureToolbarForPreview(file);
                Fragment mediaFragment = PreviewMediaFragment.newInstance(file, user.get(), startPlaybackPosition, autoplay, false);
                setLeftFragment(mediaFragment, false);
            }
        } else {
            Intent previewIntent = new Intent();
            previewIntent.putExtra(EXTRA_FILE, file);
            previewIntent.putExtra(PreviewMediaFragment.EXTRA_START_POSITION, startPlaybackPosition);
            previewIntent.putExtra(PreviewMediaFragment.EXTRA_AUTOPLAY, autoplay);
            FileOperationsHelper fileOperationsHelper = new FileOperationsHelper(this, getUserAccountManager(), connectivityService, editorUtils);
            fileOperationsHelper.startSyncForFileAndIntent(file, previewIntent);
        }
    }

    private void startMediaActivity(OCFile file, long startPlaybackPosition, boolean autoplay, User user) {
        Intent previewMediaIntent = new Intent(this, PreviewMediaActivity.class);
        previewMediaIntent.putExtra(PreviewMediaActivity.EXTRA_FILE, file);

        // Safely handle the absence of a user
        if (user != null) {
            previewMediaIntent.putExtra(PreviewMediaActivity.EXTRA_USER, user);
        }

        previewMediaIntent.putExtra(PreviewMediaActivity.EXTRA_START_POSITION, startPlaybackPosition);
        previewMediaIntent.putExtra(PreviewMediaActivity.EXTRA_AUTOPLAY, autoplay);
        startActivity(previewMediaIntent);
    }

    public void configureToolbarForPreview(OCFile file) {
        lockScrolling();
        super.updateActionBarTitleAndHomeButton(file);
    }

    /**
     * Starts the preview of a text file {@link OCFile}.
     *
     * @param file Text {@link OCFile} to preview.
     */
    public void startTextPreview(OCFile file, boolean showPreview) {
        Optional<User> optUser = getUser();
        if (!optUser.isPresent()) {
            // remnants of old unsafe system; do not crash, silently stop
            return;
        }
        User user = optUser.get();
        if (showPreview) {
            PreviewTextFileFragment fragment = PreviewTextFileFragment.create(user, file, searchOpen, searchQuery);
            setLeftFragment(fragment, false);
            configureToolbarForPreview(file);
        } else {
            Intent previewIntent = new Intent();
            previewIntent.putExtra(EXTRA_FILE, file);
            previewIntent.putExtra(TEXT_PREVIEW, true);
            FileOperationsHelper fileOperationsHelper = new FileOperationsHelper(this, getUserAccountManager(), connectivityService, editorUtils);
            fileOperationsHelper.startSyncForFileAndIntent(file, previewIntent);
        }
    }

    /**
     * Starts rich workspace preview for a folder.
     *
     * @param folder {@link OCFile} to preview its rich workspace.
     */
    public void startRichWorkspacePreview(OCFile folder) {
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_FILE, folder);
        configureToolbarForPreview(folder);
        Fragment textPreviewFragment = Fragment.instantiate(getApplicationContext(), PreviewTextStringFragment.class.getName(), args);
        setLeftFragment(textPreviewFragment, false);
    }

    public void startContactListFragment(OCFile file) {
        final User user = getUser().orElseThrow(RuntimeException::new);
        ContactsPreferenceActivity.startActivityWithContactsFile(this, user, file);
    }

    public void startPdfPreview(OCFile file) {
        if (getFileOperationsHelper().canOpenFile(file)) {
            // prefer third party PDF apps
            getFileOperationsHelper().openFile(file);
        } else {
            final Fragment pdfFragment = PreviewPdfFragment.newInstance(file);

            setLeftFragment(pdfFragment, false);
            configureToolbarForPreview(file);
            setMainFabVisible(false);
        }
    }


    /**
     * Requests the download of the received {@link OCFile} , updates the UI to monitor the download progress and
     * prepares the activity to preview or open the file when the download finishes.
     *
     * @param file         {@link OCFile} to download and preview.
     * @param parentFolder {@link OCFile} containing above file
     */
    public void startDownloadForPreview(OCFile file, OCFile parentFolder) {
        if (!file.isFileEligibleForImmediatePreview()) {
            final Optional<User> currentUser = getUser();
            if (currentUser.isPresent()) {
                Fragment detailFragment = FileDetailFragment.newInstance(file, parentFolder, currentUser.get());
                setLeftFragment(detailFragment, false);
            }
        }

        configureToolbarForPreview(file);
        mWaitingToPreview = file;
        requestForDownload();
        setFile(file);
    }


    /**
     * Opens EditImageActivity with given file loaded. If file is not available locally, it will be synced before
     * opening the image editor.
     *
     * @param file {@link OCFile} (image) to be loaded into image editor
     */
    public void startImageEditor(OCFile file) {
        if (file.isDown()) {
            Intent editImageIntent = new Intent(this, EditImageActivity.class);
            editImageIntent.putExtra(EditImageActivity.EXTRA_FILE, file);
            startActivity(editImageIntent);
        } else {
            mWaitingToPreview = file;
            requestForDownload(file, EditImageActivity.OPEN_IMAGE_EDITOR, getPackageName(), this.getClass().getSimpleName());
            updateActionBarTitleAndHomeButton(file);
            setFile(file);
        }
    }


    /**
     * Request stopping the upload/download operation in progress over the given {@link OCFile} file.
     *
     * @param file {@link OCFile} file which operation are wanted to be cancel
     */
    public void cancelTransference(OCFile file) {
        getFileOperationsHelper().cancelTransference(file);
        if (mWaitingToPreview != null && mWaitingToPreview.getRemotePath().equals(file.getRemotePath())) {
            mWaitingToPreview = null;
        }
        if (mWaitingToSend != null && mWaitingToSend.getRemotePath().equals(file.getRemotePath())) {
            mWaitingToSend = null;
        }
        onTransferStateChanged(file, false, false);
    }

    /**
     * Request stopping all upload/download operations in progress over the given {@link OCFile} files.
     *
     * @param files collection of {@link OCFile} files which operations are wanted to be cancel
     */
    public void cancelTransference(Collection<OCFile> files) {
        for (OCFile file : files) {
            cancelTransference(file);
        }
    }

    @Override
    public void onRefresh(boolean ignoreETag) {
        syncAndUpdateFolder(ignoreETag);
    }

    @Override
    public void onRefresh() {
        syncAndUpdateFolder(true);
    }

    public void syncAndUpdateFolder(boolean ignoreETag) {
        syncAndUpdateFolder(ignoreETag, false);
    }

    private void syncAndUpdateFolder(boolean ignoreETag, boolean ignoreFocus) {
        OCFileListFragment listOfFiles = getListOfFilesFragment();
        if (listOfFiles == null || listOfFiles.isSearchFragment()) {
            return;
        }

        OCFile folder = listOfFiles.getCurrentFile();
        if (folder == null) {
            return;
        }

        startSyncFolderOperation(folder, ignoreETag, ignoreFocus);

        if (getCapabilities() != null && getCapabilities().getRecommendations().isTrue() && folder.isRootDirectory()) {
            listOfFiles.fetchRecommendedFiles();
        }
    }

    @Override
    public void showFiles(boolean onDeviceOnly, boolean personalFiles) {
        super.showFiles(onDeviceOnly, personalFiles);
        if (onDeviceOnly) {
            updateActionBarTitleAndHomeButtonByString(getString(R.string.drawer_item_on_device));
        }
        OCFileListFragment ocFileListFragment = getListOfFilesFragment();
        if (ocFileListFragment != null && !(ocFileListFragment instanceof GalleryFragment) && !(ocFileListFragment instanceof SharedListFragment)) {
            ocFileListFragment.refreshDirectory();
        } else {
            setLeftFragment(new OCFileListFragment());
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(final SearchEvent event) {
        if (SearchRemoteOperation.SearchType.PHOTO_SEARCH == event.getSearchType()) {
            Log_OC.d(this, "Switch to photo search fragment");
            setLeftFragment(new GalleryFragment());
        } else if (event.getSearchType() == SearchRemoteOperation.SearchType.SHARED_FILTER) {
            Log_OC.d(this, "Switch to Shared fragment");
            setLeftFragment(new SharedListFragment());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(SyncEventFinished event) {
        Bundle bundle = event.getIntent().getExtras();
        if (event.getIntent().getBooleanExtra(TEXT_PREVIEW, false)) {
            startTextPreview((OCFile) bundle.get(EXTRA_FILE), true);
        } else if (bundle.containsKey(PreviewMediaFragment.EXTRA_START_POSITION)) {
            startMediaPreview((OCFile) bundle.get(EXTRA_FILE), (long) bundle.get(PreviewMediaFragment.EXTRA_START_POSITION), (boolean) bundle.get(PreviewMediaFragment.EXTRA_AUTOPLAY), true, true, true);
        } else if (bundle.containsKey(PreviewImageActivity.EXTRA_VIRTUAL_TYPE)) {
            startImagePreview((OCFile) bundle.get(EXTRA_FILE), (VirtualFolderType) bundle.get(PreviewImageActivity.EXTRA_VIRTUAL_TYPE), true);
        } else {
            startImagePreview((OCFile) bundle.get(EXTRA_FILE), true);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(TokenPushEvent event) {
        if (!preferences.isKeysReInitEnabled()) {
            PushUtils.reinitKeys(getUserAccountManager());
        } else {
            PushUtils.pushRegistrationToServer(getUserAccountManager(), preferences.getPushToken());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        final Optional<User> optionalUser = getUser();
        final FileDataStorageManager storageManager = getStorageManager();
        if (optionalUser.isPresent() && storageManager != null) {
            /// Check whether the 'main' OCFile handled by the Activity is contained in the
            // current Account
            OCFile file = getFile();
            // get parent from path
            if (file != null) {
                if (file.isDown() && file.getLastSyncDateForProperties() == 0) {
                    // upload in progress - right now, files are not inserted in the local
                    // cache until the upload is successful get parent from path
                    String parentPath = file.getRemotePath().substring(0, file.getRemotePath().lastIndexOf(file.getFileName()));
                    if (storageManager.getFileByPath(parentPath) == null) {
                        file = null; // not able to know the directory where the file is uploading
                    }
                } else {
                    file = storageManager.getFileByPath(file.getRemotePath());
                    // currentDir = null if not in the current Account
                }
            }
            if (file == null) {
                // fall back to root folder
                file = storageManager.getFileByPath(OCFile.ROOT_PATH);  // never returns null
            }
            setFile(file);

            User user = optionalUser.get();
            setupDrawer();

            mSwitchAccountButton.setTag(user.getAccountName());
            DisplayUtils.setAvatar(user, this, getResources().getDimension(R.dimen.nav_drawer_menu_avatar_radius), getResources(), mSwitchAccountButton, this);
            final boolean userChanged = !user.nameEquals(lastDisplayedUser);
            if (userChanged) {
                Log_OC.d(TAG, "Initializing Fragments in onAccountChanged..");
                initFragments();
                if (file.isFolder() && TextUtils.isEmpty(searchQuery)) {
                    startSyncFolderOperation(file, false);
                }
            } else {
                updateActionBarTitleAndHomeButton(file.isFolder() ? null : file);
            }
        }
        lastDisplayedUser = optionalUser.orElse(null);

        EventBus.getDefault().post(new TokenPushEvent());
        checkForNewDevVersionNecessary(getApplicationContext());
    }

    private void registerRefreshFolderEventReceiver() {
        IntentFilter filter = new IntentFilter(REFRESH_FOLDER_EVENT_RECEIVER);
        LocalBroadcastManager.getInstance(this).registerReceiver(refreshFolderEventReceiver, filter);
    }

    private final BroadcastReceiver refreshFolderEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            syncAndUpdateFolder(true);
        }
    };

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshFolderEventReceiver);
        super.onDestroy();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        checkForNewDevVersionNecessary(getApplicationContext());
    }

    public void setSearchQuery(String query) {
        searchQuery = query;
    }

    private void handleOpenFileViaIntent(Intent intent) {
        DisplayUtils.showSnackMessage(this, getString(R.string.retrieving_file));

        String userName = intent.getStringExtra(KEY_ACCOUNT);
        String fileId = intent.getStringExtra(KEY_FILE_ID);
        String filePath = intent.getStringExtra(KEY_FILE_PATH);

        if (userName == null && fileId == null && intent.getData() != null) {
            openDeepLink(intent.getData());
        } else {
            Optional<User> optionalUser = userName == null ? getUser() : getUserAccountManager().getUser(userName);
            if (optionalUser.isPresent()) {
                if (!TextUtils.isEmpty(fileId)) {
                    openFile(optionalUser.get(), fileId);
                } else if (!TextUtils.isEmpty(filePath)) {
                    openFileByPath(optionalUser.get(), filePath);
                } else {
                    accountClicked(optionalUser.get().hashCode());
                }
            } else {
                DisplayUtils.showSnackMessage(this, getString(R.string.associated_account_not_found));
            }
        }
    }

    private void openDeepLink(Uri uri) {
        DeepLinkHandler linkHandler = new DeepLinkHandler(getUserAccountManager());
        DeepLinkHandler.Match match = linkHandler.parseDeepLink(uri);

        if (match == null) {
            handleDeepLink(uri);
        } else if (match.getUsers().isEmpty()) {
            DisplayUtils.showSnackMessage(this, getString(R.string.associated_account_not_found));
        } else if (match.getUsers().size() == SINGLE_USER_SIZE) {
            openFile(match.getUsers().get(0), match.getFileId());
        } else {
            selectUserAndOpenFile(match.getUsers(), match.getFileId());
        }
    }

    private void selectUserAndOpenFile(List<User> users, String fileId) {
        final CharSequence[] userNames = new CharSequence[users.size()];
        for (int i = 0; i < userNames.length; i++) {
            userNames[i] = users.get(i).getAccountName();
        }
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.common_choose_account).setItems(userNames, (dialog, which) -> {
            User user = users.get(which);
            openFile(user, fileId);
            showLoadingDialog(getString(R.string.retrieving_file));
        });

        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(getApplicationContext(), builder);

        final AlertDialog dialog = builder.create();
        dismissLoadingDialog();
        dialog.show();
    }

    private void openFile(User user, String fileId) {
        setUser(user);

        if (fileId == null) {
            onFileRequestError(null);
            return;
        }

        FileDataStorageManager storageManager = getStorageManager();

        if (storageManager == null) {
            storageManager = new FileDataStorageManager(user, getContentResolver());
        }

        FetchRemoteFileTask fetchRemoteFileTask = new FetchRemoteFileTask(user, fileId, storageManager, this);
        fetchRemoteFileTask.execute();
    }

    private void openFileByPath(User user, String filepath) {
        setUser(user);

        if (filepath == null) {
            onFileRequestError(null);
            return;
        }

        FileDataStorageManager storageManager = getStorageManager();

        if (storageManager == null) {
            storageManager = new FileDataStorageManager(user, getContentResolver());
        }

        OwnCloudClient client;
        try {
            client = clientFactory.create(user);
        } catch (ClientFactory.CreationException e) {
            onFileRequestError(null);
            return;
        }

        GetRemoteFileTask getRemoteFileTask = new GetRemoteFileTask(this, filepath, client, storageManager, user);
        asyncRunner.postQuickTask(getRemoteFileTask, this::onFileRequestResult, this::onFileRequestError);
    }

    private Unit onFileRequestError(Throwable throwable) {
        dismissLoadingDialog();
        DisplayUtils.showSnackMessage(this, getString(R.string.error_retrieving_file));
        Log_OC.e(TAG, "Requesting file from remote failed!", throwable);
        return null;
    }


    private Unit onFileRequestResult(GetRemoteFileTask.Result result) {
        dismissLoadingDialog();

        setFile(result.getFile());

        OCFileListFragment fileFragment = new OCFileListFragment();
        setLeftFragment(fileFragment);

        getSupportFragmentManager().executePendingTransactions();

        fileFragment.onItemClicked(result.getFile());

        return null;
    }

    public void performUnifiedSearch(String query, ArrayList<String> listOfHiddenFiles) {
        UnifiedSearchFragment unifiedSearchFragment = UnifiedSearchFragment.Companion.newInstance(query, listOfHiddenFiles);
        setLeftFragment(unifiedSearchFragment, false);
    }

    public void setMainFabVisible(final boolean visible) {
        final int visibility = visible ? View.VISIBLE : View.GONE;
        binding.fabMain.setVisibility(visibility);
    }

    public void showFile(OCFile selectedFile, String message) {
        dismissLoadingDialog();

        getOCFileListFragmentFromFile(listOfFiles -> {
            if (TextUtils.isEmpty(message)) {
                OCFile temp = getFile();
                setFile(getCurrentDir());
                listOfFiles.listDirectory(getCurrentDir(), temp, MainApp.isOnlyOnDevice(), false);
                updateActionBarTitleAndHomeButton(null);
            } else {
                final var view = listOfFiles.getView();
                if (view != null) {
                    DisplayUtils.showSnackMessage(view, message);
                }
            }

            if (selectedFile != null) {
                listOfFiles.onItemClicked(selectedFile);
            }
        });
    }
}
