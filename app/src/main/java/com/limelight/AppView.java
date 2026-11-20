package com.limelight;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.AppGridAdapter;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.profiles.ProfilesManager;
import com.limelight.ui.AdapterFragment;
import com.limelight.ui.AdapterFragmentCallbacks;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.xmlpull.v1.XmlPullParserException;

public class AppView extends AppCompatActivity implements AdapterFragmentCallbacks {
    private AppGridAdapter appGridAdapter;
    private String uuidString;
    private ShortcutHelper shortcutHelper;

    private ComputerDetails computer;
    private ComputerManagerService.ApplistPoller poller;
    private SpinnerDialog blockingLoadSpinner;
    private String lastRawApplist;
    private int lastRunningAppId;
    private boolean suspendGridUpdates;
    private boolean inForeground;
    private boolean showHiddenApps;
    private HashSet<Integer> hiddenAppIds = new HashSet<>();

    private PreferenceConfiguration prefConfig;

    private final static int START_OR_RESUME_ID = 1;
    private final static int QUIT_ID = 2;
    private final static int START_WITH_QUIT = 4;
    private final static int VIEW_DETAILS_ID = 5;
    private final static int CREATE_SHORTCUT_ID = 6;
    private final static int EXPORT_LAUNCHER_FILE_ID = 7;
    private final static int HIDE_APP_ID = 8;
    private final static int START_WITH_VDISPLAY = 20;
    private final static int START_WITH_QUIT_VDISPLAY = 21;

    public final static String HIDDEN_APPS_PREF_FILENAME = "HiddenApps";

