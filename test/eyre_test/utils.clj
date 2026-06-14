(ns eyre-test.utils
  (:import [java.net Socket InetSocketAddress]
           [java.io IOException]))

(defn port-open?
  "Returns true if a TCP connection can be established to host:port."
  [host port timeout-ms]
  (try
    (let [sock (Socket.)]
      (.connect sock (InetSocketAddress. host port) timeout-ms)
      (.close sock)
      true)
    (catch IOException _ false)
    (catch Exception _ false)))

(defn wait-for-port
  "Blocks until host:port accepts a TCP connection, or the deadline is reached.

   Options (map, all optional):
     :timeout-ms   Total time to wait in ms        (default: 30000)
     :interval-ms  Time between attempts in ms      (default: 100)
     :connect-ms   Per-attempt connection timeout   (default: 1000)
     :on-retry     0-arity fn called on each miss   (default: nil)

   Returns :ok on success, :timeout on failure."
  ([host port] (wait-for-port host port {}))
  ([host port {:keys [timeout-ms interval-ms connect-ms on-retry]
               :or   {timeout-ms  30000
                      interval-ms 100
                      connect-ms  1000}}]
   (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
     (loop []
       (cond
         (port-open? host port connect-ms)
         :ok

         (>= (System/currentTimeMillis) deadline)
         :timeout

         :else
         (do
           (when on-retry (on-retry))
           (Thread/sleep interval-ms)
           (recur)))))))

(defn wait-for-port!
  "Like wait-for-port but throws ex-info on timeout instead of returning :timeout."
  ([host port] (wait-for-port! host port {}))
  ([host port opts]
   (let [result (wait-for-port host port opts)]
     (when (= result :timeout)
       (throw (ex-info (str "Timed out waiting for " host ":" port)
                       {:host host :port port :opts opts})))
     result)))
