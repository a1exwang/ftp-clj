(ns ftp-clj.server.sm
  (:import (java.nio ByteBuffer)
           (java.nio.charset Charset)))

(use '[clojure.string :only [split lower-case trim trim-newline]])
(use '[ftp-clj.server.code])
(use '[ftp-clj.server.commands])

(defn read-command [state env command-line channel selector]
  (let [words (split (trim (trim-newline command-line)) #"\s+")]
    (if (> (count words) 0)
      (try
        (let [cmd (lower-case (get words 0))]
          (if (and (= state ftp-state-init) (not (= "user" cmd)))
            (do
              (send-str channel login-with-user-and-pass)
              [state env])
            (do
              (println "Cmd:\t" cmd)
              (case (lower-case cmd)
                "help"  (help state env channel words)
                "syst"  (syst state env channel words)
                "feat"  (feat state env channel words)
                "type"  (transfer-type state env channel words)
                "mdtm"  (mdtm state env channel words)
                "user"  (user state env channel words)
                "pass"  (pass state env channel words)
                "pwd"   (pwd state env channel words)
                "cwd"   (cwd state env channel words)
                "pasv"  (pasv state env channel selector words)
                "retr"  (retrieve-file state env channel selector words)
                "list"  (list-dir state env channel words)
                (do
                  (send-str channel (syntax-error env words (str "Unknown command " cmd)))
                  [state env])))))
          (catch Exception e
            (do
              (.printStackTrace e)
              (send-str channel (syntax-error env words (str e)))
              [state env]))))))
