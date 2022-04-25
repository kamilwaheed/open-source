(ns lioss.hiccup
  "Babashka(nbb)-compatible hiccup

  - supports #id and .class shorthands
  - escape attributes and text elements
  - outputs indented/pretty-printed HTML/XML
  - prevent escaping by using a [:raw-html \"...\"] element
  "
  (:require [clojure.string :as str]
            #?@(:cljs [[goog.string :as gstring]])))

(defn- split-tag [tag]
  (let [tag (name tag)
        [tag & classes] (str/split tag #"\.")
        [tag id] (if (str/includes? tag "#")
                   (str/split tag #"#")
                   [tag nil])]
    [tag id classes]))

(defn- split-el [[tag & tail]]
  (let [[tag id kls] (split-tag tag)]
    [tag
     (cond-> (if (map? (first tail))
               (first tail)
               {})
       id
       (assoc :id id)
       (seq kls)
       (update :class str (str/join " " kls)))
     (filter
      some?
      (if (map? (first tail))
        (next tail)
        tail))]))

(defn- escape-attr [a]
  (-> a
      (str/replace #"&" "&amp;")
      (str/replace #"'" "&#x27;")
      (str/replace #"\"" "&quot;")))

(defn- escape-text [t]
  (-> t
      (str/replace #"&" "&amp;")
      (str/replace #"'" "&#x27;")
      (str/replace #"\"" "&quot;")
      (str/replace #"<" "&lt;")
      (str/replace #">" "&gt;")))

(defn- render-attrs [m]
  (->> m
       (map (fn [[k v]]
              (#?(:clj format :cljs gstring/format) " %s=\"%s\"" (name k) (escape-attr v))))
       (str/join "")))

(def ^:dynamic *nesting* 0)

(declare h)

(defn- h* [hiccup]
  (binding [*nesting* (inc *nesting*)]
    (let [els (h hiccup)]
      (if (seq? els)
        els
        (list els)))))

(defn h [hiccup]
  (cond
    (string? hiccup)
    (escape-text hiccup)

    (vector? hiccup)
    (let [[tag attrs children] (split-el hiccup)]
      (cond
        (= "raw-html" tag)
        (apply str children)

        (or (empty? children)
            (and (= 1 (count children))
                 (not (coll? (first children)))))
        (#?(:clj format :cljs gstring/format)
         (str "\n%s"
              "<%s%s>"
              "%s"
              "</%s>")
         (apply str (repeat *nesting* "  "))
         tag (render-attrs attrs)
         (apply str (mapcat h* children))
         tag)

        :else
        (#?(:clj format :cljs gstring/format)
         (str "\n%s"
              "<%s%s>"
              "%s"
              "\n%s"
              "</%s>")
         (apply str (repeat *nesting* "  "))
         tag (render-attrs attrs)
         (apply str (mapcat h* children))
         (apply str (repeat *nesting* "  "))
         tag)))

    (seq? hiccup)
    (apply str (mapcat h* hiccup))

    :else
    (h (str hiccup))))
