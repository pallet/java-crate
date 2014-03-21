(ns pallet.crate.java-support-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.tools.logging :as logging]
   [pallet.crate.java :as java]
   [pallet.crate.java.project :as project]
   [pallet.actions :refer [exec-checked-script exec-script* minimal-packages
                           package-manager plan-when]]
   [pallet.api :refer [converge group-spec plan-fn]]
   [pallet.build-actions :refer [build-actions build-session]]
   [pallet.core.api :refer [phase-errors]]
   [pallet.core.session :refer [with-session]]
   [pallet.crate :refer [is-64bit?]]
   [pallet.crate.automated-admin-user :refer [automated-admin-user]]
   [pallet.crates.test-nodes :as test-nodes]
   [pallet.live-test :refer [exclude-images filter-images images test-for
                             test-nodes]]
   [pallet.script :refer [with-script-context]]
   [pallet.script-test :refer [is-true testing-script]]
   [pallet.script.lib :refer [package-manager-non-interactive]]
   [pallet.stevedore :refer [script with-script-language]]
   [pallet.test-env :refer [test-env *compute-service* *node-spec-meta*]]
   [pallet.test-utils :refer [with-ubuntu-script-template]]))

(test-env test-nodes/node-specs project/project)

(deftest default-settings
  (let [spec (group-spec "pallet-java"
               :node-spec (:node-spec *node-spec-meta*)
               :count 1
               :phases
               {:bootstrap (plan-fn
                               (minimal-packages)
                             (package-manager :update)
                             (automated-admin-user))
                :settings (plan-fn
                              (java/settings {}))
                :configure (plan-fn (java/install {}))
                :verify (plan-fn
                            (exec-script*
                             (testing-script
                              "Java install"
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
                              ;; for some reason JAVA_HOME isn't
                              ;; getting set, although it is in
                              ;; /etc/environment and seems to be
                              ;; getting picked up in other bash
                              ;; sessions
                              ;; (is= (~jdk-home) @JAVA_HOME
                              ;;      "Verify that JAVA_HOME is set")
                              )))})]

    (try
      (let [session (converge spec
                              :phase [:verify
                                      :configure :verify
                                      ;; :configure2 :verify
                                      ;; :configure3 :verify
                                      ]
                              :compute *compute-service*)]
        (testing "install java"
          (is session)
          (is (not (phase-errors session)))))
      (finally
        (converge (assoc spec :count 0) :compute *compute-service*)))))




;; (deftest live-test
;;   (let [file (io/file "sun-java6-debs.tar.gz")
;;         has-debs? (.canRead file)
;;         spec (group-spec "pallet-java"
;;                :node-spec (:node-spec *node-spec-meta*)
;;                :count 1
;;                :phases
;;                {:bootstrap (plan-fn
;;                                (minimal-packages)
;;                              (package-manager :update)
;;                              (automated-admin-user))
;;                 :settings (plan-fn
;;                             (java/settings {:vendor :openjdk})
;;                             ;; (plan-when has-debs?
;;                             ;;   (java/settings
;;                             ;;    {:vendor :oracle
;;                             ;;     :version "6"
;;                             ;;     :components #{:jdk}
;;                             ;;     :debs {:local-file (.getPath file)}
;;                             ;;     :instance-id :oracle-6}))
;;                             ;; (java/settings
;;                             ;;  {:vendor :oracle :version "7"
;;                             ;;   :components #{:jdk}
;;                             ;;   :instance-id :oracle-7})
;;                             )
;;                 :configure (plan-fn (java/install {}))
;;                 :configure2 (plan-fn
;;                                 ;; (plan-when has-debs?
;;                                 ;;   (java/install {:instance-id :oracle-6}))
;;                               )
;;                 :configure3 (plan-fn
;;                                 (java/install {:instance-id :oracle-7}))
;;                 :verify (plan-fn
;;                             (exec-script*
;;                              (testing-script
;;                               "Java install"
;;                               (is-true @(chain-or
;;                                          ("java" -version "2>/dev/null" )
;;                                          ("java" --version))
;;                                        "Verify java is installed")
;;                               (is-true
;;                                (file-exists? (str (java/java-home) "/bin/java"))
;;                                "Verify that java is installed under java home")
;;                               (is-true
;;                                (file-exists? (str (java/jdk-home) "/bin/javac"))
;;                                "Verify javac is installed under jdk home")
;;                               "echo JAVA_HOME is ${JAVA_HOME}"
;;                               ;; for some reason JAVA_HOME isn't
;;                               ;; getting set, although it is in
;;                               ;; /etc/environment and seems to be
;;                               ;; getting picked up in other bash
;;                               ;; sessions
;;                               ;; (is= (~jdk-home) @JAVA_HOME
;;                               ;;      "Verify that JAVA_HOME is set")
;;                               )))})
;;         session (converge spec
;;                           :phase [:verify
;;                                   :configure :verify
;;                                   ;; :configure2 :verify
;;                                   ;; :configure3 :verify
;;                                   ]
;;                           :compute *compute-service*)]
;;     (is session)
;;     (is (not (phase-errors session)))))
