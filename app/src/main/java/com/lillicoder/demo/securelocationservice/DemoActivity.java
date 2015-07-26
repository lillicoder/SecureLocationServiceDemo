/**
 * Copyright 2014 Scott Weeden-Moody
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lillicoder.demo.securelocationservice;

import android.content.*;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.lillicoder.demo.securelocationservice.location.LocalBinder;
import com.lillicoder.demo.securelocationservice.location.SecureLocationService;

import java.util.ArrayList;
import java.util.List;

public class DemoActivity extends ActionBarActivity {

    private SecureLocationService mService;

    private ListView mLocationInfoList;
    private ProgressBar mProgressBar;
    private TextView mMockLocationStatus;
    private TextView mServiceStatus;

    /**
     * {@link BroadcastReceiver} that responds to the
     * {@link SecureLocationService#BROADCAST_CACHED_ADDRESS_CHANGED} broadcast.
     */
    private BroadcastReceiver mCachedAddressChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshStatus(mService);
        }
    };

    /**
     * {@link BroadcastReceiver} that responds to the
     * {@link SecureLocationService#BROADCAST_CACHED_LOCATION_CHANGED} broadcast.
     */
    private BroadcastReceiver mCachedLocationChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshStatus(mService);
        }
    };

    /**
     * {@link ServiceConnection} for the secure location service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        @SuppressWarnings("unchecked")
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocalBinder<SecureLocationService> binder = (LocalBinder<SecureLocationService>) service;
            mService = binder.getService();

            refreshStatus(mService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            refreshStatus(null);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        mLocationInfoList = (ListView) findViewById(R.id.DemoActivity_locationInfoList);
        mProgressBar = (ProgressBar) findViewById(R.id.DemoActivity_progressBar);
        mMockLocationStatus = (TextView) findViewById(R.id.DemoActivity_mockLocationStatus);
        mServiceStatus = (TextView) findViewById(R.id.DemoActivity_serviceStatus);

        showProgressBar();

        Intent secureLocationService = new Intent(this, SecureLocationService.class);
        bindService(secureLocationService, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceivers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus(mService);
        registerReceivers();
    }

    /**
     * Gets the collection of {@link LocationInfoAdapter.LocationInfo} for the given {@link Address}.
     * @param address Address to get location info for.
     * @return Address location info.
     */
    private List<LocationInfoAdapter.LocationInfo<?>> getLocationInfo(Address address) {
        List<LocationInfoAdapter.LocationInfo<?>> info = new ArrayList<LocationInfoAdapter.LocationInfo<?>>();
        info.add(new LocationInfoAdapter.LocationInfo<Object>(R.string.cached_address));

        if (address == null) {
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_not_available, "N/A"));
        } else {
            StringBuilder addressBuilder = new StringBuilder();
            for (int index = 0; index < address.getMaxAddressLineIndex(); index++) {
                if (index > 0) {
                    addressBuilder.append("\n");
                }

                String addressLine = address.getAddressLine(index);
                if (addressLine == null) {
                    addressBuilder.append("null");
                } else {
                    addressBuilder.append(addressLine);
                }
            }

            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_address,
                                                                  addressBuilder.toString()));
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_feature,
                                                                  address.getFeatureName()));
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_admin,
                                                                  address.getAdminArea()));
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_sub_admin,
                                                                  address.getSubAdminArea()));
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_locality,
                                                                  address.getLocality()));
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_thoroughfare,
                                                                  address.getThoroughfare()));
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_postal_code,
                                                                  address.getPostalCode()));
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_country_code,
                                                                  address.getCountryCode()));
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_country_name,
                                                                  address.getCountryName()));

            // Latitude and longitude are optional, use N/A when not available
            if (address.hasLatitude()) {
                info.add(new LocationInfoAdapter.LocationInfo<Double>(R.string.location_info_latitude,
                                                                      address.getLatitude()));
            } else {
                info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_latitude,
                                                                      "N/A"));
            }

            if (address.hasLongitude()) {
                info.add(new LocationInfoAdapter.LocationInfo<Double>(R.string.location_info_longitude,
                                                                      address.getLongitude()));
            } else {
                info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_longitude,
                                                                      "N/A"));
            }

            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_phone,
                                                                  address.getPhone()));
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_url,
                                                                  address.getUrl()));
        }

        return info;
    }

    /**
     * Gets the collection of {@link LocationInfoAdapter.LocationInfo} for the given {@link Location}.
     * @param location Location to get location info for.
     * @return Location info.
     */
    private List<LocationInfoAdapter.LocationInfo<?>> getLocationInfo(Location location) {
        List<LocationInfoAdapter.LocationInfo<?>> info = new ArrayList<LocationInfoAdapter.LocationInfo<?>>();
        info.add(new LocationInfoAdapter.LocationInfo<Object>(R.string.cached_location));

        if (location == null) {
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_not_available, "N/A"));
        } else {
            info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_provider,
                                                                  location.getProvider()));
            info.add(new LocationInfoAdapter.LocationInfo<Long>(R.string.location_info_time,
                                                                location.getTime()));
            info.add(new LocationInfoAdapter.LocationInfo<Double>(R.string.location_info_latitude,
                                                                  location.getLatitude()));
            info.add(new LocationInfoAdapter.LocationInfo<Double>(R.string.location_info_longitude,
                                                                  location.getLongitude()));

            if (location.hasAccuracy()) {
                info.add(new LocationInfoAdapter.LocationInfo<Float>(R.string.location_info_accuracy,
                                                                     location.getAccuracy()));
            } else {
                info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_accuracy,
                                                                      "N/A"));
            }

            if (location.hasAltitude()) {
                info.add(new LocationInfoAdapter.LocationInfo<Double>(R.string.location_info_altitude,
                                                                      location.getAltitude()));
            } else {
                info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_altitude,
                                                                      "N/A"));
            }

            if (location.hasBearing()) {
                info.add(new LocationInfoAdapter.LocationInfo<Float>(R.string.location_info_bearing,
                                                                     location.getBearing()));
            } else {
                info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_bearing,
                                                                      "N/A"));
            }

            if (location.hasSpeed()) {
                info.add(new LocationInfoAdapter.LocationInfo<Float>(R.string.location_info_speed,
                                                                     location.getSpeed()));
            } else {
                info.add(new LocationInfoAdapter.LocationInfo<String>(R.string.location_info_speed,
                                                                      "N/A"));
            }
        }

        return info;
    }

    /**
     * Gets the status label for the mock location setting.
     * @param service {@link SecureLocationService} to check mock location setting status with.
     * @return Mock location setting status label.
     */
    private int getMockLocationStatusLabelResourceId(SecureLocationService service) {
        if (service != null && service.isMockLocationEnabled()) {
            return R.string.mock_location_status_enabled;
        } else {
            return R.string.mock_location_status_disabled;
        }
    }

    /**
     * Gets the status label for the given {@link SecureLocationService}.
     * @param service Service to get the status label for.
     * @return Secure location service status label.
     */
    private int getServiceStatusLabelResourceId(SecureLocationService service) {
        return service != null ? R.string.service_status_connected : R.string.service_status_disconnected;
    }

    /**
     * Refreshes status messaging for this activity.
     * @param service Current {@link SecureLocationService} for this activity.
     */
    private void refreshStatus(SecureLocationService service) {
        updateServiceStatus(service);

        if (service != null) {
            updateLocationInfo(service.getCachedAddress(), service.getCachedLocation());
        } else {
            mLocationInfoList.setAdapter(null);
        }
    }

    /**
     * Registers {@link BroadcastReceiver} for the {@link SecureLocationService} broadcasts.
     */
    private void registerReceivers() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.registerReceiver(mCachedAddressChangedReceiver,
                                 new IntentFilter(SecureLocationService.BROADCAST_CACHED_ADDRESS_CHANGED));
        manager.registerReceiver(mCachedLocationChangedReceiver,
                                 new IntentFilter(SecureLocationService.BROADCAST_CACHED_LOCATION_CHANGED));
    }

    /**
     * Shows this activity's location providers {@link ListView}.
     */
    private void showLocationInfoList() {
        mProgressBar.setVisibility(View.GONE);

        mLocationInfoList.setVisibility(View.VISIBLE);
        mMockLocationStatus.setVisibility(View.VISIBLE);
        mServiceStatus.setVisibility(View.VISIBLE);
    }

    /**
     * Shows this activity's {@link ProgressBar}.
     */
    private void showProgressBar() {
        mLocationInfoList.setVisibility(View.GONE);
        mMockLocationStatus.setVisibility(View.GONE);
        mServiceStatus.setVisibility(View.GONE);

        mProgressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Unregisters {@link BroadcastReceiver} for the {@link SecureLocationService} broadcasts.
     */
    private void unregisterReceivers() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.unregisterReceiver(mCachedAddressChangedReceiver);
        manager.unregisterReceiver(mCachedLocationChangedReceiver);
    }

    /**
     * Displays the information for the given {@link Address} and {@link Location}.
     * @param address Address to display.
     * @param location Location to display.
     */
    private void updateLocationInfo(Address address, Location location) {
        List<LocationInfoAdapter.LocationInfo<?>> info = new ArrayList<LocationInfoAdapter.LocationInfo<?>>();
        info.addAll(getLocationInfo(address));
        info.addAll(getLocationInfo(location));

        LocationInfoAdapter adapter = new LocationInfoAdapter(info);
        mLocationInfoList.setAdapter(adapter);
        showLocationInfoList();
    }

    /**
     * Displays the status of the {@link SecureLocationService} this activity is bound to.
     */
    private void updateServiceStatus(SecureLocationService service) {
        mServiceStatus.setText(getServiceStatusLabelResourceId(service));
        mMockLocationStatus.setText(getMockLocationStatusLabelResourceId(service));
    }

    /**
     * {@link BaseAdapter} that displays views for {@link LocationInfo}.
     */
    private static class LocationInfoAdapter extends BaseAdapter {

        private List<LocationInfo<?>> mLocationInfo;

        /**
         * Instantiates this adapter with the given collection of {@link LocationInfo}.
         * @param locationInfo Location info to back this adapter.
         */
        public LocationInfoAdapter(List<LocationInfo<?>> locationInfo) {
            mLocationInfo = locationInfo;
        }

        @Override
        public int getCount() {
            return mLocationInfo.size();
        }

        @Override
        public LocationInfo<?> getItem(int position) {
            return mLocationInfo.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LocationInfo<?> info = getItem(position);
            if (info.getType() == LocationInfo.Type.HEADER) {
                convertView = getHeaderView(info, convertView, parent);
            } else {
                convertView = getItemView(info, convertView, parent);
            }

            return convertView;
        }

        /**
         * Gets a header view for the given {@link LocationInfo}.
         * @param info Location info to get a view for.
         * @param convertView The old view to reuse, if possible.
         * @param parent The parent that this view will eventually be attached to
         * @return Header view for the given location info.
         */
        private View getHeaderView(LocationInfo<?> info, View convertView, ViewGroup parent) {
            if (convertView == null || !(convertView instanceof TextView)) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                convertView = inflater.inflate(R.layout.list_item_header, parent, false);
            }

            ((TextView) convertView).setText(info.getLabelResourceId());

            return convertView;
        }

        /**
         * Gets an item view for the given {@link LocationInfo}.
         * @param info Location info to get a view for.
         * @param convertView The old view to reuse, if possible.
         * @param parent The parent that this view will eventually be attached to
         * @return Item view for the given location info.
         */
        private View getItemView(LocationInfo<?> info, View convertView, ViewGroup parent) {
            if (convertView == null || !(convertView instanceof RelativeLayout)) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                convertView = inflater.inflate(R.layout.list_item_location_info, parent, false);
            }

            TextView label = (TextView) convertView.findViewById(R.id.LocationInfoListItem_label);
            label.setText(info.getLabelResourceId());

            TextView value = (TextView) convertView.findViewById(R.id.LocationInfoListItem_value);
            Object infoValue = info.getValue();
            value.setText(infoValue != null ? infoValue.toString() : null);

            return convertView;
        }

        /**
         * Represents a single piece of location information.
         * @param <T> Type of information represented by this info.
         */
        public static class LocationInfo<T> {

            private enum Type {
                HEADER,
                INFO;
            }

            private final int mLabelResourceId;
            private final T mValue;
            private final Type mType;

            /**
             * Instantiates this info instance with the given label resource ID and value.
             * @param labelResourceId Info label resource ID.
             * @param value Info value.
             */
            public LocationInfo(int labelResourceId, T value) {
                mLabelResourceId = labelResourceId;
                mValue = value;
                mType = Type.INFO;
            }

            /**
             * Instantiates this info instance as a header item with the given label resource ID.
             * @param labelResourceId Header label resource ID.
             */
            public LocationInfo(int labelResourceId) {
                mLabelResourceId = labelResourceId;
                mValue = null;
                mType = Type.HEADER;
            }

            /**
             * Gets the label resource ID for this info.
             * @return Info label resource ID.
             */
            public int getLabelResourceId() {
                return mLabelResourceId;
            }

            /**
             * Gets the {@link Type} for this info.
             * @return Info type.
             */
            protected Type getType() {
                return mType;
            }

            /**
             * Gets the value for this info.
             * @return Info value.
             */
            public T getValue() {
                return mValue;
            }

        }

    }
}
