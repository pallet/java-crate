(ns pallet.crate.java
  "Crates for java installation and configuration.

   Sun Java installation on CentOS requires use of Oracle rpm's. Download from
   http://www.oracle.com/technetwork/java/javase/downloads/index.html and get
   the .rpm.bin file onto the node with remote-file.  Then pass the location of
   the rpm.bin file on the node using the :rpm-bin option. The rpm will be
   installed."

;; Add
;;http://www.webupd8.org/2012/01/install-oracle-java-jdk-7-in-ubuntu-via.html

  (:require
   [clojure.tools.logging :as logging]
   [pallet.action :as action]
   [pallet.parameter :as parameter]
   [pallet.script :as script]
   [pallet.script.lib :as lib]
   [pallet.session :as session]
   [pallet.stevedore :as stevedore]
   [clojure.string :as string])
  (:use
   [pallet.action :only [with-action-options]]
   [pallet.action.exec-script :only [exec-checked-script]]
   [pallet.action.package
    :only [package package-source package-manager* install-deb]]
   [pallet.action.package.jpackage :only [add-jpackage]]
   [pallet.action.remote-directory :only [remote-directory]]
   [pallet.action.remote-file :only [remote-file]]
   [pallet.common.context :only [throw-map]]
   [pallet.compute :only [os-hierarchy]]
   [pallet.core :only [server-spec]]
   [pallet.crate.environment :only [system-environment]]
   [pallet.crate.package-repo :only [repository-packages rebuild-repository]]
   [pallet.parameter :only [assoc-target-settings get-target-settings]]
   [pallet.phase :only [phase-fn]]
   [pallet.thread-expr :only [when-> apply-map->]]
   [pallet.utils :only [apply-map]]
   [pallet.version-dispatch
    :only [defmulti-version-crate defmulti-version defmulti-os-crate
           multi-version-session-method multi-version-method
           multi-os-session-method]]
   [pallet.versions :only [version-string]]))

