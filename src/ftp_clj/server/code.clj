(ns ftp-clj.server.code
  (:gen-class))

(def transfer-started "150 Opening binary mode for transfering.")

; 2XX
(def help-message (str "214-The following commands are recognized.\n"
                       " USER PASS PASV PORT CD PWD LIST GET\n"
                       "214 Help OK."))
(def system-type (str "215 UNIX Type: L8"))
(def server-hello (str "220 Alex-FTP"))
(def server-features (str "211-Features:\nPASV\n211 End"))
(def login-successful "230 Login successful. ")
(def transfer-complete "226 Transfer complete")
(def enter-passive-mode "227 Passive mode entered. ")
(def pathname-created "257 Pathname created. ")

; 3XX
(def user-name-ok-need-pass "331 Please specify the password. ")

; 5XX Error
(def wrong-command-or-parameter "500 Syntax error.")
(def wrong-state "500 Wrong state.")
(def login-with-user-first "503 Please enter password with PASS")
(def login-with-user-and-pass "530 Please login with USER and PASS")
(def path-does-not-exist "550 Failed to change directory.")
