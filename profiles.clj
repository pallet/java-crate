{:dev {:dependencies
       [[org.clojure/clojure "1.5.1"]
        [com.palletops/pallet "0.8.0-RC.9" :classifier "tests"]
        [com.palletops/pallet-test-env "0.8.0-SNAPSHOT"]
        [com.palletops/crates "0.1.2-SNAPSHOT"]
        [ch.qos.logback/logback-classic "1.0.9"]]
       :plugins [[lein-set-version "0.3.0"]
                 [lein-resource "0.3.2" :exclusions [stencil]]
                 [com.palletops/lein-pallet-crate "0.1.0"]
                 [com.palletops/pallet-lein "0.8.0-alpha.1"]
                 [codox/codox.leiningen "0.6.4"]
                 [lein-marginalia "0.7.1"]
                 [configleaf "0.4.6"]]
       :test-selectors {:default (complement :support)
                        :support :support
                        :all (constantly true)}
       :aliases {"live-test-up"
                 ["pallet" "up" "--phases" "settings,configure,test"]
                 "live-test-down" ["pallet" "down"]
                 "live-test" ["do" "live-test-up," "live-test-down"]}
       :configleaf {:config-source-path "test"
                    :namespace pallet.crate.java.project
                    :verbose true}
       :hooks [configleaf.hooks]}
 :latest {:dependencies
          [[com.palletops/pallet "0.8.0-SNAPSHOT"]
           [com.palletops/pallet "0.8.0-SNAPSHOT" :classifier "tests"]]}
 :doc {:dependencies [[codox-md "0.2.0"]]
       :codox {:writer codox-md.writer/write-docs
               :output-dir "doc/0.8/api"
               :src-dir-uri "https://github.com/pallet/java-crate/blob/develop"
               :src-linenum-anchor-prefix "L"}
       :aliases {"marg" ["marg" "-d" "doc/0.8/annotated"]
                 "codox" ["doc"]
                 "doc" ["do" "codox," "marg"]}}
 :release
 {:set-version
  {:updates [{:path "README.md" :no-snapshot true}
             {:path "resources/pallet_crate/java_crate/meta.edn"
              :no-snapshot true}]}}
 :no-checkouts {:checkout-shares ^:replace []}
 :jclouds {:dependencies [[com.palletops/pallet-jclouds "1.7.0-alpha.2"]
                          [org.apache.jclouds.driver/jclouds-slf4j "1.7.1"
                           :exclusions [org.slf4j/slf4j-api]]
                          [org.apache.jclouds.driver/jclouds-sshj "1.7.1"]]
           :pallet/test-env {:service :aws
                             :test-specs
                             [{:selector :ubuntu-13-04}
                              {:selector :ubuntu-13-10}]}}
 :aws {:dependencies [[com.palletops/pallet-aws "0.2.0"]
                      [ch.qos.logback/logback-classic "1.1.1"]
                      [org.slf4j/jcl-over-slf4j "1.7.6"]]
       :pallet/test-env {:service :ec2
                         :test-specs
                         [{:selector :ubuntu-13-10}
                          {:selector :ubuntu-13-04
                           :expected [{:feature ["oracle-java-8"]
                                       :expected? :not-supported}]}]}}
 :vmfest {:dependencies [[com.palletops/pallet-vmfest "0.3.0-RC.1"]]
          :pallet/test-env {:service :vmfest
                            :test-specs
                            [{:selector :ubuntu-13-04
                              :expected [{:feature ["oracle-java-8"]
                                          :expected? :not-supported}]}]}}}
