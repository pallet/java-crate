{:dev {:dependencies
       [[com.palletops/pallet "0.8.0-RC.4" :classifier "tests"]
        [com.palletops/crates "0.1.0"]
        [ch.qos.logback/logback-classic "1.0.9"]]
       :plugins [[lein-pallet-release "RELEASE"]
                 [lein-resource "0.3.2" :exclusions [stencil]]
                 [com.palletops/lein-pallet-crate "0.1.0"]
                 [codox/codox.leiningen "0.6.4"]
                 [lein-marginalia "0.7.1"]]
       :aliases {"live-test-up"
                 ["pallet" "up" "--phases" "settings,configure,test"]
                 "live-test-down" ["pallet" "down"]
                 "live-test" ["do" "live-test-up," "live-test-down"]}}
 :latest {:dependencies
          [[com.palletops/pallet "0.8.0-SNAPSHOT"]
           [com.palletops/pallet "0.8.0-SNAPSHOT" :classifier "tests"]]}
 :release
 {:set-version
  {:updates [{:path "README.md" :no-snapshot true}
             {:path "resources/pallet_crate/java_crate/meta.edn"
              :no-snapshot true}]}}
 :no-checkouts {:checkout-shares ^:replace []}
 :jclouds {:repositories
           {"sonatype"
            "https://oss.sonatype.org/content/repositories/releases/"}
           :dependencies [[org.cloudhoist/pallet-jclouds "1.5.2"]
                          [org.jclouds/jclouds-allblobstore "1.5.5"]
                          [org.jclouds/jclouds-allcompute "1.5.5"]
                          [org.jclouds.driver/jclouds-slf4j "1.5.5"
                           :exclusions [org.slf4j/slf4j-api]]
                          [org.jclouds.driver/jclouds-sshj "1.5.5"]]}
 :vmfest {:dependencies [[com.palletops/pallet-vmfest "0.3.0-SNAPSHOT"]]}}
