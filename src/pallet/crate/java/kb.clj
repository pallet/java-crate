(ns pallet.crate.java.kb
  "A rule base for java"
  (:require
   [clara.rules
    :refer [fire-rules insert insert! make-query make-rule mk-session
            query retract!]]
   [pallet.versions :refer [version-matches? version-string]]
   [schema.core :as schema]))

(defmacro defrule [name & body]
  `(def ~name (make-rule ~name ~@body)))
(defmacro defquery [name args & body]
  `(def ~name (make-query ~name ~args ~@body)))

(defn facts
  "Return a sequence of active facts."
  [s]
  (let [content (:content (clara.rules.engine/working-memory s))
        get-keys (fn [type-key]
                   (filter (fn [k]
                             (and
                              (not (nil? k))
                              (vector? k)
                              (= type-key (first k)))) (keys content)))
        pm-keys (get-keys :production-memory)
        alpha-keys (get-keys :alpha-memory)
        alpha-nodes (vals (select-keys content alpha-keys))]
    (into #{}
          (concat
           (map :fact (mapcat #(filter :fact %) alpha-nodes))
           (mapcat :facts (mapcat #(filter :facts %) pm-keys))))))

(defn rules
  "Return a sequence of rule names for the session."
  [session]
  (map :name (-> (clara.rules.engine/working-memory session)
                 :rulebase :productions)))

;;; ------------------------------------------------------------
;;; # Operating system types
(def Os
  {:type (schema/eq ::os)
   :os-family schema/Keyword
   :os-version [schema/Int]})

(def OsFamily
  {:type (schema/eq ::os-family)
   :os-family schema/Keyword})

(defn os [m]
  (schema/validate Os (assoc m :type ::os)))

(defn os-family [m]
  (schema/validate OsFamily (assoc m :type ::os-family)))

(defmacro os-family-rule
  "Define a rule for os-family memebers."
  [family family-member?]
  {:pre [(keyword? family)]}
  `(make-rule ~(symbol (str (name family) "-family"))
     [::os-family [{family# :os-family}] (~family-member? family#)]
     ~'=>
     (insert! (os-family {:os-family ~family}))))

(def os-rules
  [(os-family-rule :linux #{:rh-base :debian-base :arch-base :suse-base
                            :bsd-base :gentoo-base})
   (os-family-rule :rh-base #{:centos :rhel :amzn-linux :fedora})
   (os-family-rule :debian-base #{:debian :ubuntu :jeos})
   (os-family-rule :suse-base #{:suse})
   (os-family-rule :arch-base #{:arch})
   (os-family-rule :gentoo-base #{:gentoo})
   (os-family-rule :bsd-base #{:darwin :os-x})
   (make-rule add-os-family
     [?os <- ::os] => (insert! (os-family {:os-family (:os-family ?os)})))
   (make-query os-family []
     [?os-family <- ::os-family])])

(defn os-families [os]
  {:pre [(schema/validate Os os)]}
  (-> (mk-session os-rules :cache false :fact-type-fn :type)
      (insert os)
      fire-rules
      (query 'os-family)
      (->> (map :?os-family))))

;;; ------------------------------------------------------------
;;; # Facts
;;; ## Fact Types

(def Component (schema/enum :jre :jdk :bin))
(def Vendor (schema/enum :openjdk :oracle))

(def PackagesFact
  {:type (schema/eq ::packages)
   :names [schema/Str]})

(def PartialTarget
  (merge Os
         {:type (schema/eq ::partial-target)
          :os-family [schema/Keyword]
          (schema/optional-key :version) [schema/Int]
          (schema/optional-key :components) #{schema/Keyword}
          (schema/optional-key :vendor) Vendor}))

(def Target
  (merge Os
         {:type (schema/eq ::target)
          :os-family [schema/Keyword]
          :version [schema/Int]
          :components #{schema/Keyword}
          :vendor Vendor}))

(def Strategy
  {:type (schema/eq ::strategy)
   :install-strategy schema/Keyword
   schema/Keyword schema/Any})

;;; ## Fact Generators
(defn partial-target
  "Return a validated target map, describing the installation target."
  [m]
  (schema/validate PartialTarget (assoc m :type ::partial-target)))

(defn target
  "Return a validated target map, describing the installation target."
  [m]
  (schema/validate Target (assoc m :type ::target)))

(defn packages-fact
  "Return a validated packages map, describing package names."
  [m]
  (schema/validate PackagesFact (assoc m :type ::packages)))

(defn strategy [m]
  (schema/validate Strategy (assoc m :type ::strategy)))

;;; ## Fact Queries

(defquery partial-targets []
  [?partial-target <- ::partial-target])

(defquery targets []
  [?target <- ::target])

(defquery strategies []
  [?strategy <- ::strategy])

(defquery package-facts []
  [?packages <- ::packages])

;;; ------------------------------------------------------------
;;; # Default Target

;;; Given a OS information, find a default java version for that OS.

;;; We force an order for filling in defaults by specifying which
;;; previous defaults are set.

;;; ## Rules

(defrule default-vendor
  "Default vendor (:openjdk or :oracle)."
  [?target <- ::partial-target [{vendor :vendor}] (not vendor)]
  =>
  (insert! (partial-target (assoc ?target :vendor :openjdk))))

(defrule default-components
  "Default components to install."
  [?target <- ::partial-target [{:keys [components vendor]}]
   (and (not components)
        vendor)]
  =>
  (insert! (partial-target (assoc ?target :components #{:jdk :bin}))))

(defrule ubuntu-default-version
  "Default version on ubuntu.  Should match the default java package."
  [?target <- ::partial-target [{:keys [os-family version components vendor]}]
   (and (not version)
        components vendor
        ((set os-family) :ubuntu))]
  =>
  (insert! (partial-target (assoc ?target :version [7]))))

(defrule partial-target->target
  "Convert a partial target into a target when it is fully specified."
  [?target <- ::partial-target [target]
   (every? target [:vendor :version :components])]
  =>
  (insert! (target ?target)))

;;; ## Rule Sets

(def default-target-rules
  [partial-target->target
   default-vendor
   default-components
   ubuntu-default-version
   targets])

;;; ## Default Target Generator
(defn default-target
  "Return a default target for a given partial target.  Return
  nil if none found."
  [os {:keys [vendor version components] :as spec}]
  (let [matches (-> (mk-session default-target-rules :fact-type-fn :type)
                    (insert
                     (partial-target
                      (assoc (merge os spec)
                        :os-family (map :os-family (os-families os)))))
                    fire-rules
                    (query 'targets)
                    (->> (map :?target)))]
    (when (> (count matches) 1)
      (throw
       (ex-info
        "default-target found more than one default target."
        {})))
    (first matches)))

(default-target
  (os {:os-family :ubuntu :os-version [13 10]})
  {})

;;; ------------------------------------------------------------
;;; # Package Names

;;; http://openjdk.java.net/install/
;;; https://wiki.archlinux.org/index.php/java
;;; https://aur.archlinux.org/packages/jdk/?comments=all

;;; ## Rules
(defrule debian-openjdk-packages
  [?target <- ::target [{:keys [os-family vendor version]}]
   (and ((set os-family) :debian-base)
        (version-matches? version [[6][7]])
        (= vendor :openjdk))]
  =>
  (insert!
   (packages-fact
    {:names
     (map
      (comp #(str "openjdk-" (version-string (:version ?target)) "-" %) name)
      (filter #{:jdk :jre} (:components ?target)))})))

(defrule rh-openjdk-packages
  [?target <- ::target [{:keys [os-family vendor version]}]
   (and ((set os-family) :rh-base)
        (version-matches? version [[6][7]])
        (= vendor :openjdk))]
  =>
  (insert!
   (packages-fact
    {:names
     (map
      (comp
       (partial str "java-1." (version-string (:version ?target)) ".0-openjdk")
       #({:jdk "-devel" :jre ""} % ""))
      (:components ?target))})))

(defrule arch-oracle67-packages
  [?target <- ::target [{:keys [os-family vendor version]}]
   (and ((set os-family) :arch-base)
        (version-matches? version [[6][7]])
        (= vendor :oracle))]
  =>
  (insert!
   (packages-fact
    {:names (->>
             (filter #{:jdk :jre} (:components ?target))
             (map #(str (name %) (first (:version ?target)))))})))

(defrule arch-openjdk-packages
  [?target <- ::target [{:keys [os-family vendor version]}]
   (and ((set os-family) :arch-base)
        (version-matches? version [[6][7]])
        (= vendor :openjdk))]
  =>
  (insert!
   (packages-fact
    {:names (->>
             (filter #{:jdk :jre} (:components ?target))
             (map #(str (name %) (first (:version ?target)) "-openjdk")))})))

(defrule arch-oracle8-packages
  [?target <- ::target [{:keys [os-family vendor version]}]
   (and ((set os-family) :arch-base)
        (version-matches? version [8])
        (= vendor :oracle))]
  =>
  (insert!
   (packages-fact
    {:names (->>
             (filter #{:jdk :jre} (:components ?target))
             (map name))})))


;;; ## Rule Sets

(def package-rules
  [debian-openjdk-packages
   rh-openjdk-packages
   arch-openjdk-packages
   arch-oracle67-packages
   arch-oracle8-packages
   package-facts])

;;; ## Package Names Generator
(defn package-names
  "Return a sequence of package names for the given target.  Return
  nil if none found."
  [target]
  (let [matches (-> (mk-session package-rules :cache false :fact-type-fn :type)
                    (insert target)
                    fire-rules
                    (query 'package-facts)
                    (->> (map (comp :names :?packages))))]
    (when (> (count matches) 1)
      (throw
       (ex-info
        "package-names found more than one list of packages."
        {})))
    (first matches)))

;;; ------------------------------------------------------------
;;; # Install Strategy

;;; ## Install Strategies

(defn from-packages
  "Return an install strategy map for installing target from packages.
  Return nil if not possible to install from system packages."
  [package-names]
  {:install-strategy :packages
   :packages package-names})

(defn from-webupd8
  [version]
  (let [package (str "oracle-java" (first version) "-installer")]
    {:install-strategy :package-source
     :package-source {:apt {:url "ppa:webupd8team/java"}
                      :name "webupd8team-java"}
     :packages [package]
     :preseeds [{:package package
                 :question "shared/accepted-oracle-license-v1-1"
                 :type :select
                 :value true}]}))

;;; ## Rules
(defrule from-system-packages-strategy
  [?target <- ::target
   [{:keys [os-family version vendor] :as target}]
   (seq (package-names target))]
  =>
  (insert! (strategy (from-packages (package-names ?target)))))

(defrule from-webupd8-strategy
  [?target <- ::target [{:keys [os-family os-version version]}]
   (and ((set os-family) :ubuntu)
        (version-matches? os-version [[13 10]])
        (version-matches? version [[7][8]]))]
  =>
  (insert! (strategy (from-webupd8 (:version ?target)))))


;;; ### Rule Sets

(def install-rules
  [;; install strategies
   from-system-packages-strategy
   from-webupd8-strategy
   ;; queries
   partial-targets
   targets
   strategies])

;;; # Install Strategy Generator
(defn install-strategy
  "Return a sequence of install strategies for the given os and java version."
  [target-map]
  (-> (mk-session install-rules :cache false :fact-type-fn :type)
      (insert (target target-map))
      fire-rules
      (query 'strategies)
      (->> (map :?strategy))))

;; Local Variables:
;; mode: clojure
;; eval: (define-clojure-indent (make-rule 1) (make-query 2))
;; End:
