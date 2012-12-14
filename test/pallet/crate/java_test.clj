(ns pallet.crate.java-test
  (:use pallet.crate.java)
  (:require
   [clojure.java.io :as io]
   [clojure.tools.logging :as logging]
   [pallet.core :as core]
   [pallet.live-test :as live-test]
   [pallet.script :as script]
   [pallet.stevedore :as stevedore]
   [pallet.utils :as utils])
  (:use
   clojure.test
   pallet.test-utils
   [pallet.api :only [lift plan-fn]]
   [pallet.build-actions :only [build-actions build-session]]
   [pallet.actions
    :only [exec-script exec-script* exec-checked-script file minimal-packages
           package package-manager package-source remote-file
           pipeline-when]]
   [pallet.context :only [with-phase-context]]
   [pallet.crate :only [is-64bit?]]
   [pallet.crate.automated-admin-user :only [automated-admin-user]]
   [pallet.crate.environment :only [system-environment]]
   [pallet.live-test
    :only [exclude-images filter-images images test-for test-nodes]]
   [pallet.monad :only [chain-s phase-pipeline wrap-pipeline]]
   [pallet.script :only [with-script-context]]
   [pallet.script-test :only [is= is-true testing-script]]
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

(deftest settings-map-test
  (testing "openjdk"
    (is (= {:packages ["openjdk-6-jre"], :install-strategy :packages
            :vendor :openjdk :version [6] :components #{:jre}}
           (->
            ((#'pallet.crate.java/settings-map
              {:vendor :openjdk :components #{:jre} :version [6]})
             (build-session
              {:server {:image {:os-family :ubuntu :os-version "10.10"}}}))
            first))))

  (testing "oracle"
    (is (= {:packages ["sun-java6-bin" "sun-java6-jdk"],
            :package-source {:name "pallet-packages"
                             :aptitude {:path "pallet-packages"
                                        :url "file://$(pwd)/pallet-packages"
                                        :release "./" :scopes []}}
            :install-strategy :debs
            :debs "some.tar.gz"
            :vendor :oracle :version [6] :components #{:jdk}}
           (->
            ((#'pallet.crate.java/settings-map
              {:vendor :oracle :components #{:jdk} :version [6]
               :debs "some.tar.gz"})
             (build-session
              {:server {:image {:os-family :ubuntu :os-version "10.10"}}}))
            first)))))

(comment
  ;; TODO fix these tests
  (deftest java-openjdk-test
    (is (= (first
            (with-phase-context {:msg "install-java" :kw :install-java}
              (build-actions {}
                (phase-pipeline install {}
                  (package "openjdk-6-jre"))
                (exec-script "")        ; an extra newline, for some reason
                (phase-pipeline set-environment {}
                  (wrap-pipeline p-when
                    (with-phase-context
                      {:msg "pipeline-when" :kw :pipeline-when})
                    (system-environment
                     "java"
                     {"JAVA_HOME" (script (~java-home))}))))))
           (first
            (build-actions {}
              (java-settings {:vendor :openjdk :components #{:jre}})
              (install-java)))))
    (is (= (first
            (with-phase-context {:msg "install-java" :kw :install-java}
              (build-actions {:server {:image {} :packager :pacman}}
                (phase-pipeline install {}
                  (package "openjdk-6-jre"))
                ;; (exec-script "") ; an extra newline, for some reason
                (phase-pipeline set-environment {}
                  (wrap-pipeline p-when
                    (with-phase-context
                      {:msg "pipeline-when" :kw :pipeline-when})
                    (system-environment
                     "java"
                     {"JAVA_HOME" (script (~java-home))}))))))
           (first
            (build-actions {:server {:image {} :packager :pacman}}
              (java-settings {:vendor :openjdk :components #{:jre}})
              (install-java)))))))


(deftest invoke-test
  (is
   (build-actions
    {}
    (java-settings {:vendor :openjdk :components #{:jdk}})
    (install-java)
    (jce-policy-file "f" :content ""))))

(deftest spec-test
  (is (map? (java {:vendor :openjdk}))))

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
            {:bootstrap (plan-fn
                          (minimal-packages)
                          (package-manager :update)
                          (automated-admin-user))
             :settings (plan-fn
                         (java-settings {:vendor :openjdk})
                         (pipeline-when has-debs?
                           (java-settings {:vendor :oracle
                                           :version "6"
                                           :components #{:jdk}
                                           :debs {:local-file (.getPath file)}
                                           :instance-id :oracle-6}))
                         (java-settings {:vendor :oracle :version "7"
                                         :components #{:jdk}
                                         :instance-id :oracle-7}))
             :configure (install-java)
             :configure2 (plan-fn
                           (pipeline-when has-debs?
                             (install-java :instance-id :oracle-6)))
             :configure3 (plan-fn
                           (install-java :instance-id :oracle-7))
             :verify (plan-fn
                       (exec-script*
                        (testing-script "Java install"
                          (is-true @(chain-or
                                     ("java" -version "2>/dev/null" )
                                     ("java" --version))
                                   "Verify java is installed")
                          (is-true
                           (file-exists? (str (~java-home) "/bin/java"))
                           "Verify that java is installed under java home")
                          (is-true
                           (file-exists? (str (~jdk-home) "/bin/javac"))
                           "Verify javac is installed under jdk home")
                          "echo JAVA_HOME is ${JAVA_HOME}"
                          ;; for some reason JAVA_HOME isn't getting set,
                          ;; although it is in /etc/environment and seems to be
                          ;; getting picked up in other bash sessions
                          ;; (is= (~jdk-home) @JAVA_HOME
                          ;;      "Verify that JAVA_HOME is set")
                          )))}}}
          @(lift (val (first node-types))
              :phase [:verify :configure2 :verify :configure3 :verify]
              :compute compute)))))

;; To run this test you will need to download the Oracle Java rpm downloads in
;; the artifacts directory.
(deftest centos-live-test
  (test-for [image (filter-images (images) rh)]
    (logging/infof "testing %s" (pr-str image))
    (test-nodes
        [compute node-map node-types]
        {:java
         {:image image
          :count 1
          :phases
          {:bootstrap (plan-fn
                        (minimal-packages)
                        (package-manager :update)
                        (automated-admin-user))
           :settings (plan-fn
                       [is-64bit (is-64bit?)]
                       (java-settings
                        {:vendor :sun
                         :rpm {:local-file
                               (str
                                "./artifacts/"
                                (if is-64bit
                                  "jdk-6u23-linux-x64-rpm.bin"
                                  "jdk-6u24-linux-i586-rpm.bin"))}}))
           :configure (install-java)
           :verify (plan-fn
                     (exec-checked-script
                      "check java installed"
                      ("java" -version))
                     (exec-checked-script
                      "check java installed under java home"
                      (file-exists? (quoted (str (~java-home) "/bin/java"))))
                     (exec-checked-script
                      "check javac installed under jdk home"
                      (file-exists? (str (~jdk-home) "/bin/javac")))
                     (exec-checked-script
                      "check JAVA_HOME set to jdk home"
                      (source "/etc/profile.d/java.sh")
                      (= (~jdk-home) @JAVA_HOME)))}}}
        @(lift (val (first node-types)) :phase :verify :compute compute))))
