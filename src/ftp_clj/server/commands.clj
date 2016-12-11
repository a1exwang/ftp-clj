(ns ftp-clj.server.commands
  (:gen-class)
  (:import (java.nio ByteBuffer)
           (java.nio.charset Charset)
           (java.net InetSocketAddress)
           (java.nio.channels ServerSocketChannel SelectionKey)))

(import
  '(java.io BufferedReader InputStreamReader DataOutputStream)
  '(java.net ServerSocket SocketException))
(use '[ftp-clj.server.code])

(require '[clojure.java.io :as io])
(require '[clojure.string :as str])

(defn send-str [channel result]
  (.write channel
          (ByteBuffer/wrap (.getBytes (str result "\n") (Charset/forName "UTF-8")))))

(defn send-raw [channel result]
  (.write channel
          (ByteBuffer/wrap (.getBytes result (Charset/forName "UTF-8")))))

(def ftp-state-init "init")
(def ftp-state-need-pass "need-pass")
(def ftp-state-logged-in "logged-in")
(def ftp-state-passive "passive")
(def ftp-state-passive-connected "passive-connected")
(def ftp-state-passive-wait-for-connection "passive-wait-for-connection")
(def ftp-state-active "active")

(defn help [state env channel words]
  (send-str channel help-message)
  [state env])
(defn syst [state env channel words]
  (send-str channel system-type)
  [state env])
(defn feat [state env channel words]
  (send-str channel server-features)
  [state env])
(defn transfer-type [state env channel words]
  (send-str channel "200 Switching to binary mode.")
  [state env])
(defn mdtm [state env channel words]
  (send-str channel "213 20161204191607")
  [state env])

(defn user [state env channel words]
  (send-str channel user-name-ok-need-pass)
  [ftp-state-need-pass
   (merge env {:user (get words 1)})])

(defn pass [state env channel words]
  (send-str channel login-successful)
  [ftp-state-logged-in
   (merge env {:password (get words 1), :cwd "/"})])

(defn pwd [state env channel words]
  (if (some (partial = state) [ftp-state-logged-in ftp-state-passive ftp-state-active])
    (do
      (send-str channel (str pathname-created "\"" (get env :cwd) "\" is current directory"))
      [state env])
    [state env]))

(defn cwd [state env channel words]
  (if (= state ftp-state-logged-in)
    (let [new-cwd
          (let [cwd (get env :cwd)
                new-path (get words 1)]
            (if (str/starts-with? new-path "/")
              new-path 
              (.getPath (io/file cwd new-path))))]
      (if (.isDirectory (io/file (get env :ftp-root-path) (subs new-cwd 1)))

        (do
          (send-str channel (str pathname-created "\"" new-cwd "\" is current directory."))
          [state (merge env {:cwd new-cwd})])
        (do
          (send-str channel path-does-not-exist)
          [state env])))
    [state env]))

(defn setup-data-server-sock []
  (let [server-socket-channel (ServerSocketChannel/open)
        _ (.configureBlocking server-socket-channel false)
        server-socket (.socket server-socket-channel)
        inet-socket-address (InetSocketAddress. 0)]
    (.bind server-socket inet-socket-address)

    (let [port (.getLocalPort server-socket)
          ip [0 0 0 0]
          end-point-str
          (str/join "," (concat ip [(quot port 0x100) (bit-and port 0xff)]))]
    [server-socket-channel port end-point-str])))

(defn pasv [state env channel selector words]
  (if (not (= state ftp-state-logged-in))
    (do
      (println "PASV: state != logged in but \"" state "\"")
      (send-str channel (str wrong-state "Cannot use command PASV"))
      [state env])
    (let [[server-channel port end-point-str] (setup-data-server-sock)]
      (.register server-channel selector SelectionKey/OP_ACCEPT "data-server-socket")
      (println "Data socket is up at port " port)
      (send-str channel (str enter-passive-mode "(" end-point-str ")"))
      [ftp-state-passive
       (merge env {:data-server-channel server-channel})])))

(defn send-passive [env data]
  (let [channel (get env :data-client-channel)]
    (send-raw channel data)
    (.close channel)))

(defn create-list-dir-info [parent-dir file-name]
  (let [f (io/file parent-dir file-name)
        privs (if (.isDirectory f) "drwxrwxrwx" "-rwxrwxrwx")
        user-n 0
        group-n 0
        inode-n 0
        date-str "Dec 9 2016"
        size-n (.length f)]
    (str/join " " [privs inode-n user-n group-n size-n date-str file-name "\n"])))

(defn list-dir [state env channel words]
  (if (not (= state ftp-state-passive-connected))
    (do
      (send-str channel (str wrong-state "Cannot use command LIST"))
      [state env])
    (do
      (let [parent-dir (io/file (get env :ftp-root-path) (subs (get env :cwd) 1))
            result (str/join "\n" (map (partial create-list-dir-info parent-dir) (.list parent-dir)))]
        (send-passive env result)
        (send-str channel (str transfer-complete))
        [ftp-state-logged-in
         (merge env {:data-client-channel nil})]))))

(def buffer-size 1500)

(defn send-file [env file]
  (let [channel (get env :data-client-channel)
        is (io/input-stream file)]
    (loop []
      (let [buf (byte-array buffer-size)
            read-count (.read is buf)]
        (if (> read-count 0)
          (do
            (send-raw channel (String. buf 0 read-count (Charset/forName "UTF-8")))
            (if (= read-count buffer-size)
              (recur))))))
    (.close is)
    (.close channel)))

(defn retrieve-file [state env channel selector words]
  (if (= state ftp-state-passive-connected)
    (do
      (let [f (io/file (get env :ftp-root-path) (subs (get env :cwd) 1) (get words 1))]
        (send-str channel (str transfer-started))
        ;(send-passive env (slurp f))
        (send-file env f)
        (send-str channel (str transfer-complete))
        [ftp-state-logged-in env]))
    [state env]))

(defn syntax-error [env words msg]
  (str wrong-command-or-parameter " " msg))
