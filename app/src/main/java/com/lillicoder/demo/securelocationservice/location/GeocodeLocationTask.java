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

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;
import com.lillicoder.demo.securelocationservice.task.BaseTask;
import com.lillicoder.demo.securelocationservice.task.OnTaskCompletedListener;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Task that geocodes the given {@link Location} and gets the best {@link Address} match
 * from the {@link Geocoder}. Note that only the first location given will be processed.
 *
 * @author lillicoder
 */
public class GeocodeLocationTask extends BaseTask<Location, Void, Address> {

	private static final String TAG = GeocodeLocationTask.class.getSimpleName();
	
	private static final String ERROR_FAILED_TO_GET_LOCATION_PARAM = 
		"Failed to get location parameter, was one given in execute()?";

	private static final String ERROR_GEOCODER_FAILURE = 
		"Geocoder failed, returning address with latitude and longitude only.";

	private Context mContext;
	
	/**
	 * Instantiates this task with the given {@link Context} and {@link OnTaskCompletedListener}.
	 * @param context {@link Context} in which geocoding will be performed.
	 * @param listener {@link OnTaskCompletedListener} to recieve task callbacks.
	 */
	public GeocodeLocationTask(Context context, OnTaskCompletedListener<Address> listener) {
		super(listener);
		
		if (context == null) {
			throw new IllegalArgumentException("The given Context must not be null.");
        }
		
		mContext = context;
	}

	@Override
	protected Address doInBackground(Location... params) {
		Address bestMatch = null;
		
		try {
			Location location = params[0];
			double latitude = location.getLatitude();
			double longitude = location.getLongitude();
			
			// Generate a default Address instance in case the Geocoder
			// fails to get any results.
			bestMatch = new Address(Locale.ENGLISH);
			bestMatch.setLatitude(latitude);
			bestMatch.setLongitude(longitude);
			
			Geocoder geocoder = new Geocoder(mContext);
			List<Address> matches = 
				geocoder.getFromLocation(latitude, longitude, 1);
			if (matches != null && ! matches.isEmpty()) {
				bestMatch = matches.get(0);
				bestMatch.setLatitude(latitude);
				bestMatch.setLongitude(longitude);
			}
		} catch (IndexOutOfBoundsException e) {
			Log.e(TAG, ERROR_FAILED_TO_GET_LOCATION_PARAM, e);
			setException(e);
		} catch (IOException e) {
			Log.e(TAG, ERROR_GEOCODER_FAILURE, e);
			setException(e);
		}
		
		return bestMatch;
	}	
	
}
