(ns pallet.crate.java-test
  (:use pallet.crate.java)
  (:require
   [pallet.script :as script]
   [pallet.session :as session]
   [pallet.thread-expr :as thread-expr]
   [pallet.utils :as utils]
   [clojure.tools.logging :as logging])
  (:use
   clojure.test
   pallet.test-utils
   [pallet.action.environment :only [system-environment]]
   [pallet.action.exec-script :only [exec-script exec-checked-script]]
   [pallet.action.file :only [file]]
   [pallet.action.package
    :only [minimal-packages package package-manager package-source]]
   [pallet.action.package.jpackage :only [add-jpackage]]
   [pallet.action.remote-file :only [remote-file]]
   [pallet.core :only [lift]]
   [pallet.build-actions :only [build-actions]]
   [pallet.crate.automated-admin-user :only [automated-admin-user]]
   [pallet.live-test
    :only [exclude-images filter-images images test-for test-nodes]]
   [pallet.phase :only [phase-fn]]
   [pallet.script :only [with-script-context]]
   [pallet.stevedore :only [script with-script-language]]))

(use-fixtures :once with-ubuntu-script-template)

(defn pkg-config [session]
  (-> session
      (package-manager :universe)
      (package-manager :multiverse)
      (package-manager :update)))

(def noninteractive
  (with-script-context [:ubuntu]
    (with-script-language :pallet.stevedore.bash/bash
      (script (package-manager-non-interactive)))))

;; (defn debconf [session pkg]
;;   (package/package-manager
;;    session
;;    :debconf
;;    (str pkg " shared/present-sun-dlj-v1-1 note")
;;    (str pkg " shared/accepted-sun-dlj-v1-1 boolean true")))

;; (deftest java-default-test
;;   (is (= (first
;;           (build-actions/build-actions
;;            {}
;;            (package/package-source
;;             "Partner"
;;             :aptitude {:url ubuntu-partner-url
;;                        :scopes ["partner"]})
;;            (pkg-config)
;;            (package/package-manager :update)
;;            (debconf "sun-java6-bin")
;;            (package/package "sun-java6-bin")
;;            (debconf "sun-java6-jdk")
;;            (package/package "sun-java6-jdk")
;;            (environment/system-environment
;;             "java"
;;             {"JAVA_HOME" (stevedore/script (~jdk-home))})))
;;          (first
;;           (build-actions/build-actions
;;            {}
;;            (java))))))

;; (deftest java-sun-test
;;   (is (= (first
;;           (build-actions/build-actions
;;            {}
;;            (package/package-source
;;             "Partner"
;;             :aptitude {:url ubuntu-partner-url
;;                        :scopes ["partner"]})
;;            (pkg-config)
;;            (package/package-manager :update)
;;            (debconf "sun-java6-bin")
;;            (package/package "sun-java6-bin")
;;            (debconf "sun-java6-jdk")
;;            (package/package "sun-java6-jdk")
;;            (environment/system-environment
;;             "java"
;;             {"JAVA_HOME" (stevedore/script (~jdk-home))})))
;;          (first
;;           (build-actions/build-actions
;;            {}
;;            (java :sun :bin :jdk))))))

(deftest settings-map-test
  (is (= {:packages ["openjdk-6-jre"], :strategy :package
          :vendor :openjdk :version [6] :components #{:jre}}
         (#'pallet.crate.java/settings-map
          {:server {:image {:os-family :ubuntu :os-version "10.10"}}}
          {:vendor :openjdk :components #{:jre}}))))

(deftest java-openjdk-test
  (is (= (first
          (build-actions {}
            (package "openjdk-6-jre")
            (system-environment
             "java"
             {"JAVA_HOME" (script (~java-home))})))
         (first
          (build-actions {}
            (java-settings {:vendor :openjdk :components #{:jre}})
            (install-java)))))
  (is (= (first
          (build-actions {:server {:image {} :packager :pacman}}
            (package "openjdk-6-jre")
            (system-environment
             "java"
             {"JAVA_HOME" (script (~java-home))})))
         (first
          (build-actions {:server {:image {} :packager :pacman}}
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
  (test-for
      [image (exclude-images (images) rh)]
    (test-nodes
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
                       (java-settings {:vendor :openjdk}))
           :configure install-java
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
      (lift (val (first node-types)) :phase :verify :compute compute))))

;; To run this test you will need to download the Oracle Java rpm downloads in
;; the artifacts directory.
(deftest centos-live-test
  (test-for
   [image (filter-images (images) rh)]
   (logging/infof "testing %s" (pr-str image))
   (test-nodes
    [compute node-map node-types]
    {:java
     {:image image
      :count 1
      :phases
      {:bootstrap (phase-fn
                   (minimal-packages)
                   (package-manager :update)
                   (automated-admin-user))
       :settings (fn [session]
                   (let [is-64bit (session/is-64bit? session)]
                     (java-settings {:vendor :sun
                                     :rpm (str
                                           "./"
                                           (if is-64bit
                                             "jdk-6u23-linux-x64-rpm.bin"
                                             "jdk-6u24-linux-i586-rpm.bin"))})))
       :configure install-java
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
    (lift (val (first node-types)) :phase :verify :compute compute))))
