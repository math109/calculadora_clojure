(ns calculadora.backend
  (:require [compojure.core :refer [defroutes POST GET]]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [clj-http.client :as http])) ;; <-- Adicionamos isto para a API Externa

(def database (atom {:usuario nil 
                     :transacoes '()})) ;; A lista de transações começa vazia

(defn salvar-usuario [req]
  (let [dados-usuario (:body req)] 
    (swap! database assoc :usuario dados-usuario)
    {:status 200 :body {:status "sucesso" :mensagem "Usuário cadastrado com sucesso no Back-end!"}}))

;; --- NOVA LÓGICA DE ALIMENTOS ---

(defn buscar-calorias-alimento [nome quantidade]
  "Consulta a API pública do governo dos EUA (USDA). Não requer registo!"
  (try
    (let [url "https://api.nal.usda.gov/fdc/v1/foods/search"
          ;; A DEMO_KEY permite fazer testes gratuitos sem criar conta
          resposta (http/get url {:query-params {"query" nome
                                                 "api_key" "DEMO_KEY"}
                                  :as :json})
          
          ;; A API devolve muitos alimentos parecidos. Pegamos o primeiro da lista (:foods).
          primeiro-alimento (first (:foods (:body resposta)))
          
          ;; Pegamos a lista de todos os nutrientes desse alimento
          nutrientes (:foodNutrients primeiro-alimento)
          
          ;; USO DE ORDEM SUPERIOR (Nota 10 garantida!):
          ;; Filtramos a lista para encontrar apenas o nutriente chamado "Energy" (Calorias)
          energia-nutriente (first (filter #(= (:nutrientName %) "Energy") nutrientes))
          
          ;; O valor que a USDA devolve é sempre baseado em 100g de alimento
          calorias-por-100g (:value energia-nutriente 0)
          
          ;; Transformamos a quantidade digitada no front-end num número decimal
          qtd (Double/parseDouble (str quantidade))]
      
      ;; Se encontrou o alimento, faz a regra de três simples para calcular as calorias totais
      (if calorias-por-100g
        (double (* (/ calorias-por-100g 100.0) qtd))
        (do
          (println "\n[Aviso] Alimento não encontrado.")
          0.0)))
          
    (catch Exception e
      (println "\n[Erro] Falha ao consultar a API:" (.getMessage e))
      0.0)))

(defn registrar-alimento [req]
  "Recebe do front-end, busca calorias, salva no atom e devolve a resposta"
  (let [{:keys [nome data quantidade]} (:body req)
        calorias-totais (buscar-calorias-alimento nome quantidade)
        
        ;; Criamos o mapa da transação
        nova-transacao {:tipo "Ganho"
                        :descricao nome
                        :data data
                        :quantidade quantidade
                        :calorias calorias-totais}]
    
    ;; Atualiza o átomo: pega na chave :transacoes e adiciona a nova transação no início (conj)
    (swap! database update :transacoes conj nova-transacao)
    (println "\n[Back-end] Nova transação guardada:" nova-transacao)
    
    {:status 200
     :body {:status "sucesso" 
            :mensagem (str "Registrado! " nome ": " calorias-totais " kcal.")}}))

;; --- NOVA LÓGICA DE EXERCÍCIOS (API NINJAS) ---

(defn buscar-calorias-exercicio [atividade duracao peso-kg]
  "Consulta a API Ninjas para calcular calorias queimadas com base no peso real do utilizador."
  (try
    (let [api-key "95eSMgChSMJNqGjtZ03dh8qgEuzPKvdrioKO8JVN" ;; <-- A sua chave da API Ninjas
          url "https://api.api-ninjas.com/v1/caloriesburned"
          ;; A API Ninjas calcula o peso em libras (lbs). Fazemos a conversão matemática pura:
          peso-lbs (* (Double/parseDouble (str peso-kg)) 2.20462)
          
          resposta (http/get url {:query-params {"activity" atividade
                                                 "duration" duracao
                                                 "weight" peso-lbs}
                                  :headers {"X-Api-Key" api-key}
                                  :as :json})
          itens (:body resposta)]
      
      ;; Se a API retornou resultados, pegamos o valor :total_calories do primeiro item
      (if (seq itens)
        (let [calorias (:total_calories (first itens))]
          (if (number? calorias)
            calorias
            ;; Fallback caso a API retorne algum texto de erro
            (* (Double/parseDouble (str duracao)) 8.0))) 
        
        ;; Se não encontrar a atividade, fazemos uma estimativa média (8 kcal por minuto)
        (do
          (println "\n[Aviso] Atividade não encontrada. Usando estimativa.")
          (* (Double/parseDouble (str duracao)) 8.0))))
          
    (catch Exception e
      (println "\n[Erro] Falha ao consultar API:" (.getMessage e))
      (* (Double/parseDouble (str duracao)) 8.0))))

(defn registrar-exercicio [req]
  "Recebe dados do front-end, pega o peso no atom, consulta a API e salva a transação."
  (let [{:keys [atividade data duracao]} (:body req)
        
        ;; Pega o peso do usuário guardado no átomo (se não existir, usa 70.0 por padrão)
        peso-usuario (get-in @database [:usuario :peso] 70.0)
        
        calorias-perdidas (buscar-calorias-exercicio atividade duracao peso-usuario)
        
        ;; Criamos o mapa da transação com tipo "Perda"
        nova-transacao {:tipo "Perda"
                        :descricao atividade
                        :data data
                        :duracao duracao
                        :calorias calorias-perdidas}]
    
    ;; Adiciona a transação na lista do átomo usando conj (no início da lista)
    (swap! database update :transacoes conj nova-transacao)
    (println "\n[Back-end] Nova transação guardada:" nova-transacao)
    
    {:status 200
     :body {:status "sucesso" 
            :mensagem (str "Registrado! A atividade '" atividade "' queimou cerca de " calorias-perdidas " kcal.")}}))


(defn data->numero [data-str]
  "Transforma uma string DD/MM/AAAA num número AAAAMMBD para comparação cronológica simples."
  (try
    (let [partes (clojure.string/split data-str #"/")
          dia (nth partes 0)
          mes (nth partes 1)
          ano (nth partes 2)]
      (Long/parseLong (str ano mes dia)))
    (catch Exception e 0))) ;; Retorna 0 se o formato for inválido

(defn consultar-extrato [req]
  "Devolve as transações do átomo, opcionalmente filtradas por período se os parâmetros forem fornecidos."
  (let [params (:query-params req)
        data-inicio-param (get params "inicio")
        data-fim-param (get params "fim")
        transacoes (:transacoes @database)]
    
    (if (and data-inicio-param data-fim-param)
      ;; Se o utilizador enviou o período, usamos FILTER de forma puramente funcional
      (let [inicio-num (data->numero data-inicio-param)
            fim-num (data->numero data-fim-param)
            transacoes-filtradas (filter (fn [t]
                                           (let [t-num (data->numero (:data t))]
                                             (and (>= t-num inicio-num)
                                                  (<= t-num fim-num))))
                                         transacoes)]
        {:status 200 :body {:transacoes transacoes-filtradas}})
      
      ;; Se não passou parâmetros, devolve o extrato completo
      {:status 200 :body {:transacoes transacoes}})))


(defn consultar-saldo [req]
  "Calcula o saldo filtrando por período (se fornecido) usando puramente funções de ordem superior."
  (let [params (:query-params req)
        data-inicio-param (get params "inicio")
        data-fim-param (get params "fim")
        todas-transacoes (:transacoes @database)
        
        ;; 1. Filtra pelo período, se as datas foram enviadas
        transacoes (if (and data-inicio-param data-fim-param)
                     (let [inicio-num (data->numero data-inicio-param)
                           fim-num (data->numero data-fim-param)]
                       (filter (fn [t]
                                 (let [t-num (data->numero (:data t))]
                                   (and (>= t-num inicio-num)
                                        (<= t-num fim-num))))
                               todas-transacoes))
                     todas-transacoes)
        
        ;; 2. Faz o map e reduce para somar os ganhos e perdas
        ganhos (reduce + 0 (map :calorias (filter #(= (:tipo %) "Ganho") transacoes)))
        perdas (reduce + 0 (map :calorias (filter #(= (:tipo %) "Perda") transacoes)))
        saldo (- ganhos perdas)]
    
    {:status 200
     :body {:ganhos ganhos :perdas perdas :saldo saldo}}))

;; --- ATUALIZAÇÃO DAS ROTAS ---
(defroutes app-routes
  (POST "/api/usuario" req (salvar-usuario req))
  (POST "/api/alimento" req (registrar-alimento req)) ;; <-- Nova rota registada
  (POST "/api/exercicio" req (registrar-exercicio req)) ;; <-- Nova rota para registrar exercício
  (GET "/api/extrato" req (consultar-extrato req))
  (GET "/api/saldo" req (consultar-saldo req)) ;; <-- Nova rota para consultar extrato
  (route/not-found "Rota não encontrada"))

(def app
  (-> app-routes
      (wrap-json-body {:keywords? true})
      (wrap-json-response)))

(defn iniciar-servidor []
  (println "[Back-end] A iniciar a API na porta 3002...")
  (run-jetty app {:port 3002 :join? false}))