(ns pallet.crate.java-test
  (:use pallet.crate.java)
  (:require
   [pallet.script :as script]
   [pallet.session :as session]
   [pallet.utils :as utils]
   [clojure.tools.logging :as logging]
   [clojure.java.io :as io])
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
   [pallet.stevedore :only [script with-script-language]]
   [pallet.thread-expr :only [when->]]))

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

(deftest settings-map-test
  (testing "openjdk"
    (is (= {:packages ["openjdk-6-jre"], :strategy :package
            :vendor :openjdk :version [6] :components #{:jre}}
           (#'pallet.crate.java/settings-map
            {:server {:image {:os-family :ubuntu :os-version "10.10"}}}
            {:vendor :openjdk :components #{:jre}}))))

  (testing "oracle"
    (is (= {:packages ["sun-java6-bin" "sun-java6-jdk"],
            :package-source {:name "pallet-packages"
                             :aptitude {:path "pallet-packages"
                                        :url "file://$(pwd)/pallet-packages"}}
            :strategy :debs
            :debs "some.tar.gz"
            :vendor :oracle :version [6] :components #{:jdk}}
           (#'pallet.crate.java/settings-map
            {:server {:image {:os-family :ubuntu :os-version "10.10"}}}
            {:vendor :oracle :components #{:jdk}
             :debs "some.tar.gz"})))))

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
  (let [file (io/file "sun-java6-debs.tar.gz")
        has-debs? (.canRead file)]
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
                         (java-settings {:vendor :openjdk})
                         (when-> has-debs?
                             (java-settings {:vendor :oracle
                                             :version "6"
                                             :components #{:jdk}
                                             :debs {:local-file (.getPath file)}
                                             :instance-id :oracle-6}))
                         (java-settings {:vendor :oracle :version "7"
                                         :components #{:jdk}
                                         :instance-id :oracle-7}))
             :configure install-java
             :configure2 (phase-fn
                           (when-> has-debs?
                               (install-java :instance-id :oracle-6)))
             :configure3 (phase-fn
                           (install-java :instance-id :oracle-7))
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
                        (println "jdk home is" (~jdk-home))
                        (println "JAVA_HOME is" @JAVA_HOME)
                        ("test" (= (~jdk-home) @JAVA_HOME))))
             :verify2 (phase-fn
                        (when-> has-debs?
                            (exec-checked-script
                             "check java installed"
                             ("java" -version))
                          (exec-checked-script
                           "check java installed under java home"
                           ("test"
                            (file-exists? (str (~java-home) "/bin/java"))))
                          (exec-checked-script
                           "check javac installed under jdk home"
                           ("test"
                            (file-exists? (str (~jdk-home) "/bin/javac"))))
                          (exec-checked-script
                           "check JAVA_HOME set to jdk home"
                           (source "/etc/environment")
                           (println "jdk home is" (~jdk-home))
                           (println "JAVA_HOME is" @JAVA_HOME)
                           ("test" (= (~jdk-home) @JAVA_HOME)))))}}}
        (lift (val (first node-types))
              :phase [:verify :configure2 :verify2 :configure3]
              :compute compute)))))

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
