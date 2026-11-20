package com.limelight.grid;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.limelight.R;

import java.util.ArrayList;

public abstract class GenericGridAdapter<T> extends BaseAdapter {
    private static final int SKELETON_COUNT = 6;

    protected final Context context;
    private int layoutId;
    private int skeletonLayoutId;
    private boolean loading;
    final ArrayList<T> itemList = new ArrayList<>();
    private final LayoutInflater inflater;

    GenericGridAdapter(Context context, int layoutId) {
        this.context = context;
        this.layoutId = layoutId;

        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    void setLayoutId(int layoutId) {
        if (layoutId != this.layoutId) {
            this.layoutId = layoutId;

            // Force the view to be redrawn with the new layout
            notifyDataSetInvalidated();
        }
    }

    void setSkeletonLayoutId(int skeletonLayoutId) {
        this.skeletonLayoutId = skeletonLayoutId;
    }

    public void setLoading(boolean loading) {
        boolean effectiveLoading = loading && skeletonLayoutId != 0;
        if (this.loading != effectiveLoading) {
            this.loading = effectiveLoading;
            notifyDataSetChanged();
        }
    }

    public boolean isLoading() {
        return loading;
    }

    public void clear() {
        itemList.clear();
    }

    @Override
    public int getCount() {
        return loading ? SKELETON_COUNT : itemList.size();
    }

    @Override
    public Object getItem(int i) {
        return loading ? null : itemList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return loading ? 1 : 0;
    }

    public abstract void populateView(View parentView, ImageView imgView, RelativeLayout gridMask, ProgressBar prgView, TextView txtView, ImageView overlayView, T obj);

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        if (loading) {
            if (convertView == null) {
                convertView = inflater.inflate(skeletonLayoutId, viewGroup, false);
            }
            return convertView;
        }

        if (convertView == null) {
            convertView = inflater.inflate(layoutId, viewGroup, false);
        }

        ImageView imgView = convertView.findViewById(R.id.grid_image);
        RelativeLayout gridMask = convertView.findViewById(R.id.grid_mask);
        ImageView overlayView = convertView.findViewById(R.id.grid_overlay);
        TextView txtView = convertView.findViewById(R.id.grid_text);
        ProgressBar prgView = convertView.findViewById(R.id.grid_spinner);

        populateView(convertView, imgView, gridMask, prgView, txtView, overlayView, itemList.get(i));

        return convertView;
    }
}
