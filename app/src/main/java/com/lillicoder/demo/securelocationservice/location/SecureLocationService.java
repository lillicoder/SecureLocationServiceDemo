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

package com.lillicoder.demo.securelocationservice.location;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.location.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.lillicoder.demo.securelocationservice.task.OnTaskCompletedListener;

import java.util.List;

/**
 * {@link Service} that provides consistent, reliable location updates. This service
 * will monitor the mock location system setting and will not return location results
 * while mock location is active. This service is most useful when wanting to avoid
 * location spoofing via the mock location setting. This service does not defeat rooted
 * devices, as there are several undetectable techniques to spoof location on a rooted device.
 * <p/>
 * Note that this service is battery friendly. Location listeners will only be turned
 * on until the first update comes in, after which location listeners will stop. The listeners
 * are activated periodically to maintain location freshness. When no location providers are
 * enabled on a device, we will not draw power as that hardware is turned off. Once location
 * hardware is activated, we will start the location update process again.
 *
 * @author lillicoder
 */
public class SecureLocationService extends Service {

    private static final String TAG = "SecureLocationService";

    /**
     * Broadcast message type that indicates a change in cached {@link Address} for this service.
     */
    public static final String BROADCAST_CACHED_ADDRESS_CHANGED = "broadcast_cachedAddressChanged";

    /**
     * Broadcast message type that indicates a change in cached {@link Location} for this service.
     */
    public static final String BROADCAST_CACHED_LOCATION_CHANGED = "broadcast_cachedLocationChanged";

    /**
     * Extra included with {@link #BROADCAST_CACHED_ADDRESS_CHANGED} broadcasts. Packs a {@link Address} object.
     */
    public static final String BROADCAST_EXTRA_ADDRESS = "broadcastExtra_address";

    /**
     * Extra included with {@link #BROADCAST_CACHED_LOCATION_CHANGED} broadcasts. Packs a {@link Location} object.
     */
    public static final String BROADCAST_EXTRA_LOCATION = "broadcastExtra_location";

    private static final String DEBUG_BEST_PROVIDERS =
        "Best provider: %s / Best available provider: %s";

    private static final String DEBUG_NEW_CACHED_LOCATION =
        "New cached location set: %f/%f";

    private static final String DEBUG_NEW_LOCATION_STALE_OR_UNTRUSTED_WILL_NOT_CACHE =
        "Given location is stale or source is untrusted, will not be cached. Location timestamp: %s";

    private static final String ERROR_FAILED_TO_NOTIFY_CACHED_ADDRESS_CHANGED =
        "Failed to broadcast notification of changed address, broadcast manager is null.";

    private static final String ERROR_FAILED_TO_NOTIFY_CACHED_LOCATION_CHANGED =
        "Failed to broadcast notification of changed location, broadcast manager is null.";

    private static final String WARNING_FAILED_TO_GEOCODE_ADDRESS =
        "Failed to geocode location, minimal Address instance will be used.";

    private static final String WARNING_NO_LOCATION_PROVIDERS_AVAILABLE =
        "No location providers exist on this device, listeners will not be set.";

    private static final long LOCATION_EXPIRY_AGE_IN_MILLIS = 600000L; // 10 minutes
    private static final int LOCATION_UPDATE_INTERVAL_IN_MILLIS = 600000; // 10 minutes
    private static final int LOCATION_UPDATE_INTERVAL_IMMEDIATE = 0; // 0 seconds

    private static final long MIN_UPDATE_TIME_IN_MILLIS = 30000L; // 30 seconds
    private static final float MIN_UPDATE_DISTANCE_IN_METERS = 0.0F; // 0 meters

    private boolean mAreLocationProvidersUntrusted;

    private ContentObserver mSecureSettingsContentObserver;
    private Handler mHandler;
    private LocalBroadcastManager mBroadcastManager;
    private LocationManager mLocationManager;

    private Address mCachedAddress;
    private Location mCachedLocation;

