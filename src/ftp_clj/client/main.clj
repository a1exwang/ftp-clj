(ns ftp-clj.client.main
  (:import (java.nio.channels Selector SelectionKey ServerSocketChannel SocketChannel)
           (java.net InetSocketAddress Inet4Address)
           (java.nio ByteBuffer)
           (java.nio.charset Charset)))

(def client-state-init "client-init")

(defn read-response [state env line channel selector]
  (println line)
  [state env])

(defn setup-client-channel [host port]
  (let [socket-channel (SocketChannel/open)
        inet-socket-address (new InetSocketAddress host port)
        _ (.configureBlocking socket-channel false)
        _ (.connect socket-channel inet-socket-address)]
    socket-channel))

(defn conn-ctrl-client-sock [channel selector]
  (while (.isConnectionPending channel)
    (.finishConnect channel))
  (println "Connected"))

(defn read-ctrl-client-sock [state env channel selector]
  (let [buffer (ByteBuffer/allocate 65536)]
    (.read channel buffer)
    (let [line (new String (.array buffer) (Charset/forName "UTF-8"))]
      (read-response state env line channel selector))))
(defn read-data-client-sock [state env channel selector]
  (let [buffer (ByteBuffer/allocate 65536)]
    (.read channel buffer)
    (let [line (new String (.array buffer) (Charset/forName "UTF-8"))]
      (read-response state env line channel selector))))
(defn conn-data-client-sock [state env channel selector]
  (let [buffer (ByteBuffer/allocate 65536)]
    (.read channel buffer)
    (let [line (new String (.array buffer) (Charset/forName "UTF-8"))]
      (read-response state env line channel selector))))

(defn selected-is [state selected-key]
  (and
    (not (= (bit-and (.readyOps selected-key) (get state 1)) 0))
    (= (.attachment selected-key) (get state 0))))

(defn do-state [state env key selector]
  (condp selected-is key
    ["control-client-socket" SelectionKey/OP_CONNECT]
    (do
      (conn-ctrl-client-sock (.channel key) selector)
      (println "Connected")
      [state env])

    ["control-client-socket" SelectionKey/OP_READ]
    (let [[new-state new-env] (read-ctrl-client-sock state env (.channel key) selector)]
      [new-state new-env])

    ["data-client-socket" SelectionKey/OP_READ]
    (let [[new-state new-env] (read-data-client-sock state env (.channel key) selector)]
      [new-state new-env])

    ["data-client-socket" SelectionKey/OP_CONNECT]
    (let [[new-state new-env] (conn-data-client-sock state env (.socket (.channel key)) selector)]
      [new-state new-env])))


(defn -main [& args]
  (println args)
  (if (not (= (count args) 2))
    (println "Wrong arguments")
    (let [[host-str port-str] args
          port (new Integer port-str)]
      (let [selector (Selector/open)
            client-channel (setup-client-channel host-str port)]
        (.register client-channel selector SelectionKey/OP_CONNECT "control-client-socket")
        (println "Connecting to FTP Server at " host-str ":" port ".")
        (loop [state client-state-init
               env {:ftp-root-path  "/home/alexwang/pl/ftp"
                    :cwd            "/"
                    :username       "anonymous"
                    :password       ""}]
          (if (> (.select selector) 0)
            (let [selected-keys (.selectedKeys selector)
                  key-seq (iterator-seq (.iterator selected-keys))
                  _ (println (type key-seq))
                  [state-out env-out]
                  (loop [[key & other-keys] key-seq
                         state-in state
                         env-in env]
                    (if key
                      (let [[state1 env1] (do-state state-in env-in key selector)]
                        (println (str "State:\t" state1 "\nEnv:\t" env1 "\n"))
                        (recur other-keys state1 env1))
                      [state-in env-in]))]
              (.clear selected-keys)
              (recur state-out env-out))
            (recur state env)))))))
