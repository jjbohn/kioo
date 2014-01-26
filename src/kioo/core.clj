(ns kioo.core
  (:refer-clojure :exclude [compile])
  (:require [kioo.util :refer [convert-attrs]]
            [net.cgrand.enlive-html :refer [at html-resource select
                                            any-node]]))

(declare compile)

(defn attach-transform [trans]
  (fn [node]
    (if (:trans node)
      (assoc node :trans `(fn [node]
                           (cljs.core/-> node
                               (~(:trans node))
                               (~trans))))
      (assoc node :trans trans)))) 


(defn resolve-enlive-var [sym]
  (ns-resolve 'net.cgrand.enlive-html sym))

(defn eval-selector [sel]
  (reduce
   #(conj %1
          (cond
           (list? %2) (apply (resolve-enlive-var (first %2)) (rest %2))
           (vector? %2) (eval-selector %2)
           (symbol? %2) (resolve-enlive-var %2)
            :else %2))
   [] sel))

(defn map-trans [node trans-lst]
  (reduce (fn [node [sel trans]]
            (at node (eval-selector sel) (attach-transform trans)))
          node
          trans-lst))

(defn get-react-sym [tag]
  (symbol "js" (str "React.DOM." (name tag))))


(defmacro component [path & body]
  (let [[sel trans-lst] (if (map? (first body))
                          [[:body :> any-node] (first body)]
                          body)
        root (html-resource path)
        start (if (= :root sel)
                root
                (select root (eval-selector sel)))]
    `(let [ch# ~(compile (map-trans start trans-lst))]
       (if (= 1 (count ch#))
         (first ch#)
         (apply ~(get-react-sym :span) nil ch#)))))


(defn compile-node [node]
  (let [children (compile (:content node))]
    (if (:trans node)
      `(kioo.core/make-react-dom
        (~(:trans node) ~(-> node
                             (dissoc :trans)
                             (assoc :attrs (convert-attrs (:attrs node))
                                    :content children
                                    :sym (get-react-sym (:tag node))))))
      `(apply ~(get-react-sym (:tag node))
        (cljs.core/clj->js ~(convert-attrs (:attrs node)))
        ~children))))


(defn compile [node]
  (let [nodes (if (map? node) [node] node)
        react-nodes (vec (map #(if (map? %) (compile-node %) %)
                              nodes))]
    `(kioo.core/flatten-nodes ~react-nodes)))
