{:dev {:dependencies
       [[com.palletops/pallet "0.8.0-beta.1" :classifier "tests"]
        [ch.qos.logback/logback-classic "1.0.9"]]
       :plugins [[lein-set-version "0.3.0"]]}
 :doc {:dependencies [[codox-md "0.2.0"]]
       :codox {:writer codox-md.writer/write-docs
               :output-dir "doc/api/0.8"
               :src-dir-uri "https://github.com/pallet/java-crate/blob/develop"
               :src-linenum-anchor-prefix "L"}
       :aliases {"marg" ["marg" "-d" "doc/annotated/0.8/"]
                 "codox" ["doc"]
                 "doc" ["do" "codox," "marg"]}}
 :release
 {:set-version
  {:updates [{:path "README.md" :no-snapshot true}]}}}
