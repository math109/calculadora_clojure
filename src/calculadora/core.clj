(ns calculadora.core
  (:require [calculadora.backend :as backend]
            [calculadora.frontend :as frontend])
  (:gen-class))

(defn -main
  "Ponto de entrada que arranca o Back-end e o Front-end."
  [& args]
  (backend/iniciar-servidor)
  (Thread/sleep 1000)
  
  ;; AQUI: Passamos 'false' porque o programa acaba de iniciar
  (frontend/menu-principal false) 
  
  (System/exit 0))