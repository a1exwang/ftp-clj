(ns ftp-clj.server.main
  (:gen-class))

(import
  '(java.io BufferedReader InputStreamReader DataOutputStream)
  '(java.net ServerSocket SocketException))

(use '[clojure.string :only [split lower-case trim]])
(use '[ftp-clj.server.code])
(use '[ftp-clj.server.commands])

(defn create-env []
  {:state ftp-state-init
   :ftp-root-path "/home/alexwang/pl/ftp"})

(defn server-pi [env command-line]
  (let [words (split (trim command-line) #"\s+")]
    (if (= (count words) 0)
      ""
      (try
        (let [cmd (get words 0)]
          (case (lower-case cmd)
            "help"  (help env words)
            "user"  (user env words)
            "pass"  (pass env words)
            "pwd"   (pwd env words)
            "cwd"   (cwd env words)
            "pasv"  (pasv env words)
            "list"  (list-dir env words)))
        (catch Exception e 
          [env (syntax-error env words (str e))])))))

(defn new-client-connect [client-socket]
  (try
    (let [is (new BufferedReader (new InputStreamReader (.getInputStream client-socket)))
          os (new DataOutputStream (.getOutputStream client-socket))]
      ; Initialize env in here!
      (loop [env (create-env)]
        (let [line (.readLine is)]
          (println (str "-->: " line))
          (let [[new-env reply]
                (server-pi env (.toLowerCase line))]
            (.writeBytes os (str reply "\n"))
            (println (str "new-env:\n" new-env))
            (recur new-env)))))
    (catch SocketException e
      (do
        (.close client-socket)
        (println "Client socket unexpected closed!")))))

(defn -main
  "This is the entry point of FTP server."
  [& args]
  (let [server-socket (new ServerSocket 2001)]
    (do
      (println "FTP-clj is up at port 2001.")
      (new-client-connect (.accept server-socket)))))

