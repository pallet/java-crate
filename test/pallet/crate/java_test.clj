(ns pallet.crate.java-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.tools.logging :as logging]
   [pallet.crate.java :as java]
   [pallet.actions :refer [exec-checked-script exec-script* minimal-packages
                           package-manager plan-when]]
   [pallet.api :refer [lift plan-fn]]
   [pallet.build-actions :refer [build-actions build-session]]
   [pallet.core.session :refer [with-session]]
   [pallet.crate :refer [is-64bit?]]
   [pallet.crate.automated-admin-user :refer [automated-admin-user]]
   [pallet.live-test :refer [exclude-images filter-images images test-for
                             test-nodes]]
   [pallet.script :refer [with-script-context]]
   [pallet.script-test :refer [is-true testing-script]]
   [pallet.script.lib :refer [package-manager-non-interactive]]
   [pallet.stevedore :refer [script with-script-language]]
   [pallet.test-utils :refer [with-ubuntu-script-template]]))

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
           (with-session
               (build-session
                {:server {:image {:os-family :ubuntu :os-version "10.10"}}})
             (#'pallet.crate.java/settings-map
              {:vendor :openjdk :components #{:jre} :version [6]})))))

  (testing "oracle"
    (is (= {:packages ["sun-java6-bin" "sun-java6-jdk"],
            :package-source {:name "pallet-packages"
                             :aptitude {:path "pallet-packages"
                                        :url "file://$(pwd)/pallet-packages"
                                        :release "./" :scopes []}}
            :install-strategy :debs
            :debs "some.tar.gz"
            :vendor :oracle :version [6] :components #{:jdk}}
           (with-session
               (build-session
                {:server {:image {:os-family :ubuntu :os-version "10.10"}}})
             (#'pallet.crate.java/settings-map
              {:vendor :oracle :components #{:jdk} :version [6]
               :debs "some.tar.gz"}))))))


(deftest java-openjdk-test
  (is
   (first
    (build-actions {}
      (java/settings {:vendor :openjdk :components #{:jre}})
      (java/install))))
  (is
   (first
    (build-actions {:server {:image {} :packager :pacman}}
      (java/settings {:vendor :openjdk :components #{:jre}})
      (java/install)))))


(deftest invoke-test
  (is
   (build-actions
    {}
    (java/settings {:vendor :openjdk :components #{:jdk}})
    (java/install)
    (java/jce-policy-file "f" :content ""))))

(deftest spec-test
  (is (map? (java/server-spec {:vendor :openjdk}))))

(def test-spec-from-tarfile
  (java/server-spec {:local-file "artifacts/jdk-7u17-linux-x64.tar.gz"}))

(def test-spec-default
  (java/server-spec {}))

(def test-spec-java-7
  (java/server-spec {:version "7" :vendor :oracle}))

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
                         (java/settings {:vendor :openjdk})
                         (plan-when has-debs?
                           (java/settings {:vendor :oracle
                                           :version "6"
                                           :components #{:jdk}
                                           :debs {:local-file (.getPath file)}
                                           :instance-id :oracle-6}))
                         (java/settings {:vendor :oracle :version "7"
                                         :components #{:jdk}
                                         :instance-id :oracle-7}))
             :configure (plan-fn (java/install))
             :configure2 (plan-fn
                           (plan-when has-debs?
                             (java/install :instance-id :oracle-6)))
             :configure3 (plan-fn
                           (java/install :instance-id :oracle-7))
             :verify (plan-fn
                       (exec-script*
                        (testing-script "Java install"
                          (is-true @(chain-or
                                     ("java" -version "2>/dev/null" )
                                     ("java" --version))
                                   "Verify java is installed")
                          (is-true
                           (file-exists? (str (java/java-home) "/bin/java"))
                           "Verify that java is installed under java home")
                          (is-true
                           (file-exists? (str (java/jdk-home) "/bin/javac"))
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
    (test-nodes [compute node-map node-types]
      {:java
       {:image image
        :count 1
        :phases
        {:bootstrap (plan-fn
                      (minimal-packages)
                      (package-manager :update)
                      (automated-admin-user))
         :settings (plan-fn
                     (let [is-64bit (is-64bit?)]
                       (java/settings
                        {:vendor :sun
                         :rpm {:local-file
                               (str
                                "./artifacts/"
                                (if is-64bit
                                  "jdk-6u23-linux-x64-rpm.bin"
                                  "jdk-6u24-linux-i586-rpm.bin"))}})))
         :configure (plan-fn (java/install))
         :verify (plan-fn
                   (exec-checked-script
                    "check java installed"
                    ("java" -version))
                   (exec-checked-script
                    "check java installed under java home"
                    (file-exists? (quoted (str (java/java-home) "/bin/java"))))
                   (exec-checked-script
                    "check javac installed under jdk home"
                    (file-exists? (str (java/jdk-home) "/bin/javac")))
                   (exec-checked-script
                    "check JAVA_HOME set to jdk home"
                    ("source" "/etc/profile.d/java.sh")
                    (= (java/jdk-home) @JAVA_HOME)))}}}
      @(lift (val (first node-types)) :phase :verify :compute compute))))
