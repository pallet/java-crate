(ns pallet.crate.java
  "Crates for java installation and configuration.

   Sun Java installation on CentOS requires use of Oracle rpm's. Download from
   http://www.oracle.com/technetwork/java/javase/downloads/index.html and get
   the .rpm.bin file onto the node with remote-file.  Then pass the location of
   the rpm.bin file on the node using the :rpm-bin option. The rpm will be
   installed."
  (:require
   [pallet.api :as api]
   [pallet.crate-install :as crate-install]
   [pallet.script.lib :as lib]
   [clojure.tools.logging :refer [debugf]]
   [pallet.action :refer [with-action-options]]
   [pallet.actions :refer [content-options exec-checked-script
                           remote-directory remote-file]]
   [pallet.api :refer [plan-fn]]
   [pallet.compute :refer [os-hierarchy]]
   [pallet.crate :refer [assoc-settings defmethod-plan defplan get-settings
                         os-family os-version]]
   [pallet.crate.environment :refer [system-environment]]
   [pallet.crate.java.kb :as kb]
   [pallet.script :refer [defimpl defscript]]
   [pallet.stevedore :refer [fragment script]]
   [pallet.utils :refer [apply-map]]
   [pallet.version-dispatch
    :refer [defmethod-version defmethod-version-plan defmulti-version
            defmulti-version-plan os-map os-map-lookup]]
   [pallet.versions :refer [version-vector version-string]]))