    /**
     * {@link LocationListener} for best, unavailable {@link LocationProvider}.
     * If this listener's provider becomes available, we will re-register
     * location listeners in order to get the best possible provider.
     */
    private LocationListener mBestProviderListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
            // We have a new location to cache
            cacheLocation(location);
        }

        @Override
        public void onProviderDisabled(String provider) {
            // No-op, providers using this listener were already disabled
        }

        @Override
        public void onProviderEnabled(String provider) {
            // Provider is now enabled and may be a better choice than
            // the currently registered listeners, so attempt
            // to register again
            registerLocationListeners();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

    };

    /**
     * {@link LocationListener} for best, available {@link LocationProvider}.
     * If this listener's provider becomes unavailable, we will re-register
     * location listeners in order to get the best possible provider.
     */
    private LocationListener mBestAvailableProviderListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
            // We have a new location to cache
            cacheLocation(location);
        }

        @Override
        public void onProviderDisabled(String provider) {
            // Provider is now disabled, register a new listener (if available)
            registerLocationListeners();
        }

        @Override
        public void onProviderEnabled(String provider) {
            // No-op, disabled providers were registered with the best provider
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

    };

    /**
     * {@link OnTaskCompletedListener} callback for the results of location geocode operations.
     */
    private OnTaskCompletedListener<Address> mGeocodeLocationResponse = new OnTaskCompletedListener<Address>() {
        @Override
        public void onPreExecute() {
        }

        @Override
        public void onSuccess(Address result) {
            setCachedAddress(result);
        }

        @Override
        public void onFailure(Exception exception) {
            Log.w(TAG, WARNING_FAILED_TO_GEOCODE_ADDRESS, exception);
        }
    };

    /**
     * {@link Runnable} that registers listeners to get new location updates.
     */
    private Runnable mUpdateLocationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mAreLocationProvidersUntrusted) {
                registerLocationListeners();
            } else {
                // We do not have trusted providers, try again later
                scheduleLocationUpdate(LOCATION_UPDATE_INTERVAL_IN_MILLIS);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        // We can safely use new local binder instances
        // as the core Service reference will not be leaked
        return new LocalBinder<SecureLocationService>(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler();
        mBroadcastManager = LocalBroadcastManager.getInstance(this);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mAreLocationProvidersUntrusted = isMockLocationEnabled(); // Marks location as untrusted if Mock Location is on

        // Configure secure settings observer and register it
        mSecureSettingsContentObserver = new ContentObserver(mHandler) {
            @Override
            public boolean deliverSelfNotifications() {
                return true;
            }

            @Override
            public void onChange(boolean selfChange) {
                if (isMockLocationEnabled()) {
                    // Mock Location setting is active, we cannot trust location
                    mAreLocationProvidersUntrusted = true;
                    clearCache();
                } else if (mAreLocationProvidersUntrusted) {
                    // Location was untrusted but Mock Location is now disabled,
                    // start listeners for a new update immediately
                    mAreLocationProvidersUntrusted = false;
                    registerLocationListeners();
                }
            }
        };
        getContentResolver().registerContentObserver(android.provider.Settings.Secure.CONTENT_URI,
                                                     true,
                                                     mSecureSettingsContentObserver);

        // Kick off initial location update
        updateLocation();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Unregister all active listeners
        getContentResolver().unregisterContentObserver(mSecureSettingsContentObserver);
        unregisterLocationListeners();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Note we will not process anything here, first-run location updates automatically
        // happen in onCreate()

        // This service can be reclaimed for memory at any time, return START_NOT_STICKY
        return Service.START_NOT_STICKY;
    }

    /**
     * Determines if at least one {@link LocationProvider} is active on the device.
     * If no providers are active, location services are disabled and the user will need
     * to turn on the location settings or the location hardware.
     * @return <code>true</code> if at least one location provider is enabled,
     * <code>false</code> otherwise.
     */
    public boolean areLocationProvidersEnabled() {
        LocationManager manager = getLocationManager();
        List<String> activeProviders = manager.getProviders(true);

        // Check to make sure that we don't just have the passive provider,
        // as that provider is always present
        boolean isOnlyPassiveProvider =
            activeProviders.size() == 1 && activeProviders.contains("passive");
        return activeProviders != null && !activeProviders.isEmpty() && !isOnlyPassiveProvider;
    }

    /**
     * Caches the given {@link Location}.
     * @param location {@link Location} to cache.
     * @return <code>true</code> if the given location was cached,
     * <code>false</code> if the given location was rejected for being stale.
     */
    private boolean cacheLocation(Location location) {
        boolean isLocationStale = isLocationStale(location);
        if (!isLocationStale && !mAreLocationProvidersUntrusted) {
            Log.d(TAG, String.format(DEBUG_NEW_CACHED_LOCATION, location.getLatitude(), location.getLongitude()));

            // We have a better location, save the location and stop listening for updates
            setCachedLocation(location);
            unregisterLocationListeners();

            // Run a background task to geocode the new location to an Address instance
            GeocodeLocationTask task =
                new GeocodeLocationTask(this, mGeocodeLocationResponse);
            task.execute(location);

            // We have saved a new location, we can disable the untrusted location flag
            mAreLocationProvidersUntrusted = false;

            // Schedule an update for a future location check
            scheduleLocationUpdate(LOCATION_UPDATE_INTERVAL_IN_MILLIS);
        } else if (location != null) {
            Log.d(TAG, String.format(DEBUG_NEW_LOCATION_STALE_OR_UNTRUSTED_WILL_NOT_CACHE, Long.toString(location.getTime())));
        }

        return isLocationStale;
    }

    /**
     * Clears all currently cached {@link Location} and {@link Address} values in this service.
     */
    private void clearCache() {
        setCachedAddress(null);
        setCachedLocation(null);
    }

    /**
     * Determines if the given provider exists on the device. This check includes
     * both active and non-active providers.
     * @param provider Provider name to check for.
     * @return <code>true</code> if this device has the location provider,
     * <code>false</code> otherwise.
     */
    public boolean hasLocationProvider(String provider) {
        LocationManager manager = getLocationManager();
        List<String> providers = manager.getProviders(false);

        return providers != null && providers.contains(provider);
    }

    /**
     * Determines if the given {@link Location} is stale.
     * @param location {@link Location} to check.
     * @return <code>true</code> if the given location is older than the expiry age,
     * <code>false</code> otherwise.
     */
    private boolean isLocationStale(Location location) {
        if (location == null)
            // No location is always stale
            return true;

        // Check against maximum allowed location age
        long timeSinceLastUpdate = System.currentTimeMillis() - location.getTime();
        return timeSinceLastUpdate >= LOCATION_EXPIRY_AGE_IN_MILLIS;
    }

    /**
     * Determines if the Mock Location setting on the device is enabled.
     * @return <code>true</code> if Mock Location is enabled,
     * <code>false</code> otherwise.
     */
    public boolean isMockLocationEnabled() {
        boolean isMockLocationEnabled = false;

        ContentResolver resolver = getContentResolver();
        String mockLocationSetting =
            Settings.Secure.getString(resolver, Settings.Secure.ALLOW_MOCK_LOCATION);
        isMockLocationEnabled = mockLocationSetting != null && mockLocationSetting.equals("1");

        Log.d(TAG, "Mock Location setting is " + (isMockLocationEnabled ? "ENABLED" : "DISABLED"));

        return isMockLocationEnabled;
    }

    /**
     * Sends a local broadcast that nofities of a change in cached {@link Address} for this service.
     * @param address Newly cached address.
     */
    private void notifyCachedAddressChanged(Address address) {
        if (mBroadcastManager != null) {
            Intent notify = new Intent(BROADCAST_CACHED_ADDRESS_CHANGED);
            notify.putExtra(BROADCAST_EXTRA_ADDRESS, address);

            mBroadcastManager.sendBroadcast(notify);
        } else {
            Log.e(TAG, ERROR_FAILED_TO_NOTIFY_CACHED_ADDRESS_CHANGED);
        }
    }

    /**
     * Sends a local broadcast that notifies of a change in cached {@link Location} for this service.
     * @param location Newly cached location.
     */
    private void notifyCachedLocationChanged(Location location) {
        if (mBroadcastManager != null) {
            Intent notify = new Intent(BROADCAST_CACHED_LOCATION_CHANGED);
            notify.putExtra(BROADCAST_EXTRA_LOCATION, location);

            mBroadcastManager.sendBroadcast(notify);
        } else {
            Log.e(TAG, ERROR_FAILED_TO_NOTIFY_CACHED_LOCATION_CHANGED);
        }
    }

    /**
     * Registers the best available {@link LocationListener}. Better {@link LocationProvider}
     * that are not currently enabled are set with a listener that will re-run this method when they
     * become enabled to order to allow them to be selected as the best available provider.
     */
    private void registerLocationListeners() {
        // Remove active listener before attempting to register a new ones
        unregisterLocationListeners();

        LocationManager manager = getLocationManager();
        Criteria criteria = getListenerCriteria();
        String bestProvider =
            manager.getBestProvider(criteria, false);
        String bestAvailableProvider =
            manager.getBestProvider(criteria, true);

        Log.d(TAG, String.format(DEBUG_BEST_PROVIDERS, bestProvider, bestAvailableProvider));

        if (bestProvider == null) {
            Log.w(TAG, WARNING_NO_LOCATION_PROVIDERS_AVAILABLE);
        } else if (bestProvider.equals(bestAvailableProvider)) {
            // We have access to the best provider, start getting updates
            manager.requestLocationUpdates(bestAvailableProvider,
                                           MIN_UPDATE_TIME_IN_MILLIS,
                                           MIN_UPDATE_DISTANCE_IN_METERS,
                                           mBestAvailableProviderListener);
        } else {
            // We do not have access to the best provider, request updates for the best
            // available provider and set a listener for the best provider so we can monitor
            // when it comes online
            manager.requestLocationUpdates(bestProvider,
                                           MIN_UPDATE_TIME_IN_MILLIS,
                                           MIN_UPDATE_DISTANCE_IN_METERS,
                                           mBestProviderListener);

            if (bestAvailableProvider != null) {
                manager.requestLocationUpdates(bestAvailableProvider,
                                               MIN_UPDATE_TIME_IN_MILLIS,
                                               MIN_UPDATE_DISTANCE_IN_METERS,
                                               mBestAvailableProviderListener);
            } else {
                // No device providers are enabled at this time, attach a listener to each
                // provider so that when they are turned on, we can attempt to switch to
                // the best available provider (note that since no providers are enabled,
                // we will not draw extra power by requesting frequent updates as the hardware
                // is disabled)
                for (String provider : manager.getAllProviders())
                    manager.requestLocationUpdates(provider, 0, 0, mBestProviderListener);
            }
        }
    }

    /**
     * Schedules a location update to run in the given amount of milliseconds from now.
     * @param updateTimeInMillis Time (in milliseconds) to wait before starting location update.
     */
    private void scheduleLocationUpdate(int updateTimeInMillis) {
        Handler handler = getHandler();
        handler.removeCallbacks(mUpdateLocationRunnable);
        handler.postDelayed(mUpdateLocationRunnable, updateTimeInMillis);
    }

    /**
     * Sets the cached {@link Address} for this service.
     * @param address
     */
    private void setCachedAddress(Address address) {
        mCachedAddress = address;
        notifyCachedAddressChanged(address);
    }

    /**
     * Sets the cached {@link Location} for this service.
     * @param location
     */
    private void setCachedLocation(Location location) {
        mCachedLocation = location;
        notifyCachedLocationChanged(location);
    }

    /**
     * Unregisters all {@link LocationListener}. This will stop location updates.
     */
    private void unregisterLocationListeners() {
        LocationManager manager = getLocationManager();
        manager.removeUpdates(mBestProviderListener);
        manager.removeUpdates(mBestAvailableProviderListener);
    }

    /**
     * Updates the cached location maintained by this service. If the currently
     * cached location is not stale, we schedule for future location updates.
     * If our currently cached location is stale, we immediately get new location
     * updates.
     */
    private void updateLocation() {
        // Check to see if we have stale locations and if so, register listeners for an initial update
        if (isLocationStale(getCachedLocation())) {
            scheduleLocationUpdate(LOCATION_UPDATE_INTERVAL_IMMEDIATE);
        } else {
            scheduleLocationUpdate(LOCATION_UPDATE_INTERVAL_IN_MILLIS);
        }
    }

    /**
     * Gets the {@link Address} cached by this service.
     * @return Last cached {@link Address} update or {@code null} if no cached address is available.
     */
    public Address getCachedAddress() {
        return mCachedAddress;
    }

    /**
     * Gets the {@link Location} cached by this service.
     * @return Last cached {@link Location} update.
     */
    public Location getCachedLocation() {
        return mCachedLocation;
    }

    /**
     * Gets the {@link Criteria} to use when attempting to find the best
     * {@link LocationProvider} on the current device.
     * @return {@link Criteria} to use when finding location providers.
     */
    private Criteria getListenerCriteria() {
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(false);
        criteria.setCostAllowed(true);

        return criteria;
    }

    /**
     * Gets the last passive location update.
     * @return Most recent passive {@link Location} update.
     */
    public Location getPassiveLocation() {
        LocationManager manager =
            getLocationManager();
        return manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
    }

    /**
     * Gets this service's {@link Handler}.
     * @return {@link Handler} for this service.
     */
    private Handler getHandler() {
        return mHandler;
    }

    /**
     * Gets this service's {@link LocationManager}.
     * @return {@link LocationManager} for this service.
     */
    private LocationManager getLocationManager() {
        return mLocationManager;
    }

}
