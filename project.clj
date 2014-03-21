(defproject com.palletops/java-crate "0.8.0-SNAPSHOT"
  :description "Pallet crate to install, configure and use java"
  :url "http://palletops.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:url "git@github.com:pallet/java-crate.git"}

  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.palletops/pallet "0.8.0-RC.9"]
                 [org.toomuchcode/clara-rules "0.5.0-SNAPSHOT"]
                 ;; [prismatic/schema "0.2.1"]
                 ]
  :resource {:resource-paths ["doc-src"]
             :target-path "target/classes/pallet_crate/java_crate/"
             :includes [#"doc-src/USAGE.*"]}
  :prep-tasks ["resource" "crate-doc"])
