(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as b]))

(def lib 'istu-archive)
(def version (format "0.2.%s" (b/git-count-revs nil)))
(def ^:private class-dir "build/classes")
(def ^:private uber-file (format "build/lib/%s-%s-standalone.jar"
                                 (name lib) version))

(defn clean [_]
  (println "BUILD: Cleaning build/ directory...")
  (b/delete {:path "build"}))

(defn compile [_]
  (println "BUILD: Compiling Clojure sources...")
  (b/compile-clj {:basis      (b/create-basis {:aliases [:compile]})
                  :ns-compile '[istu.archive.fuseki.module]
                  :class-dir  class-dir
                  :bindings   {#'clojure.core/*warn-on-reflection* true}}))

(defn uber [_]
  (println "BUILD: Cleaning classes directory...")
  (b/delete {:path class-dir})
  (println "BUILD: Copying resources...")
  (b/copy-dir {:src-dirs   ["resources"]
               :target-dir class-dir})
  (compile nil)
  (println "BUILD: Creating an uberjar...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     (b/create-basis nil)}))
