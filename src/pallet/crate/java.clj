(ns pallet.crate.java
  "Crates for java installation and configuration.

   Sun Java installation on CentOS requires use of Oracle rpm's. Download from
   http://www.oracle.com/technetwork/java/javase/downloads/index.html and get
   the .rpm.bin file onto the node with remote-file.  Then pass the location of
   the rpm.bin file on the node using the :rpm-bin option. The rpm will be
   installed."
  (:require
   [pallet.script.lib :as lib])
  (:use
   [clojure.algo.monads :only [m-map]]
   [pallet.action :only [with-action-options]]
   [pallet.actions
    :only [exec-script exec-checked-script install-deb package package-source
           remote-directory remote-file pipeline-when]]
   [pallet.api :only [server-spec]]
   [pallet.common.context :only [throw-map]]
   [pallet.compute :only [os-hierarchy]]
   [pallet.crate :only [def-plan-fn assoc-settings get-settings defmethod-plan]]
   [pallet.crate-install :only [install]]
   [pallet.crate.environment :only [system-environment]]
   [pallet.monad :only [chain-s]]
   [pallet.stevedore :only [script]]
   [pallet.script :only [defimpl defscript]]
   [pallet.utils :only [apply-map]]
   [pallet.version-dispatch
    :only [defmulti-version-plan defmulti-version
           defmethod-version-plan defmethod-version
           os-map os-map-lookup]]
   [pallet.versions :only [version-string]]))

