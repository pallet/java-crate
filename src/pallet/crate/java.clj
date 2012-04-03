(ns pallet.crate.java
  "Crates for java installation and configuration.

   Sun Java installation on CentOS requires use of Oracle rpm's. Download from
   http://www.oracle.com/technetwork/java/javase/downloads/index.html and get
   the .rpm.bin file onto the node with remote-file.  Then pass the location of
   the rpm.bin file on the node using the :rpm-bin option. The rpm will be
   installed."
  (:require
   [pallet.action :as action]
   [pallet.parameter :as parameter]
   [pallet.script :as script]
   [pallet.script.lib :as lib]
   [pallet.session :as session]
   [pallet.stevedore :as stevedore]
   [pallet.thread-expr :as thread-expr]
   [clojure.string :as string])
  (:use
   [clojure.algo.monads :only [m-map]]
   [pallet.action :only [with-action-options]]
   [pallet.actions
    :only [exec-script exec-checked-script install-deb package package-source
           remote-directory remote-file]]
   [pallet.common.context :only [throw-map]]
   [pallet.compute :only [os-hierarchy]]
   [pallet.crate.environment :only [system-environment]]
   [pallet.monad :only [chain-s]]
   [pallet.parameter :only [assoc-settings get-settings]]
   [pallet.phase :only [def-crate-fn]]
   [pallet.utils :only [apply-map]]
   [pallet.version-dispatch
    :only [defmulti-version-crate defmulti-version
           multi-version-crate-method multi-version-method]]
   [pallet.versions :only [version-string]]))

