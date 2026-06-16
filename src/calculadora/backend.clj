(ns calculadora.backend
  (:require [clojure.string :as str]
            [compojure.core :refer [defroutes POST GET]]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [clj-http.client :as http]))

(def database (atom {:usuario nil 
                     :transacoes '()}))

(defn salvar-usuario [req]
  (let [dados-usuario (:body req)] 
    (swap! database assoc :usuario dados-usuario)
    {:status 200 :body {:status "sucesso" :mensagem "Usuário cadastrado com sucesso!"}}))

(defn consultar-usuario [req]
  (let [user (:usuario @database)]
    (if user
      {:status 200 :body {:usuario user}}
      {:status 404 :body {:mensagem "Usuário não cadastrado."}})))

(defn buscar-calorias-alimento [nome quantidade]
  (try
    (let [url "https://api.nal.usda.gov/fdc/v1/foods/search"
          resposta (http/get url {:query-params {"query" nome
                                                 "api_key" "DEMO_KEY"}
                                  :as :json
                                  :keywordize? true})
          primeiro-alimento (first (:foods (:body resposta)))
          nutrientes (:foodNutrients primeiro-alimento)
          
          energia-nutriente (first (filter #(= (:nutrientName %) "Energy") nutrientes))
          calorias-por-100g (:value energia-nutriente 0)
          qtd (Double/parseDouble (str quantidade))]
      
      (if calorias-por-100g
        (double (* (/ calorias-por-100g 100.0) qtd))
        (do
          (println "\n[Aviso] Alimento não encontrado na base de dados.")
          0.0)))
    (catch Exception e
      (println "\n[Erro] Falha ao consultar a API da USDA:" (.getMessage e))
      0.0)))

(defn registrar-alimento [req]
  (let [{:keys [nome data quantidade]} (:body req)
        calorias-totais (buscar-calorias-alimento nome quantidade)
        nova-transacao {:tipo "Ganho"
                        :descricao nome
                        :data data
                        :quantidade quantidade
                        :calorias calorias-totais}]
    
    (swap! database update :transacoes conj nova-transacao)
    
    {:status 200
     :body {:status "sucesso" 
            :mensagem (str "Registrado: " nome " - " calorias-totais " kcal.")}}))

(defn buscar-calorias-exercicio [atividade duracao peso-kg]
  (try
    (let [api-key "95eSMgChSMJNqGjtZ03dh8qgEuzPKvdrioKO8JVN"
          url "https://api.api-ninjas.com/v1/caloriesburned"
          peso-lbs (* (Double/parseDouble (str peso-kg)) 2.20462)
          resposta (http/get url {:query-params {"activity" atividade
                                                 "duration" duracao
                                                 "weight" peso-lbs}
                                  :headers {"X-Api-Key" api-key}
                                  :as :json
                                  :keywordize? true})
          itens (:body resposta)]
      
      (if (seq itens)
        (let [calorias (:total_calories (first itens))]
          (if (number? calorias)
            calorias
            (* (Double/parseDouble (str duracao)) 8.0))) 
        (do
          (println "\n[Aviso] Atividade não encontrada. Utilizando estimativa.")
          (* (Double/parseDouble (str duracao)) 8.0))))
    (catch Exception e
      (println "\n[Erro] Falha ao consultar a API Ninjas:" (.getMessage e))
      (* (Double/parseDouble (str duracao)) 8.0))))

(defn registrar-exercicio [req]
  (let [{:keys [atividade data duracao]} (:body req)
        peso-usuario (get-in @database [:usuario :peso] 70.0)
        calorias-perdidas (buscar-calorias-exercicio atividade duracao peso-usuario)
        nova-transacao {:tipo "Perda"
                        :descricao atividade
                        :data data
                        :duracao duracao
                        :calorias calorias-perdidas}]
    
    (swap! database update :transacoes conj nova-transacao)
    
    {:status 200
     :body {:status "sucesso" 
            :mensagem (str "Registrado: " atividade " - " calorias-perdidas " kcal gastas.")}}))

(defn data->numero [data-str]
  (try
    (let [partes (clojure.string/split data-str #"/")
          dia (nth partes 0)
          mes (nth partes 1)
          ano (nth partes 2)]
      (Long/parseLong (str ano mes dia)))
    (catch Exception e 0)))

(defn consultar-extrato [req]
  (let [params (:query-params req)
        data-inicio-param (get params "inicio")
        data-fim-param (get params "fim")
        transacoes (:transacoes @database)]
    
    (if (and data-inicio-param data-fim-param)
      (let [inicio-num (data->numero data-inicio-param)
            fim-num (data->numero data-fim-param)
            transacoes-filtradas (filter (fn [t]
                                           (let [t-num (data->numero (:data t))]
                                             (and (>= t-num inicio-num)
                                                  (<= t-num fim-num))))
                                         transacoes)]
        {:status 200 :body {:transacoes transacoes-filtradas}})
      {:status 200 :body {:transacoes transacoes}})))

(defn consultar-saldo [req]
  (let [params (:query-params req)
        data-inicio-param (get params "inicio")
        data-fim-param (get params "fim")
        todas-transacoes (:transacoes @database)
        
        transacoes (if (and data-inicio-param data-fim-param)
                     (let [inicio-num (data->numero data-inicio-param)
                           fim-num (data->numero data-fim-param)]
                       (filter (fn [t]
                                 (let [t-num (data->numero (:data t))]
                                   (and (>= t-num inicio-num)
                                        (<= t-num fim-num))))
                               todas-transacoes))
                     todas-transacoes)
        
        ganhos (reduce + 0 (map :calorias (filter #(= (:tipo %) "Ganho") transacoes)))
        perdas (reduce + 0 (map :calorias (filter #(= (:tipo %) "Perda") transacoes)))
        saldo (- ganhos perdas)]
    
    {:status 200
     :body {:ganhos ganhos :perdas perdas :saldo saldo}}))

(defroutes app-routes
  (POST "/api/usuario" req (salvar-usuario req))
  (GET "/api/usuario" req (consultar-usuario req)) 
  (POST "/api/alimento" req (registrar-alimento req))
  (POST "/api/exercicio" req (registrar-exercicio req))
  (GET "/api/extrato" req (consultar-extrato req))
  (GET "/api/saldo" req (consultar-saldo req))
  (route/not-found "Rota não encontrada"))

(def app
  (-> app-routes
      wrap-params
      (wrap-json-body {:keywords? true})
      (wrap-json-response)))

(defn iniciar-servidor []
  (println "[Back-end] Iniciando a API na porta 3002...")
  (run-jetty app {:port 3002 :join? false}))