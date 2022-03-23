/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.tworx.eud.mqttdroid.AuthState;

import org.pepzer.mqttdroid.sqlite.AppAuthDetails;
import org.pepzer.mqttdroid.sqlite.AppAuthPub;
import org.pepzer.mqttdroid.sqlite.AppAuthSub;
import org.pepzer.mqttdroid.sqlite.AuthDataSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class CustomArrayAdapter extends ArrayAdapter<AppAuthDetails> implements Filterable {
    private static final String TAG = "CustomAdapter";
    private final Context context;
    private final List<AppAuthDetails> values;
    private List<AppAuthDetails> filteredValues;
    private PackageManager pm;
    AuthDataSource dataSource;
    private ItemFilter itemFilter = new ItemFilter();
    private String lastFilter = "";

    /**
     * Compare app label field.
     */
    private Comparator<AppAuthDetails> comparatorName = new Comparator<AppAuthDetails>() {
        @Override
        public int compare(AppAuthDetails authSubL, AppAuthDetails authSubR) {
            return authSubL.getAppLabel().compareTo(authSubR.getAppLabel());
        }
    };

    /**
     * Compare app package field.
     */
    private Comparator<AppAuthDetails> comparatorPackage = new Comparator<AppAuthDetails>() {
        @Override
        public int compare(AppAuthDetails authSubL, AppAuthDetails authSubR) {
            return authSubL.getAppPackage().compareTo(authSubR.getAppPackage());
        }
    };

    /**
     * Compare request timestamp.
     */
    private Comparator<AppAuthDetails> comparatorDate = new Comparator<AppAuthDetails>() {
        @Override
        public int compare(AppAuthDetails authSubL, AppAuthDetails authSubR) {
            long lTimestamp = authSubL.getTimestamp();
            long rTimestamp = authSubR.getTimestamp();
            return (int) (rTimestamp - lTimestamp);
        }
    };

    public CustomArrayAdapter(Context context, List<AppAuthDetails> values) {
        super(context, -1, values);
        this.context = context;
        this.values = values;
        this.filteredValues = values;
        this.pm = context.getPackageManager();
        this.dataSource = ((MainActivity) context).getDataSource();
    }

    /**
     * Sort items using the specified comparator.
     * @param sortBy
     *   Enum representing the comparator to use.
     */
    public void sortItems(AppUtils.SortBy sortBy) {
        switch (sortBy) {
            case NAME:
                sort(comparatorName);
                notifyDataSetChanged();
                break;
            case PACKAGE:
                sort(comparatorPackage);
                notifyDataSetChanged();
                break;
            case DATE:
                sort(comparatorDate);
                notifyDataSetChanged();
                break;
            default:
                break;
        }
    }

    @Override
    public int getCount() {
        return filteredValues.size();
    }

    @Override
    public AppAuthDetails getItem(int position) {
        return filteredValues.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public Filter getFilter() {
        return itemFilter;
    }

    private class ItemFilter extends Filter {

        /**
         * Filter the results based on the specified constraint.
         * If constraint matches a special form use it to filter allowed/refused packages.
         * The special form is two letters inside square brackets, t for true, f for false,
         * the first column shows/hides allowed packages, the second shows/hides refused ones.
         * If constraint is any other string, filter items that contain it in the label and/or in the
         * package name.
         * @param constraint
         *   String to use to filter the items.
         * @return
         *   A `FilterResults` object containing the filtered items.
         */
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {

            Log.v(TAG, "filtering: " + constraint);

            lastFilter = constraint.toString();

            FilterResults results = new FilterResults();
            int count = values.size();
            final ArrayList<AppAuthDetails> filteredList = new ArrayList<AppAuthDetails>();

            if (constraint.length() < 1) {
                results.values = values;
                results.count = count;
                return results;
            }

            if (constraint.length() >= 4 &&
                    constraint.charAt(0) == '[' &&
                    constraint.charAt(3) == ']') {
                boolean showAllowed = constraint.charAt(1) == 't';
                boolean showRefused = constraint.charAt(2) == 't';

                Log.v(TAG, "allowed: " + showAllowed + ", refused: " + showRefused);

                if (showAllowed && showRefused) {
                    results.values = values;
                    results.count = count;
                    return results;
                }

                for (int i = 0; i < count; ++i) {
                    final AppAuthDetails authDetails = values.get(i);
                    AuthState appStatus = authDetails.getAuthStatus();
                    Log.v(TAG, "App: " + authDetails.getAppLabel() + ", status: " + appStatus);
                    switch (appStatus) {
                        case APP_ALLOWED:
                            if (showAllowed) {
                                Log.v(TAG, "App allowed: " + authDetails.getAppLabel());
                                filteredList.add(authDetails);
                            }
                            break;
                        case APP_REFUSED:
                            if (showRefused) {
                                Log.v(TAG, "App refused: " + authDetails.getAppLabel());
                                filteredList.add(authDetails);
                            }
                            break;
                        default:
                            break;
                    }
                }

            } else {

                String filterString = constraint.toString().toLowerCase();

                String filterableString;
                AppAuthDetails authDetails;

                for (int i = 0; i < count; i++) {
                    authDetails = values.get(i);
                    filterableString = authDetails.getAppLabel() + " " + authDetails.getAppPackage();
                    if (filterableString.toLowerCase().contains(filterString)) {
                        filteredList.add(authDetails);
                    }
                }
            }

            results.values = filteredList;
            results.count = filteredList.size();
            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredValues = (ArrayList<AppAuthDetails>) results.values;
            notifyDataSetChanged();
        }
    }

    /**
     * Build and return the view representing the item in the specified position.
     * Populate the collapsed view with the data of the item, find the icon from the package name.
     * @param position
     *   The position of the item in the list.
     * @param convertView
     * @param parent
     *   The parent view.
     * @return
     *   The item view.
     */
    @Override
    public View getView(int position, View convertView, final ViewGroup parent) {
        final LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View rowView = inflater.inflate(R.layout.list_item, parent, false);
        TextView first_line = (TextView) rowView.findViewById(R.id.firstLine);
        final TextView second_line = (TextView) rowView.findViewById(R.id.secondLine);
        ImageView imageView = (ImageView) rowView.findViewById(R.id.icon);

        final AppAuthDetails item = filteredValues.get(position);

        Drawable icon = null;

        try {
            icon = pm.getApplicationIcon(item.getAppPackage());
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        //Bitmap bitmap = ((BitmapDrawable)icon).getBitmap();

        first_line.setText(item.getAppLabel());

        TextView textView = (TextView) rowView.findViewById(R.id.icon_extend);
        textView.setTypeface(FontManager.getTypeface(context, FontManager.FONTAWESOME));

        textView = (TextView) rowView.findViewById(R.id.icon_package);
        textView.setTypeface(FontManager.getTypeface(context, FontManager.FONTAWESOME));

        textView = (TextView) rowView.findViewById(R.id.icon_auth);
        textView.setTypeface(FontManager.getTypeface(context, FontManager.FONTAWESOME));

        textView = (TextView) rowView.findViewById(R.id.icon_up);
        textView.setTypeface(FontManager.getTypeface(context, FontManager.FONTAWESOME));

        textView = (TextView) rowView.findViewById(R.id.icon_down);
        textView.setTypeface(FontManager.getTypeface(context, FontManager.FONTAWESOME));

        imageView.setImageDrawable(icon);
        //imageView.setImageBitmap(bitmap);

        Switch switch_button = (Switch) rowView.findViewById(R.id.switchButton);

        switch (item.getAuthStatus()) {
            case APP_ALLOWED:
                second_line.setText("Allowed");
                switch_button.setChecked(true);
                break;
            case APP_REFUSED:
                if (isNew(item.getTimestamp())) {
                    second_line.setText("New");
                    rowView.setBackgroundColor(context.getResources().getColor(R.color.colorPending));
                } else {
                    second_line.setText("Refused");
                }
                switch_button.setChecked(false);
                break;
            default: break;
        }

        /**
         * Listen for checkedChange events, store the new value on the db and update the view.
         */
        switch_button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Log.v(TAG, "Item: " + item + " ,checked: " + checked);

                if (checked) {
                    item.setAuthStatus(AuthState.APP_ALLOWED);
                    rowView.setBackgroundColor(context.getResources().getColor(R.color.colorBg));
                    second_line.setText("Allowed");
                } else {
                    item.setAuthStatus(AuthState.APP_REFUSED);
                    if (isNew(item.getTimestamp())) {
                        second_line.setText("New");
                        rowView.setBackgroundColor(context.getResources().getColor(R.color.colorPending));
                    } else {
                        second_line.setText("Refused");
                    }
                }
                dataSource.updateAuthDetails(item);
                ((MainActivity) context).restartProxyService();

                getFilter().filter(lastFilter);
//                remove(item);
//                add(newItem);
//                filteredValues.remove(item);
//                filteredValues.add(newItem);
//                Utils.SortBy sortBy = ((MainActivity) context).getSortBy();
//                sortItems(sortBy);
            }
        });

        final View outer = rowView.findViewById(R.id.detailsScrollView);
        final TextView extend = (TextView) rowView.findViewById(R.id.icon_extend);
        View details = outer.findViewById(R.id.detailsView);

        /**
         * Listen for onClick on the details view and collapse it when receive a tap.
         */
        details.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (outer.getVisibility() == View.VISIBLE) {
                    collapse(outer);
                    extend.setText(R.string.fa_chevron_up);
                }
            }});

        /**
         * Listen for click events on the collapsed view, build and expand the details view.
         * Read from db the publish and subscribe topics requested by the app and show those.
         */
        rowView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                Log.v(TAG, "onClick");

                int visible = outer.getVisibility();
                if (visible == View.VISIBLE) {
                    collapse(outer);
                    extend.setText(R.string.fa_chevron_up);
                }
                else {
                    //dataSource.open();
                    List<AppAuthPub> pubList = dataSource.getAuthPubsByPkg(item.getAppPackage());
                    List<AppAuthSub> subList = dataSource.getAuthSubsByPkg(item.getAppPackage());
                    //dataSource.close();
                    TextView packageText = (TextView) view.findViewById(R.id.package_name);
                    packageText.setText(item.getAppPackage());
                    TextView pubs = (TextView) view.findViewById(R.id.detailsPub);
                    //Collections.sort(pubList);
                    StringBuilder sb = new StringBuilder();
                    for (AppAuthPub pub : pubList) {
                        sb.append(pub.getTopic());
                        sb.append("\n");
                    }
                    pubs.setText(sb.toString());
                    TextView subs = (TextView) view.findViewById(R.id.detailsSub);
                    TextView qosSubs = (TextView) view.findViewById(R.id.detailsSubQos);
                    sb = new StringBuilder();
                    StringBuilder qosSb = new StringBuilder();
                    for (AppAuthSub sub : subList) {
                        sb.append(sub.getTopic());
                        sb.append("\n");
                        qosSb.append("QoS: " + sub.getQos());
                        if(sub.isActive()) {
                            qosSb.append("  <A>");
                        }
                        qosSb.append("\n");
                    }
                    subs.setText(sb.toString());
                    qosSubs.setText(qosSb.toString());
                    expand(outer);
                    extend.setText(R.string.fa_chevron_down);
                }
            }
        });

        return rowView;
    }

    /**
     * Perform an expand animation on a view.
     * Solution from https://stackoverflow.com/questions/4946295/android-expand-collapse-animation.
     * @param v
     *   View to animate.
     */
    public static void expand(final View v) {
        v.measure(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT);
        final int targetHeight = v.getMeasuredHeight();
        float density = v.getContext().getResources().getDisplayMetrics().density;

        Log.v(TAG, "expand, targetHeight: " + targetHeight);

        // Older versions of android (pre API 21) cancel animations for views with a height of 0.
        v.getLayoutParams().height = 1;
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation()
        {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                v.getLayoutParams().height = interpolatedTime == 1
                        ? LinearLayout.LayoutParams.WRAP_CONTENT
                        : (int)(targetHeight * interpolatedTime);
                v.requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration((int)(targetHeight / density * 1.2));
        v.startAnimation(a);
    }

    /**
     * Perform a collapse animation on a view.
     * Solution from https://stackoverflow.com/questions/4946295/android-expand-collapse-animation.
     * @param v
     *   The view to animate.
     */
    public static void collapse(final View v) {
        final int initialHeight = v.getMeasuredHeight();

        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    v.setVisibility(View.GONE);
                } else {
                    v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration((int)(initialHeight / v.getContext().getResources().getDisplayMetrics().density * 1.2));
        v.startAnimation(a);
    }

    /**
     * Check if an item is new.
     * Use the timestamp of the auth request and check if more recent than a predefined interval.
     * @param timestamp
     *   The timestamp of the stored auth request.
     * @return
     *   True if the item is new.
     */
    private boolean isNew(long timestamp) {
        long offset = 3600000; //one hour
        Date now = new Date();
        if (timestamp > (now.getTime() - offset)) {
            return true;
        }
        return  false;
    }
}