(def vendor-keywords #{:openjdk :sun :oracle})
(def component-keywords #{:jdk :jre :bin})
(def all-keywords (into #{} (concat vendor-keywords component-keywords)))

;;; ## Script
(defscript java-home [])
(defimpl java-home :default []
  ~(fragment @("dirname" @("dirname" @("readlink" -f @("which" java))))))
(defimpl java-home [#{:aptitude :apt}] []
  ~(fragment
    @("dirname"
      @("dirname"
        @(pipe ("update-alternatives" --query java)
               ("grep" "Best:")
               ("cut" -f 2 -d "' '"))))))
(defimpl java-home [#{:darwin :os-x}] []
  ~(fragment @JAVA_HOME))

(defscript jdk-home [])
(defimpl jdk-home :default []
  ~(fragment
    @("dirname" @("dirname" @("readlink" -f @("which" javac))))))
(defimpl jdk-home [#{:aptitude :apt}] []
  ~(fragment
    @("dirname"
      @("dirname"
        @(pipe ("update-alternatives" --query javac)
               ("grep" "Best:")
               ("cut" -f 2 -d "' '"))))))
(defimpl jdk-home [#{:darwin :os-x}] []
  ~(fragment @JAVA_HOME))

(defscript jre-lib-security [])
(defimpl jre-lib-security :default []
  ~(fragment
    (str @(pipe ("update-java-alternatives" -l)
                ("cut" "-d ' '" -f 3)
                ("head" -1))
         "/jre/lib/security/")))

;;; # Settings
(defn- target-os []
  (kb/os {:os-family (os-family)
          :os-version (version-vector (os-version))}))

(defn system-package-names
  [{:keys [version components] :as spec}]
  (kb/package-names (kb/default-target (target-os) spec)))

(defn from-system-packages
  [{:keys [version components] :as spec}]
  (kb/from-packages (system-package-names spec)))

(defn from-rpm-bin
  "Install from an RPM binary installer, located according to
  the remote-file-source options."
  [remote-file-source]
  (merge
   {:install-strategy ::rpm-bin
    :rpm remote-file-source}))

(defn from-webupd8
  [{:keys [version] :as spec}]
  (kb/from-webupd8 version))

(defn from-tar-file
  "Install from a tar file."
  [remote-directory-source
   {:keys [install-dir]
    :or {install-dir "/usr/local/java"}
    :as options}]
  (merge
   {:install-strategy ::tarfile}
   remote-directory-source))

(defn from-rpm-bin
  "Install from an RPM binary installer, located according to
  the remote-file-source options."
  [remote-file-source]
  (merge
   {:install-strategy ::rpm-bin
    :rpm remote-file-source}))

(defn from-debs
  "Install from an archive of debs files, located according to the
  remote-directory-source options. The archive should have no top
  level directory."
  [remote-directory-source packages
   {:keys [package-source] :or {package-source {}}}]
  {:install-strategy :debs
   :debs remote-directory-source
   :packages packages
   :package-source (->
                    package-source
                    (update-in [:url] #(or % "file://$(pwd)/pallet-packages"))
                    (update-in [:path] #(or % "pallet-packages"))
                    (update-in [:release] #(or % "./"))
                    (update-in [:scopes] #(or % [])))})

(defn from-default
  "Install from the default strategy"
  [{:keys [version vendor components] :as spec}]
  (let [target (kb/default-target (target-os) spec)
        strategies (kb/install-strategy target)]
    (when (> (count strategies) 1)
      (throw (ex-info "More than one install strategy available."
                      {:strategies strategies})))
    (when (not (seq strategies))
      (throw (ex-info "No install strategy available." {})))
    (merge
     (first strategies)
     (select-keys target [:version :components]))))

(defplan settings
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
  (let [settings (if (:install-strategy settings)
                   settings
                   (merge settings
                          (from-default
                           (select-keys settings
                                        [:version :vendor :components]))))]
    (debugf "java settings %s" settings)
    (assoc-settings :java settings {:instance-id instance-id})))

;;; ## Environment variable helpers
(defplan set-environment
  [components]
  (debugf "set-environment for java components %s" components)
  (when (:jdk components)
    (system-environment
     "java" {"JAVA_HOME" (fragment (~jdk-home))}))
  (when (and (:jre components) (not (:jdk components)))
    (system-environment
     "java" {"JAVA_HOME" (fragment (~java-home))})))

;;; # Install

;;; custom install method for oracle rpm.bin method
(defmethod-plan crate-install/install ::rpm-bin
  [facility instance-id]
  (let [{:keys [rpm]} (get-settings facility {:instance-id instance-id})]
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
       ("chmod" "+x" "java.rpm.bin")
       ("./java.rpm.bin" < "java-bin-resp")))))

(defmethod-plan crate-install/install ::tarfile
  [facility instance-id]
  (let [{:keys [install-dir] :as settings}
        (get-settings facility {:instance-id instance-id})]
    (apply-map
     remote-directory install-dir
     :strip-components 0
     (select-keys settings content-options))
    (exec-checked-script
     "Update java alternatives"
     ;; Add an alternaive
     ("update-alternatives"
      "--install" "/usr/bin/java" "java"
      (str ~install-dir "/jdk*/bin/java") 1)
     ("update-alternatives"
      "--install" "/usr/bin/javac" "javac"
      (str ~install-dir "/jdk*/bin/javac") 1)
     ("update-alternatives"
      "--install" "/usr/bin/javaws" "javaws"
      (str ~install-dir "/jdk*/bin/javaws") 1)

     ;; Use the alternative
     ("update-alternatives"
      "--set" "java" (str ~install-dir "/jdk*/bin/java"))
     ("update-alternatives"
      "--set" "javac" (str ~install-dir "/jdk*/bin/javac"))
     ("update-alternatives"
      "--set" "javaws" (str ~install-dir "/jdk*/bin/javaws")))))

(defmethod-plan crate-install/install ::no-op
  [facility instance-id]
  (let [{:keys [rpm]} (get-settings facility {:instance-id instance-id})]
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
       ("chmod" "+x" "java.rpm.bin")
       ("./java.rpm.bin" < "java-bin-resp")))))

(defplan install
  "Install java. OpenJDK installs from system packages by default."
  [{:keys [instance-id]}]
  (let [settings (get-settings
                  :java {:instance-id instance-id :default ::no-settings})]
    (debugf "install settings %s" settings)
    (crate-install/install :java instance-id)
    (set-environment (:components settings))))

(defplan jce-policy-file
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

(defn server-spec
  "Returns a service-spec for installing java."
  [settings & {:keys [instance-id] :as options}]
  (api/server-spec
   :phases {:settings (plan-fn
                        (pallet.crate.java/settings (merge settings options)))
            :install (plan-fn (install options))}
   :default-phases [:install]))
