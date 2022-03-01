package com.lahsuak.apps.locationtracker.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.io.IOException
import java.util.*

object Util {
    fun getLocationPoint(context: Context, locationAddress: String): Address? {
        val geocoder = Geocoder(context, Locale.getDefault())
        var address: Address? = null
        try {
            val addressList = geocoder.getFromLocationName(locationAddress, 1);
            if (addressList != null && addressList.size > 0) {
                address = addressList[0] as Address
            }
        } catch (e: IOException) {
        }
        return address
    }
}