package cn.com.sgcc.gdt.opc.lib.da;

import cn.com.sgcc.gdt.opc.core.dcom.da.bean.OpcServerStatus;
import cn.com.sgcc.gdt.opc.core.dcom.da.impl.OPCServer;
import lombok.extern.slf4j.Slf4j;

/**
 * A server state operation which can be interruped
 *
 * @author Jens Reimann
 */
@Slf4j
public class ServerStateOperation implements Runnable {

    public OpcServerStatus _serverStatus = null;

    public OPCServer _server;

    public Throwable _error;

    public Object _lock = new Object();

    public boolean _running = false;

    public ServerStateOperation(final OPCServer server) {
        super();
        this._server = server;
    }

    /**
     * Perform the operation.
     * <p>
     * This method will block until either the serve state has been aquired or the
     * timeout triggers cancels the call.
     * </p>
     */
    @Override
    public void run() {
        synchronized (this._lock) {
            this._running = true;
        }
        try {
            this._serverStatus = this._server.getStatus();
            synchronized (this._lock) {
                this._running = false;
                this._lock.notify();
            }
        } catch (Throwable e) {
            log.info("无法获取服务状态", e);
            this._error = e;
            this._running = false;
            synchronized (this._lock) {
                this._lock.notify();
            }
        }

    }

    /**
     * Get the server state with a timeout.
     *
     * @param timeout the timeout in ms
     * @return the server state or <code>null</code> if the server is not set.
     * @throws Throwable any error that occurred
     */
    public OpcServerStatus getServerState(final int timeout) throws Throwable {
        if (this._server == null) {
            log.debug("服务器未连接，跳过操作...");
            return null;
        }

        Thread t = new Thread(this, "客户端任务-读取服务器状态");

        synchronized (this._lock) {
            t.start();
            this._lock.wait(timeout);
            if (this._running) {
                log.warn("State operation still running. Interrupting...");
                t.interrupt();
                throw new InterruptedException("Interrupted getting server state");
            }
        }
        if (this._error != null) {
            log.warn("An error occurred while getting server state", this._error);
            throw this._error;
        }

        return this._serverStatus;
    }

}
