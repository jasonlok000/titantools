package com.everyonesniche.titantools.shizuku

import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import java.io.FileDescriptor

/** A system service's binder whose calls run as Shizuku's user, like the Shizuku library's ShizukuBinderWrapper. */
internal class ShizukuBinder(private val original: IBinder) : IBinder {
    override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
        Shizuku.transactRemote(original, code, data, reply, flags)

    override fun getInterfaceDescriptor(): String? = original.interfaceDescriptor
    override fun pingBinder(): Boolean = original.pingBinder()
    override fun isBinderAlive(): Boolean = original.isBinderAlive
    override fun queryLocalInterface(descriptor: String): IInterface? = null
    override fun dump(fd: FileDescriptor, args: Array<out String>?) = original.dump(fd, args)
    override fun dumpAsync(fd: FileDescriptor, args: Array<out String>?) = original.dumpAsync(fd, args)
    override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) = original.linkToDeath(recipient, flags)
    override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean = original.unlinkToDeath(recipient, flags)
}
