(ns pallet.crate.java-test
  (:use pallet.crate.java)
  (:require
   [pallet.core :as core]
   [pallet.live-test :as live-test]
   [pallet.script :as script]
   [pallet.session :as session]
   [pallet.stevedore :as stevedore]
   [pallet.thread-expr :as thread-expr]
   [pallet.utils :as utils]
   [clojure.tools.logging :as logging])
  (:use
   clojure.test
   pallet.test-utils
   [pallet.build-actions :only [build-actions]]
   [pallet.actions
    :only [exec-script exec-checked-script file minimal-packages
           package package-manager package-source remote-file]]
   [pallet.context :only [with-phase-context]]
   [pallet.crate.automated-admin-user :only [automated-admin-user]]
   [pallet.crate.environment :only [system-environment]]
   [pallet.monad :only [phase-pipeline]]
   [pallet.phase :only [phase-fn]]
   [pallet.session :only [is-64bit?]]))

(use-fixtures :once with-ubuntu-script-template)

(def pkg-config
  (phase-pipeline pkg-config {}
    (package-manager :universe)
    (package-manager :multiverse)
    (package-manager :update)))

(def noninteractive
  (script/with-script-context [:ubuntu]
    (stevedore/with-script-language :pallet.stevedore.bash/bash
      (stevedore/script (package-manager-non-interactive)))))

(defn debconf [pkg]
  (package-manager
   :debconf
   (str pkg " shared/present-sun-dlj-v1-1 note")
   (str pkg " shared/accepted-sun-dlj-v1-1 boolean true")))

;; (deftest java-default-test
;;   (is (= (first
;;           (build-actions
;;            {}
;;            (package-source
;;             "Partner"
;;             :aptitude {:url ubuntu-partner-url
;;                        :scopes ["partner"]})
;;            pkg-config
;;            (package-manager :update)
;;            (debconf "sun-java6-bin")
;;            (package "sun-java6-bin")
;;            (debconf "sun-java6-jdk")
;;            (package "sun-java6-jdk")
;;            (system-environment
;;             "java"
;;             {"JAVA_HOME" (stevedore/script (~jdk-home))})))
;;          (first
;;           (build-actions
;;            {}
;;            (java-settings {})
;;            (install-java))))))

;; (deftest java-sun-test
;;   (is (= (first
;;           (build-actions
;;            {}
;;            (package-source
;;             "Partner"
;;             :aptitude {:url ubuntu-partner-url
;;                        :scopes ["partner"]})
;;            pkg-config
;;            (package-manager :update)
;;            (debconf "sun-java6-bin")
;;            (package "sun-java6-bin")
;;            (debconf "sun-java6-jdk")
;;            (package "sun-java6-jdk")
;;            (system-environment
;;             "java"
;;             {"JAVA_HOME" (stevedore/script (~jdk-home))})))
;;          (first
;;           (build-actions
;;            {}
;;            (java-settings {:vendor :sun :components #{:bin :jdk}})
;;            (install-java))))))

(deftest java-openjdk-test
  (is (= (first
          (build-actions
              {:phase-context "install-java"}
            (phase-pipeline package-install {}
              (package "openjdk-6-jre"))
            (system-environment
             "java"
             {"JAVA_HOME" (stevedore/script (~java-home))})))
         (first
          (build-actions
              {}
            (java-settings {:vendor :openjdk :components #{:jre}})
            (install-java)))))
  (is (= (first
          (build-actions
              {:phase-context "install-java"
               :server {:image {:os-family :arch}}}
            (phase-pipeline package-install {}
              (package "openjdk6"))
            (system-environment
             "java"
             {"JAVA_HOME" (stevedore/script (~java-home))})))
         (first
          (build-actions
              {:server {:image {:os-family :arch}}}
            (java-settings {:vendor :openjdk :components #{:jre}})
            (install-java))))))


(deftest invoke-test
  (is
   (build-actions
    {}
    (java-settings {:vendor :openjdk :components #{:jdk}})
    (install-java)
    (jce-policy-file "f" :content ""))))

(def rh [{:os-family :centos} {:os-family :fedora} {:os-family :rhel}])

(deftest live-test
  (live-test/test-for
   [image (live-test/exclude-images (live-test/images) rh)]
   (live-test/test-nodes
    [compute node-map node-types]
    {:java
     {:image image
      :count 1
      :phases {:bootstrap (phase-fn
                            (minimal-packages)
                            ;;(package-manager :update)
                            (automated-admin-user))
               :settings (java-settings {:vendor :openjdk})
               :configure (install-java)
               :verify (phase-fn
                         (exec-checked-script
                          "check java installed"
                          ("java" -version))
                         (exec-checked-script
                          "check java installed under java home"
                          ("test" (file-exists? (str (~java-home) "/bin/java"))))
                         (exec-checked-script
                          "check javac installed under jdk home"
                          ("test" (file-exists? (str (~jdk-home) "/bin/javac"))))
                         (exec-checked-script
                          "check JAVA_HOME set to jdk home"
                          (source "/etc/environment")
                          ("test" (= (~jdk-home) @JAVA_HOME))))}}}
    (core/lift (val (first node-types)) :phase :verify :compute compute))))

;; To run this test you will need to download the Oracle Java rpm downloads in
;; the artifacts directory.
(deftest centos-live-test
  (live-test/test-for
   [image (live-test/filter-images (live-test/images) rh)]
   (logging/infof "testing %s" (pr-str image))
   (live-test/test-nodes
    [compute node-map node-types]
    {:java
     {:image image
      :count 1
      :phases
      {:bootstrap (phase-fn
                    (minimal-packages)
                    (package-manager :update)
                    (automated-admin-user))
       :settings (phase-fn
                   [is-64bit (session/is-64bit?)]
                   ;;(rpm-bin-file file :local-file (str "artifacts/" file))
                   (java-settings {:vendor :sun
                                   :rpm (str
                                         "./"
                                         (if is-64bit
                                           "jdk-6u23-linux-x64-rpm.bin"
                                           "jdk-6u24-linux-i586-rpm.bin"))}))
       :configure (install-java)
       :verify (phase-fn
                 (exec-checked-script
                  "check java installed"
                  ("java" -version))
                 (exec-checked-script
                  "check java installed under java home"
                  ("test" (file-exists? (str (~java-home) "/bin/java"))))
                 (exec-checked-script
                  "check javac installed under jdk home"
                  ("test" (file-exists? (str (~jdk-home) "/bin/javac"))))
                 (exec-checked-script
                  "check JAVA_HOME set to jdk home"
                  (source "/etc/profile.d/java.sh")
                  ("test" (= (~jdk-home) @JAVA_HOME))))}}}
    (core/lift (val (first node-types)) :phase :verify :compute compute))))