(def vendor-keywords #{:openjdk :sun :oracle})
(def component-keywords #{:jdk :jre :bin})
(def all-keywords (into #{} (concat vendor-keywords component-keywords)))


;;; ## Script
(defscript java-home [])
(defimpl java-home :default []
  @("dirname" @("dirname" @("readlink" -f @("which" java)))))
(defimpl java-home [#{:aptitude}] []
  @("dirname" @("dirname" @("update-alternatives" --list java))))
(defimpl java-home [#{:darwin :os-x}] []
   @JAVA_HOME)

(defscript jdk-home [])
(defimpl jdk-home :default []
  @("dirname" @("dirname" @("readlink" -f @("which" javac)))))
(defimpl jdk-home [#{:aptitude}] []
  @("dirname" @("dirname" @("update-alternatives" --list javac))))
(defimpl jdk-home [#{:darwin :os-x}] []
   @JAVA_HOME)

(defscript jre-lib-security [])
(defimpl jre-lib-security :default []
  (str @(update-java-alternatives -l "|" cut "-d ' '" -f 3 "|" head -1)
       "/jre/lib/security/"))

;;; Default Java package version
(def java-package-version
  (atom                                 ; allow for open extension
   (os-map
    {{:os :linux} [6]
     {:os :ubuntu :os-version [12]} [7]})))

;;; ## openJDK package names
(defmulti-version openjdk-packages [os os-version version components]
  #'os-hierarchy)

(defmethod-version
    openjdk-packages {:os :rh-base}
    [os os-version version components]
  (map
   (comp
    (partial str "java-1." (version-string version) ".0-openjdk")
    #({:jdk "-devel" :jre ""} % ""))
   components))

(defmethod-version
    openjdk-packages {:os :debian-base}
    [os os-version version components]
  (map
   (comp (partial str "openjdk-" (version-string version) "-") name)
   components))

(defmethod-version
    openjdk-packages {:os :arch-base}
    [os os-version version components]
  [(str "openjdk" (version-string version))])

;;; ## Oracle package names
(defmulti-version oracle-packages [os os-version version components]
  #'os-hierarchy)

(defmethod-version
    oracle-packages {:os :rh-base :version [7]}
    [os os-version version components]
  (map
   (comp
    (partial str "oracle-java" (version-string version) "-")
    #({:jdk "-devel" :jre ""} % ""))
   components))

(defmethod-version
    oracle-packages {:os :rh-base :version [6]}
    [os os-version version components]
  (map
   (comp
    (partial str "sun-java" (version-string version) "-")
    #({:jdk "-devel" :jre ""} % ""))
   components))

(defmethod-version
    oracle-packages {:os :debian-base :version [7]}
    [os os-version version components]
  {:pre [(seq components)]}
  (conj
   (map
    (comp (partial str "oracle-java" (version-string version) "-") name)
    components)
   (str "oracle-java" (version-string version) "-bin")))

(defmethod-version
    oracle-packages {:os :debian-base :version [6]}
    [os os-version version components]
  {:pre [(seq components)]}
  (conj
   (map
    (comp (partial str "sun-java" (version-string version) "-") name)
    components)
   (str "sun-java" (version-string version) "-bin")))

(defmethod-version
    oracle-packages {:os :arch-base :version [7]}
    [os os-version version components]
  [(str "oracle-java" (version-string version))])

(defmethod-version
    oracle-packages {:os :arch-base :version [6]}
    [os os-version version components]
  [(str "sun-java" (version-string version))])

;;; ## Oracle java
;;; Based on supplied settings, decide which install strategy we are using
;;; for oracle java.

(defmulti-version-plan oracle-java-settings [version settings])

(defmethod-version-plan
    oracle-java-settings {:os :rh-base}
    [os os-version version settings]
  (m-result
   (cond
     (:install-strategy settings) settings
     (:rpm settings) (assoc settings :install-strategy ::rpm-bin)
     (:package-source settings) (assoc settings
                                  :install-strategy :package-source
                                  :packages (oracle-packages
                                             os os-version version
                                             (:components settings)))
     :else (throw (Exception. "No install method selected for Oracle JDK")))))

(defmethod-version-plan
    oracle-java-settings {:os :debian-base :version [7]}
    [os os-version version settings]
  [strategy (m-result (:install-strategy settings))]
  (m-result
   (cond
     (or (= strategy :debs) (:debs settings))
     (->
      settings
      (assoc :install-strategy :debs)
      (update-in
       [:packages]
       #(or % (oracle-packages os os-version version (:components settings))))
      (update-in
       [:package-source :aptitude]
       #(or (and % (assoc % :package-path (.getPath (java.net.URL. (:url %)))))
            {:path "pallet-packages"
             :url "file://$(pwd)/pallet-packages"
             :release "./"
             :scopes []}))
      (update-in
       [:package-source]
       #(merge {:name "pallet-packages"} %)))

     (or (= strategy :package-source) (:package-source settings))
     (->
      settings
      (assoc :install-strategy :package-source)
      (update-in
       [:packages]
       #(or % (oracle-packages os os-version version (:components settings)))))

     :else
     (->
      settings
      (assoc :install-strategy :w8-ppa)
      (update-in
       [:packages]
       #(or % ["oracle-java7-installer"]))
      (update-in
       [:package-source :aptitude]
       #(or % {:url "ppa:webupd8team/java"}))
      (update-in
       [:package-source :name]
       #(or % "webupd8team-java-$(lsb_release -c -s)"))))))

(defmethod-version-plan
    oracle-java-settings {:os :debian-base :version [6]}
    [os os-version version settings]
  [strategy (m-result (:install-strategy settings))]
  (m-result
   (cond
     (or (= strategy :debs) (:debs settings))
     (->
      settings
      (assoc :install-strategy :debs)
      (update-in
       [:packages]
       #(or % (oracle-packages os os-version version (:components settings))))
      (update-in
       [:package-source :aptitude]
       #(or (and % (assoc % :package-path (.getPath (java.net.URL. (:url %)))))
            {:path "pallet-packages"
             :url "file://$(pwd)/pallet-packages"
             :release "./"
             :scopes []}))
      (update-in
       [:package-source]
       #(merge {:name "pallet-packages"} %)))
     (or (= strategy :package-source) (:package-source settings))
     (->
      settings
      (assoc :install-strategy :package-source)
      (update-in
       [:packages]
       #(or % (oracle-packages os os-version version (:components settings)))))

     :else (throw
            (Exception. "No install method selected for Oracle java 6")))))


;;; ## OpenJDK java
;;; Based on supplied settings, decide which install strategy we are using
;;; for openjdk java.
(defmulti-version-plan openjdk-java-settings [version settings])

(defmethod-version-plan
    openjdk-java-settings {:os :linux :version [7]}
    [os os-version version settings]
  [settings (m-result
             (cond
               (:install-strategy settings) settings
               :else (assoc settings
                       :install-strategy :packages
                       :packages (openjdk-packages
                                  os os-version version
                                  (:components settings)))))])

(defmethod-version-plan
    openjdk-java-settings {:os :linux :version [6]}
    [os os-version version settings]
  [settings (m-result
             (cond
               (:install-strategy settings) settings
               :else (assoc settings
                       :install-strategy :packages
                       :packages (openjdk-packages
                                  os os-version version
                                  (:components settings)))))])

;;; ## Settings
(defn- settings-map
  "Dispatch to either openjdk or oracle settings"
  [settings]
  ;; TODO - lookup default java version based on os-version
  (fn [session]
    (let [settings (merge {:vendor :openjdk :components #{:jdk}} settings)]
      (if (= :openjdk (:vendor settings))
        ((openjdk-java-settings (:version settings) settings) session)
        ((oracle-java-settings (:version settings) settings) session)))))

(def-plan-fn java-settings
  "Capture settings for java

- :vendor one of #{:openjdk :oracle :sun}
- :components a set of #{:jdk :jre}

- :package installs from packages

- :rpm takes a map of remote-file options specifying a self-extracting rpm file
  to install

- :debs takes a map of remote-directory options specifying an archive of deb
  files to install. The archive should have no top level directory.

- :package-source takes a map of options to package-source. When used
  with :debs, specifies the local path for the deb files to be expanded to.
  should specify a :name key.

- :download takes a map of options to remote-file

The default for openjdk is to install from system packages.

The story for Oracle JDK is way more complicated.

## RPM based systems

Download the rpm.bin file, and pass it in the :rpm option.

## Apt based systems

Since Oracle don't provide debs, this gets complex.

### JDK 6

Build packages using https://github.com/rraptorr/sun-java6, possibly via
https://github.com/palletops/java-package-builder. Pass the resulting debs as a
tar file to :debs.

### JDK 7

Use the webupd8.org ppa. This is the default

http://www.webupd8.org/2012/01/install-oracle-java-jdk-7-in-ubuntu-via.html"
  [{:keys [vendor version components instance-id] :as settings}]
  [default-version (os-map-lookup @java-package-version)
   settings (settings-map
             (merge {:version (or version (version-string default-version))}
                    settings))]
  (assoc-settings :java settings {:instance-id instance-id}))

;;; ## Environment variable helpers
(def-plan-fn set-environment
  [components]
  (pipeline-when (:jdk components)
    (system-environment
     "java" {"JAVA_HOME" (script (~jdk-home))}))
  (pipeline-when (and (:jre components) (not (:jdk components)))
    (system-environment
     "java" {"JAVA_HOME" (script (~java-home))})))

;;; # Install

;;; custom install method for oracle rpm.bin method
(defmethod-plan install ::rpm-bin
  [facility instance-id]
  [{:keys [rpm]} (get-settings facility {:instance-id instance-id})]
  (with-action-options {:action-id ::upload-rpm-bin
                        :always-before ::unpack-sun-rpm}
    (apply-map
     remote-file "java.rpm.bin"
     (merge
      {:local-file-options {:always-before #{`unpack-sun-rpm}} :mode "755"}
      rpm)))
  (with-action-options {:action-id ::unpack-sun-rpm}
    (exec-checked-script
     (format "Unpack java rpm %s" "java.rpm.bin")
     (~lib/heredoc "java-bin-resp" "A\n\n" {})
     (chmod "+x" "java.rpm.bin")
     ("./java.rpm.bin" < "java-bin-resp"))))

(def-plan-fn install-java
  "Install java. OpenJDK installs from system packages by default."
  [& {:keys [instance-id]}]
  [settings (get-settings
             :java {:instance-id instance-id :default ::no-settings})]
  (install :java instance-id)
  (set-environment (:components settings)))

(def-plan-fn jce-policy-file
  "Installs a local JCE policy jar at the given path in the remote JAVA_HOME's
   lib/security directory, enabling the use of \"unlimited strength\" crypto
   implementations. Options are as for remote-file.

   e.g. (jce-policy-file
          \"local_policy.jar\" :local-file \"path/to/local_policy.jar\")

   Note this only intended to work for ubuntu/aptitude-managed systems and Sun
   JDKs right now."
  [filename & {:as options}]
  (apply-map remote-file
    (script (str (jre-lib-security) ~filename))
    (merge {:owner "root" :group "root" :mode 644} options)))

(defn java
  "Returns a service-spec for installing java."
  [settings]
  (server-spec
   :phase {:settings (java-settings settings)
           :configure (install-java)}))
