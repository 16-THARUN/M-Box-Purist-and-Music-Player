package com.mediaplayer.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

object WifiDiscoveryManager {

    private const val SERVICE_TYPE = "_mbox._tcp."
    private const val SERVICE_NAME = "MBox-AudioPlayer"
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun registerService(context: Context, port: Int = 8888) {
        if (registrationListener != null) return
        try {
            nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = SERVICE_NAME
                serviceType = SERVICE_TYPE
                setPort(port)
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                    Log.i("WifiDiscoveryManager", "mDNS Service registered: ${NsdServiceInfo.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e("WifiDiscoveryManager", "mDNS Registration failed: $errorCode")
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    Log.i("WifiDiscoveryManager", "mDNS Service unregistered")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e("WifiDiscoveryManager", "mDNS Unregistration failed: $errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e("WifiDiscoveryManager", "Error registering mDNS service: ${e.message}", e)
        }
    }

    fun unregisterService() {
        try {
            registrationListener?.let { listener ->
                nsdManager?.unregisterService(listener)
            }
            registrationListener = null
        } catch (e: Exception) {
            Log.e("WifiDiscoveryManager", "Error unregistering mDNS service: ${e.message}")
        }
    }
}
