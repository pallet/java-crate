(ns pallet.crate.java.kb-test
  (:require
   [clojure.test :refer :all]
   [pallet.crate.java.kb :refer [os os-families
                                 default-target
                                 install-strategy]]))

(deftest os-test
  (is (= [:ubuntu :debian-base :linux]
         (map :os-family
              (os-families (os {:os-family :ubuntu :os-version [13 10]}))))))

(deftest install-strategy-test
  (is (seq (install-strategy
            (default-target
              (os {:os-family :ubuntu :os-version [13 10]})
              {:version [7]})))))
