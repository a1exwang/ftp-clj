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

(defn list-dir [env words]
  (if (not (= (count words) 1))
    (throw (new Exception "FTP Syntax error")))
  [env 
   (str pathname-created 
        (str/join " " (io/file (get env :ftp-root-path) (get env :cwd))))])

(defn create-passive-mode-end-point []
  ; (let [sock (new ServerSocket 0)
  ;       port (.getPort sock)]
  ;   (println (str "Passive socket is up at port " port))
  ;   (new-client-connect (.accept server-socket)))
  [127, 0, 0, 1, 100, 78])

(defn pasv [env words]
  (if (not (= (count words) 1))
    (throw (new Exception "FTP Syntax error")))
  [env (str enter-passive-mode (create-passive-mode-end-point))])


(defn syntax-error [env words msg]
  (str wrong-command-or-parameter " " msg))
