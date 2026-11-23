package com.everyonesniche.titantools.shizuku

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet

/**
 * A small Shizuku client pinned to Shizuku's v13 binder protocol, from Scale Toggle 1.2.0 (no Shizuku library). Shizuku hands
 * its binder to [ShizukuProvider] whenever it starts or this app's process starts; everything else goes through that binder.
 *
 * Transactions (IShizukuService, wire code = AIDL ordinal + 1): 1 transact, 8 newProcess, 15 requestPermission,
 * 16 checkSelfPermission, 17 shouldShowRequestPermissionRationale, 18 attachApplication.
 */
object Shizuku {
    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val SERVICE = "moe.shizuku.server.IShizukuService"
    private const val APPLICATION = "moe.shizuku.server.IShizukuApplication"
    private const val REQUEST = 500600
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile private var server: IBinder? = null
    @Volatile private var attached = false
    @Volatile private var context: Context? = null

    /** Called on the main thread whenever Shizuku comes, goes, or answers a permission request. */
    fun listen(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    private fun changed() = main.post { listeners.forEach { it() } }

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (error: Exception) {
        false
    }

    /** Shizuku is running and this app holds its binder. */
    fun running(): Boolean = attached && server?.pingBinder() == true

    /** Running, and Titan Tools is allowed in Shizuku. */
    fun allowed(): Boolean = running() && try {
        boolCall(16).also { if (it) remember() }
    } catch (error: Exception) {
        false
    }

    /** Titan Tools was allowed in Shizuku once, so a stopped Shizuku means it needs starting again (after a restart). */
    fun everAllowed(context: Context): Boolean = prefs(context).getBoolean("ever_allowed", false)

    private fun remember() {
        val c = context ?: return
        val p = prefs(c)
        if (!p.getBoolean("ever_allowed", false)) p.edit().putBoolean("ever_allowed", true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("shizuku", Context.MODE_PRIVATE)

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    private val application = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(APPLICATION)
                return true
            }
            if (code == 2) { // bindApplication
                data.enforceInterface(APPLICATION)
                val bundle = if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data) else null
                if (bundle != null) {
                    attached = bundle.getInt("shizuku:attach-reply-version", -1) >= 13
                    Log.i(TAG, if (attached) "Shizuku attached" else "Shizuku is older than version 13")
                    changed()
                }
                return true
            }
            if (code == 3) { // dispatchRequestPermissionResult
                data.enforceInterface(APPLICATION)
                val request = data.readInt()
                val bundle = if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data) else null
                if (request == REQUEST && bundle?.getBoolean("shizuku:request-permission-reply-allowed", false) == true) remember()
                changed()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    @Synchronized
    fun receive(binder: IBinder, packageName: String) {
        if (server?.pingBinder() == true && attached && server === binder) return
        server = binder
        attached = false
        Log.i(TAG, "Shizuku binder received")
        try {
            binder.linkToDeath({
                attached = false
                server = null
                changed()
            }, 0)
            val args = Bundle().apply {
                putString("shizuku:attach-package-name", packageName)
                putInt("shizuku:attach-api-version", 13)
            }
            call(18) {
                it.writeStrongBinder(application)
                it.writeInt(1)
                args.writeToParcel(it, 0)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Couldn't attach to Shizuku", error)
            changed()
        }
    }

    /** Asks Shizuku to show its Allow dialog. */
    fun requestPermission() {
        call(15) { it.writeInt(REQUEST) }
    }

    /** True when the person chose "Deny" with "don't ask again": only Shizuku's own app can allow it now. */
    fun deniedForGood(): Boolean = try { boolCall(17) } catch (error: Exception) { false }

    private fun boolCall(code: Int): Boolean = call(code) {}

    /** Makes call [code] and returns its boolean answer (false for calls that answer nothing). */
    private fun call(code: Int, write: (Parcel) -> Unit): Boolean {
        val binder = require()
        val input = Parcel.obtain()
        val output = Parcel.obtain()
        try {
            input.writeInterfaceToken(SERVICE)
            write(input)
            if (!binder.transact(code, input, output, 0)) throw RemoteException("Shizuku call $code isn't supported")
            output.readException()
            return output.dataAvail() >= 4 && output.readInt() != 0
        } finally {
            output.recycle()
            input.recycle()
        }
    }

    private fun require(): IBinder {
        val binder = server
        if (!attached || binder == null || !binder.pingBinder()) throw RemoteException("Shizuku isn't running")
        return binder
    }

    /** IShizukuService.newProcess: a fixed command run as Shizuku's user (shell). Returns an IRemoteProcess binder. */
    fun newProcess(command: Array<String>): IBinder {
        val binder = require()
        val input = Parcel.obtain()
        val output = Parcel.obtain()
        try {
            input.writeInterfaceToken(SERVICE)
            input.writeStringArray(command)
            input.writeStringArray(null)
            input.writeString(null)
            if (!binder.transact(8, input, output, 0)) throw RemoteException("Shizuku can't run commands")
            output.readException()
            return output.readStrongBinder() ?: throw RemoteException("Shizuku returned no process")
        } finally {
            output.recycle()
            input.recycle()
        }
    }

    /**
     * IShizukuService.transact: runs a binder call on [target] as Shizuku's user. The same wire format as the Shizuku library's
     * ShizukuBinderWrapper: interface token, the target binder, the code, the flags, then the original call's data.
     */
    fun transactRemote(target: IBinder, code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        val binder = require()
        val wrapped = Parcel.obtain()
        try {
            wrapped.writeInterfaceToken(SERVICE)
            wrapped.writeStrongBinder(target)
            wrapped.writeInt(code)
            wrapped.writeInt(flags)
            wrapped.appendFrom(data, 0, data.dataSize())
            return binder.transact(1, wrapped, reply, flags)
        } finally {
            wrapped.recycle()
        }
    }

    private const val TAG = "TitanTools"
}