(def vendor-keywords #{:openjdk :sun :oracle})
(def component-keywords #{:jdk :jre :bin})
(def all-keywords (into #{} (concat vendor-keywords component-keywords)))


;;; ## Script
(script/defscript java-home [])
(script/defimpl java-home :default []
  @("dirname" @("dirname" @("readlink" -f @("which" java)))))
(script/defimpl java-home [#{:aptitude}] []
  @("dirname" @("dirname" @("update-alternatives" --list java))))
(script/defimpl java-home [#{:darwin :os-x}] []
   @JAVA_HOME)

(script/defscript jdk-home [])
(script/defimpl jdk-home :default []
  @("dirname" @("dirname" @("readlink" -f @("which" javac)))))
(script/defimpl jdk-home [#{:aptitude}] []
  @("dirname" @("dirname" @("update-alternatives" --list javac))))
(script/defimpl jdk-home [#{:darwin :os-x}] []
   @JAVA_HOME)

(script/defscript jre-lib-security [])
(script/defimpl jre-lib-security :default []
  (str @(update-java-alternatives -l "|" cut "-d ' '" -f 3 "|" head -1)
       "/jre/lib/security/"))

;;; ## openJDK package names
(defmulti-version openjdk-packages [os os-version version components]
  #'os-hierarchy)

(multi-version-method
    openjdk-packages {:os :rh-base}
    [os os-version version components]
  (map
   (comp
    (partial str "openjdk-" (version-string version) "-")
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
    oracle-packages {:os :rh-base}
    [os os-version version components]
  (map
   (comp
    (partial str "sun-java" (version-string version) "-")
    #({:jdk "-devel" :jre ""} % ""))
   components))

(multi-version-method
    oracle-packages {:os :debian-base}
    [os os-version version components]
  (conj
   (map
    (comp (partial str "sun-java" (version-string version) "-") name)
    components)
   ("sun-java" version "-bin")))

(multi-version-method
    oracle-packages {:os :arch-base}
    [os os-version version components]
  [(str "sun-java" (version-string version))])

;;; ## Oracle java
;;; Based on supplied settings, decide which install strategy we are using
;;; for oracle java.
(def jdk7url "http://download.oracle.com/otn-pub/java/jdk/7/jdk-7-linux-x64")

(defmulti-version-crate oracle-java-settings [session version settings])

(multi-version-crate-method
    oracle-java-settings {:os :rh-base :version [7]}
    [os os-version version settings]
  (cond
    (:strategy settings) (m-result settings)
    (:rpm settings) (m-result (assoc settings :strategy :rpm))
    (:package-source settings) (m-result
                                (assoc settings
                                  :strategy :package-source
                                  :packages (oracle-packages
                                             os os-version version
                                             (:components settings))))
    :else (m-result
           (assoc settings :strategy :rpm :rpm {:url (str jdk7url ".rpm")}))))

(multi-version-crate-method
    oracle-java-settings {:os :rh-base :version [6]}
    [os os-version version settings]
  (cond
    (:strategy settings) (m-result settings)
    (:rpm settings) (m-result (assoc settings :strategy :rpm))
    (:package-source settings) (m-result
                                (assoc settings
                                  :strategy :package-source
                                  :packages (oracle-packages
                                             os os-version version
                                             (:components settings))))
    :else (throw (Exception. "No install method selected for Oracle java 6"))))

(multi-version-crate-method
    oracle-java-settings {:os :debian-base}
    [os os-version version settings]
  (cond
    (:strategy settings) (m-result settings)
    (:deb settings) (m-result (assoc settings :strategy :deb))
    (:package-source settings) (m-result
                                (assoc settings
                                  :strategy :package-source
                                  :packages (oracle-packages
                                             os os-version version
                                             (:components settings))))
    :else (m-result (assoc settings :strategy :download))))


;;; ## OpenJDK java
;;; Based on supplied settings, decide which install strategy we are using
;;; for openjdk java.
(defmulti-version-crate openjdk-java-settings [session version settings])

(multi-version-crate-method
    openjdk-java-settings {:os :linux :version [7]}
    [os os-version version settings]
  (cond
    (:strategy settings) (m-result settings)
    :else (m-result (assoc settings
                      :strategy :package
                      :packages (openjdk-packages
                                 os os-version version
                                 (:components settings))))))

(multi-version-crate-method
    openjdk-java-settings {:os :linux :version [6]}
    [os os-version version settings]
  (cond
    (:strategy settings) (m-result settings)
    :else (m-result
           (assoc settings
             :strategy :package
             :packages (openjdk-packages
                        os os-version version
                        (:components settings))))))

;;; ## Settings
(defn- settings-map
  "Dispatch to either openjdk or oracle settings"
  [settings]
  ;; TODO - lookup default java version based on os-version
  (fn [session]
    (let [settings (merge {:vendor :openjdk :version [6] :components #{:jdk}}
                          settings)]
      (if (= :openjdk (:vendor settings))
        ((openjdk-java-settings
          session (:version settings) settings) session)
        ((oracle-java-settings
          session (:version settings) settings) session)))))

(require 'pallet.debug)

(def-crate-fn java-settings
  "Capture settings for java

- :vendor one of #{:openjdk :oracle :sun}
- :components a set of #{:jdk :jre}

- :package installs from packages

- :rpm takes a map of remote-file options specifying an rpm file to install

- :deb takes a map of remote-file options specifying a deb file to install

- :package-source takes a map of options to package-source

- :download takes a boolean, or map of options to remote-file"
  [{:keys [vendor version components instance] :as settings}]
  [settings (settings-map settings)]
  (assoc-settings :java instance settings))

;;; ## Environment variable helpers
(def-crate-fn set-environment
  [components]
  (when (:jdk components)
    (system-environment
     "java" {"JAVA_HOME" (stevedore/script (~jdk-home))}))
  (when (and (:jre components) (not (:jdk components)))
    (system-environment
     "java" {"JAVA_HOME" (stevedore/script (~java-home))})))

;;; ## Install via packages
(def-crate-fn package-install
  []
  [settings (get-settings :java nil {})]
  (m-map package (:packages settings)))

;;; ## Install via packages from a specific package source
(def-crate-fn package-source-install
  []
  [settings (get-settings :java nil {})]
  (package-source (:package-source settings))
  (m-map package (:packages settings)))

;;; ## rpm file install
(def-crate-fn rpm-install
  "Upload an rpm bin file for java. Options are as for remote-file"
  []
  [settings (get-settings :java nil {})]
  (with-action-options {:action-id ::upload-rpm-bin
                               :always-before ::unpack-sun-rpm}
    (apply-map
     remote-file "java.rpm.bin"
     (merge
      {:local-file-options {:always-before #{`unpack-sun-rpm}} :mode "755"}
      (:rpm settings))))
  (with-action-options {:action-id ::unpack-sun-rpm}
    (exec-checked-script
     (format "Unpack java rpm %s" "java.rpm.bin")
     (~lib/heredoc "java-bin-resp" "A\n\n" {})
     (chmod "+x" "java.rpm.bin")
     ("java.rpm.bin" < "java-bin-resp"))))

;;; ## deb file install
(def-crate-fn deb-install
  "Upload an deb bin file for java. Options are as for remote-file"
  []
  [settings (get-settings :java nil {})]
  (with-action-options {:action-id ::upload-deb-bin
                               :always-before ::unpack-sun-deb}
    (apply-map
     remote-file "java.deb"
     (merge
      {:local-file-options {:always-before #{`unpack-sun-deb}} :mode "755"}
      (:deb settings))))
  (with-action-options {:action-id ::unpack-sun-deb}
    (install-deb "java.deb")))

;;; ## download install
(def-crate-fn download-install
  "Download and unpack a jdk tar.gz file"
  []
  [settings (get-settings :java nil {})]
  (apply-map
   remote-directory "/usr/local"
   (merge
    {:url (str jdk7url ".tar.gz")}
    (:download settings))))

;;; # Install

;;; Dispatch to install strategy
(defmulti install-method (fn [strategy] strategy))
(defmethod install-method :package [_] (package-install))
(defmethod install-method :package-source [_] (package-source-install))
(defmethod install-method :rpm [_] (rpm-install))
(defmethod install-method :deb [_] (deb-install))
(defmethod install-method :download [_] (download-install))

(def-crate-fn install-java
  "Install java. OpenJDK installs from system packages by default."
  []
  [settings (get-settings :java nil ::no-settings)]
  (if (= settings ::no-settings)
    (throw-map
     "Attempt to install java without specifying settings"
     {:message "Attempt to install java without specifying settings"
      :type :invalid-operation})
    (chain-s
     (install-method (:strategy settings))
     (set-environment (:components settings)))))






;; (def ubuntu-partner-url "http://archive.canonical.com/ubuntu")

;; (defn- use-jpackage
;;   "Determine if jpackage should be used"
;;   [session]
;;   (#{:centos :rhel :fedora} (session/os-family session)))




;; (defn make-compat
;;   [session update]
;;   ;; arch is hard coded to i586 for ix86 in the spec file
;;   (let [arch (stevedore/script
;;               (pipe (~lib/arch) (sed -e (quoted "s/[1-6]86/586/"))))
;;         pkg (format "java-1.6.0-sun-compat-1.6.0.%s-1jpp.%s" update arch)]
;;     (->
;;      session
;;      (remote-file
;;       "java-1.6.0-sun-compat-1.6.0.03-1jpp.src.rpm"
;;       :url "http://mirrors.dotsrc.org/jpackage/5.0/generic/non-free/SRPMS/java-1.6.0-sun-compat-1.6.0.03-1jpp.src.rpm")
;;      (package "rpm-build")
;;      (package "libxslt")
;;      (exec-checked-script
;;       "rebuild source rpm"
;;       (if-not (rpm -q ~pkg > "/dev/null" "2>&1")
;;         (do
;;           (rpm -ivh "java-1.6.0-sun-compat-1.6.0.03-1jpp.src.rpm")
;;           (cd "/usr/src/redhat/SPECS")
;;           (~lib/sed-file
;;            "java-1.6.0-sun-compat.spec"
;;            ~{"buildver.*03" (str "buildver " (format "%s" update))}
;;            {})

;;           (sed
;;            -i -e
;;            (quoted
;;             (str "\\_%config.*/lib/security/java.security_ i \\\n"
;;                  "%config(noreplace) %{_jvmdir}/%{jredir}/lib/security/blacklist")
;;             " \n")
;;            -e
;;            (quoted
;;             (str "\\_%config.*/lib/security/java.security_ i \\\n"
;;                  "%config(noreplace) %{_jvmdir}/%{jredir}/lib/security/javaws.policy")
;;             " \n")
;;            -e
;;            (quoted
;;             (str
;;              "\\_%config.*/lib/security/java.security_ i \\\n"
;;              "%config(noreplace) %{_jvmdir}/%{jredir}/lib/security/trusted.libraries")
;;             " \n")
;;            "java-1.6.0-sun-compat.spec")
;;           (rpmbuild -ba java-1.6.0-sun-compat.spec)
;;           (rpm -Uvh ~(str "/usr/src/redhat/RPMS/" arch "/" pkg ".rpm"))))))))

;; (def sun-paths
;;   {:jdk {:jre "/usr/java/%s%s/jre"
;;          :jdk "/usr/java/%s%s"
;;          :jdk-bin "/usr/java/%s%s/bin/"
;;          :jre-bin "/usr/java/%s%s/jre/bin/"
;;          :jce_local_policy "/usr/java/%s%s/jre/lib/security/local_policy.jar"}
;;    :jre {:jre "/usr/java/%s%s/jre"
;;          :jre-bin "/usr/java/%s%s/jre/bin/"
;;          :jce_local_policy "/usr/java/%s%s/jre/lib/security/local_policy.jar"}})

;; (defn sun-version
;;   "Extract the sun version from the rpm file name.
;;        (sun-version \"jdk-6u23-linux-x64-rpm.bin\")
;;          ==> '(:jdk \"6\" \"23\")"
;;   [rpm]
;;   (let [file (java.io.File. rpm)
;;         filename (.getName file)]
;;     (vec
;;      (concat
;;       [(keyword (second (re-find #"(j..)-"  filename)))]
;;       ((juxt second #(nth % 2))
;;        (re-find #"j..-([0-9]+)u([0-9]+)-" filename))))))

;; (def slave-binaries
;;   {:jdk [:appletviewer :apt :extcheck :HtmlConverter :idlj :jar
;;          :jarsigner :javadoc :javah :javap :jconsole :jdb :jhat :jinfo :jmap
;;          :jps :jrunscript :jsadebugd :jstack :jstat :jstatd :jnative2:ascii
;;          :rmic :schemagen :serialver :wsgen :wsimport :xjc]
;;    :jre [:javaws :keytool :orbd :pack200 :rmid :rmiregistry
;;          :servertool :tnameserv :unpack200]})

;; (defn sun-alternatives
;;   [[component major update]]
;;   (let [version (format "1.%s.%s_%s" major 0 update)
;;         priority (format "1%s%s0" major update)
;;         jdk-bin (format
;;                  (:jdk-bin (sun-paths component)) (name component) version)
;;         jre-bin (format
;;                  (:jre-bin (sun-paths component)) (name component) version)
;;         jdk-binary (fn [prog] (str jdk-bin (name prog)))
;;         jre-binary (fn [prog] (str jre-bin (name prog)))]
;;     (stevedore/checked-script
;;      "Set alternatives"
;;      ~(if (= :jdk component)
;;         (stevedore/chained-script
;;          (alternatives
;;           --install "/usr/bin/javac" javac ~(jdk-binary :javac) ~priority
;;           ~(string/join
;;             " "
;;             (map
;;              (fn [prog]
;;                (stevedore/script
;;                 (--slave
;;                  ~(format "/usr/bin/%s" (name prog))
;;                  ~(name prog) ~(jdk-binary prog))))
;;              (slave-binaries component))))
;;          (alternatives --auto javac)))
;;      ~(if (#{:jre :jdk} component)
;;         (stevedore/chained-script
;;          (alternatives
;;           --install "/usr/bin/java" java ~(jre-binary :java) ~priority
;;           ~(string/join
;;             " "
;;             (map
;;              (fn [prog]
;;                (stevedore/script
;;                 (--slave
;;                  ~(format "/usr/bin/%s" (name prog))
;;                  ~(name prog) ~(jre-binary prog))))
;;              (slave-binaries :jre))))
;;          (alternatives --auto java))))))



;; (defn java
;;   "Install java. Options can be :sun, :openjdk, :jdk, :jre.
;;    By default openjdk will be installed.

;;    On CentOS, when specifying :sun, you can also pass the path of the
;;    Oracle rpm.bin file to the :rpm-bin option, and the rpm will be installed."
;;   [session & options]
;;   (let [vendors (or (seq (filter vendor-keywords options))
;;                     [:sun])
;;         components (into #{} (or (seq (filter #{:jdk :jre} options))
;;                                  #{:jdk}))
;;         packager (session/packager session)
;;         os-family (session/os-family session)
;;         use-jpackage (use-jpackage session)
;;         use-alternatives (use-alternatives session)
;;         rpm-bin (:rpm-bin (apply hash-map (remove all-keywords options)))]
;;     (let [vc (fn [session vendor component]
;;                (let [pkgs (java-package-name packager vendor component)]
;;                  (->
;;                   session
;;                   (for->
;;                    [p pkgs]
;;                    (when-> (and (= packager :aptitude) (= vendor :sun))
;;                        (package/package-manager
;;                             :debconf
;;                             (str
;;                              p " shared/present-sun-dlj-v1-1 note")
;;                             (str
;;                              p " shared/accepted-sun-dlj-v1-1 boolean true")))
;;                    (package/package p)))))]
;;       (->
;;        session
;;        (when-> (some #(= :sun %) vendors)
;;                (when-> (= packager :aptitude)
;;                        (when-> (= os-family :ubuntu)
;;                                (package/package-source
;;                                 "Partner"
;;                                 :aptitude {:url ubuntu-partner-url
;;                                            :scopes ["partner"]})
;;                                (package/package-manager :universe)
;;                                (package/package-manager :multiverse)
;;                                (package/package-manager :update))
;;                        (when-> (= os-family :debian)
;;                                (package/package-manager
;;                                 :add-scope :scope :non-free)
;;                                (package/package-manager :update)))
;;                (when->
;;                 use-jpackage
;;                 (jpackage/add-jpackage)
;;                 (jpackage/package-manager-update-jpackage)
;;                 (jpackage/jpackage-utils))
;;                (when->
;;                 rpm-bin
;;                 (unpack-sun-rpm rpm-bin)
;;                 (when->
;;                  use-alternatives
;;                  (arg->
;;                   [request]
;;                   (exec-checked-script
;;                    "Set alternatives for java"
;;                    ~(sun-alternatives (sun-version rpm-bin))))))
;;                (when->
;;                 use-jpackage
;;                 (arg->
;;                  [request]
;;                  (action/with-precedence
;;                    {:action-id ::install-java-compat
;;                     :always-after
;;                     :pallet.action.package.jpackage/install-jpackage-compat}
;;                    (make-compat (last (sun-version rpm-bin)))))))
;;        (package/package-manager :update)
;;        (for-> [vendor vendors]
;;               (for-> [component components]
;;                      (vc vendor component)))
;;        (when->
;;         (components :jdk)
;;         (environment/system-environment
;;          "java"
;;          {"JAVA_HOME" (stevedore/script (~jdk-home))}))
;;        (when->
;;         (and (components :jre) (not (components :jdk)))
;;         (environment/system-environment
;;          "java"
;;          {"JAVA_HOME" (stevedore/script (~java-home))}))))))

(def-crate-fn jce-policy-file
  "Installs a local JCE policy jar at the given path in the remote JAVA_HOME's
   lib/security directory, enabling the use of \"unlimited strength\" crypto
   implementations. Options are as for remote-file.

   e.g. (jce-policy-file
          \"local_policy.jar\" :local-file \"path/to/local_policy.jar\")

   Note this only intended to work for ubuntu/aptitude-managed systems and Sun
   JDKs right now."
  [filename & {:as options}]
  (apply-map remote-file
    (stevedore/script (str (jre-lib-security) ~filename))
    (merge {:owner "root" :group "root" :mode 644} options)))
