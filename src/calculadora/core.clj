(ns calculadora.core
  (:require [calculadora.backend :as backend]
            [calculadora.frontend :as frontend])
  (:gen-class))

(defn -main [& args]
  (backend/iniciar-servidor)
  (Thread/sleep 1000)
  (frontend/menu-principal false) 
  (System/exit 0))