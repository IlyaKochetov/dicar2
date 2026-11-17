package com.dicar.vehicle.data.source.bydauto

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager

/**
 * Requirement 2.6: signature permissions via reflection and a custom Context.
 *
 * BYDAuto device classes check android.permission.BYDAUTO_* locally using the supplied Context;
 * _GET/_SET are signature permissions unavailable to third-party apps. Allowing local checks
 * was reported to work on DiLink 3 (Dolphin / Song Pro GS), per wheregoes/byd-apps BydPermissionContext.java.
 * DiLink 4.0 is unverified. Server-side autoservice checks can still throw SecurityException;
 * use the DiPlus source in that case.
 */
class BydPermissionContext(base: Context) : ContextWrapper(base) {

    override fun checkCallingOrSelfPermission(permission: String): Int =
        if (isByd(permission)) PackageManager.PERMISSION_GRANTED else super.checkCallingOrSelfPermission(permission)

    override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
        if (isByd(permission)) PackageManager.PERMISSION_GRANTED else super.checkPermission(permission, pid, uid)

    override fun checkSelfPermission(permission: String): Int =
        if (isByd(permission)) PackageManager.PERMISSION_GRANTED else super.checkSelfPermission(permission)

    override fun enforceCallingOrSelfPermission(permission: String, message: String?) {
        if (!isByd(permission)) super.enforceCallingOrSelfPermission(permission, message)
    }

    override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) {
        if (!isByd(permission)) super.enforcePermission(permission, pid, uid, message)
    }

    private fun isByd(permission: String?) = permission?.startsWith(BYD_PERMISSION_PREFIX) == true

    companion object {
        private const val BYD_PERMISSION_PREFIX = "android.permission.BYDAUTO_"
    }
}