    public final static String NAME_EXTRA = "Name";
    public final static String UUID_EXTRA = "UUID";
    public final static String NEW_PAIR_EXTRA = "NewPair";
    public final static String SHOW_HIDDEN_APPS_EXTRA = "ShowHiddenApps";

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder)binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Get the computer object
                    computer = localBinder.getComputer(uuidString);
                    if (computer == null) {
                        finish();
                        return;
                    }

                    // Add a launcher shortcut for this PC (forced, since this is user interaction)
                    shortcutHelper.createAppViewShortcut(computer, true, getIntent().getBooleanExtra(NEW_PAIR_EXTRA, false));
                    shortcutHelper.reportComputerShortcutUsed(computer);

                    try {
                        appGridAdapter = new AppGridAdapter(AppView.this,
                                PreferenceConfiguration.readPreferences(AppView.this),
                                computer, localBinder.getUniqueId(),
                                showHiddenApps);
                    } catch (Exception e) {
                        e.printStackTrace();
                        finish();
                        return;
                    }

                    appGridAdapter.setLoading(true);
                    appGridAdapter.updateHiddenApps(hiddenAppIds, true);

                    // Avoid an endless skeleton if the host never returns an app list.
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!isFinishing() && appGridAdapter != null && appGridAdapter.isLoading()) {
                            appGridAdapter.setLoading(false);
                            updateEmptyState();
                        }
                    }, 6000);

                    // Now make the binder visible. We must do this after appGridAdapter
                    // is set to prevent us from reaching updateUiWithServerinfo() and
                    // touching the appGridAdapter prior to initialization.
                    managerBinder = localBinder;

                    // Load the app grid with cached data (if possible).
                    // This must be done _before_ startComputerUpdates()
                    // so the initial serverinfo response can update the running
                    // icon.
                    populateAppGridWithCache();

                    // Start updates
                    startComputerUpdates();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isChangingConfigurations()) {
                                return;
                            }

                            // Despite my best efforts to catch all conditions that could
                            // cause the activity to be destroyed when we try to commit
                            // I haven't been able to, so we have this try-catch block.
                            try {
                                getFragmentManager().beginTransaction()
                                        .replace(R.id.appFragmentContainer, new AdapterFragment())
                                        .commitAllowingStateLoss();
                            } catch (IllegalStateException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        this.prefConfig = PreferenceConfiguration.readPreferences(this);

        // If appGridAdapter is initialized, let it know about the configuration change.
        // If not, it will pick it up when it initializes.
        if (appGridAdapter != null) {
            // Update the app grid adapter to create grid items with the correct layout
            appGridAdapter.updateLayoutWithPreferences(this, this.prefConfig);

            try {
                // Reinflate the app grid itself to pick up the layout change
                getFragmentManager().beginTransaction()
                        .replace(R.id.appFragmentContainer, new AdapterFragment())
                        .commitAllowingStateLoss();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    private void startComputerUpdates() {
        // Don't start polling if we're not bound or in the foreground
        if (managerBinder == null || !inForeground) {
            return;
        }

        managerBinder.startPolling(new ComputerManagerListener() {
            @Override
            public void notifyComputerUpdated(final ComputerDetails details) {
                // Do nothing if updates are suspended
                if (suspendGridUpdates) {
                    return;
                }

                // Don't care about other computers
                if (!details.uuid.equalsIgnoreCase(uuidString)) {
                    return;
                }

                if (details.state == ComputerDetails.State.OFFLINE) {
                    // The PC is unreachable now
                    AppView.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Display a toast to the user and quit the activity
                            Toast.makeText(AppView.this, R.string.lost_connection, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // Close immediately if the PC is no longer paired
                if (details.state == ComputerDetails.State.ONLINE && details.pairState != PairingManager.PairState.PAIRED) {
                    AppView.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Disable shortcuts referencing this PC for now
                            shortcutHelper.disableComputerShortcut(details,
                                    getResources().getString(R.string.scut_not_paired));

                            // Display a toast to the user and quit the activity
                            Toast.makeText(AppView.this, R.string.scut_not_paired, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // App list is the same or empty
                if (details.rawAppList == null || details.rawAppList.equals(lastRawApplist)) {

                    // Let's check if the running app ID changed
                    if (details.runningGameId != lastRunningAppId) {
                        // Update the currently running game using the app ID
                        lastRunningAppId = details.runningGameId;
                        updateUiWithServerinfo(details);
                    }

                    return;
                }

                lastRunningAppId = details.runningGameId;
                lastRawApplist = details.rawAppList;

                try {
                    updateUiWithAppList(NvHTTP.getAppListByReader(new StringReader(details.rawAppList)));
                    updateUiWithServerinfo(details);

                    if (blockingLoadSpinner != null) {
                        blockingLoadSpinner.dismiss();
                        blockingLoadSpinner = null;
                    }
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                }
            }
        });

        if (poller == null) {
            poller = managerBinder.createAppListPoller(computer);
        }
        poller.start();
    }

    private void stopComputerUpdates() {
        if (poller != null) {
            poller.stop();
        }

        if (managerBinder != null) {
            managerBinder.stopPolling();
        }

        if (appGridAdapter != null) {
            appGridAdapter.cancelQueuedOperations();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        shortcutHelper = new ShortcutHelper(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_app_view);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setSubtitle(getString(R.string.apollo_2026));
        }

        // Allow floating expanded PiP overlays while browsing apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        UiHelper.notifyNewRootView(this);

        // Setup the profiles button
        findViewById(R.id.profilesButton)
            .setOnClickListener(v -> startActivity(new Intent(this, ProfilesActivity.class)));

        showHiddenApps = getIntent().getBooleanExtra(SHOW_HIDDEN_APPS_EXTRA, false);
        uuidString = getIntent().getStringExtra(UUID_EXTRA);

        SharedPreferences hiddenAppsPrefs = getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE);
        for (String hiddenAppIdStr : hiddenAppsPrefs.getStringSet(uuidString, new HashSet<String>())) {
            hiddenAppIds.add(Integer.parseInt(hiddenAppIdStr));
        }

        String computerName = getIntent().getStringExtra(NAME_EXTRA);
        setTitle(computerName);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(computerName);
        }

        this.prefConfig = PreferenceConfiguration.readPreferences(this);

        updateEmptyState();

        // Bind to the computer manager service
        bindService(new Intent(this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
    }

    private void updateEmptyState() {
        TextView empty = findViewById(R.id.appEmptyText);
        if (empty == null) {
            return;
        }
        empty.setVisibility(appGridAdapter != null && appGridAdapter.getCount() == 0
                ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.app_view_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.action_search));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        if (appGridAdapter != null) {
                            appGridAdapter.setFilter(query);
                        }
                        updateEmptyState();
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (appGridAdapter != null) {
                            appGridAdapter.setFilter(newText);
                        }
                        updateEmptyState();
                        return true;
                    }
                });
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        }
        else if (itemId == R.id.action_settings) {
            startActivity(new Intent(this, com.limelight.preferences.StreamSettings.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateHiddenApps(boolean hideImmediately) {
        HashSet<String> hiddenAppIdStringSet = new HashSet<>();

        for (Integer hiddenAppId : hiddenAppIds) {
            hiddenAppIdStringSet.add(hiddenAppId.toString());
        }

        getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .putStringSet(uuidString, hiddenAppIdStringSet)
                .apply();

        appGridAdapter.updateHiddenApps(hiddenAppIds, hideImmediately);
    }

    private void populateAppGridWithCache() {
        try {
            // Try to load from cache
            lastRawApplist = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuidString));
            List<NvApp> applist = NvHTTP.getAppListByReader(new StringReader(lastRawApplist));
            updateUiWithAppList(applist);
            LimeLog.info("Loaded applist from cache");
        } catch (IOException | XmlPullParserException e) {
            if (lastRawApplist != null) {
                LimeLog.warning("Saved applist corrupted: "+lastRawApplist);
                e.printStackTrace();
            }
            LimeLog.info("Loading applist from the network");
            // We'll need to load from the network
            loadAppsBlocking();
        }
    }

    private void loadAppsBlocking() {
        blockingLoadSpinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.applist_refresh_title),
                getResources().getString(R.string.applist_refresh_msg), true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        inForeground = true;
        startComputerUpdates();

        ExtendedFloatingActionButton profilesButton = findViewById(R.id.profilesButton);
        // User report Samsung and Xiaomi devices have this problem
        // Why just these two brands have the most problems?
        if (profilesButton == null) {
            return;
        }
        String activeProfileName = ProfilesManager.getInstance().getActiveName();
        if (activeProfileName.isEmpty()) {
            profilesButton.shrink();
        } else {
            profilesButton.setText(activeProfileName);
            profilesButton.extend();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        stopComputerUpdates();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ShortcutHelper.REQUEST_CODE_EXPORT_ART_FILE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                ShortcutHelper.writeArtFileToUri(this, uri);
            } else {
                // Clear the content if the user cancelled or if there was an error before this point
                ShortcutHelper.artFileContentToExport = null;
                // Show "File export cancelled." toast only if the user explicitly cancelled.
                if (resultCode == Activity.RESULT_CANCELED) {
                    Toast.makeText(this, R.string.file_export_cancelled, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void showAppActions(final AppObject app, View targetView) {
        Bitmap shortcutBitmap = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && targetView != null) {
            ImageView appImageView = targetView.findViewById(R.id.grid_image);
            if (appImageView != null && appImageView.getDrawable() instanceof BitmapDrawable) {
                shortcutBitmap = ((BitmapDrawable) appImageView.getDrawable()).getBitmap();
            }
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        UiHelper.configureBottomSheetForTablets(this, dialog);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.bg_bottom_sheet);

        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        container.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(app.app.getAppName());
        TextViewCompat.setTextAppearance(title, R.style.TextAppearance_Artemis_Title);
        container.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(computer.name);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.artemis_text_muted));
        TextViewCompat.setTextAppearance(subtitle, R.style.TextAppearance_Artemis_Caption);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = (int) (4 * density);
        subtitleParams.bottomMargin = (int) (8 * density);
        container.addView(subtitle, subtitleParams);

        View divider = new View(this);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.artemis_border));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) density));
        dividerParams.bottomMargin = (int) (8 * density);
        container.addView(divider, dividerParams);

        if (lastRunningAppId == 0) {
            if (prefConfig.useVirtualDisplay) {
                addSheetAction(container, dialog, app, shortcutBitmap, START_OR_RESUME_ID,
                        R.string.applist_menu_start_primarydisplay);
            } else {
                addSheetAction(container, dialog, app, shortcutBitmap, START_WITH_VDISPLAY,
                        R.string.applist_menu_start_vdisplay);
            }
        } else if (lastRunningAppId == app.app.getAppId()) {
            addSheetAction(container, dialog, app, shortcutBitmap, START_OR_RESUME_ID,
                    R.string.applist_menu_resume);
            addSheetAction(container, dialog, app, shortcutBitmap, QUIT_ID,
                    R.string.applist_menu_quit);
        } else {
            if (prefConfig.useVirtualDisplay) {
                addSheetAction(container, dialog, app, shortcutBitmap, START_WITH_QUIT_VDISPLAY,
                        R.string.applist_menu_quit_and_start);
                addSheetAction(container, dialog, app, shortcutBitmap, START_WITH_QUIT,
                        R.string.applist_menu_quit_and_start_primarydisplay);
            } else {
                addSheetAction(container, dialog, app, shortcutBitmap, START_WITH_QUIT,
                        R.string.applist_menu_quit_and_start);
                addSheetAction(container, dialog, app, shortcutBitmap, START_WITH_QUIT_VDISPLAY,
                        R.string.applist_menu_quit_and_start_vdisplay);
            }
        }

        // Only show the hide option if this is not the currently running app or it's already hidden
        if (lastRunningAppId != app.app.getAppId() || app.isHidden) {
            addSheetAction(container, dialog, app, shortcutBitmap, HIDE_APP_ID,
                    app.isHidden ? R.string.applist_menu_show_app : R.string.applist_menu_hide_app);
        }

        addSheetAction(container, dialog, app, shortcutBitmap, VIEW_DETAILS_ID,
                R.string.applist_menu_details);

        if (shortcutBitmap != null) {
            addSheetAction(container, dialog, app, shortcutBitmap, CREATE_SHORTCUT_ID,
                    R.string.applist_menu_scut);
        }

        addSheetAction(container, dialog, app, shortcutBitmap, EXPORT_LAUNCHER_FILE_ID,
                R.string.applist_menu_export_launcher);

        dialog.setContentView(container);
        dialog.show();
    }

    private void addSheetAction(LinearLayout container, BottomSheetDialog dialog,
                                final AppObject app, final Bitmap shortcutBitmap,
                                final int actionId, int labelRes) {
        float density = getResources().getDisplayMetrics().density;
        TextView row = new TextView(this);
        row.setText(labelRes);
        row.setTextColor(ContextCompat.getColor(this, R.color.artemis_text));
        row.setTextSize(16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight((int) (48 * density));
        row.setPadding((int) (8 * density), 0, (int) (8 * density), 0);

        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);

        row.setOnClickListener(v -> {
            dialog.dismiss();
            handleAppAction(actionId, app, shortcutBitmap);
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (2 * density);
        container.addView(row, params);
    }

    private void handleAppAction(int itemId, final AppObject app, Bitmap shortcutBitmap) {
        switch (itemId) {
            case START_WITH_QUIT:
            case START_WITH_QUIT_VDISPLAY: {
                boolean withVDisplay = itemId == START_WITH_QUIT_VDISPLAY;
                if (withVDisplay && !(computer.vDisplaySupported && computer.vDisplayDriverReady)) {
                    UiHelper.displayVdisplayConfirmationDialog(
                            AppView.this,
                            computer,
                            () -> UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                                @Override
                                public void run() {
                                    ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, true);
                                }
                            }, null),
                            null
                    );
                } else {
                    UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                        @Override
                        public void run() {
                            ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, withVDisplay);
                        }
                    }, null);
                }
                return;
            }

            case START_OR_RESUME_ID:
            case START_WITH_VDISPLAY: {
                boolean withVDisplay = itemId == START_WITH_VDISPLAY;
                if (withVDisplay && !(computer.vDisplaySupported && computer.vDisplayDriverReady)) {
                    UiHelper.displayVdisplayConfirmationDialog(
                            AppView.this,
                            computer,
                            () -> ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, true),
                            null
                    );
                } else {
                    // Resume is the same as start for us
                    ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, withVDisplay);
                }
                return;
            }

            case QUIT_ID: {
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        suspendGridUpdates = true;
                        ServerHelper.doQuit(AppView.this, computer,
                                app.app, managerBinder, new Runnable() {
                                    @Override
                                    public void run() {
                                        // Trigger a poll immediately
                                        suspendGridUpdates = false;
                                        if (poller != null) {
                                            poller.pollNow();
                                        }
                                    }
                                });
                    }
                }, null);
                return;
            }

            case VIEW_DETAILS_ID: {
                Dialog.displayDialog(AppView.this, getResources().getString(R.string.title_details), app.app.toString(), false);
                return;
            }

            case HIDE_APP_ID: {
                if (app.isHidden) {
                    // Transitioning hidden to shown
                    hiddenAppIds.remove(app.app.getAppId());
                } else {
                    // Transitioning shown to hidden
                    hiddenAppIds.add(app.app.getAppId());
                }
                updateHiddenApps(false);
                return;
            }

            case CREATE_SHORTCUT_ID: {
                if (shortcutBitmap == null) {
                    Toast.makeText(AppView.this, R.string.unable_to_pin_shortcut, Toast.LENGTH_LONG).show();
                    return;
                }
                if (!shortcutHelper.createPinnedGameShortcut(computer, app.app, shortcutBitmap)) {
                    Toast.makeText(AppView.this, R.string.unable_to_pin_shortcut, Toast.LENGTH_LONG).show();
                }
                return;
            }

            case EXPORT_LAUNCHER_FILE_ID: {
                if (app.app.getAppUUID() == null || app.app.getAppUUID().isEmpty()) {
                    UiHelper.displayConfirmationDialog(
                            AppView.this,
                            getResources().getString(R.string.title_export_sunshine_launcher_file),
                            getResources().getString(R.string.message_export_sunshine_launcher_file),
                            getResources().getString(R.string.proceed),
                            getResources().getString(R.string.cancel),
                            () -> shortcutHelper.exportLauncherFile(computer, app.app),
                            null
                    );
                } else {
                    shortcutHelper.exportLauncherFile(computer, app.app);
                }
                return;
            }
        }
    }

    private void updateUiWithServerinfo(final ComputerDetails details) {
        AppView.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                // Look through our current app list to tag the running app
                for (AppObject existingApp : appGridAdapter.getAllApps()) {
                    // There can only be one or zero apps running.
                    if (existingApp.isRunning &&
                            existingApp.app.getAppId() == details.runningGameId) {
                        // This app was running and still is, so we're done now
                        return;
                    }
                    else if (existingApp.app.getAppId() == details.runningGameId) {
                        // This app wasn't running but now is
                        existingApp.isRunning = true;
                        updated = true;
                    }
                    else if (existingApp.isRunning) {
                        // This app was running but now isn't
                        existingApp.isRunning = false;
                        updated = true;
                    }
                    else {
                        // This app wasn't running and still isn't
                    }
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                }
                updateEmptyState();
            }
        });
    }

    private void updateUiWithAppList(final List<NvApp> appList) {
        AppView.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                // First handle app updates and additions
                for (NvApp app : appList) {
                    boolean foundExistingApp = false;

                    // Try to update an existing app in the list first
                    for (AppObject existingApp : appGridAdapter.getAllApps()) {
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            // Found the app; update its properties
                            if (!existingApp.app.getAppName().equals(app.getAppName())) {
                                existingApp.app.setAppName(app.getAppName());
                                updated = true;
                            }

                            foundExistingApp = true;
                            break;
                        }
                    }

                    if (!foundExistingApp) {
                        // This app must be new
                        appGridAdapter.addApp(new AppObject(app));

                        // We could have a leftover shortcut from last time this PC was paired
                        // or if this app was removed then added again. Enable those shortcuts
                        // again if present.
                        shortcutHelper.enableAppShortcut(computer, app);

                        updated = true;
                    }
                }

                // Next handle app removals
                for (AppObject existingApp : appGridAdapter.getAllApps()) {
                    boolean foundExistingApp = false;

                    // Check if this app is in the latest list
                    for (NvApp app : appList) {
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            foundExistingApp = true;
                            break;
                        }
                    }

                    // This app was removed in the latest app list
                    if (!foundExistingApp) {
                        shortcutHelper.disableAppShortcut(computer, existingApp.app, getString(R.string.app_removed_from_pc));
                        appGridAdapter.removeApp(existingApp);
                        updated = true;
                    }
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                }
                appGridAdapter.setLoading(false);
                updateEmptyState();
            }
        });
    }

    @Override
    public int getAdapterFragmentLayoutId() {
        return PreferenceConfiguration.readPreferences(AppView.this).smallIconMode ?
                    R.layout.app_grid_view_small : R.layout.app_grid_view;
    }

    @Override
    public void receiveAbsListView(AbsListView listView) {
        listView.setAdapter(appGridAdapter);
        updateEmptyState();
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
                                    long id) {
                AppObject app = (AppObject) appGridAdapter.getItem(pos);
                if (app == null) {
                    return;
                }

                // Only open the context menu if something is running, otherwise start it
                if (lastRunningAppId != 0) {
                    if (prefConfig.resumeWithoutConfirm && lastRunningAppId == app.app.getAppId()) {
                        ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, prefConfig.useVirtualDisplay);
                    } else {
                        showAppActions(app, arg1);
                    }
                } else {
                    if (prefConfig.useVirtualDisplay && !(computer.vDisplaySupported && computer.vDisplayDriverReady)) {
                        UiHelper.displayVdisplayConfirmationDialog(
                                AppView.this,
                                computer,
                                () -> ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, true),
                                null
                        );
                    } else {
                        ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, prefConfig.useVirtualDisplay);
                    }
                }
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            AppObject app = (AppObject) appGridAdapter.getItem(position);
            if (app == null) {
                return true;
            }
            showAppActions(app, view);
            return true;
        });
        UiHelper.applyStatusBarPadding(listView);
        listView.requestFocus();
    }

    public static class AppObject {
        public final NvApp app;
        public boolean isRunning;
        public boolean isHidden;

        public AppObject(NvApp app) {
            if (app == null) {
                throw new IllegalArgumentException("app must not be null");
            }
            this.app = app;
        }

        @Override
        public String toString() {
            return app.getAppName();
        }
    }
}
