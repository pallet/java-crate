{:dev {:dependencies
       [[com.palletops/pallet "0.8.0-beta.1" :classifier "tests"]
        [com.palletops/crates "0.8.0-SNAPSHOT"]
        [ch.qos.logback/logback-classic "1.0.9"]]
       :plugins [[lein-set-version "0.3.0"]
                 [lein-resource "0.3.2"]]
       :aliases {"live-test-up"
                 ["pallet" "up" "--phases" "settings,configure,test"]
                 "live-test-down" ["pallet" "down"]
                 "live-test" ["do" "live-test-up," "live-test-down"]}}
 :latest {:dependencies
          [[com.palletops/pallet "0.8.0-beta.3"]
           [com.palletops/pallet "0.8.0-beta.3" :classifier "tests"]]}
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
  {:updates [{:path "README.md" :no-snapshot true}]}}}
