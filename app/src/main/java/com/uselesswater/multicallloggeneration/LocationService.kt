package com.uselesswater.multicallloggeneration

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.CancellationException

/**
 * 定位服务
 * 获取当前位置并解析城市信息
 */
class LocationService(private val context: Context) {

    companion object {
        private const val TAG = "LocationService"
        private const val DEFAULT_PROVINCE = "山西"
        private const val DEFAULT_CITY = "太原"
    }

    /**
     * 获取当前位置信息（城市级）
     * 返回 Pair<省份, 城市>
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // 尝试获取最后已知位置
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var bestLocation: Location? = null

            for (provider in providers) {
                try {
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location != null && (bestLocation == null || location.accuracy < bestLocation.accuracy)) {
                        bestLocation = location
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "无权限访问位置: $provider")
                }
            }

            bestLocation?.let { location ->
                parseLocationToCity(location)
            } ?: run {
                Log.w(TAG, "无法获取位置，使用默认值")
                DEFAULT_PROVINCE to DEFAULT_CITY
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "定位失败: ${e.message}")
            DEFAULT_PROVINCE to DEFAULT_CITY
        }
    }

    /**
     * 将经纬度解析为城市信息
     */
    private suspend fun parseLocationToCity(location: Location): Pair<String, String> =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.CHINA)
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                addresses?.firstOrNull()?.let { address ->
                    val province = address.adminArea?.replace("省", "")?.replace("市", "") ?: DEFAULT_PROVINCE
                    val city = address.locality?.replace("市", "") ?: DEFAULT_CITY

                    Log.d(TAG, "定位成功: $province - $city")
                    province to city
                } ?: (DEFAULT_PROVINCE to DEFAULT_CITY)

            } catch (e: Exception) {
                Log.e(TAG, "地理编码失败: ${e.message}")
                DEFAULT_PROVINCE to DEFAULT_CITY
            }
        }

    /**
     * 检查定位权限
     */
    fun checkLocationPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}