(def vendor-keywords #{:openjdk :sun :oracle})
(def component-keywords #{:jdk :jre :bin})
(def all-keywords (into #{} (concat vendor-keywords component-keywords)))


;;; ## Script
(script/defscript java-home [])
(script/defimpl java-home :default []
  @("dirname" @("dirname" @("readlink" -f @("which" java)))))
(script/defimpl java-home [#{:aptitude :apt}] []
  @("dirname"
    @("dirname"
      @(pipe ("update-alternatives" --list java) (head -1)))))
(script/defimpl java-home [#{:darwin :os-x}] []
   @JAVA_HOME)

(script/defscript jdk-home [])
(script/defimpl jdk-home :default []
  @("dirname" @("dirname" @("readlink" -f @("which" javac)))))
(script/defimpl jdk-home [#{:aptitude :apt}] []
  @("dirname"
    @("dirname"
      @(pipe ("update-alternatives" --list javac) (head -1)))))
(script/defimpl jdk-home [#{:darwin :os-x}] []
   @JAVA_HOME)

(script/defscript jre-lib-security [])
(script/defimpl jre-lib-security :default []
  (str @(update-java-alternatives -l "|" cut "-d ' '" -f 3 "|" head -1)
       "/jre/lib/security/"))

;;; Default Java package version
(defmulti-os-crate java-package-version [session])

(multi-os-session-method
    java-package-version {:os :linux}
    [os os-version session]
  [6])

(multi-os-session-method
    java-package-version {:os :ubuntu :os-version [12]}
    [os os-version session]
  [7])

;;; ## openJDK package names
(defmulti-version openjdk-packages [os os-version version components]
  #'os-hierarchy)

(multi-version-method
    openjdk-packages {:os :rh-base}
    [os os-version version components]
  (map
   (comp
    (partial str "java-1." (version-string version) ".0-openjdk")
    #({:jdk "-devel" :jre ""} % ""))
   components))

(multi-version-method
    openjdk-packages {:os :debian-base}
    [os os-version version components]
  (map
   (comp (partial str "openjdk-" (version-string version) "-") name)
   components))

(multi-version-method
    openjdk-packages {:os :arch-base}
    [os os-version version components]
  [(str "openjdk" (version-string version))])

;;; ## Oracle package names
(defmulti-version oracle-packages [os os-version version components]
  #'os-hierarchy)

(multi-version-method
    oracle-packages {:os :rh-base :version [7]}
    [os os-version version components]
  (map
   (comp
    (partial str "oracle-java" (version-string version) "-")
    #({:jdk "-devel" :jre ""} % ""))
   components))

(multi-version-method
    oracle-packages {:os :rh-base :version [6]}
    [os os-version version components]
  (map
   (comp
    (partial str "sun-java" (version-string version) "-")
    #({:jdk "-devel" :jre ""} % ""))
   components))

(multi-version-method
    oracle-packages {:os :debian-base :version [7]}
    [os os-version version components]
  {:pre [(seq components)]}
  (conj
   (map
    (comp (partial str "oracle-java" (version-string version) "-") name)
    components)
   (str "oracle-java" (version-string version) "-bin")))

(multi-version-method
    oracle-packages {:os :debian-base :version [6]}
    [os os-version version components]
  {:pre [(seq components)]}
  (conj
   (map
    (comp (partial str "sun-java" (version-string version) "-") name)
    components)
   (str "sun-java" (version-string version) "-bin")))

(multi-version-method
    oracle-packages {:os :arch-base :version [7]}
    [os os-version version components]
  [(str "oracle-java" (version-string version))])

(multi-version-method
    oracle-packages {:os :arch-base :version [6]}
    [os os-version version components]
  [(str "sun-java" (version-string version))])

;;; ## Oracle java
;;; Based on supplied settings, decide which install strategy we are using
;;; for oracle java.

(defmulti-version-crate oracle-java-settings [version session settings])

(multi-version-session-method
    oracle-java-settings {:os :rh-base}
    [os os-version version session settings]
  (cond
    (:strategy settings) settings
    (:rpm settings) (assoc settings :strategy :rpm)
    (:package-source settings) (assoc settings
                                 :strategy :package-source
                                 :packages (oracle-packages
                                            os os-version version
                                            (:components settings)))
    :else (throw (Exception. "No install method selected for Oracle JDK"))))

(multi-version-session-method
    oracle-java-settings {:os :debian-base :version [7]}
    [os os-version version session settings]
  (let [strategy (:strategy settings)]
    (cond
      (or (= strategy :debs) (:debs settings))
      (->
       settings
       (assoc :strategy :debs)
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
       (assoc :strategy :package-source)
       (update-in
        [:packages]
        #(or % (oracle-packages os os-version version (:components settings)))))

      :else
      (->
       settings
       (assoc :strategy :w8-ppa)
       (update-in
        [:packages]
        #(or % ["oracle-java7-installer"]))
       (update-in
        [:package-source :aptitude]
        #(or % {:url "ppa:webupd8team/java"}))
       (update-in
        [:package-source :name]
        #(or % "webupd8team-java-$(lsb_release -c -s)"))))))

(multi-version-session-method
    oracle-java-settings {:os :debian-base :version [6]}
    [os os-version version session settings]
  (let [strategy (:strategy settings)]
    (cond
      (or (= strategy :debs) (:debs settings))
      (->
       settings
       (assoc :strategy :debs)
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
       (assoc :strategy :package-source)
       (update-in
        [:packages]
        #(or % (oracle-packages os os-version version (:components settings)))))

      :else (throw
             (Exception. "No install method selected for Oracle java 6")))))


;;; ## OpenJDK java
;;; Based on supplied settings, decide which install strategy we are using
;;; for openjdk java.
(defmulti-version-crate openjdk-java-settings [version session settings])

(multi-version-session-method
    openjdk-java-settings {:os :linux :version [7]}
    [os os-version version session settings]
  (cond
    (:strategy settings) settings
    :else (assoc settings
            :strategy :package
            :packages (openjdk-packages
                       os os-version version
                       (:components settings)))))

(multi-version-session-method
    openjdk-java-settings {:os :linux :version [6]}
    [os os-version version session settings]
  (cond
    (:strategy settings) settings
    :else (assoc settings
            :strategy :package
            :packages (openjdk-packages
                       os os-version version
                       (:components settings)))))

;;; ## Settings
(defn- settings-map
  "Dispatch to either openjdk or oracle settings"
  [session settings]
  ;; TODO - lookup default java version based on os-version
  (let [settings (merge {:vendor :openjdk :version [6] :components #{:jdk}}
                        settings)]
    (if (= :openjdk (:vendor settings))
      (openjdk-java-settings session (:version settings) settings)
      (oracle-java-settings session (:version settings) settings))))

(require 'pallet.debug)

(defn java-settings
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

http://www.webupd8.org/2012/01/install-oracle-java-jdk-7-in-ubuntu-via.html
"
  [session {:keys [vendor version components instance-id]
            :or {version (version-string (java-package-version session))}
            :as settings}]
  (let [settings (settings-map session (merge {:version version} settings))]
    (assoc-target-settings session :java instance-id settings)))

;;; ## Environment variable helpers
(defn set-environment
  [session components]
  (->
   session
   (when-> (:jdk components)
     (system-environment
      "java" {"JAVA_HOME" (stevedore/script (~jdk-home))}))
   (when-> (and (:jre components) (not (:jdk components)))
     (system-environment
      "java" {"JAVA_HOME" (stevedore/script (~java-home))}))))

;;; ## Install via packages
(defn package-install
  [session settings]
  (reduce package session (:packages settings)))

;;; ## Install via packages from a specific package source
(defn package-source-install
  [session settings]
  (let [repo-name (-> settings :package-source :name)
        _ (assert repo-name)    ;  "Must provide a repo name for package-source"
        pkg-list-update (package-manager* session :update)
        _ (logging/infof "update package list with %s" pkg-list-update)
        session (->
                 session
                 (with-action-options {:action-id ::install-package-source}
                   (apply-map->
                    package-source repo-name (:package-source settings)))
                 (with-action-options
                     {:always-before #{`package}
                      :always-after #{`package-source ::deb-install}
                      :action-id ::update-package-source}
                   (exec-checked-script
                    (str "Update package list for repository " repo-name)
                    ~pkg-list-update)))]
    (reduce
     #(package %1 %2 :allow-untrusted true)
     session (:packages settings))))

;;; ## rpm file install
(defn rpm-install
  "Upload an rpm bin file for java. Options are as for remote-file"
  [session settings]
  (->
   session
   (with-action-options {:action-id ::upload-rpm-bin
                         :always-before ::unpack-sun-rpm}
     (apply-map->
      remote-file "java.rpm.bin"
      (merge
       {:local-file-options {:always-before #{`unpack-sun-rpm}} :mode "755"}
       (:rpm settings))))
   (with-action-options {:action-id ::unpack-sun-rpm}
     (exec-checked-script
      (format "Unpack java rpm %s" "java.rpm.bin")
      (~lib/heredoc "java-bin-resp" "A\n\n" {})
      (chmod "+x" "java.rpm.bin")
      ("./java.rpm.bin" < "java-bin-resp")))))

;;; ## deb files install
(defn deb-install
  "Upload a deb archive for java. Options for the :debs key are as for
  remote-directory (e.g. a :local-file key with a path to a local tar
  file). Pallet uploads the deb files, creates a repository from them, then
  installs from the repository."
  [session settings]
  (let [path (-> settings :package-source :aptitude :path)]
    (->
     session
     (with-action-options
         {:action-id ::deb-install
          :always-before #{::update-package-source ::install-package-source}}
       (apply-map->
        remote-directory path
        (merge
         {:local-file-options
          {:always-before #{::update-package-source ::install-package-source}}
          :mode "755"
          :strip-components 0}
         (:debs settings)))
       (repository-packages)
       (rebuild-repository path))
     (package-source-install settings))))

;;;  ## w8 PPA install

;; See
;; http://www.webupd8.org/2012/01/install-oracle-java-jdk-7-in-ubuntu-via.html
(defn w8-install
  "Install via the w8 PPA."
  [session settings]
  (package-source-install session settings))

;;; ## download install
(defn download-install
  "Download and unpack a jdk tar.gz file"
  [session settings]
  (apply-map remote-directory session "/usr/local" (:download settings)))

;;; # Install

;;; Dispatch to install strategy
(defmulti install-method (fn [session settings] (:strategy settings)))
(defmethod install-method :package [session settings]
  (package-install session settings))
(defmethod install-method :package-source [session settings]
  (package-source-install session settings))
(defmethod install-method :rpm [session settings]
  (rpm-install session settings))
(defmethod install-method :debs [session settings]
  (deb-install session settings))
(defmethod install-method :w8-ppa [session settings]
  (w8-install session settings))
(defmethod install-method :download [session settings]
  (download-install session settings))

(defn install-java
  "Install java. OpenJDK installs from system packages by default."
  [session & {:keys [instance-id]}]
  (let [settings (get-target-settings session :java instance-id ::no-settings)]
    (logging/debugf "install-java settings %s" settings)
    (if (= settings ::no-settings)
      (throw-map
       "Attempt to install java without specifying settings"
       {:message "Attempt to install java without specifying settings"
        :type :invalid-operation})
      (->
       session
       (install-method settings)
       (set-environment (:components settings))))))

(defn jce-policy-file
  "Installs a local JCE policy jar at the given path in the remote JAVA_HOME's
   lib/security directory, enabling the use of \"unlimited strength\" crypto
   implementations. Options are as for remote-file.

   e.g. (jce-policy-file
          \"local_policy.jar\" :local-file \"path/to/local_policy.jar\")

   Note this only intended to work for ubuntu/aptitude-managed systems and Sun
   JDKs right now."
  [session filename & {:as options}]
  (apply-map remote-file session
    (stevedore/script (str (jre-lib-security) ~filename))
    (merge {:owner "root" :group "root" :mode 644} options)))

(defn java
  "Returns a service-spec for installing java."
  [settings]
  (server-spec
   :phases {:settings (phase-fn (java-settings settings))
            :configure (phase-fn (install-java))}))
