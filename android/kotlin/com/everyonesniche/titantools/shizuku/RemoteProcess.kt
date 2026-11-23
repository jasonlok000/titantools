package com.everyonesniche.titantools.shizuku

import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A command run through Shizuku, from Scale Toggle's ShizukuProcess: IRemoteProcess calls 2 stdout, 3 stderr, 5 exitValue,
 * 6 destroy, 8 waitForTimeout. Only [Shell]'s fixed commands come here.
 */
internal class RemoteProcess private constructor(private val binder: IBinder) {
    class Result(val exit: Int, val out: String, val err: String)

    fun destroy() {
        val input = Parcel.obtain()
        val output = Parcel.obtain()
        try {
            input.writeInterfaceToken(DESCRIPTOR)
            if (binder.transact(6, input, output, 0)) output.readException()
        } catch (ignored: Exception) {
        } finally {
            output.recycle()
            input.recycle()
        }
    }

    fun alive(): Boolean = binder.pingBinder() && try { !waitFor(1) } catch (error: Exception) { false }

    private fun stream(code: Int): InputStream {
        val input = Parcel.obtain()
        val output = Parcel.obtain()
        try {
            input.writeInterfaceToken(DESCRIPTOR)
            if (!binder.transact(code, input, output, 0)) throw RemoteException("No command output")
            output.readException()
            if (output.readInt() == 0) throw IOException("No command output")
            return ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.CREATOR.createFromParcel(output))
        } finally {
            output.recycle()
            input.recycle()
        }
    }

    private fun waitFor(millis: Long): Boolean {
        val input = Parcel.obtain()
        val output = Parcel.obtain()
        try {
            input.writeInterfaceToken(DESCRIPTOR)
            input.writeLong(millis)
            input.writeString("MILLISECONDS")
            if (!binder.transact(8, input, output, 0)) throw RemoteException("No command timeout")
            output.readException()
            return output.readInt() != 0
        } finally {
            output.recycle()
            input.recycle()
        }
    }

    private fun exitValue(): Int {
        val input = Parcel.obtain()
        val output = Parcel.obtain()
        try {
            input.writeInterfaceToken(DESCRIPTOR)
            if (!binder.transact(5, input, output, 0)) throw RemoteException("No command exit")
            output.readException()
            return output.readInt()
        } finally {
            output.recycle()
            input.recycle()
        }
    }

    companion object {
        private const val DESCRIPTOR = "moe.shizuku.server.IRemoteProcess"
        private val io = Executors.newCachedThreadPool { r -> Thread(r, "titantools-io").apply { isDaemon = true } }

        /** Starts [command] and leaves it running; the caller must [destroy] it. Its output is drained and dropped. */
        fun start(command: Array<String>): RemoteProcess {
            val process = RemoteProcess(Shizuku.newProcess(command))
            for (code in intArrayOf(2, 3)) {
                val stream = try { process.stream(code) } catch (error: Exception) { null } ?: continue
                io.execute { try { stream.use { s -> val b = ByteArray(512); while (s.read(b) != -1) Unit } } catch (ignored: Exception) {} }
            }
            return process
        }

        /** Runs [command] to the end, at most [timeoutMillis]. Throws on a non-zero exit. */
        fun run(timeoutMillis: Long, command: Array<String>): Result {
            val process = RemoteProcess(Shizuku.newProcess(command))
            try {
                val out = io.submit<String> { read(process.stream(2)) }
                val err = io.submit<String> { read(process.stream(3)) }
                if (!process.waitFor(timeoutMillis)) throw TimeoutException("${command[0]} took too long")
                val exit = process.exitValue()
                val result = Result(exit, out.get(1500, TimeUnit.MILLISECONDS), err.get(1500, TimeUnit.MILLISECONDS))
                if (exit != 0) throw IOException("${command[0]} exited $exit: ${(result.err + result.out).trim().take(200)}")
                return result
            } finally {
                process.destroy()
            }
        }

        private fun read(stream: InputStream): String = stream.use {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val n = it.read(buffer)
                if (n == -1) break
                if (out.size() + n > 65536) throw IOException("Too much command output")
                out.write(buffer, 0, n)
            }
            out.toString(Charsets.UTF_8.name())
        }
    }
}
