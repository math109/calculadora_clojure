(defproject calculadora "0.1.0-SNAPSHOT"
  :description "Calculadora de Calorias - AV3 Unifor"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [ring/ring-core "1.10.0"]
                 [ring/ring-jetty-adapter "1.10.0"] ; Para rodar o servidor local
                 [compojure "1.7.0"]                 ; Para gerenciar as rotas/endpoints
                 [ring/ring-json "0.5.1"]            ; Para lidar com JSON automaticamente
                 [clj-http "3.12.3"]]                ; Para bater nas APIs externas
  :main ^:skip-aot calculadora.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
