(ns calculadora.frontend
  (:require [clj-http.client :as http]))

;; ==========================================
;; 1. FUNÇÕES DE CAPTURA DE DADOS
;; ==========================================
(defn obter-dados-usuario-cli []
  (println "\n=======================================")
  (println "   CADASTRO DA CALCULADORA DE CALORIAS  ")
  (println "=======================================")
  (print "Digite sua altura em metros (ex: 1.75): ") (flush)
  (let [v-altura (Double/parseDouble (read-line))]
    (print "Digite seu peso em kg (ex: 70.5): ") (flush)
    (let [v-peso (Double/parseDouble (read-line))]
      (print "Digite sua idade: ") (flush)
      (let [v-idade (Integer/parseInt (read-line))]
        (print "Digite seu sexo (M/F): ") (flush)
        (let [v-sexo (clojure.string/upper-case (read-line))]
          {:altura v-altura :peso v-peso :idade v-idade :sexo v-sexo})))))

(defn obter-dados-alimento-cli []
  (println "\n=======================================")
  (println "   REGISTRAR CONSUMO DE ALIMENTO       ")
  (println "=======================================")
  (print "Alimento (em ingles, ex: apple, rice, chicken): ") (flush)
  (let [nome (read-line)]
    (print "Data do consumo (ex: 14/06/2026): ") (flush)
    (let [data (read-line)]
      (print "Quantidade consumida em gramas (ex: 150): ") (flush)
      (let [quantidade (read-line)]
        {:nome nome :data data :quantidade quantidade}))))

(defn obter-dados-exercicio-cli []
  (println "\n=======================================")
  (println "   REGISTRAR ATIVIDADE FÍSICA    ")
  (println "=======================================")
  (print "Atividade (em ingles, ex: running, cycling, swimming): ") (flush)
  (let [atividade (read-line)]
    (print "Data (ex: 14/06/2026): ") (flush)
    (let [data (read-line)]
      (print "Duração em minutos (ex: 30): ") (flush)
      (let [duracao (read-line)]
        {:atividade atividade :data data :duracao duracao}))))

;; ==========================================
;; 2. FUNÇÃO RECURSIVA (A QUE ESTAVA FALTANDO!)
;; ==========================================
(defn imprimir-extrato-recursivo
  "Imprime a lista de transações usando recursão de cauda (sem loops como doseq/for)."
  [transacoes]
  (when (seq transacoes)
    (let [t (first transacoes)]
      (println (str " > " (:data t) " | " (:tipo t) " | " (:descricao t) " | " (:calorias t) " kcal")))
    (recur (rest transacoes))))

;; ==========================================
;; 3. MENUS E LÓGICA PRINCIPAL
;; ==========================================
(defn exibir-opcoes-menu [cadastrado?]
  (println "\n=== CALCULADORA DE CALORIAS ===")
  (if cadastrado?
    (do
      (println "2. Registrar consumo de alimento")
      (println "3. Registrar atividade fisica")
      (println "4. Consultar extrato de transações")
      (println "5. Consultar saldo de calorias")
      (println "6. Sair"))
    (do
      (println "1. Cadastrar dados pessoais")
      (println "6. Sair")))
  (print "Escolha uma opcao: ")
  (flush))

(defn menu-principal 
  [cadastrado?]
  (exibir-opcoes-menu cadastrado?)
  (let [opcao (read-line)]
    (cond
      (= opcao "1")
      (let [dados (obter-dados-usuario-cli)
            sucesso? (try
                       (let [resposta (http/post "http://localhost:3002/api/usuario"
                                                 {:form-params dados
                                                  :content-type :json
                                                  :as :json})]
                         (println "\n[Front-end] Resposta da API:" (get-in resposta [:body :mensagem]))
                         true)
                       (catch Exception e
                         (println "\n[Erro] Falha de comunicação com o Back-end.")
                         false))]
        (if sucesso?
          (recur true)
          (recur cadastrado?)))

      (= opcao "6")
      (println "\nSaindo...")

      (and cadastrado? (= opcao "2"))
      (let [dados-alimento (obter-dados-alimento-cli)]
        (try
          (let [resposta (http/post "http://localhost:3002/api/alimento"
                                    {:form-params dados-alimento
                                     :content-type :json
                                     :as :json})]
            (println "\n[Front-end] Resposta:" (get-in resposta [:body :mensagem])))
          (catch Exception e
            (println "\n[Erro] Falha ao comunicar com o Back-end ou API Externa.")))
        (recur cadastrado?))

      (and cadastrado? (= opcao "3"))
      (let [dados-exercicio (obter-dados-exercicio-cli)]
        (try
          (let [resposta (http/post "http://localhost:3002/api/exercicio"
                                    {:form-params dados-exercicio
                                     :content-type :json
                                     :as :json})]
            (println "\n[Front-end] Resposta:" (get-in resposta [:body :mensagem])))
          (catch Exception e
            (println "\n[Erro] Falha ao comunicar com o Back-end.")))
        (recur cadastrado?))

      (and cadastrado? (= opcao "4"))
      (do 
        (println "\n=== CONSULTAR EXTRATO ===")
        (println "1. Ver extrato completo")
        (println "2. Filtrar por período (datas)")
        (print "Escolha uma opção: ") (flush)
        (let [sub-opcao (read-line)]
          (try
            (let [url "http://localhost:3002/api/extrato"
                  config-requisicao (if (= sub-opcao "2")
                                      (do
                                        (print "Digite a data de início (ex: 01/06/2026): ") (flush)
                                        (let [ini (read-line)]
                                          (print "Digite a data de fim (ex: 15/06/2026): ") (flush)
                                          (let [fim (read-line)]
                                            {:query-params {"inicio" ini "fim" fim} :as :json})))
                                      {:as :json})
                  resposta (http/get url config-requisicao)
                  transacoes (get-in resposta [:body :transacoes])]
              
              (println "\n=== EXTRATO DE TRANSAÇÕES FILTRADO ===")
              (if (empty? transacoes)
                (println "Nenhuma transação encontrada para este critério.")
                (imprimir-extrato-recursivo transacoes)))
            (catch Exception e
              (println "\n[Erro] Falha ao consultar o extrato no Back-end."))))
        (recur cadastrado?))

      (and cadastrado? (= opcao "5"))
      (do 
        (try
          (let [resposta (http/get "http://localhost:3002/api/saldo" {:as :json})
                dados (:body resposta)]
            (println "\n=== SALDO DE CALORIAS ===")
            (println "Total Consumido (Ganhos): " (:ganhos dados) " kcal")
            (println "Total Gasto (Perdas):     " (:perdas dados) " kcal")
            (println "---------------------------------")
            (println "SALDO FINAL:              " (:saldo dados) " kcal"))
          (catch Exception e
            (println "\n[Erro] Falha ao consultar o saldo no Back-end.")))
        (recur cadastrado?))

      :else
      (do
        (println "\nOpção inválida! Tente novamente.")
        (recur cadastrado?)))))