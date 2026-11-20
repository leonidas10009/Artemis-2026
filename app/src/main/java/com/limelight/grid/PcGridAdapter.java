package com.limelight.grid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    private final ArrayList<PcView.ComputerObject> allComputers = new ArrayList<>();
    private String filterQuery = "";

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        super(context, getLayoutIdForPreferences(prefs));
        setSkeletonLayoutId(R.layout.pc_grid_item_skeleton);
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        return R.layout.pc_grid_item;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    public void addComputer(PcView.ComputerObject computer) {
        allComputers.add(computer);
        sortList(allComputers);
        rebuildItemList();
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        boolean removed = allComputers.remove(computer);
        if (removed) {
            rebuildItemList();
        }
        return removed;
    }

    /**
     * Returns a copy of every known computer, ignoring the current filter.
     * Used by PcView for data maintenance (add/remove/update), while the
     * adapter's visible list may be filtered by the search box.
     */
    public List<PcView.ComputerObject> getAllComputers() {
        return new ArrayList<>(allComputers);
    }

    public void setFilter(String query) {
        filterQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        rebuildItemList();
    }

    public boolean hasFilter() {
        return !filterQuery.isEmpty();
    }

    private void rebuildItemList() {
        itemList.clear();
        for (PcView.ComputerObject computer : allComputers) {
            if (filterQuery.isEmpty() ||
                    computer.details.name.toLowerCase(Locale.ROOT).contains(filterQuery)) {
                itemList.add(computer);
            }
        }
        notifyDataSetChanged();
    }

    private void sortList(List<PcView.ComputerObject> list) {
        Collections.sort(list, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                return lhs.details.name.toLowerCase(Locale.ROOT)
                        .compareTo(rhs.details.name.toLowerCase(Locale.ROOT));
            }
        });
    }

    @Override
    public void populateView(View parentView, ImageView imgView, RelativeLayout gridMask, ProgressBar prgView, TextView txtView, ImageView overlayView, PcView.ComputerObject obj) {
        imgView.setImageResource(R.drawable.ic_computer);
        if (obj.details.state == ComputerDetails.State.ONLINE) {
            imgView.setAlpha(1.0f);
        }
        else {
            imgView.setAlpha(0.4f);
        }

        if (obj.details.state == ComputerDetails.State.UNKNOWN) {
            prgView.setVisibility(View.VISIBLE);
        }
        else {
            prgView.setVisibility(View.INVISIBLE);
        }

        txtView.setText(obj.details.name);
        if (obj.details.state == ComputerDetails.State.ONLINE) {
            txtView.setAlpha(1.0f);
        }
        else {
            txtView.setAlpha(0.4f);
        }

        if (obj.details.state == ComputerDetails.State.OFFLINE) {
            overlayView.setImageResource(R.drawable.ic_pc_offline);
            overlayView.setAlpha(0.4f);
            overlayView.setVisibility(View.VISIBLE);
        }
        // We must check if the status is exactly online and unpaired
        // to avoid colliding with the loading spinner when status is unknown
        else if (obj.details.state == ComputerDetails.State.ONLINE &&
                obj.details.pairState == PairingManager.PairState.NOT_PAIRED) {
            overlayView.setImageResource(R.drawable.ic_lock);
            overlayView.setAlpha(1.0f);
            overlayView.setVisibility(View.VISIBLE);
        }
        else {
            overlayView.setVisibility(View.GONE);
        }

        updateStatus(parentView, obj.details.state);
    }

    private void updateStatus(View parentView, ComputerDetails.State state) {
        TextView statusText = parentView.findViewById(R.id.grid_status_text);
        View statusDot = parentView.findViewById(R.id.grid_status_dot);
        if (statusText == null || statusDot == null) {
            return;
        }

        int textRes;
        int colorRes;
        switch (state) {
            case ONLINE:
                textRes = R.string.pc_card_online;
                colorRes = R.color.artemis_success;
                break;
            case OFFLINE:
                textRes = R.string.pc_card_offline;
                colorRes = R.color.artemis_danger;
                break;
            case UNKNOWN:
            default:
                textRes = R.string.pc_card_unknown;
                colorRes = R.color.artemis_warning;
                break;
        }

        statusText.setText(textRes);
        statusDot.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(parentView.getContext(), colorRes)));
    }
}
