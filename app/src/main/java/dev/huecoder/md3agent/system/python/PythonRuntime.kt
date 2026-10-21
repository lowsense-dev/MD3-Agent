package dev.huecoder.md3agent.system.python

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

data class PythonResult(val output: String, val failed: Boolean, val timedOut: Boolean)

/**
 * Client for [PythonService]. Python runs in its own process because a script stuck in native code cannot be
 * interrupted from inside and a native crash would take the app down; here such a script is simply killed.
 */
class PythonRuntime(private val context: Context) {
    private val calls = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connecting = Mutex()
    private var bound: Bound? = null

    val version: String get() = PYTHON_VERSION

    suspend fun run(code: String, workdir: File, timeoutSeconds: Long, bridge: ScriptBridge? = null): PythonResult {
        val service = connect()
        val call = calls.async { service.run(code, workdir.absolutePath, timeoutSeconds, bridge?.let(::BridgeBinder)) }
        return try {
            // The runner stops a script at its timeout by itself; only a script wedged in native code outlives the grace.
            val raw = withTimeoutOrNull((timeoutSeconds + WEDGED_GRACE_S) * MS_PER_SECOND) { call.await() }
            if (raw == null) {
                restart()
                PythonResult(WEDGED_TEXT.format(timeoutSeconds), failed = true, timedOut = true)
            } else {
                parse(raw)
            }
        } catch (e: CancellationException) {
            // Stop pressed or the turn ended: kill the process so the script really stops instead of running on.
            withContext(NonCancellable) { restart() }
            throw e
        } catch (e: RemoteException) {
            Log.w(TAG, "Python process died", e)
            drop()
            PythonResult(CRASHED_TEXT, failed = true, timedOut = false)
        } finally {
            bridge?.abort()
        }
    }

    private suspend fun connect(): IPythonService = connecting.withLock {
        bound?.takeIf { it.service.asBinder().isBinderAlive }?.let { return it.service }
        bound?.let(::unbind)
        val ready = CompletableDeferred<IPythonService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                ready.complete(IPythonService.Stub.asInterface(binder))
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        check(context.bindService(Intent(context, PythonService::class.java), connection, Context.BIND_AUTO_CREATE)) { "Python service could not be started" }
        val service = try {
            withTimeout(CONNECT_TIMEOUT_MS) { ready.await() }
        } catch (e: Exception) {
            runCatching { context.unbindService(connection) }
            throw e
        }
        Bound(service, connection).also { bound = it }.service
    }

    private suspend fun restart() = connecting.withLock {
        val current = bound ?: return@withLock
        runCatching { Process.killProcess(current.service.pid()) }
        unbind(current)
    }

    private suspend fun drop() = connecting.withLock { bound?.let(::unbind) }

    private fun unbind(current: Bound) {
        runCatching { context.unbindService(current.connection) }
        if (bound === current) bound = null
    }

    private fun parse(raw: String): PythonResult {
        val json = JSONObject(raw)
        return PythonResult(json.optString(PythonService.OUTPUT), json.optBoolean(PythonService.FAILED), json.optBoolean(PythonService.TIMED_OUT))
    }

    private class Bound(val service: IPythonService, val connection: ServiceConnection)

    private class BridgeBinder(private val bridge: ScriptBridge) : IScriptBridge.Stub() {
        override fun begin(operation: String, payload: String): Int = bridge.begin(operation, payload)

        override fun poll(handle: Int): String? = bridge.poll(handle)
    }

    private companion object {
        const val TAG = "PythonRuntime"
        const val PYTHON_VERSION = "3.13"
        const val WEDGED_GRACE_S = 15L
        const val MS_PER_SECOND = 1_000L
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val WEDGED_TEXT = "The script did not stop after %d s and was killed together with the Python process. Variables and open files are gone; the next run starts fresh."
        const val CRASHED_TEXT = "The Python process crashed while running the script (likely a native library). The next run starts a fresh process."
    }
}
