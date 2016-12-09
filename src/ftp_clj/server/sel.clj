(ns ftp-clj.server.sel
  (:import (java.net InetSocketAddress)
           (java.nio.channels ServerSocketChannel Selector SelectionKey)
           (java.nio ByteBuffer)
           (java.nio.charset Charset)))
(use '[ftp-clj.server.sm :as sm])

(defn setup-server-sock [port]
  (let [server-socket-channel (ServerSocketChannel/open)
        _ (.configureBlocking server-socket-channel false)
        server-socket (.socket server-socket-channel)
        inet-socket-address (InetSocketAddress. port)]
    (.bind server-socket inet-socket-address)
    server-socket-channel))


(defn selected-is [state selected-key]
  (and
    (not (= (bit-and (.readyOps selected-key) (get state 1)) 0))
    (= (.attachment selected-key) (get state 0))))

(defn acc-ctrl-srv-sock [server-socket selector]
  (let [channel (-> server-socket (.accept) (.getChannel))]
    (doto channel
      (.configureBlocking false)
      (.register selector SelectionKey/OP_READ "control-server-socket"))
    (ftp-clj.server.commands/send-str channel ftp-clj.server.code/server-hello)))

(defn acc-data-srv-sock [state env server-socket selector]
  (let [channel (-> server-socket (.accept) (.getChannel))]
    (doto channel
      (.configureBlocking true))
    [ftp-clj.server.commands/ftp-state-passive-connected
     (merge env {:data-client-channel channel})]))

(defn read-ctrl-srv-sock [state env channel selector]
  (let [buffer (ByteBuffer/allocate 65536)]
    (.read channel buffer)
    (let [line (new String (.array buffer) (Charset/forName "UTF-8"))]
      (sm/read-command state env line channel selector))))

(defn do-state [state env key selector]
  (condp selected-is key
    ["control-server-socket" SelectionKey/OP_ACCEPT]
    (do
      (acc-ctrl-srv-sock (.socket (.channel key)) selector)
      [state env])

    ["control-server-socket" SelectionKey/OP_READ]
    (let [[new-state new-env] (read-ctrl-srv-sock state env (.channel key) selector)]
      [new-state new-env])

    ["data-server-socket" SelectionKey/OP_ACCEPT]
    (let [[new-state new-env] (acc-data-srv-sock state env (.socket (.channel key)) selector)]
      [new-state new-env])))

(defn -main [&]
  (let [selector (Selector/open)]
    (.register (setup-server-sock 2001) selector SelectionKey/OP_ACCEPT "control-server-socket")
    (println "FTP Server is up at 2001.")
    (loop [state ftp-clj.server.commands/ftp-state-init
           env {:ftp-root-path  "/home/alexwang/pl/ftp"
                :cwd            "/"
                :username       "anonymous"
                :password       ""
                :pasv-mode-port 25678}]
      (if (> (.select selector) 0)
        (let [selected-keys (.selectedKeys selector)
              key-seq (iterator-seq (.iterator selected-keys))
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
        (recur state env)))))

