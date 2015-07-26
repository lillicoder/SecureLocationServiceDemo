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
import android.os.Binder;

import java.lang.ref.WeakReference;

/**
 * Utility {@link Binder} implementation that takes any arbitrary concrete {@link Service}.
 * Binders should never be anonymous, local classes as shown in Android framework samples,
 * so we wrap a {@link WeakReference} around the given concrete service in order to prevent
 * service leaks.
 * @param <T> Type of {@link Service} this binder represents.
 * 
 * @author lillicoder
 */
public class LocalBinder<T extends Service> extends Binder {

	private WeakReference<T> mService;
	
	public LocalBinder(T service) {
		this.setService(service);
	}
	
	public T getService() {
		return mService.get();
	}
	
	private void setService(T service) {
		mService = new WeakReference<T>(service);
	}
	
}
