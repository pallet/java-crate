;;; Pallet project configuration file

(require
 '[pallet.crate.java-test :refer [test-spec-java-7 test-spec-from-tarfile]]
 '[pallet.crates.test-nodes :refer [node-specs]])

(defproject lein-crate
  :provider node-specs                  ; supported pallet nodes
  :groups [(group-spec "java-from-tarfile"
                       :extends [with-automated-admin-user
                                 test-spec-from-tarfile]
                       :roles #{:live-test :tarfile})
           (group-spec "java-7"
                       :extends [with-automated-admin-user
                                 test-spec-java-7]
                       :roles #{:live-test :java-7})])
