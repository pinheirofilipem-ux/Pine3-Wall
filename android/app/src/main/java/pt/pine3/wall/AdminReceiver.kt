package pt.pine3.wall

import android.app.admin.DeviceAdminReceiver

/** Necessário apenas para poder chamar DevicePolicyManager.lockNow() (desliga o ecrã a sério). */
class AdminReceiver : DeviceAdminReceiver()
