package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.PcGridAdapter;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.HostHttpResponseException;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.StreamSettings;
import com.limelight.profiles.ProfilesManager;
import com.limelight.ui.AdapterFragment;
import com.limelight.ui.AdapterFragmentCallbacks;
import com.limelight.utils.Dialog;
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PcView extends AppCompatActivity implements AdapterFragmentCallbacks {
    private View noPcFoundLayout;
    private PcGridAdapter pcGridAdapter;
    private ShortcutHelper shortcutHelper;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private boolean hideHelpForFireTv;
    private ComputerDetails.AddressTuple pendingPairingAddress;
    private String pendingPairingPin, pendingPairingPassphrase;
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

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Start updates
                    startComputerUpdates();

                    // Force a keypair to be generated early to avoid discovery delays
                    new AndroidCryptoProvider(PcView.this).getClientCertificate();
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

        // Only reinitialize views if completeOnCreate() was called
        // before this callback. If it was not, completeOnCreate() will
        // handle initializing views with the config change accounted for.
        // This is not prone to races because both callbacks are invoked
        // in the main thread.
        if (completeOnCreateCalled) {
            // Reinitialize views just in case orientation changed
            initializeViews();
        }

        refreshProfileButton();
    }

    private final static int PAIR_ID = 2;
    private final static int UNPAIR_ID = 3;
    private final static int WOL_ID = 4;
    private final static int DELETE_ID = 5;
    private final static int RESUME_ID = 6;
    private final static int QUIT_ID = 7;
    private final static int VIEW_DETAILS_ID = 8;
    private final static int FULL_APP_LIST_ID = 9;
    private final static int TEST_NETWORK_ID = 10;
    private final static int GAMESTREAM_EOL_ID = 11;
    private final static int OPEN_MANAGEMENT_PAGE_ID = 20;
    private final static int PAIR_ID_OTP = 21;

    private void initializeViews() {
        setContentView(R.layout.activity_pc_view);

        UiHelper.notifyNewRootView(this);

        // Allow floating expanded PiP overlays while browsing PCs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        // Set default preferences if we've never been run
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Set the correct layout for the PC grid
        pcGridAdapter.updateLayoutWithPreferences(this, PreferenceConfiguration.readPreferences(this));

        // Apollo 2026 top bar
        hideHelpForFireTv = getPackageManager().hasSystemFeature("amazon.hardware.fire_tv");
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.app_label));
            getSupportActionBar().setSubtitle(getString(R.string.apollo_2026) + " · v" + BuildConfig.VERSION_NAME);
        }

        ExtendedFloatingActionButton profilesButton = findViewById(R.id.profilesButton);
        profilesButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PcView.this, ProfilesActivity.class));
            }
        });

        getFragmentManager().beginTransaction()
            .replace(R.id.pcFragmentContainer, new AdapterFragment())
            .commitAllowingStateLoss();

        noPcFoundLayout = findViewById(R.id.no_pc_found_layout);
        updateNoPcFoundVisibility();
        pcGridAdapter.notifyDataSetChanged();
    }

    private void openSettings() {
        startActivity(new Intent(PcView.this, StreamSettings.class));
    }

    private void openAddComputer() {
        startActivity(new Intent(PcView.this, AddComputerManually.class));
    }

    private void openHelp() {
        HelpLauncher.launchSetupGuide(PcView.this);
    }

    private void updateNoPcFoundVisibility() {
        if (noPcFoundLayout == null) {
            return;
        }

        boolean empty = pcGridAdapter.getCount() == 0;
        noPcFoundLayout.setVisibility(empty ? View.VISIBLE : View.GONE);

        TextView emptyText = findViewById(R.id.no_pc_found_text);
        ProgressBar emptySpinner = findViewById(R.id.pcs_loading);
        if (emptyText != null) {
            emptyText.setText(pcGridAdapter.hasFilter() ? R.string.no_pcs_found : R.string.searching_pc);
        }
        if (emptySpinner != null) {
            emptySpinner.setVisibility(pcGridAdapter.hasFilter() ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.pc_view_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.action_search));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        pcGridAdapter.setFilter(query);
                        updateNoPcFoundVisibility();
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        pcGridAdapter.setFilter(newText);
                        updateNoPcFoundVisibility();
                        return true;
                    }
                });
            }
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem helpItem = menu.findItem(R.id.action_help);
        if (helpItem != null) {
            helpItem.setVisible(!hideHelpForFireTv);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_settings) {
            openSettings();
            return true;
        }
        else if (itemId == R.id.action_add_pc) {
            openAddComputer();
            return true;
        }
        else if (itemId == R.id.action_help) {
            openHelp();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        // Create a GLSurfaceView to fetch GLRenderer unless we have
        // a cached result already.
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    // Save the GLRenderer string so we don't need to do this next time
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();

                    LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            completeOnCreate();
                        }
                    });
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int i, int i1) {
                }

                @Override
                public void onDrawFrame(GL10 gl10) {
                }
            });
            setContentView(surfaceView);
        }
        else {
            LimeLog.info("Cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }

        Intent intent = getIntent();

        String hostname = intent.getStringExtra("hostname");
        int port = intent.getIntExtra("port", NvHTTP.DEFAULT_HTTP_PORT);
        pendingPairingPin = intent.getStringExtra("pin");
        pendingPairingPassphrase = intent.getStringExtra("passphrase");

        if (hostname != null && pendingPairingPin != null && pendingPairingPassphrase != null) {
            pendingPairingAddress = new ComputerDetails.AddressTuple(hostname, port);
        } else {
            pendingPairingPin = null;
            pendingPairingPassphrase = null;
        }
    }

    private void completeOnCreate() {
        completeOnCreateCalled = true;

        shortcutHelper = new ShortcutHelper(this);

        UiHelper.setLocale(this);

        // Bind to the computer manager service
        bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        pcGridAdapter = new PcGridAdapter(this, PreferenceConfiguration.readPreferences(this));
        pcGridAdapter.setLoading(true);

        initializeViews();

        // Avoid an endless skeleton if the initial discovery never reports a host.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && pcGridAdapter != null && pcGridAdapter.isLoading()) {
                pcGridAdapter.setLoading(false);
                updateNoPcFoundVisibility();
            }
        }, 4000);
    }

    private void startComputerUpdates() {
        // Only allow polling to start if we're bound to CMS, polling is not already running,
        // and our activity is in the foreground.
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    if (!freezeUpdates) {
                        PcView.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateComputer(details);
                            }
                        });

                        // Add a launcher shortcut for this PC (off the main thread to prevent ANRs)
                        if (details.pairState == PairState.PAIRED) {
                            shortcutHelper.createAppViewShortcutForOnlineHost(details);
//                        } else
                        }
                            if (pendingPairingAddress != null) {
                            if (
                                details.state == ComputerDetails.State.ONLINE &&
                                details.activeAddress.equals(pendingPairingAddress)
                            ) {
                                PcView.this.runOnUiThread(() -> {
                                    doPair(details, pendingPairingPin, pendingPairingPassphrase);
                                    pendingPairingAddress = null;
                                    pendingPairingPin = null;
                                    pendingPairingPassphrase = null;
                                });
                            }
                        }
                    }
                }
            });
            runningPolling = true;
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    private void refreshProfileButton() {
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
    public void onDestroy() {
        super.onDestroy();

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        refreshProfileButton();

        inForeground = true;
        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        stopComputerUpdates(false);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Dialog.closeDialogs();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        stopComputerUpdates(false);

        // Call superclass
        super.onCreateContextMenu(menu, v, menuInfo);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(info.position);

        // Add a header with PC status details
        menu.clearHeader();
        String headerTitle = computer.details.name + " - ";
        switch (computer.details.state)
        {
            case ONLINE:
                headerTitle += getResources().getString(R.string.pcview_menu_header_online);
                break;
            case OFFLINE:
                menu.setHeaderIcon(R.drawable.ic_pc_offline);
                headerTitle += getResources().getString(R.string.pcview_menu_header_offline);
                break;
            case UNKNOWN:
                headerTitle += getResources().getString(R.string.pcview_menu_header_unknown);
                break;
        }

        menu.setHeaderTitle(headerTitle);

        // Inflate the context menu
        if (computer.details.state == ComputerDetails.State.OFFLINE ||
            computer.details.state == ComputerDetails.State.UNKNOWN) {
            menu.add(Menu.NONE, WOL_ID, 1, getResources().getString(R.string.pcview_menu_send_wol));
            menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 2, getResources().getString(R.string.pcview_menu_eol));
        }
        else if (computer.details.pairState != PairState.PAIRED) {
            menu.add(Menu.NONE, PAIR_ID_OTP, 1, getResources().getString(R.string.pcview_menu_pair_pc_otp));
            menu.add(Menu.NONE, PAIR_ID, 2, getResources().getString(R.string.pcview_menu_pair_pc));
            if (computer.details.nvidiaServer) {
                menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 3, getResources().getString(R.string.pcview_menu_eol));
            } else {
                menu.add(Menu.NONE, OPEN_MANAGEMENT_PAGE_ID, 3, getResources().getString(R.string.pcview_menu_open_management_page));
            }
        }
        else {
            if (computer.details.runningGameId != 0) {
                menu.add(Menu.NONE, RESUME_ID, 1, getResources().getString(R.string.applist_menu_resume));
                menu.add(Menu.NONE, QUIT_ID, 2, getResources().getString(R.string.applist_menu_quit));
            }

            if (computer.details.nvidiaServer) {
                menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 3, getResources().getString(R.string.pcview_menu_eol));
            } else {
                menu.add(Menu.NONE, OPEN_MANAGEMENT_PAGE_ID, 3, getResources().getString(R.string.pcview_menu_open_management_page));
            }

            menu.add(Menu.NONE, FULL_APP_LIST_ID, 4, getResources().getString(R.string.pcview_menu_app_list));
        }

        menu.add(Menu.NONE, TEST_NETWORK_ID, 5, getResources().getString(R.string.pcview_menu_test_network));
        menu.add(Menu.NONE, DELETE_ID, 6, getResources().getString(R.string.pcview_menu_delete_pc));
        menu.add(Menu.NONE, VIEW_DETAILS_ID, 7,  getResources().getString(R.string.pcview_menu_details));
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        // For some reason, this gets called again _after_ onPause() is called on this activity.
        // startComputerUpdates() manages this and won't actual start polling until the activity
        // returns to the foreground.
        startComputerUpdates();
    }

    private void doPair(final ComputerDetails computer, String otp, String passphrase) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.pairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                boolean success = false;
                try {
                    // Stop updates and wait while pairing
                    stopComputerUpdates(true);

                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        // Don't display any toast, but open the app list
                        message = null;
                        success = true;
                    }
                    else {
                        String pinStr = otp;
                        if (pinStr == null) {
                            pinStr = PairingManager.generatePinString();
                        }

                        // Spin the dialog off in a thread because it blocks
                        if (passphrase == null) {
                            Dialog.displayDialog(PcView.this, getResources().getString(R.string.pair_pairing_title),
                                    getResources().getString(R.string.pair_pairing_msg)+" "+pinStr+"\n\n"+
                                            getResources().getString(R.string.pair_pairing_help), false);
                        } else {
                            Dialog.displayDialog(PcView.this, getResources().getString(R.string.pair_pairing_title),
                                    getResources().getString(R.string.pair_otp_pairing_msg)+"\n\n"+
                                            getResources().getString(R.string.pair_otp_pairing_help), false);
                        }

                        PairingManager pm = httpConn.getPairingManager();

                        PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr, passphrase);
                        if (pairState == PairState.PIN_WRONG) {
                            message = getResources().getString(R.string.pair_incorrect_pin);
                        }
                        else if (pairState == PairState.FAILED) {
                            if (computer.runningGameId != 0) {
                                message = getResources().getString(R.string.pair_pc_ingame);
                            }
                            else {
                                message = getResources().getString(R.string.pair_fail);
                            }
                        }
                        else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                            message = getResources().getString(R.string.pair_already_in_progress);
                        }
                        else if (pairState == PairState.PAIRED) {
                            // Just navigate to the app view without displaying a toast
                            message = null;
                            success = true;

                            // Pin this certificate for later HTTPS use
                            managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();

                            // Invalidate reachability information after pairing to force
                            // a refresh before reading pair state again
                            managerBinder.invalidateStateForComputer(computer.uuid);
                        }
                        else {
                            // Should be no other values
                            message = null;
                        }
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    message = e.getMessage();
                }

                Dialog.closeDialogs();

                final String toastMessage = message;
                final boolean toastSuccess = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (toastMessage != null) {
                            Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                        }

                        if (toastSuccess) {
                            // Open the app list after a successful pairing attempt
                            doAppList(computer, true, false);
                        }
                        else {
                            // Start polling again if we're still in the foreground
                            startComputerUpdates();
                        }
                    }
                });
            }
        }).start();
    }

    private void doOTPPair(final ComputerDetails computer) {
        Context context = PcView.this;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText otpInput = new EditText(context);
        otpInput.setHint("PIN");
        otpInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        otpInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(4) });

        final EditText passphraseInput = new EditText(context);
        passphraseInput.setHint(getString(R.string.pair_passphrase_hint));
        passphraseInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(otpInput);
        layout.addView(passphraseInput);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle(R.string.pcview_menu_pair_pc_otp);
        dialogBuilder.setView(layout);

        dialogBuilder.setPositiveButton(getString(R.string.proceed), null);

        dialogBuilder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = dialogBuilder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pin = otpInput.getText().toString();
            String passphrase = passphraseInput.getText().toString();
            if (pin.length() != 4) {
                Toast.makeText(context, getString(R.string.pair_pin_length_msg), Toast.LENGTH_SHORT).show();
                return;
            }
            if (passphrase.length() < 4 ) {
                Toast.makeText(context, getString(R.string.pair_passphrase_length_msg), Toast.LENGTH_SHORT).show();
                return;
            }
            doPair(computer, pin, passphrase);
            dialog.dismiss(); // Manually dismiss the dialog if the input is valid
        });
    }

    private void doWakeOnLan(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.ONLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_pc_online), Toast.LENGTH_SHORT).show();
            return;
        }

        if (computer.macAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_no_mac), Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    WakeOnLanSender.sendWolPacket(computer);
                    message = getResources().getString(R.string.wol_waking_msg);
                } catch (IOException e) {
                    message = getResources().getString(R.string.wol_fail);
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doUnpair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.unpairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        httpConn.unpair();
                        if (httpConn.getPairState() == PairState.NOT_PAIRED) {
                            message = getResources().getString(R.string.unpair_success);
                        }
                        else {
                            message = getResources().getString(R.string.unpair_fail);
                        }
                    }
                    else {
                        message = getResources().getString(R.string.unpair_error);
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Intent i = new Intent(this, AppView.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired);
        i.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        startActivity(i);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        final ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(info.position);
        if (computer == null) {
            return true;
        }
        return handlePcAction(item.getItemId(), computer);
    }

    private boolean handlePcAction(int actionId, final ComputerObject computer) {
        switch (actionId) {
            case PAIR_ID:
                doPair(computer.details, null, null);
                return true;

            case PAIR_ID_OTP:
                doOTPPair(computer.details);
                return true;

            case UNPAIR_ID:
                doUnpair(computer.details);
                return true;

            case WOL_ID:
                doWakeOnLan(computer.details);
                return true;

            case DELETE_ID:
                if (ActivityManager.isUserAMonkey()) {
                    LimeLog.info("Ignoring delete PC request from monkey");
                    return true;
                }
                UiHelper.displayDeletePcConfirmationDialog(this, computer.details, new Runnable() {
                    @Override
                    public void run() {
                        if (managerBinder == null) {
                            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                            return;
                        }
                        removeComputer(computer.details);
                    }
                }, null);
                return true;

            case FULL_APP_LIST_ID:
                doAppList(computer.details, false, true);
                return true;

            case RESUME_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                ServerHelper.doStart(this, new NvApp("app", null, computer.details.runningGameId, false), computer.details, managerBinder, false);
                return true;

            case QUIT_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        ServerHelper.doQuit(PcView.this, computer.details,
                                new NvApp("app", null, 0, false), managerBinder, null);
                    }
                }, null);
                return true;

            case VIEW_DETAILS_ID:
                Dialog.displayDialog(PcView.this, getResources().getString(R.string.title_details), computer.details.toString(), false);
                return true;

            case TEST_NETWORK_ID:
                ServerHelper.doNetworkTest(PcView.this);
                return true;

            case GAMESTREAM_EOL_ID:
                HelpLauncher.launchGameStreamEolFaq(PcView.this);
                return true;

            case OPEN_MANAGEMENT_PAGE_ID:
                String managementUrl = computer.guessManagementUrl();
                if (managementUrl == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.pcview_error_no_management_url), Toast.LENGTH_LONG).show();
                } else {
                    HelpLauncher.launchUrl(PcView.this, managementUrl);
                }
                return true;

            default:
                return false;
        }
    }

    private void showPcActions(final ComputerObject computer) {
        stopComputerUpdates(false);

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        UiHelper.configureBottomSheetForTablets(this, dialog);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.bg_bottom_sheet);

        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        container.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(computer.details.name);
        TextViewCompat.setTextAppearance(title, R.style.TextAppearance_Artemis_Title);
        container.addView(title);

        TextView status = new TextView(this);
        status.setText(getPcStatusString(computer.details.state));
        status.setTextColor(ContextCompat.getColor(this, getPcStatusColor(computer.details.state)));
        TextViewCompat.setTextAppearance(status, R.style.TextAppearance_Artemis_Caption);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = (int) (4 * density);
        statusParams.bottomMargin = (int) (8 * density);
        container.addView(status, statusParams);

        View divider = new View(this);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.artemis_border));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) density));
        dividerParams.bottomMargin = (int) (8 * density);
        container.addView(divider, dividerParams);

        if (computer.details.state == ComputerDetails.State.OFFLINE ||
                computer.details.state == ComputerDetails.State.UNKNOWN) {
            addSheetAction(container, dialog, computer, WOL_ID, R.string.pcview_menu_send_wol);
            addSheetAction(container, dialog, computer, GAMESTREAM_EOL_ID, R.string.pcview_menu_eol);
        }
        else if (computer.details.pairState != PairState.PAIRED) {
            addSheetAction(container, dialog, computer, PAIR_ID_OTP, R.string.pcview_menu_pair_pc_otp);
            addSheetAction(container, dialog, computer, PAIR_ID, R.string.pcview_menu_pair_pc);
            addSheetAction(container, dialog, computer,
                    computer.details.nvidiaServer ? GAMESTREAM_EOL_ID : OPEN_MANAGEMENT_PAGE_ID,
                    computer.details.nvidiaServer ? R.string.pcview_menu_eol : R.string.pcview_menu_open_management_page);
        }
        else {
            if (computer.details.runningGameId != 0) {
                addSheetAction(container, dialog, computer, RESUME_ID, R.string.applist_menu_resume);
                addSheetAction(container, dialog, computer, QUIT_ID, R.string.applist_menu_quit);
            }

            addSheetAction(container, dialog, computer,
                    computer.details.nvidiaServer ? GAMESTREAM_EOL_ID : OPEN_MANAGEMENT_PAGE_ID,
                    computer.details.nvidiaServer ? R.string.pcview_menu_eol : R.string.pcview_menu_open_management_page);
            addSheetAction(container, dialog, computer, FULL_APP_LIST_ID, R.string.pcview_menu_app_list);
        }

        addPowerActions(container, dialog, computer);

        addSheetAction(container, dialog, computer, TEST_NETWORK_ID, R.string.pcview_menu_test_network);
        addSheetAction(container, dialog, computer, VIEW_DETAILS_ID, R.string.pcview_menu_details);
        addSheetAction(container, dialog, computer, DELETE_ID, R.string.pcview_menu_delete_pc);

        dialog.setContentView(container);
        dialog.setOnDismissListener(d -> startComputerUpdates());
        dialog.show();
    }

    private void addSheetAction(LinearLayout container, BottomSheetDialog dialog,
                                final ComputerObject computer, final int actionId,
                                int labelRes) {
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
            handlePcAction(actionId, computer);
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (2 * density);
        container.addView(row, params);
    }

    private void addPowerActions(LinearLayout container, BottomSheetDialog dialog, final ComputerObject computer) {
        List<String> commands = computer.details.serverCommands;
        if (commands == null || commands.isEmpty()) {
            return;
        }

        int shutdownIndex = findServerCommand(commands,
                "apagar", "shutdown", "shut down", "poweroff", "power off", "power-off");
        int rebootIndex = findServerCommand(commands,
                "reiniciar", "restart", "reboot");
        int cancelIndex = findServerCommand(commands,
                "cancelar apagado", "cancel shutdown", "shutdown /a", "cancelar");

        if (shutdownIndex < 0 && rebootIndex < 0 && cancelIndex < 0) {
            return;
        }

        addSheetSectionTitle(container, R.string.power_section);

        if (shutdownIndex >= 0) {
            addPowerAction(container, dialog, computer, shutdownIndex,
                    R.string.power_off,
                    R.string.power_off_confirm_title,
                    R.string.power_off_confirm_message,
                    R.color.artemis_danger);
        }
        if (rebootIndex >= 0) {
            addPowerAction(container, dialog, computer, rebootIndex,
                    R.string.power_reboot,
                    R.string.power_reboot_confirm_title,
                    R.string.power_reboot_confirm_message,
                    R.color.artemis_warning);
        }
        if (cancelIndex >= 0) {
            addPowerAction(container, dialog, computer, cancelIndex,
                    R.string.power_cancel_shutdown,
                    R.string.power_cancel_shutdown,
                    R.string.power_cancel_shutdown_message,
                    R.color.artemis_text_muted);
        }
    }

    private void addSheetSectionTitle(LinearLayout container, int titleRes) {
        float density = getResources().getDisplayMetrics().density;
        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextColor(ContextCompat.getColor(this, R.color.artemis_text_muted));
        title.setTextSize(12);
        title.setAllCaps(true);
        title.setPadding((int) (8 * density), (int) (16 * density),
                (int) (8 * density), (int) (4 * density));
        container.addView(title);
    }

    private void addPowerAction(LinearLayout container, BottomSheetDialog dialog,
                                final ComputerObject computer, final int commandIndex,
                                int labelRes, int titleRes, int messageRes, int colorRes) {
        float density = getResources().getDisplayMetrics().density;
        TextView row = new TextView(this);
        row.setText(labelRes);
        row.setTextColor(ContextCompat.getColor(this, colorRes));
        row.setTextSize(16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight((int) (48 * density));
        row.setPadding((int) (8 * density), 0, (int) (8 * density), 0);

        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);

        row.setOnClickListener(v -> {
            dialog.dismiss();
            UiHelper.displayConfirmationDialog(
                    PcView.this,
                    getString(titleRes),
                    getString(messageRes),
                    getString(R.string.proceed),
                    getString(R.string.cancel),
                    () -> sendServerCommand(computer, commandIndex),
                    null);
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (2 * density);
        container.addView(row, params);
    }

    private int findServerCommand(List<String> commands, String... keywords) {
        for (int i = 0; i < commands.size(); i++) {
            String name = commands.get(i).toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (name.contains(keyword)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void sendServerCommand(final ComputerObject computer, final int commandIndex) {
        if (managerBinder == null) {
            Toast.makeText(PcView.this, R.string.error_manager_not_running, Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(() -> {
            try {
                NvHTTP httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer.details),
                        computer.details.httpsPort, managerBinder.getUniqueId(),
                        computer.details.serverCert, PlatformBinding.getCryptoProvider(PcView.this));
                httpConn.sendServerCommand(commandIndex);
                runOnUiThread(() -> Toast.makeText(PcView.this,
                        R.string.power_command_sent, Toast.LENGTH_SHORT).show());
            } catch (HostHttpResponseException e) {
                runOnUiThread(() -> Toast.makeText(PcView.this,
                        R.string.power_permission_denied, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(PcView.this,
                        getString(R.string.power_command_failed, String.valueOf(e.getMessage())),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private int getPcStatusString(ComputerDetails.State state) {
        switch (state) {
            case ONLINE:
                return R.string.pc_card_online;
            case OFFLINE:
                return R.string.pc_card_offline;
            case UNKNOWN:
            default:
                return R.string.pc_card_unknown;
        }
    }

    private int getPcStatusColor(ComputerDetails.State state) {
        switch (state) {
            case ONLINE:
                return R.color.artemis_success;
            case OFFLINE:
                return R.color.artemis_danger;
            case UNKNOWN:
            default:
                return R.color.artemis_warning;
        }
    }

    private void removeComputer(ComputerDetails details) {
        managerBinder.removeComputer(details);

        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);

        // Delete hidden games preference value
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .remove(details.uuid)
                .apply();

        for (ComputerObject computer : pcGridAdapter.getAllComputers()) {
            if (details.equals(computer.details)) {
                // Disable or delete shortcuts referencing this PC
                shortcutHelper.disableComputerShortcut(details,
                        getResources().getString(R.string.scut_deleted_pc));

                pcGridAdapter.removeComputer(computer);
                updateNoPcFoundVisibility();

                break;
            }
        }
    }

    private void updateComputer(ComputerDetails details) {
        ComputerObject existingEntry = null;

        for (ComputerObject computer : pcGridAdapter.getAllComputers()) {
            // Check if this is the same computer
            if (details.uuid.equals(computer.details.uuid)) {
                existingEntry = computer;
                break;
            }
        }

        if (existingEntry != null) {
            // Replace the information in the existing entry
            existingEntry.details = details;
        }
        else {
            // Add a new entry
            pcGridAdapter.addComputer(new ComputerObject(details));
        }

        pcGridAdapter.setLoading(false);
        updateNoPcFoundVisibility();

        // Notify the view that the data has changed
        pcGridAdapter.notifyDataSetChanged();
    }

    @Override
    public int getAdapterFragmentLayoutId() {
        return R.layout.pc_grid_view;
    }

    @Override
    public void receiveAbsListView(AbsListView listView) {
        listView.setAdapter(pcGridAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
                                    long id) {
                ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(pos);
                if (computer == null) {
                    return;
                }
                if (computer.details.state == ComputerDetails.State.UNKNOWN ||
                    computer.details.state == ComputerDetails.State.OFFLINE) {
                    // Show the actions sheet if a PC is offline or refreshing
                    showPcActions(computer);
                } else if (computer.details.pairState != PairState.PAIRED) {
                    // Pair an unpaired machine by default
                    doPair(computer.details, null, null);
                } else {
                    doAppList(computer.details, false, false);
                }
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(position);
            if (computer == null) {
                return true;
            }
            showPcActions(computer);
            return true;
        });
        UiHelper.applyStatusBarPadding(listView);
    }

    public static class ComputerObject {
        public ComputerDetails details;

        public ComputerObject(ComputerDetails details) {
            if (details == null) {
                throw new IllegalArgumentException("details must not be null");
            }
            this.details = details;
        }

        @Override
        public String toString() {
            return details.name;
        }
        public String guessManagementUrl() {
            if (details.activeAddress == null) return null;
            return "https://" + details.activeAddress.address + ":" + (details.guessExternalPort() + 1);
        }
    }
}
