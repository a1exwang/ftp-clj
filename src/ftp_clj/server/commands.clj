(ns ftp-clj.server.commands (:gen-class))

(import
  '(java.io BufferedReader InputStreamReader DataOutputStream)
  '(java.net ServerSocket SocketException))
(use '[ftp-clj.server.code])

(require '[clojure.java.io :as io])
(require '[clojure.string :as str])

(def ftp-state-init "init")
(def ftp-state-need-pass "need-pass")
(def ftp-state-logged-in "logged-in")
(def ftp-state-passive "passive")
(def ftp-state-active "active")

(defn help [env words]
  (if (not (= (count words) 1))
    (throw (new Exception "FTP Syntax error")))
  [env help-message])


(defn user [env words]
  (if (not (= (count words) 2))
    (throw (new Exception "FTP Syntax error")))
  [(merge env 
          {:user (get words 1)
           :state ftp-state-need-pass})
   user-name-ok-need-pass])

(defn pass [env words]
  (if (not (= (count words) 2))
    (throw (new Exception "FTP Syntax error")))
  [(merge env
          {:state ftp-state-logged-in
           :password (get words 1)
           :cwd "/"})
   login-successful])

(defn pwd [env words]
  (if (not (= (count words) 1))
    (throw (new Exception "FTP Syntax error")))
  (if (some (partial = (get env :state)) [ftp-state-logged-in ftp-state-passive ftp-state-active])
    [env (str pathname-created "\"" (get env :cwd) "\" is current directory")]
    [env wrong-state]))

(defn cwd [env words]
  (if (not (= (count words) 2))
    (throw (new Exception "FTP Syntax error")))
  (if (= (get env :state) ftp-state-logged-in)
    (let [new-cwd
          (let [cwd (get env :cwd)
                new-path (get words 1)]
            (if (str/starts-with? new-path "/")
              new-path 
              (.getPath (io/file cwd new-path))))]
      (if (.isDirectory (io/file (get env :ftp-root-path) (subs new-cwd 1)))
        [(merge env {:cwd new-cwd})
         (str pathname-created "\"" new-cwd "\" is current directory.")]
        [env path-does-not-exist]))
    [env wrong-state]))

(defn create-passive-mode-end-point []
  (let [sock (new ServerSocket 0)
        ipstr (.toString (.getInetAddress sock))
        ip   [0 0 0 0]
        port (.getLocalPort sock)]
    (println (str "Passive socket is up at port " port))
    [sock
     (str/join "," (concat ip [(quot port 0x100) (bit-and port 0xff)]))]))

(defn wait-for-data-client [env]
  (merge env {:data-client-sock (.accept (get env :data-sock))}))

(defn pasv [env words]
  (if (not (= (count words) 1))
    (throw (new Exception "FTP Syntax error")))
  (let [[sock end-point-str] (create-passive-mode-end-point)]
    [(merge env {:state ftp-state-passive :data-sock sock})
     (str enter-passive-mode "(" end-point-str ")")
     wait-for-data-client]))

(defn send-passive [env data]
  (let [client-socket (get env :data-client-sock)
        server-socket (get env :data-sock)
        os (new DataOutputStream (.getOutputStream client-socket))]
    (.writeBytes os data)
    (.close os)
    (.close client-socket)
    (.close server-socket)))

(defn list-dir [env words]
  (if (not (= (count words) 1))
    (throw (new Exception "FTP Syntax error")))
  (if (= (get env :state ftp-state-passive))
    (do
      (let [result (str/join " " (.list (io/file (get env :ftp-root-path) (subs (get env :cwd) 1))))]
        (send-passive env result)
        [env (str pathname-created)]))
    [env (str wrong-state)]))

(defn retrieve-file [env words]
  (if (not (= (count words) 2))
    (throw (new Exception "FTP Syntax error")))
  (if (= (get env :state ftp-state-passive))
    (do
      (let [f (io/file (get env :ftp-root-path) (subs (get env :cwd) 1) (get words 1))]
        (send-passive env (slurp f))
        [env (str transfer-complete)]))
    [env (str wrong-state)]))

(defn syntax-error [env words msg]
  (str wrong-command-or-parameter " " msg))
