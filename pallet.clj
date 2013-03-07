;;; Pallet project configuration file

(require
 '[pallet.crate.java-test :refer [test-spec-from-tarfile]]
 '[pallet.crates.test-nodes :refer [node-specs]])

(defproject lein-crate
  :provider node-specs                  ; supported pallet nodes
  :groups [(group-spec "java-live-test"
                       :extends [with-automated-admin-user
                                 test-spec-from-tarfile])])
