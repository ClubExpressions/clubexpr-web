(ns club.db
  (:require [cljs.spec.alpha :as s]
            [clojure.walk :refer [keywordize-keys]]
            [webpack.bundle]
            [goog.object :refer [getValueByKeys]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [club.utils :refer [error error-fn data-from-js-obj]]
            [club.expr :refer [all-exprs]]
            [club.config :as config]))

(s/def ::current-page keyword?)
(s/def ::attempt-code string?)
(s/def ::show-help boolean?)
(s/def ::game-idx number?)

(s/def ::profile-page
  (s/and map?
         (s/keys :req-un [::quality
                          ::school
                          ::teachers-list  ; UI only, not stored in profile
                          ::teacher
                          ::lastname
                          ::firstname])))
(s/def ::quality string?)
(s/def ::school string?)
(s/def ::teachers-list #(instance? PersistentVector %))
; TODO: empty or containing maps like {:id "val" :lastname "val"}
(s/def ::teacher string?)
(s/def ::lastname string?)
(s/def ::firstname string?)

(s/def ::authenticated boolean?)
(s/def ::auth-data
  (s/and map?
         (s/keys :req-un [::auth0-id
                          ::kinto-id
                          ::access-token
                          ::expires-at])))
(s/def ::auth0-id string?)
(s/def ::kinto-id string?)
(s/def ::access-token string?)
(s/def ::expires-at   string?)

(s/def ::groups-page
  (s/and map?
         ; TODO for each scholar id we have a map with
         ; :lastname (string) :firstname (string)
         ; :groups (set of strings)
         ))

(s/def ::series
  (s/and map?
         (s/keys :req-un [::title  ; TODO: specify those 3
                          ::desc
                          ::exprs])))
(s/def ::current-series-id string?)
(s/def ::current-series
  (s/and map?
         (s/or :empty empty?
               :series ::series)))
(s/def ::expr-mod-showing boolean?)
(s/def ::expr-mod-template string?)
(s/def ::expr-mod-map map?)
(s/def ::teacher-testing boolean?)
(s/def ::teacher-testing-idx number?)
(s/def ::teacher-attempt string?)
(s/def ::teacher-attempt-error string?)
(s/def ::editing-series boolean?)
(s/def ::series-page
  (s/and #(instance? PersistentVector %)
         ; TODO each elt is a map {:id string? :series ::series}
         ))
(s/def ::expressions #(instance? PersistentVector %))
(s/def ::filters map?)  ; TODO {:kw1 callable1 :kw2 callable2}
(s/def ::nature string?)
(s/def ::depth  #(instance? PersistentVector %))
(s/def ::nb-ops #(instance? PersistentVector %))
(s/def ::prevented-ops #(instance? PersistentVector %))
(s/def ::series-filtering
  (s/and map?
         (s/keys :req-un [::expressions
                          ::filters
                          ::nature
                          ::depth
                          ::nb-ops
                          ::prevented-ops])))
(s/def ::works-teacher-page
  (s/and #(instance? PersistentVector %)
         ; TODO each elt is a map
         ; {:id :teacher-id :from :to :series-id :series-title :group}
         ))
(s/def ::works-scholar-page
  (s/and #(instance? PersistentVector %)
         ; TODO each elt is a map
         ; {:id :from :to :series-id :series-title}
         ))
(s/def ::scholar-working boolean?)
(s/def ::scholar-work-id string?)
(s/def ::scholar-work
  (s/and map?
         (s/keys :req-un [::series
                          ::current-expr-idx
                          ::current-expr
                          ::interactive
                          ::shown-at
                          ::attempt
                          ::error])))

(s/def ::db
  (s/and map?
         (s/or :empty empty?
               :keys (s/keys :req-un
                             [::current-page
                              ::authenticated
                              ::auth-data
                              ::attempt-code
                              ::show-help
                              ::game-idx
                              ::profile-page
                              ::groups-page
                              ::series-page
                              ::works-teacher-page
                              ::works-scholar-page
                              ::current-series-id
                              ::current-series
                              ::expr-mod-showing
                              ::expr-mod-template
                              ::expr-mod-map
                              ::teacher-testing
                              ::teacher-testing-idx
                              ::teacher-attempt
                              ::teacher-attempt-error
                              ::editing-series
                              ::series-filtering
                              ::scholar-working
                              ::scholar-work-id
                              ::scholar-work
                              ]))))

(def new-series
  {:title ""
   :desc ""
   :exprs []})

(def logout-db-fragment
  {:current-page :landing
   :authenticated false
   :auth-data {:kinto-id ""
               :auth0-id ""
               :access-token ""
               :expires-at   ""}
   :groups-page {}
   :series-page []
   :works-teacher-page []
   :works-scholar-page []
   :current-series-id ""
   :current-series new-series
   :expr-mod-showing false
   :expr-mod-template ""
   :expr-mod-map {}
   :teacher-testing false
   :teacher-testing-idx 0
   :teacher-attempt ""
   :teacher-attempt-error "Expression vide"
   :editing-series false
   :series-filtering
     {:expressions []
      :filters {:identity identity}
      :nature "All"
      :depth  [1 7]
      :nb-ops [1 7]
      :prevented-ops []}
   :scholar-work-id ""
   :scholar-working false
   :scholar-work
     {:series new-series
      :current-expr-idx 0
      :current-expr ""
      :interactive true
      :shown-at ""
      :attempt ""
      :error "Expression vide"}
   :profile-page {:quality "scholar"
                  :school "fake-id-no-school"
                  :teachers-list []
                  :teacher "no-teacher"
                  :lastname ""
                  :firstname ""}})

(def default-db
   (merge logout-db-fragment {:attempt-code "(Somme 1 2)"
                              :show-help false
                              :game-idx 0}))

(def k-client
  (let [b64 (js/window.btoa "user:pass")
        url (if club.config/debug?
              "http://localhost:8887/v1"
              "https://kinto.expressions.club/v1")
        opts (clj->js {:remote url
                       :headers {:Authorization (str "Basic " b64)}})
        kinto (getValueByKeys js/window "deps" "kinto")
        kinto-instance (new kinto opts)]
    (.-api kinto-instance)))

(def error-404 "Error: HTTP 404; Error: HTTP 404 Not Found: Invalid Token / id")

;(def k-log (.. k-client
;               (bucket "default")
;               (collection "log")))
;
;(defn log!
;  [data]
;  (.. k-log (createRecord data)))

(def k-users (.. k-client
                 (bucket "default")
                 (collection "users")))

(def k-series (.. k-client
                  (bucket "default")
                  (collection "series")))

(def k-groups (.. k-client
                  (bucket "default")
                  (collection "groups")))

(def k-works (.. k-client
                 (bucket "default")
                 (collection "works")))

(def k-attempts (.. k-client
                    (bucket "default")
                    (collection "attempts")))

(def k-progress (.. k-client
                    (bucket "default")
                    (collection "progress")))

(defn base-user-record
  [auth0-id]
  {:auth0-id auth0-id
   :quality "scholar"
   :school "fake-id-no-school"
   :teacher "no-teacher"
   :lastname ""
   :firstname ""})

(defn set-auth-data!
  [reason
   {:keys [; from new-user-data
           auth0-id access-token expires-at
           ; from the new record
           id quality school teacher lastname firstname]}]
  (swap! app-db assoc-in [:authenticated] true)
  ; from new-user-data
  (swap! app-db assoc-in [:auth-data :auth0-id] auth0-id)
  (swap! app-db assoc-in [:auth-data :access-token] access-token)
  (swap! app-db assoc-in [:auth-data :expires-at] expires-at)
  ; from a record
  (swap! app-db assoc-in [:auth-data :kinto-id] id)
  (swap! app-db assoc-in [:profile-page] {:quality quality
                                          :school school
                                          :teacher teacher
                                          :teachers-list []
                                          :lastname lastname
                                          :firstname firstname})
  (rf/dispatch [:go-to-relevant-url reason])
  ; TODO circular dep if require events:
  ;(check-and-throw :club.db/db @app-db))
  )

(defn fetch-profile-data!
  [reason]
  (.. club.db/k-users
      (getRecord (clj->js (-> @app-db :auth-data :kinto-id)))
      (then #(set-auth-data!
               reason
               (merge {:access-token (-> @app-db :auth-data :access-token)
                       :expires-at (-> @app-db :auth-data :expires-at)}
                      (data-from-js-obj %))))
      (catch (error-fn "db/fetch-profile-data!"))))

(defn save-profile-data!
  []
  (.. club.db/k-users
      (updateRecord (clj->js
                      {:id       (-> @app-db :auth-data :kinto-id)
                       :auth0-id (-> @app-db :auth-data :auth0-id)
                       :quality   (-> @app-db :profile-page :quality)
                       :school    (-> @app-db :profile-page :school)
                       :teacher   (-> @app-db :profile-page :teacher)
                       :lastname  (-> @app-db :profile-page :lastname)
                       :firstname (-> @app-db :profile-page :firstname)}))
      (then #(rf/dispatch [:profile-save-ok]))
      (catch (error-fn "db/save-profile-data!"))))

(defn get-users!
  [{:keys [on-success] :or {on-success identity}}]
  (.. club.db/k-users
      (listRecords)
      (then on-success)
      (catch (error-fn "db/get-users!"))))

(defn groups-page-data-enhancer
  [scholar]
  (let [scholar-id (first scholar)
        scholar-data (second scholar)]
    [scholar-id {:lastname  (:lastname  scholar-data)
                 :firstname (:firstname scholar-data)
                 :groups    (set (:groups scholar-data))}]))

(defn groups-data->groups-page-data
  [data]
  (dissoc (into {} (map groups-page-data-enhancer data)) :id :last_modified))

(defn groups-reducer
  [m x]
  (into m
    (let [id (keyword (:id x))]
      {id {:lastname (:lastname x)
           :firstname (:firstname x)
           :groups #{}}})))

(defn ghost-scholars
  [scholars]
  (let [teacher-id (-> @app-db :auth-data :kinto-id)]
    (if (empty? scholars)
      [{:id (str "ghost-1-" teacher-id)
        :lastname "(fantôme)"
        :firstname "Casper"}
       {:id (str "ghost-2-" teacher-id)
        :lastname "(fantôme)"
        :firstname "Érika"}]
      scholars)))

(defn init-groups-data!
  []
  (let [teacher-id (-> @app-db :auth-data :kinto-id)]
    (get-users! {:on-success
                  #(rf/dispatch
                    [:init-groups
                      (->> % data-from-js-obj
                             (filter (fn [x] (= teacher-id (:teacher x))))
                             ghost-scholars
                             (reduce groups-reducer {}))])})))

(defn fetch-groups-data!
  []
  (.. club.db/k-groups
      (getRecord (clj->js (-> @app-db :auth-data :kinto-id)))
      (then
        #(rf/dispatch
          [:write-groups
            (-> % data-from-js-obj
                  groups-data->groups-page-data)]))
      (catch #(if (= error-404 (str %))  ; no such id in the groups coll?
                nil  ; could be that the teacher has no groups data yet.
                (error "db/fetch-groups-data!" %)))))

(defn groups-page-data-trimmer
  [scholar]
  (let [scholar-id (first scholar)
        scholar-data (second scholar)]
    [scholar-id {:lastname  (:lastname  scholar-data)
                 :firstname (:firstname scholar-data)
                 :groups    (:groups    scholar-data)}]))

(defn save-groups-data!
  []
  (let [groups-data (->> @app-db
                         :groups-page
                         (map groups-page-data-trimmer)
                         (into {}))
        record (merge {:id (-> @app-db :auth-data :kinto-id)} groups-data)]
    (.. club.db/k-groups
        (updateRecord (clj->js record))
        (then #(rf/dispatch [:groups-save-ok]))
        (catch (error-fn "db/save-groups-data!")))))

(defn fix-ranks
  [exprs]
  (vec (map #(identity {:content (:content %1) :rank %2}) exprs (range 666))))

(defn wrap-series
  [series]
  (let [wrap-w-content #(identity {:content %})]
    (-> series (update :exprs #(map wrap-w-content %))
               (update :exprs fix-ranks))))

(defn series-data->series-page-data
  [data]
  (-> data (dissoc :owner-id :last_modified)))

(defn fetch-series-data! ; lancé deux fois par page?
  []
  (let [kinto-id (-> @app-db :auth-data :kinto-id)]
    (.. club.db/k-series
        (listRecords)
        (then
          #(rf/dispatch
            [:write-series
              (->> % data-from-js-obj
                     (filter (fn [x] (= kinto-id (:owner-id x))))
                     (map series-data->series-page-data)
                     vec)]))
        (catch #(if (= error-404 (str %))  ; no such id in the series coll?
                  (swap! app-db assoc-in [:series-page] {})
                  (error "db/fetch-series-data!" %))))))

(defn series-trimmer
  [series]
  (update series :exprs #(map :content %)))

(defn save-series-data!
  []
  (let [current-series-id (-> @app-db :current-series-id)
        current-series (-> @app-db :current-series)
        record-fragment {:owner-id (-> @app-db :auth-data :kinto-id)
                         :series (series-trimmer current-series)}
        record (if (empty? current-series-id)
                 record-fragment
                 (merge record-fragment {:id current-series-id}))]
    (.. k-series
        (createRecord (clj->js record))
        (then #(rf/dispatch [:series-save-ok %]))
        (catch (error-fn "db/save-series-data!")))))

(defn delete-series!
  []
  (let [current-series-id (-> @app-db :current-series-id)]
    (.. club.db/k-series
        (deleteRecord current-series-id)
        (then #(rf/dispatch [:series-delete-ok %]))
        (catch (error-fn "db/delete-series!")))))

(defn label-feeder
  [series-clj]
  (fn [work]
    (let [title (->> series-clj
                     (filter #(= (:series-id work) (-> % :id)))
                     first
                     :series
                     :title)]
      (merge work {:series-title title}))))

(defn scholars-feeder
  [groups-record]
  (fn [work]
    (let [groups-clj (data-from-js-obj groups-record)
          group (:group work)
          scholars (->> groups-clj
                       (filter #(some #{group} (:groups (second %))))
                       (map #(identity [(first %)
                                        (dissoc (second %) :groups)]))
                       (into {}))]
      (assoc work :scholars scholars))))

(defn fetch-works-teacher!
  []
  (let [teacher-id (-> @app-db :auth-data :kinto-id)]
    (.. club.db/k-groups
      (getRecord teacher-id)
      (then
        (fn [groups-record]
          (.. club.db/k-series
            (listRecords)
            (then
              (fn [series-list]
                (.. club.db/k-works
                  (listRecords)
                  (then
                    (fn [works-list]
                      (let [groups-clj (data-from-js-obj groups-record)
                            series-clj (data-from-js-obj series-list)
                            works (->> works-list
                                       data-from-js-obj
                                       (filter #(= teacher-id
                                                   (:teacher-id %)))
                                       (map #(dissoc % :last_modified
                                                       :teacher-id))
                                       (map (label-feeder series-clj))
                                       (map (scholars-feeder groups-record))
                                       vec)]
                        (rf/dispatch [:write-works-teacher works]))))
                  (catch (error-fn "db/fetch-works-teacher! works step")))))
            (catch (error-fn "db/fetch-works-teacher! series step")))))
      (catch #(if (= error-404 (str %))  ; no such id in the groups coll?
                  (rf/dispatch [:write-works-teacher []])
                  (error "db/fetch-works-teacher! groups step" %))))))

(defn fetch-progress!
  [work]
  (let [work-id (:id work)]
    (.. club.db/k-progress
        (getRecord work-id)
        (then
          (fn [progress-record]
            (let [progress-clj (-> progress-record
                                   data-from-js-obj
                                   (dissoc :id :last_modified))]
              (rf/dispatch [:progress-write work-id progress-clj]))))
        (catch #(if (= error-404 (str %))  ; no such id in the works coll?
                    (rf/dispatch [:progress-write work-id {}])
                    (error "db/fetch-progress!" %))))))

(defn save-work!
  [{:keys [teacher-id work-state]}]
  (let [record (-> work-state
                   (merge {:teacher-id teacher-id
                           :series-id (:series-id work-state)})
                   (dissoc (if (empty? (:id work-state)) :id)
                           :editing
                           :series-title
                           :scholars
                           :progress
                           :show-progress))]
    (.. club.db/k-works
        (createRecord (clj->js record))
        (then #(rf/dispatch [:work-save-ok (data-from-js-obj %)]))
        (catch (error-fn "event :work-save")))))

(defn delete-work!
  [work-id]
  (.. club.db/k-works
      (deleteRecord work-id)
      (then #(rf/dispatch [:work-delete-ok %]))
      (catch (error-fn "db/delete-work!"))))

(defn for-the-groups
  [groups]
  ; returns a filter which keeps a work if its group belongs to `groups`
  (fn [work]
    (some #{(:group work)} groups)))

(defn fetch-works-scholar!
  []
  (let [scholar-id (-> @app-db :auth-data :kinto-id)
        teacher-id (-> @app-db :profile-page :teacher)]
    (.. club.db/k-groups
        (getRecord teacher-id)
        (then
          (fn [groups-data]
            (let [groups-data-clj (data-from-js-obj groups-data)
                  scholar-id-kw (keyword scholar-id)
                  scholar-groups (:groups (scholar-id-kw groups-data-clj))]
              (.. club.db/k-series
                  (listRecords)
                  (then
                    (fn [series-list]
                      (.. club.db/k-works
                          (listRecords)
                          (then
                            (fn [works-list]
                              (let [series-clj (data-from-js-obj series-list)
                                    works (->> works-list
                                               data-from-js-obj
                                               (filter #(= teacher-id (:teacher-id %)))
                                               (filter (for-the-groups scholar-groups))
                                               (map (label-feeder series-clj))
                                               (map #(dissoc % :last_modified :teacher-id))
                                               vec)]
                                (rf/dispatch [:write-works-scholar works]))))
                          (catch (error-fn "db/fetch-works-scholar! works step")))))
                  (catch (error-fn "db/fetch-works-scholar! series step"))))))
        (catch #(if (= error-404 (str %))  ; no such id in the groups coll?
                    (rf/dispatch [:write-works-scholar []])
                    (error "db/fetch-works-scholar! groups step" %))))))

(defn with-progress-and-work-id
  [progress work-id]
  (if (= "training" work-id)
    (let [series {:title "Entraînement"
                  :desc  "Série proposée à tous les élèves"
                  :exprs all-exprs}]
      (rf/dispatch [:write-scholar-work series progress]))
    (.. club.db/k-works
        (getRecord work-id)
        (then
          (fn [work-record]
            (.. club.db/k-series
                (getRecord (-> work-record
                               data-from-js-obj
                               :series-id))
                (then
                  (fn [series-record]
                    (let [series (-> series-record
                                     data-from-js-obj
                                     :series)]
                      (rf/dispatch [:write-scholar-work series progress]))))
                (catch (error-fn "db/fetch-scholar-works! works step")))))
        (catch (error-fn "db/fetch-scholar-works! series step")))))

(defn fetch-scholar-work!
  [work-id]
  (.. club.db/k-progress
      (getRecord work-id)
      (then #(-> %
                 data-from-js-obj
                 (with-progress-and-work-id work-id)))
      (catch #(if (= error-404 (str %))  ; no such id in the progress coll?
                  (with-progress-and-work-id {} work-id)
                  (error "db/fetch-scholar-works! progress step" %)))))

(defn save-attempt!
  [attempt]
  (.. club.db/k-attempts
      (createRecord (clj->js attempt))
      (catch (error-fn "db/save-attempt!"))))

(defn save-progress!
  [progress]
  ; We pass data and an options
  ; `:patch true` allows us to merge into existing entries
  (.. club.db/k-progress
      (updateRecord (clj->js progress)       ; data
                    (clj->js {:patch true})) ; options in a map
      (catch #(if (= error-404 (str %))  ; no such id in the progress coll?
                  (.. club.db/k-progress          ; errors if id not found
                      (createRecord (clj->js progress))  ; so we create it
                      (catch (error-fn "db/save-progress! create"))
                  (error "db/save-progress! update no 404" %))))))

(defn get-schools!
  []
  [
    ; 655 établissements de l’académie de Nantes
    ; http://annuaire-ec.ac-nantes.fr/indexAnnuaire.jsp
    ;{:id "fake-id-0440001M" :code "0440001M" :name "Lycée JOUBERT-EMILIEN MAILLARD"}
    ;{:id "fake-id-0440005S" :code "0440005S" :name "Lycée GUY MOQUET - ETIENNE LENOIR"}
    ;{:id "fake-id-0440008V" :code "0440008V" :name "Collège CACAULT"}
    ;{:id "fake-id-0440010X" :code "0440010X" :name "Collège PAUL LANGEVIN"}
    ;{:id "fake-id-0440012Z" :code "0440012Z" :name "Lycée GRAND AIR"}
    ;{:id "fake-id-0440013A" :code "0440013A" :name "Collège BELLEVUE"}
    ;{:id "fake-id-0440015C" :code "0440015C" :name "Collège JACQUES PREVERT"}
    ;{:id "fake-id-0440016D" :code "0440016D" :name "Collège ANNE DE BRETAGNE"}
    ;{:id "fake-id-0440018F" :code "0440018F" :name "Collège RAYMOND QUENEAU"}
    ;{:id "fake-id-0440021J" :code "0440021J" :name "Lycée CLEMENCEAU"}
    ;{:id "fake-id-0440022K" :code "0440022K" :name "Lycée JULES VERNE"}
    ;{:id "fake-id-0440023L" :code "0440023L" :name "Collège CHANTENAY"}
    ;{:id "fake-id-0440024M" :code "0440024M" :name "Lycée GABRIEL GUISTHAU"}
    ;{:id "fake-id-0440025N" :code "0440025N" :name "Collège HECTOR BERLIOZ"}
    ;{:id "fake-id-0440028S" :code "0440028S" :name "Collège LA COLINIERE"}
    ;{:id "fake-id-0440029T" :code "0440029T" :name "Lycée LIVET"}
    {:id "fake-id-0440030U" :code "0440030U" :name "Lycée GASPARD MONGE - LA CHAUVINIERE"}
    ;{:id "fake-id-0440033X" :code "0440033X" :name "Lycée Pro FRANCOIS ARAGO"}
    ;{:id "fake-id-0440034Y" :code "0440034Y" :name "Lycée Pro MICHELET"}
    ;{:id "fake-id-0440035Z" :code "0440035Z" :name "Lycée Pro LEONARD DE VINCI"}
    ;{:id "fake-id-0440036A" :code "0440036A" :name "Lycée Pro DE BOUGAINVILLE"}
    ;{:id "fake-id-0440045K" :code "0440045K" :name "Collège VICTOR HUGO"}
    ;{:id "fake-id-0440049P" :code "0440049P" :name "Collège ARISTIDE BRIAND"}
    ;{:id "fake-id-0440055W" :code "0440055W" :name "Collège JEAN ROSTAND"}
    ;{:id "fake-id-0440056X" :code "0440056X" :name "Lycée Pro ALBERT CHASSAGNE"}
    ;{:id "fake-id-0440061C" :code "0440061C" :name "Collège JULES VERNE"}
    ;{:id "fake-id-0440062D" :code "0440062D" :name "Lycée JEAN PERRIN"}
    ;{:id "fake-id-0440063E" :code "0440063E" :name "Lycée Pro LOUIS-JACQUES GOUSSIER"}
    ;{:id "fake-id-0440064F" :code "0440064F" :name "Collège PONT ROUSSEAU"}
    ;{:id "fake-id-0440065G" :code "0440065G" :name "Collège PETITE LANDE"}
    ;{:id "fake-id-0440066H" :code "0440066H" :name "Collège HELENE ET RENE GUY CADOU"}
    ;{:id "fake-id-0440069L" :code "0440069L" :name "Lycée ARISTIDE BRIAND"}
    ;{:id "fake-id-0440074S" :code "0440074S" :name "Lycée Pro BROSSAUD-BLANCHO"}
    ;{:id "fake-id-0440077V" :code "0440077V" :name "Lycée JACQUES PREVERT"}
    ;{:id "fake-id-0440080Y" :code "0440080Y" :name "Collège JEAN MONNET"}
    ;{:id "fake-id-0440086E" :code "0440086E" :name "Lycée LA COLINIERE"}
    ;{:id "fake-id-0440107C" :code "0440107C" :name "Collège ND DU BON ACCUEIL"}
    ;{:id "fake-id-0440119R" :code "0440119R" :name "Lycée HOTELIER STE ANNE"}
    ;{:id "fake-id-0440147W" :code "0440147W" :name "Collège RENE GUY CADOU"}
    ;{:id "fake-id-0440149Y" :code "0440149Y" :name "Lycée ST JOSEPH"}
    ;{:id "fake-id-0440151A" :code "0440151A" :name "Lycée ST JOSEPH"}
    ;{:id "fake-id-0440153C" :code "0440153C" :name "Collège ST GABRIEL"}
    ;{:id "fake-id-0440154D" :code "0440154D" :name "Lycée BLANCHE DE CASTILLE"}
    ;{:id "fake-id-0440161L" :code "0440161L" :name "Lycée ST JOSEPH DU LOQUIDY"}
    ;{:id "fake-id-0440163N" :code "0440163N" :name "Lycée ST STANISLAS"}
    ;{:id "fake-id-0440166S" :code "0440166S" :name "Lycée ND DE TOUTES AIDES"}
    ;{:id "fake-id-0440168U" :code "0440168U" :name "Collège SACRE COEUR"}
    ;{:id "fake-id-0440172Y" :code "0440172Y" :name "Lycée LA PERVERIE SACRE COEUR"}
    ;{:id "fake-id-0440175B" :code "0440175B" :name "Lycée GABRIEL DESHAYES"}
    ;{:id "fake-id-0440176C" :code "0440176C" :name "Lycée ST DOMINIQUE"}
    ;{:id "fake-id-0440177D" :code "0440177D" :name "Lycée ST LOUIS"}
    ;{:id "fake-id-0440178E" :code "0440178E" :name "Lycée ND D'ESPERANCE"}
    ;{:id "fake-id-0440179F" :code "0440179F" :name "Collège DE LA MAINE"}
    ;{:id "fake-id-0440182J" :code "0440182J" :name "Collège LA SALLE - ST LAURENT"}
    ;{:id "fake-id-0440184L" :code "0440184L" :name "Collège ST HERMELAND"}
    ;{:id "fake-id-0440188R" :code "0440188R" :name "Collège IMMACULEE CONCEPTION- LA SALLE"}
    ;{:id "fake-id-0440190T" :code "0440190T" :name "Collège STE PHILOMENE"}
    ;{:id "fake-id-0440192V" :code "0440192V" :name "Collège ST MICHEL"}
    ;{:id "fake-id-0440193W" :code "0440193W" :name "Collège ST JEAN-BAPTISTE"}
    ;{:id "fake-id-0440195Y" :code "0440195Y" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0440196Z" :code "0440196Z" :name "Collège STE ANNE"}
    ;{:id "fake-id-0440198B" :code "0440198B" :name "Collège NOTRE DAME"}
    ;{:id "fake-id-0440199C" :code "0440199C" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0440200D" :code "0440200D" :name "Collège NOTRE DAME"}
    ;{:id "fake-id-0440201E" :code "0440201E" :name "Lycée ST JOSEPH"}
    ;{:id "fake-id-0440203G" :code "0440203G" :name "Collège ND DE L'ABBAYE"}
    ;{:id "fake-id-0440206K" :code "0440206K" :name "Collège ND DU BON CONSEIL"}
    ;{:id "fake-id-0440207L" :code "0440207L" :name "Collège ST RAPHAEL"}
    ;{:id "fake-id-0440209N" :code "0440209N" :name "Collège HELDER CAMARA"}
    ;{:id "fake-id-0440210P" :code "0440210P" :name "Collège STE MADELEINE- LA JOLIVERIE"}
    ;{:id "fake-id-0440211R" :code "0440211R" :name "Collège ST THEOPHANE VENARD"}
    ;{:id "fake-id-0440219Z" :code "0440219Z" :name "Collège ST J.DE COMPOSTELLE"}
    ;{:id "fake-id-0440221B" :code "0440221B" :name "Collège ST MICHEL"}
    ;{:id "fake-id-0440223D" :code "0440223D" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0440224E" :code "0440224E" :name "Collège ST MARTIN"}
    ;{:id "fake-id-0440226G" :code "0440226G" :name "Collège ND DE RECOUVRANCE"}
    ;{:id "fake-id-0440228J" :code "0440228J" :name "Collège LE SACRE COEUR"}
    ;{:id "fake-id-0440229K" :code "0440229K" :name "Collège ST PAUL"}
    ;{:id "fake-id-0440231M" :code "0440231M" :name "Collège STE ANNE"}
    ;{:id "fake-id-0440232N" :code "0440232N" :name "Collège ST AUGUSTIN"}
    ;{:id "fake-id-0440233P" :code "0440233P" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0440235S" :code "0440235S" :name "Collège STE THERESE"}
    ;{:id "fake-id-0440236T" :code "0440236T" :name "Collège LE SACRE COEUR"}
    ;{:id "fake-id-0440238V" :code "0440238V" :name "Collège ST ROCH"}
    ;{:id "fake-id-0440239W" :code "0440239W" :name "Collège LAMORICIERE"}
    ;{:id "fake-id-0440241Y" :code "0440241Y" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0440242Z" :code "0440242Z" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0440243A" :code "0440243A" :name "Collège STE ANNE"}
    ;{:id "fake-id-0440244B" :code "0440244B" :name "Collège ST BLAISE"}
    ;{:id "fake-id-0440246D" :code "0440246D" :name "Lycée SACRE COEUR"}
    ;{:id "fake-id-0440254M" :code "0440254M" :name "TSGE TS IMS"}
    ;{:id "fake-id-0440255N" :code "0440255N" :name "Lycée Pro ENCIA"}
    ;{:id "fake-id-0440256P" :code "0440256P" :name "Lycée ST PIERRE LA JOLIVERIE"}
    ;{:id "fake-id-0440259T" :code "0440259T" :name "Lycée ND D'ESPERANCE"}
    ;{:id "fake-id-0440261V" :code "0440261V" :name "Lycée Pro ST THOMAS D'AQUIN"}
    ;{:id "fake-id-0440262W" :code "0440262W" :name "Lycée Pro NAZARETH"}
    ;{:id "fake-id-0440267B" :code "0440267B" :name "Lycée Pro COIFFURE P.MASSON"}
    ;{:id "fake-id-0440274J" :code "0440274J" :name "Lycée NOTRE DAME"}
    ;{:id "fake-id-0440279P" :code "0440279P" :name "Lycée LA BAUGERIE"}
    ;{:id "fake-id-0440282T" :code "0440282T" :name "Lycée Pro LE MASLE"}
    ;{:id "fake-id-0440283U" :code "0440283U" :name "Collège LIBERTAIRE RUTIGLIANO"}
    ;{:id "fake-id-0440284V" :code "0440284V" :name "Collège STENDHAL"}
    ;{:id "fake-id-0440285W" :code "0440285W" :name "Collège GASTON SERPETTE"}
    ;{:id "fake-id-0440286X" :code "0440286X" :name "Collège CLAUDE DEBUSSY"}
    ;{:id "fake-id-0440287Y" :code "0440287Y" :name "Collège LE HERAULT"}
    ;{:id "fake-id-0440288Z" :code "0440288Z" :name "Lycée ALBERT CAMUS"}
    ;{:id "fake-id-0440289A" :code "0440289A" :name "Collège JEAN MOUNES"}
    ;{:id "fake-id-0440291C" :code "0440291C" :name "Collège ILES DE LOIRE"}
    ;{:id "fake-id-0440292D" :code "0440292D" :name "Collège JEAN MERMOZ"}
    ;{:id "fake-id-0440293E" :code "0440293E" :name "Collège ROBERT SCHUMAN"}
    ;{:id "fake-id-0440307V" :code "0440307V" :name "Lycée Pro STE THERESE"}
    ;{:id "fake-id-0440308W" :code "0440308W" :name "Collège LE GALINET"}
    ;{:id "fake-id-0440309X" :code "0440309X" :name "Collège ROSA PARKS"}
    ;{:id "fake-id-0440310Y" :code "0440310Y" :name "Lycée Pro JEAN JACQUES AUDUBON"}
    ;{:id "fake-id-0440311Z" :code "0440311Z" :name "Collège ERNEST RENAN"}
    ;{:id "fake-id-0440314C" :code "0440314C" :name "Collège JEAN MOULIN"}
    ;{:id "fake-id-0440315D" :code "0440315D" :name "Lycée Pro ANDRE BOULLOCHE"}
    ;{:id "fake-id-0440316E" :code "0440316E" :name "Collège LA NEUSTRIE"}
    ;{:id "fake-id-0440329U" :code "0440329U" :name "EREA LA RIVIERE"}
    ;{:id "fake-id-0440347N" :code "0440347N" :name "Collège SAINT EXUPERY"}
    ;{:id "fake-id-0440348P" :code "0440348P" :name "Collège LA VILLE AUX ROSES"}
    ;{:id "fake-id-0440350S" :code "0440350S" :name "Collège ALBERT VINCON"}
    ;{:id "fake-id-0440352U" :code "0440352U" :name "Lycée Pro LOUIS ARMAND"}
    ;{:id "fake-id-0440355X" :code "0440355X" :name "Lycée Pro CHARLES PEGUY"}
    ;{:id "fake-id-0440422V" :code "0440422V" :name "Collège STE ANNE"}
    ;{:id "fake-id-0440423W" :code "0440423W" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0440534S" :code "0440534S" :name "Collège SALVADOR ALLENDE"}
    ;{:id "fake-id-0440536U" :code "0440536U" :name "Collège SOPHIE GERMAIN"}
    ;{:id "fake-id-0440537V" :code "0440537V" :name "Lycée Pro LES SAVARIERES"}
    ;{:id "fake-id-0440538W" :code "0440538W" :name "Collège JACQUES BREL"}
    ;{:id "fake-id-0440539X" :code "0440539X" :name "Collège RENE GUY CADOU"}
    ;{:id "fake-id-0440540Y" :code "0440540Y" :name "Collège QUERAL"}
    ;{:id "fake-id-0440541Z" :code "0440541Z" :name "Lycée Pro DES TROIS RIVIERES"}
    ;{:id "fake-id-0440980B" :code "0440980B" :name "Lycée Pro BRIACE DU LANDREAU"}
    ;{:id "fake-id-0440981C" :code "0440981C" :name "Lycée Pro GABRIEL DESHAYES"}
    ;{:id "fake-id-0441032H" :code "0441032H" :name "Lycée Pro BLAIN DERVAL"}
    ;{:id "fake-id-0441545R" :code "0441545R" :name "Collège LA NOE LAMBERT"}
    ;{:id "fake-id-0441547T" :code "0441547T" :name "Collège LE GRAND BEAUREGARD"}
    ;{:id "fake-id-0441548U" :code "0441548U" :name "Collège RENE BERNIER"}
    ;{:id "fake-id-0441550W" :code "0441550W" :name "Lycée Pro OLIVIER GUICHARD"}
    {:id "fake-id-0441552Y" :code "0441552Y" :name "Lycée LES BOURDONNIERES"}
    ;{:id "fake-id-0441608J" :code "0441608J" :name "Collège LA DURANTIERE"}
    ;{:id "fake-id-0441610L" :code "0441610L" :name "Collège GUTENBERG"}
    ;{:id "fake-id-0441612N" :code "0441612N" :name "Collège PIERRE DE COUBERTIN"}
    ;{:id "fake-id-0441613P" :code "0441613P" :name "Collège PIERRE NORANGE"}
    ;{:id "fake-id-0441616T" :code "0441616T" :name "Collège JULIEN LAMBOT"}
    ;{:id "fake-id-0441653H" :code "0441653H" :name "Lycée ST JOSEPH LA JOLIVERIE"}
    ;{:id "fake-id-0441654J" :code "0441654J" :name "Collège PAUL DOUMER"}
    ;{:id "fake-id-0441655K" :code "0441655K" :name "Collège LOUIS PASTEUR"}
    ;{:id "fake-id-0441656L" :code "0441656L" :name "Lycée Pro PABLO NERUDA"}
    ;{:id "fake-id-0441657M" :code "0441657M" :name "Collège ANTOINE DE SAINT-EXUPERY"}
    ;{:id "fake-id-0441658N" :code "0441658N" :name "Collège PIERRE ET MARIE CURIE"}
    ;{:id "fake-id-0441686U" :code "0441686U" :name "Collège AUGUSTE MAILLOUX"}
    ;{:id "fake-id-0441724K" :code "0441724K" :name "Collège LA REINETIERE"}
    ;{:id "fake-id-0441727N" :code "0441727N" :name "Collège ARTHUR RIMBAUD"}
    ;{:id "fake-id-0441728P" :code "0441728P" :name "Collège RENE CHAR"}
    ;{:id "fake-id-0441781X" :code "0441781X" :name "Lycée Pro SECT.HORTICOLE LA GRILLONNAIS"}
    ;{:id "fake-id-0441782Y" :code "0441782Y" :name "Lycée Pro GRAND BLOTTEREAU"}
    ;{:id "fake-id-0441783Z" :code "0441783Z" :name "Lycée Pro BRIACÉ LA MARCHANDERIE"}
    ;{:id "fake-id-0441784A" :code "0441784A" :name "Lycée Pro JEAN-BAPTISTE ERIAU"}
    ;{:id "fake-id-0441785B" :code "0441785B" :name "Lycée Pro LES PRATEAUX"}
    ;{:id "fake-id-0441787D" :code "0441787D" :name "Lycée Pro SAINT-EXUPERY"}
    ;{:id "fake-id-0441788E" :code "0441788E" :name "Lycée Pro LE BOIS TILLAC"}
    ;{:id "fake-id-0441789F" :code "0441789F" :name "Lycée Pro SAINT MARTIN"}
    ;{:id "fake-id-0441790G" :code "0441790G" :name "Lycée Pro SAINT JOSEPH"}
    ;{:id "fake-id-0441791H" :code "0441791H" :name "Lycée Pro DE L ERDRE"}
    ;{:id "fake-id-0441794L" :code "0441794L" :name "Lycée Pro KERGUENEC"}
    ;{:id "fake-id-0441795M" :code "0441795M" :name "Lycée Pro LE PELLERIN SITE DE ST PERE EN"}
    ;{:id "fake-id-0441820P" :code "0441820P" :name "Collège GBRIEL GUIST'HAU"}
    ;{:id "fake-id-0441821R" :code "0441821R" :name "Collège JULES VERNE"}
    ;{:id "fake-id-0441822S" :code "0441822S" :name "Collège GRAND AIR"}
    ;{:id "fake-id-0441823T" :code "0441823T" :name "Lycée Pro HEINLEX"}
    ;{:id "fake-id-0441858F" :code "0441858F" :name "Collège BELLESTRE"}
    ;{:id "fake-id-0441859G" :code "0441859G" :name "Collège ERIC TABARLY"}
    ;{:id "fake-id-0441862K" :code "0441862K" :name "Collège LOUISE MICHEL"}
    ;{:id "fake-id-0441917V" :code "0441917V" :name "Collège LA FONTAINE"}
    ;{:id "fake-id-0441928G" :code "0441928G" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0441929H" :code "0441929H" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0441930J" :code "0441930J" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0441931K" :code "0441931K" :name "Collège ST JOSEPH DU LOQUIDY"}
    ;{:id "fake-id-0441932L" :code "0441932L" :name "Collège BLANCHE DE CASTILLE"}
    ;{:id "fake-id-0441933M" :code "0441933M" :name "Collège EXTERNAT ENFANTS NANTAIS"}
    ;{:id "fake-id-0441934N" :code "0441934N" :name "Collège FRAN.D'AMBOISE-CHAVAGNES"}
    ;{:id "fake-id-0441935P" :code "0441935P" :name "Collège NOTRE-DAME DE TOUTES AIDES"}
    ;{:id "fake-id-0441936R" :code "0441936R" :name "Collège LA PERVERIE SACRE COEUR"}
    ;{:id "fake-id-0441937S" :code "0441937S" :name "Collège ST DONATIEN"}
    ;{:id "fake-id-0441938T" :code "0441938T" :name "Collège ST STANISLAS"}
    ;{:id "fake-id-0441939U" :code "0441939U" :name "Collège GABRIEL DESHAYES"}
    ;{:id "fake-id-0441940V" :code "0441940V" :name "Collège ST DOMINIQUE"}
    ;{:id "fake-id-0441941W" :code "0441941W" :name "Collège ST LOUIS"}
    ;{:id "fake-id-0441946B" :code "0441946B" :name "TSGE TS ENSEC"}
    ;{:id "fake-id-0441982R" :code "0441982R" :name "Lycée DE BRETAGNE"}
    ;{:id "fake-id-0441992B" :code "0441992B" :name "Lycée PAYS DE RETZ"}
    {:id "fake-id-0441993C" :code "0441993C" :name "Lycée CARCOUET"}
    ;{:id "fake-id-0442011X" :code "0442011X" :name "Collège LA COUTANCIERE"}
    ;{:id "fake-id-0442023K" :code "0442023K" :name "Collège PAUL GAUGUIN"}
    ;{:id "fake-id-0442029S" :code "0442029S" :name "Collège GERARD PHILIPE"}
    ;{:id "fake-id-0442052S" :code "0442052S" :name "Collège PIERRE ABELARD"}
    ;{:id "fake-id-0442061B" :code "0442061B" :name "Lycée SAINT HERBLAIN - JULES RIEFFEL"}
    ;{:id "fake-id-0442071M" :code "0442071M" :name "Lycée Pro DANIEL BROTTIER"}
    ;{:id "fake-id-0442083A" :code "0442083A" :name "Lycée CENS"}
    ;{:id "fake-id-0442092K" :code "0442092K" :name "Lycée Pro JACQUES-CASSARD"}
    ;{:id "fake-id-0442094M" :code "0442094M" :name "Lycée NICOLAS APPERT"}
    ;{:id "fake-id-0442095N" :code "0442095N" :name "Lycée LA HERDRIE"}
    ;{:id "fake-id-0442112G" :code "0442112G" :name "Lycée GALILEE"}
    ;{:id "fake-id-0442119P" :code "0442119P" :name "Collège DE BRETAGNE"}
    ;{:id "fake-id-0442124V" :code "0442124V" :name "TSGE TS ENACOM"}
    ;{:id "fake-id-0442186M" :code "0442186M" :name "Collège CONDORCET"}
    ;{:id "fake-id-0442207K" :code "0442207K" :name "Lycée CAMILLE CLAUDEL"}
    ;{:id "fake-id-0442226F" :code "0442226F" :name "Lycée LA MENNAIS"}
    ;{:id "fake-id-0442227G" :code "0442227G" :name "Lycée IFOM"}
    ;{:id "fake-id-0442273G" :code "0442273G" :name "Lycée CHARLES PEGUY"}
    ;{:id "fake-id-0442277L" :code "0442277L" :name "Collège DE GOULAINE"}
    ;{:id "fake-id-0442286W" :code "0442286W" :name "EXP LycéeEE EXPERIMENTAL"}
    ;{:id "fake-id-0442309W" :code "0442309W" :name "Lycée ALCIDE D'ORBIGNY"}
    ;{:id "fake-id-0442368K" :code "0442368K" :name "Collège DU PAYS BLANC"}
    ;{:id "fake-id-0442388G" :code "0442388G" :name "Collège CENS"}
    ;{:id "fake-id-0442417N" :code "0442417N" :name "Collège LES SABLES D'OR"}
    ;{:id "fake-id-0442418P" :code "0442418P" :name "Collège LE HAUT GESVRES"}
    ;{:id "fake-id-0442542Z" :code "0442542Z" :name "Collège ANDREE CHEDID"}
    ;{:id "fake-id-0442595G" :code "0442595G" :name "Collège LUCIE AUBRAC"}
    ;{:id "fake-id-0442625P" :code "0442625P" :name "Collège OLYMPE DE GOUGES"}
    ;{:id "fake-id-0442637C" :code "0442637C" :name "Collège DIWAN"}
    ;{:id "fake-id-0442691L" :code "0442691L" :name "Collège AGNES VARDA"}
    ;{:id "fake-id-0442699V" :code "0442699V" :name "Lycée EXTERNAT DES ENFANTS NANTAIS"}
    ;{:id "fake-id-0442725Y" :code "0442725Y" :name "Lycée TALENSAC - JEANNE BERNARD"}
    ;{:id "fake-id-0442728B" :code "0442728B" :name "Collège MARCELLE BARON"}
    ;{:id "fake-id-0442732F" :code "0442732F" :name "TSGE TALENSAC - JEANNE BERNARD"}
    ;{:id "fake-id-0442752C" :code "0442752C" :name "Lycée AIME CESAIRE"}
    ;{:id "fake-id-0442759K" :code "0442759K" :name "Collège ANITA CONTI"}
    {:id "fake-id-0442765S" :code "0442765S" :name "Lycée NELSON MANDELA"}
    ;{:id "fake-id-0442774B" :code "0442774B" :name "Lycée SAINT-FELIX - LA SALLE"}
    ;{:id "fake-id-0442775C" :code "0442775C" :name "Lycée Pro SAINT-FELIX - LA SALLE"}
    ;{:id "fake-id-0442778F" :code "0442778F" :name "Lycée SAINT-MARTIN"}
    ;{:id "fake-id-0442779G" :code "0442779G" :name "Lycée Pro BOUAYE"}
    ;{:id "fake-id-0442781J" :code "0442781J" :name "Collège ROSA PARKS"}
    ;{:id "fake-id-0442782K" :code "0442782K" :name "Collège JULIE-VICTOIRE DAUBIE"}
    ;{:id "fake-id-0442806L" :code "0442806L" :name "Collège MONA OZOUF"}
    ;{:id "fake-id-0442807M" :code "0442807M" :name "Collège PONTCHATEAU"}
    ;{:id "fake-id-0490001K" :code "0490001K" :name "Lycée DAVID D ANGERS"}
    ;{:id "fake-id-0490002L" :code "0490002L" :name "Lycée JOACHIM DU BELLAY"}
    ;{:id "fake-id-0490003M" :code "0490003M" :name "Lycée CHEVROLLIER"}
    ;{:id "fake-id-0490004N" :code "0490004N" :name "Collège CHEVREUL"}
    ;{:id "fake-id-0490005P" :code "0490005P" :name "Lycée Pro SIMONE VEIL"}
    ;{:id "fake-id-0490010V" :code "0490010V" :name "Collège CHATEAUCOIN"}
    ;{:id "fake-id-0490013Y" :code "0490013Y" :name "Lycée Pro DE NARCE"}
    ;{:id "fake-id-0490014Z" :code "0490014Z" :name "Collège DE L AUBANCE"}
    ;{:id "fake-id-0490017C" :code "0490017C" :name "Collège PIERRE ET MARIE CURIE"}
    ;{:id "fake-id-0490018D" :code "0490018D" :name "Lycée EUROPE ROBERT SCHUMAN"}
    ;{:id "fake-id-0490022H" :code "0490022H" :name "Collège GEORGES CLEMENCEAU"}
    ;{:id "fake-id-0490023J" :code "0490023J" :name "Collège LUCIEN MILLET"}
    ;{:id "fake-id-0490026M" :code "0490026M" :name "Collège MARYSE BASTIE"}
    ;{:id "fake-id-0490027N" :code "0490027N" :name "Collège VAL D OUDON"}
    ;{:id "fake-id-0490028P" :code "0490028P" :name "Collège FRANCOIS TRUFFAUT"}
    ;{:id "fake-id-0490029R" :code "0490029R" :name "Collège CAMILLE CLAUDEL"}
    ;{:id "fake-id-0490032U" :code "0490032U" :name "Collège JEAN ZAY"}
    ;{:id "fake-id-0490034W" :code "0490034W" :name "Collège DE L EVRE"}
    ;{:id "fake-id-0490037Z" :code "0490037Z" :name "Collège PHILIPPE COUSTEAU"}
    ;{:id "fake-id-0490039B" :code "0490039B" :name "Collège ANJOU-BRETAGNE"}
    ;{:id "fake-id-0490040C" :code "0490040C" :name "Lycée DUPLESSIS MORNAY"}
    ;{:id "fake-id-0490042E" :code "0490042E" :name "Collège PIERRE MENDES FRANCE"}
    ;{:id "fake-id-0490046J" :code "0490046J" :name "Collège LES FONTAINES"}
    ;{:id "fake-id-0490048L" :code "0490048L" :name "Collège JEAN ROSTAND"}
    ;{:id "fake-id-0490054T" :code "0490054T" :name "Lycée FERNAND RENAUDEAU"}
    ;{:id "fake-id-0490055U" :code "0490055U" :name "Lycée SADI CARNOT - JEAN BERTIN"}
    ;{:id "fake-id-0490057W" :code "0490057W" :name "Collège GEORGES POMPIDOU"}
    ;{:id "fake-id-0490060Z" :code "0490060Z" :name "Collège JEAN LURCAT"}
    ;{:id "fake-id-0490061A" :code "0490061A" :name "Collège AUGUSTE ET JEAN RENOIR"}
    ;{:id "fake-id-0490782J" :code "0490782J" :name "Lycée BLAISE PASCAL"}
    ;{:id "fake-id-0490783K" :code "0490783K" :name "Collège JEAN MERMOZ"}
    ;{:id "fake-id-0490784L" :code "0490784L" :name "Lycée Pro HENRI DUNANT"}
    ;{:id "fake-id-0490801E" :code "0490801E" :name "Lycée Pro PAUL EMILE VICTOR"}
    ;{:id "fake-id-0490819Z" :code "0490819Z" :name "Lycée STE AGNES"}
    ;{:id "fake-id-0490824E" :code "0490824E" :name "Lycée ST MARTIN"}
    ;{:id "fake-id-0490828J" :code "0490828J" :name "Lycée ND DE BONNES NOUVELLES"}
    ;{:id "fake-id-0490829K" :code "0490829K" :name "Collège JEANNE D'ARC"}
    ;{:id "fake-id-0490834R" :code "0490834R" :name "Lycée ND D'ORVEAU"}
    ;{:id "fake-id-0490835S" :code "0490835S" :name "Lycée ST JOSEPH"}
    ;{:id "fake-id-0490836T" :code "0490836T" :name "Collège STE ANNE"}
    ;{:id "fake-id-0490837U" :code "0490837U" :name "Lycée NOTRE DAME"}
    ;{:id "fake-id-0490838V" :code "0490838V" :name "Lycée ST LOUIS"}
    ;{:id "fake-id-0490839W" :code "0490839W" :name "Collège ST ANDRE"}
    ;{:id "fake-id-0490840X" :code "0490840X" :name "Lycée BOURG CHEVREAU"}
    ;{:id "fake-id-0490842Z" :code "0490842Z" :name "Collège STE MARIE"}
    ;{:id "fake-id-0490843A" :code "0490843A" :name "Collège LA CATHEDRALE - LA SALLE"}
    ;{:id "fake-id-0490844B" :code "0490844B" :name "Collège ST AUGUSTIN"}
    ;{:id "fake-id-0490845C" :code "0490845C" :name "Collège IMMACULEE CONCEPTION"}
    ;{:id "fake-id-0490849G" :code "0490849G" :name "Collège NOTRE DAME"}
    ;{:id "fake-id-0490851J" :code "0490851J" :name "Collège CHARLES DE FOUCAULD"}
    ;{:id "fake-id-0490853L" :code "0490853L" :name "Collège ST VINCENT"}
    ;{:id "fake-id-0490854M" :code "0490854M" :name "Collège STE EMILIE"}
    ;{:id "fake-id-0490856P" :code "0490856P" :name "Collège ST BENOIT"}
    ;{:id "fake-id-0490857R" :code "0490857R" :name "Collège ST FRANCOIS"}
    ;{:id "fake-id-0490858S" :code "0490858S" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0490860U" :code "0490860U" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0490862W" :code "0490862W" :name "Collège ND DU BRETONNAIS"}
    ;{:id "fake-id-0490863X" :code "0490863X" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0490865Z" :code "0490865Z" :name "Collège ST LOUIS"}
    ;{:id "fake-id-0490866A" :code "0490866A" :name "Collège FRANCOIS D'ASSISE"}
    ;{:id "fake-id-0490867B" :code "0490867B" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0490868C" :code "0490868C" :name "Collège PERE DANIEL BROTTIER"}
    ;{:id "fake-id-0490869D" :code "0490869D" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0490871F" :code "0490871F" :name "Collège LE SACRE COEUR"}
    ;{:id "fake-id-0490873H" :code "0490873H" :name "Collège JACQUES CATHELINEAU"}
    ;{:id "fake-id-0490874J" :code "0490874J" :name "Collège JEAN BLOUIN"}
    ;{:id "fake-id-0490875K" :code "0490875K" :name "Collège JEAN BOSCO"}
    ;{:id "fake-id-0490876L" :code "0490876L" :name "Collège FREDERIC OZANAM"}
    ;{:id "fake-id-0490878N" :code "0490878N" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0490879P" :code "0490879P" :name "Collège ST PAUL"}
    ;{:id "fake-id-0490881S" :code "0490881S" :name "Collège ST JEAN"}
    ;{:id "fake-id-0490886X" :code "0490886X" :name "Lycée Pro LA PROVIDENCE"}
    ;{:id "fake-id-0490890B" :code "0490890B" :name "TSGE TS CNAM IFORIS"}
    ;{:id "fake-id-0490903R" :code "0490903R" :name "Lycée Pro LE PINIER NEUF"}
    ;{:id "fake-id-0490904S" :code "0490904S" :name "Lycée JEANNE DELANOUE"}
    ;{:id "fake-id-0490910Y" :code "0490910Y" :name "Lycée Pro LES ARDILLIERS"}
    ;{:id "fake-id-0490921K" :code "0490921K" :name "Collège FRANCOIS RABELAIS"}
    ;{:id "fake-id-0490922L" :code "0490922L" :name "Collège MOLIERE"}
    ;{:id "fake-id-0490925P" :code "0490925P" :name "EREA LES TERRES ROUGES"}
    ;{:id "fake-id-0490946M" :code "0490946M" :name "Lycée ANGERS-LE-FRESNE"}
    ;{:id "fake-id-0490952U" :code "0490952U" :name "Lycée CHAMP BLANC"}
    ;{:id "fake-id-0490953V" :code "0490953V" :name "Collège FELIX LANDREAU"}
    ;{:id "fake-id-0490955X" :code "0490955X" :name "Collège SAINT-EXUPERY"}
    ;{:id "fake-id-0490956Y" :code "0490956Y" :name "Collège LES ROCHES"}
    ;{:id "fake-id-0490957Z" :code "0490957Z" :name "Collège LA VENAISERIE"}
    ;{:id "fake-id-0490960C" :code "0490960C" :name "Collège HONORE DE BALZAC"}
    ;{:id "fake-id-0490962E" :code "0490962E" :name "Collège GEORGES GIRONDE"}
    ;{:id "fake-id-0490963F" :code "0490963F" :name "Lycée Pro EDGAR PISANI"}
    ;{:id "fake-id-0491024X" :code "0491024X" :name "Collège VALLEE DU LOIR"}
    ;{:id "fake-id-0491025Y" :code "0491025Y" :name "Collège COLBERT"}
    ;{:id "fake-id-0491026Z" :code "0491026Z" :name "Collège REPUBLIQUE"}
    ;{:id "fake-id-0491027A" :code "0491027A" :name "Lycée Pro POUILLE"}
    ;{:id "fake-id-0491028B" :code "0491028B" :name "Collège MONTAIGNE"}
    ;{:id "fake-id-0491083L" :code "0491083L" :name "Collège LA MADELEINE LA RETRAITE"}
    ;{:id "fake-id-0491260D" :code "0491260D" :name "Collège FRANCOIS VILLON"}
    ;{:id "fake-id-0491261E" :code "0491261E" :name "Collège PAUL ELUARD"}
    ;{:id "fake-id-0491262F" :code "0491262F" :name "Collège CALYPSO"}
    ;{:id "fake-id-0491641T" :code "0491641T" :name "Collège ST JEAN DE LA BARRE"}
    ;{:id "fake-id-0491645X" :code "0491645X" :name "Collège JEAN RACINE"}
    ;{:id "fake-id-0491646Y" :code "0491646Y" :name "Lycée Pro LUDOVIC MENARD"}
    ;{:id "fake-id-0491648A" :code "0491648A" :name "Collège BENJAMIN DELESSERT"}
    ;{:id "fake-id-0491674D" :code "0491674D" :name "Collège CLEMENT JANEQUIN"}
    ;{:id "fake-id-0491675E" :code "0491675E" :name "Collège JOACHIM DU BELLAY"}
    ;{:id "fake-id-0491703K" :code "0491703K" :name "Collège JEAN VILAR"}
    ;{:id "fake-id-0491705M" :code "0491705M" :name "Collège JACQUES PREVERT"}
    ;{:id "fake-id-0491706N" :code "0491706N" :name "Collège LE PONT DE MOINE"}
    ;{:id "fake-id-0491707P" :code "0491707P" :name "Collège VALLEE DU LYS"}
    ;{:id "fake-id-0491764B" :code "0491764B" :name "Collège CLAUDE DEBUSSY"}
    ;{:id "fake-id-0491766D" :code "0491766D" :name "Collège PORTE D ANJOU"}
    ;{:id "fake-id-0491801S" :code "0491801S" :name "Lycée Pro LES BUISSONNETS"}
    ;{:id "fake-id-0491802T" :code "0491802T" :name "Lycée Pro ROBERT D ARBRISSEL CHEMILLE"}
    ;{:id "fake-id-0491809A" :code "0491809A" :name "Lycée Pro LES 3 PROVINCES"}
    ;{:id "fake-id-0491825T" :code "0491825T" :name "Collège DAVID D ANGERS"}
    ;{:id "fake-id-0491826U" :code "0491826U" :name "Collège YOLANDE D ANJOU"}
    ;{:id "fake-id-0491859E" :code "0491859E" :name "Collège TREMOLIERES"}
    ;{:id "fake-id-0491921X" :code "0491921X" :name "Collège ST AUBIN LA SALLE"}
    ;{:id "fake-id-0491922Y" :code "0491922Y" :name "Collège URBAIN MONGAZON"}
    ;{:id "fake-id-0491923Z" :code "0491923Z" :name "Collège ST LAUD"}
    ;{:id "fake-id-0491924A" :code "0491924A" :name "Collège ST MARTIN"}
    ;{:id "fake-id-0491927D" :code "0491927D" :name "Collège ND D'ORVEAU"}
    ;{:id "fake-id-0491928E" :code "0491928E" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0491929F" :code "0491929F" :name "Collège NOTRE DAME"}
    ;{:id "fake-id-0491930G" :code "0491930G" :name "Collège ST LOUIS"}
    ;{:id "fake-id-0491966W" :code "0491966W" :name "Lycée HENRI BERGSON"}
    ;{:id "fake-id-0492004M" :code "0492004M" :name "Collège ST CHARLES"}
    ;{:id "fake-id-0492015Z" :code "0492015Z" :name "Lycée SACRE COEUR"}
    ;{:id "fake-id-0492061Z" :code "0492061Z" :name "Lycée AUGUSTE ET JEAN RENOIR"}
    ;{:id "fake-id-0492081W" :code "0492081W" :name "Collège JEAN MONNET"}
    ;{:id "fake-id-0492089E" :code "0492089E" :name "Lycée EMMANUEL MOUNIER"}
    ;{:id "fake-id-0492113F" :code "0492113F" :name "TSGE OPTIQUE DE L'OUEST"}
    ;{:id "fake-id-0492123S" :code "0492123S" :name "Lycée JEAN MOULIN"}
    ;{:id "fake-id-0492140K" :code "0492140K" :name "Collège JEANNE D'ARC"}
    ;{:id "fake-id-0492148U" :code "0492148U" :name "Lycée JEAN BODIN"}
    ;{:id "fake-id-0492224B" :code "0492224B" :name "Lycée DE L'HYROME"}
    ;{:id "fake-id-0492285T" :code "0492285T" :name "Lycée LES ARDILLIERS"}
    ;{:id "fake-id-0492298G" :code "0492298G" :name "TSGE ETSCO"}
    ;{:id "fake-id-0492406Z" :code "0492406Z" :name "Lycée SAINTE MARIE"}
    ;{:id "fake-id-0492407A" :code "0492407A" :name "Lycée URBAIN MONGAZON"}
    ;{:id "fake-id-0492420P" :code "0492420P" :name "Lycée SAINT AUBIN LA SALLE"}
    ;{:id "fake-id-0492430A" :code "0492430A" :name "Lycée BEAUPREAU-EN-MAUGES"}
    ;{:id "fake-id-0492432C" :code "0492432C" :name "Lycée Pro JOSEPH WRESINSKI"}
    ;{:id "fake-id-0530001N" :code "0530001N" :name "Collège LEO FERRE"}
    ;{:id "fake-id-0530002P" :code "0530002P" :name "Collège DES 7 FONTAINES"}
    ;{:id "fake-id-0530003R" :code "0530003R" :name "Collège JEAN-LOUIS BERNARD"}
    ;{:id "fake-id-0530004S" :code "0530004S" :name "Lycée VICTOR HUGO"}
    ;{:id "fake-id-0530005T" :code "0530005T" :name "Collège VOLNEY"}
    ;{:id "fake-id-0530007V" :code "0530007V" :name "Collège PAUL LANGEVIN"}
    ;{:id "fake-id-0530010Y" :code "0530010Y" :name "Lycée AMBROISE PARE"}
    ;{:id "fake-id-0530011Z" :code "0530011Z" :name "Lycée DOUANIER ROUSSEAU"}
    ;{:id "fake-id-0530012A" :code "0530012A" :name "Lycée REAUMUR"}
    ;{:id "fake-id-0530013B" :code "0530013B" :name "Lycée Pro ROBERT BURON"}
    ;{:id "fake-id-0530015D" :code "0530015D" :name "Collège PIERRE DUBOIS"}
    ;{:id "fake-id-0530016E" :code "0530016E" :name "Lycée LAVOISIER"}
    ;{:id "fake-id-0530021K" :code "0530021K" :name "Collège DE MISEDON"}
    ;{:id "fake-id-0530025P" :code "0530025P" :name "Collège LES GARETTES"}
    ;{:id "fake-id-0530030V" :code "0530030V" :name "Collège L ORIETTE"}
    ;{:id "fake-id-0530031W" :code "0530031W" :name "Collège LE GRAND CHAMP"}
    ;{:id "fake-id-0530040F" :code "0530040F" :name "Lycée Pro PIERRE ET MARIE CURIE"}
    ;{:id "fake-id-0530041G" :code "0530041G" :name "Collège EMMANUEL DE MARTONNE"}
    ;{:id "fake-id-0530046M" :code "0530046M" :name "Lycée ST MICHEL"}
    ;{:id "fake-id-0530048P" :code "0530048P" :name "Lycée IMMACULEE CONCEPTION"}
    ;{:id "fake-id-0530049R" :code "0530049R" :name "Lycée D'AVESNIERES"}
    ;{:id "fake-id-0530051T" :code "0530051T" :name "Collège STE THERESE"}
    ;{:id "fake-id-0530052U" :code "0530052U" :name "Lycée DON BOSCO"}
    ;{:id "fake-id-0530053V" :code "0530053V" :name "Collège DON BOSCO JOUVENCE"}
    ;{:id "fake-id-0530054W" :code "0530054W" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0530055X" :code "0530055X" :name "Collège LE PRIEURE"}
    ;{:id "fake-id-0530059B" :code "0530059B" :name "Collège SACRE COEUR"}
    ;{:id "fake-id-0530060C" :code "0530060C" :name "Collège SACRE COEUR"}
    ;{:id "fake-id-0530061D" :code "0530061D" :name "Collège ST JEAN-BAPTISTE DE LA SALLE"}
    ;{:id "fake-id-0530063F" :code "0530063F" :name "Collège NOTRE DAME"}
    ;{:id "fake-id-0530064G" :code "0530064G" :name "Collège ST MARTIN"}
    ;{:id "fake-id-0530065H" :code "0530065H" :name "Collège NOTRE DAME"}
    ;{:id "fake-id-0530066J" :code "0530066J" :name "Collège ST NICOLAS"}
    ;{:id "fake-id-0530068L" :code "0530068L" :name "Lycée HAUTE FOLLIS"}
    ;{:id "fake-id-0530073S" :code "0530073S" :name "Lycée Pro DON BOSCO"}
    ;{:id "fake-id-0530077W" :code "0530077W" :name "Collège RENE CASSIN"}
    ;{:id "fake-id-0530078X" :code "0530078X" :name "Collège JULES FERRY"}
    ;{:id "fake-id-0530079Y" :code "0530079Y" :name "Lycée Pro LEONARD DE VINCI"}
    ;{:id "fake-id-0530081A" :code "0530081A" :name "Lycée LAVAL"}
    ;{:id "fake-id-0530082B" :code "0530082B" :name "Collège JULES RENARD"}
    ;{:id "fake-id-0530484N" :code "0530484N" :name "Collège ALAIN GERBAULT"}
    ;{:id "fake-id-0530520C" :code "0530520C" :name "Lycée Pro HAUT ANJOU"}
    ;{:id "fake-id-0530583W" :code "0530583W" :name "Collège DES AVALOIRS"}
    ;{:id "fake-id-0530584X" :code "0530584X" :name "Collège ALFRED JARRY"}
    ;{:id "fake-id-0530770Z" :code "0530770Z" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0530778H" :code "0530778H" :name "Lycée Pro GASTON LESNARD"}
    ;{:id "fake-id-0530779J" :code "0530779J" :name "Collège JEAN ROSTAND"}
    ;{:id "fake-id-0530790W" :code "0530790W" :name "Collège JACQUES MONOD"}
    ;{:id "fake-id-0530791X" :code "0530791X" :name "Collège MAURICE GENEVOIX"}
    ;{:id "fake-id-0530792Y" :code "0530792Y" :name "Collège BEATRIX DE GAVRE"}
    ;{:id "fake-id-0530793Z" :code "0530793Z" :name "Collège FRANCIS LALLART"}
    ;{:id "fake-id-0530803K" :code "0530803K" :name "Collège VICTOR HUGO"}
    ;{:id "fake-id-0530804L" :code "0530804L" :name "Collège LOUIS LAUNAY"}
    ;{:id "fake-id-0530813W" :code "0530813W" :name "Lycée Pro ROBERT SCHUMAN"}
    ;{:id "fake-id-0530815Y" :code "0530815Y" :name "Lycée Pro LP RURAL PRIVE"}
    ;{:id "fake-id-0530816Z" :code "0530816Z" :name "Lycée Pro D ORION"}
    ;{:id "fake-id-0530818B" :code "0530818B" :name "Lycée Pro ROCHEFEUILLE"}
    ;{:id "fake-id-0530826K" :code "0530826K" :name "Collège SEVIGNE"}
    ;{:id "fake-id-0530827L" :code "0530827L" :name "Collège PAUL EMILE VICTOR"}
    ;{:id "fake-id-0530874M" :code "0530874M" :name "Collège ST MICHEL"}
    ;{:id "fake-id-0530875N" :code "0530875N" :name "Collège IMMACULEE CONCEPTION"}
    ;{:id "fake-id-0530876P" :code "0530876P" :name "Collège DON BOSCO ERMITAGE"}
    ;{:id "fake-id-0530904V" :code "0530904V" :name "Lycée Pro IMMACULEE CONCEPTION"}
    ;{:id "fake-id-0530914F" :code "0530914F" :name "Collège FERNAND PUECH"}
    ;{:id "fake-id-0530931Z" :code "0530931Z" :name "TSGE SAINT-BERTHEVIN"}
    ;{:id "fake-id-0530949U" :code "0530949U" :name "Lycée RAOUL VADEPIED"}
    ;{:id "fake-id-0531006F" :code "0531006F" :name "TSGE LAVAL"}
    ;{:id "fake-id-0720001K" :code "0720001K" :name "Collège JOHN KENNEDY"}
    ;{:id "fake-id-0720002L" :code "0720002L" :name "Collège NORMANDIE-MAINE"}
    ;{:id "fake-id-0720003M" :code "0720003M" :name "Lycée Pro CLAUDE CHAPPE"}
    ;{:id "fake-id-0720004N" :code "0720004N" :name "Collège RENE CASSIN"}
    ;{:id "fake-id-0720007S" :code "0720007S" :name "Collège GUILLAUME APOLLINAIRE"}
    ;{:id "fake-id-0720010V" :code "0720010V" :name "Lycée DU MANS - LA GERMINIERE"}
    ;{:id "fake-id-0720011W" :code "0720011W" :name "Collège PIERRE DE RONSARD"}
    ;{:id "fake-id-0720012X" :code "0720012X" :name "Lycée RACAN"}
    ;{:id "fake-id-0720013Y" :code "0720013Y" :name "Lycée Pro MAL LECLERC HAUTECLOCQUE"}
    ;{:id "fake-id-0720014Z" :code "0720014Z" :name "Collège ANDRE PIOGER"}
    ;{:id "fake-id-0720015A" :code "0720015A" :name "Collège FRANCOIS GRUDE"}
    ;{:id "fake-id-0720017C" :code "0720017C" :name "Lycée ROBERT GARNIER"}
    ;{:id "fake-id-0720019E" :code "0720019E" :name "Collège LEO DELIBES"}
    ;{:id "fake-id-0720021G" :code "0720021G" :name "Lycée D'ESTOURNELLES DE CONSTANT"}
    ;{:id "fake-id-0720023J" :code "0720023J" :name "Collège PETIT VERSAILLES"}
    ;{:id "fake-id-0720024K" :code "0720024K" :name "Collège BELLE-VUE"}
    ;{:id "fake-id-0720027N" :code "0720027N" :name "Lycée PERSEIGNE"}
    ;{:id "fake-id-0720029R" :code "0720029R" :name "Lycée MONTESQUIEU"}
    ;{:id "fake-id-0720030S" :code "0720030S" :name "Lycée BELLEVUE"}
    ;{:id "fake-id-0720033V" :code "0720033V" :name "Lycée GABRIEL TOUCHARD - WASHINGTON"}
    ;{:id "fake-id-0720034W" :code "0720034W" :name "Lycée Pro FUNAY-HELENE BOUCHER"}
    ;{:id "fake-id-0720038A" :code "0720038A" :name "Collège ROGER VERCEL"}
    ;{:id "fake-id-0720040C" :code "0720040C" :name "Collège AMBROISE PARE"}
    ;{:id "fake-id-0720043F" :code "0720043F" :name "Collège JEAN MOULIN"}
    ;{:id "fake-id-0720046J" :code "0720046J" :name "Collège DES ALycée ProES MANCELLES"}
    ;{:id "fake-id-0720048L" :code "0720048L" :name "Lycée RAPHAEL ELIZE"}
    ;{:id "fake-id-0720051P" :code "0720051P" :name "Collège JULES FERRY"}
    ;{:id "fake-id-0720053S" :code "0720053S" :name "Collège VERON DE FORBONNAIS"}
    ;{:id "fake-id-0720055U" :code "0720055U" :name "Lycée PAUL SCARRON"}
    ;{:id "fake-id-0720058X" :code "0720058X" :name "Collège GABRIEL GOUSSAULT"}
    ;{:id "fake-id-0720062B" :code "0720062B" :name "Collège LE VIEUX CHENE"}
    ;{:id "fake-id-0720067G" :code "0720067G" :name "Collège DE BERCE"}
    ;{:id "fake-id-0720068H" :code "0720068H" :name "Collège LEON TOLSTOI"}
    ;{:id "fake-id-0720069J" :code "0720069J" :name "Collège ALEXANDRE MAUBOUSSIN"}
    ;{:id "fake-id-0720070K" :code "0720070K" :name "Collège PIERRE REVERDY"}
    ;{:id "fake-id-0720081X" :code "0720081X" :name "Collège ALAIN-FOURNIER"}
    ;{:id "fake-id-0720797A" :code "0720797A" :name "Collège VAUGUYON"}
    ;{:id "fake-id-0720798B" :code "0720798B" :name "Collège VIEUX COLOMBIER"}
    ;{:id "fake-id-0720799C" :code "0720799C" :name "Collège JEAN DE L'EPINE"}
    ;{:id "fake-id-0720800D" :code "0720800D" :name "Collège ALBERT CAMUS"}
    ;{:id "fake-id-0720803G" :code "0720803G" :name "Collège FRERE ANDRE"}
    ;{:id "fake-id-0720804H" :code "0720804H" :name "Collège ST JEAN"}
    ;{:id "fake-id-0720806K" :code "0720806K" :name "Collège NOTRE DAME"}
    ;{:id "fake-id-0720808M" :code "0720808M" :name "Collège ST MICHEL"}
    ;{:id "fake-id-0720811R" :code "0720811R" :name "Collège ST JOSEPH LA SALLE"}
    ;{:id "fake-id-0720812S" :code "0720812S" :name "Collège LES MURIERS"}
    ;{:id "fake-id-0720815V" :code "0720815V" :name "Collège SACRE COEUR"}
    ;{:id "fake-id-0720817X" :code "0720817X" :name "Collège ST COEUR DE MARIE"}
    ;{:id "fake-id-0720822C" :code "0720822C" :name "Lycée STE CATHERINE"}
    ;{:id "fake-id-0720825F" :code "0720825F" :name "Lycée Pro JOSEPH ROUSSEL"}
    ;{:id "fake-id-0720833P" :code "0720833P" :name "Lycée NOTRE DAME"}
    ;{:id "fake-id-0720835S" :code "0720835S" :name "Collège ST LOUIS"}
    ;{:id "fake-id-0720836T" :code "0720836T" :name "Collège PSALLETTE ST VINCENT"}
    ;{:id "fake-id-0720837U" :code "0720837U" :name "Lycée NOTRE DAME"}
    ;{:id "fake-id-0720838V" :code "0720838V" :name "Collège ST JULIEN"}
    ;{:id "fake-id-0720843A" :code "0720843A" :name "Lycée STE ANNE"}
    ;{:id "fake-id-0720847E" :code "0720847E" :name "Collège LOUIS CORDELET"}
    ;{:id "fake-id-0720885W" :code "0720885W" :name "Collège LE RONCERAY"}
    ;{:id "fake-id-0720896H" :code "0720896H" :name "Lycée PRYTANEE NATIONAL MILITAIRE"}
    ;{:id "fake-id-0720902P" :code "0720902P" :name "Collège MAROC HUCHEPIE"}
    ;{:id "fake-id-0720903R" :code "0720903R" :name "Collège HENRI LEFEUVRE"}
    ;{:id "fake-id-0720904S" :code "0720904S" :name "Collège LE JONCHERAY"}
    ;{:id "fake-id-0720905T" :code "0720905T" :name "Collège A.J.TROUVE-CHAUVEL"}
    ;{:id "fake-id-0720906U" :code "0720906U" :name "Collège JEAN COCTEAU"}
    ;{:id "fake-id-0720907V" :code "0720907V" :name "Lycée Pro BRETTE LES PINS"}
    ;{:id "fake-id-0720920J" :code "0720920J" :name "EREA RAPHAEL ELIZE"}
    ;{:id "fake-id-0720983C" :code "0720983C" :name "Collège MAUPERTUIS-ST BENOIT"}
    ;{:id "fake-id-0720984D" :code "0720984D" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0720986F" :code "0720986F" :name "Collège LA MADELEINE"}
    ;{:id "fake-id-0720987G" :code "0720987G" :name "Collège COSTA-GAVRAS"}
    ;{:id "fake-id-0720988H" :code "0720988H" :name "Collège SUZANNE BOUTELOUP"}
    ;{:id "fake-id-0720989J" :code "0720989J" :name "Collège ANJOU"}
    ;{:id "fake-id-0721009F" :code "0721009F" :name "Lycée Pro VAL DE SARTHE"}
    ;{:id "fake-id-0721042S" :code "0721042S" :name "Collège JEAN ROSTAND"}
    ;{:id "fake-id-0721043T" :code "0721043T" :name "Collège ALFRED DE MUSSET"}
    ;{:id "fake-id-0721044U" :code "0721044U" :name "Collège PAUL CHEVALLIER"}
    ;{:id "fake-id-0721086P" :code "0721086P" :name "Collège WILBUR WRIGHT"}
    ;{:id "fake-id-0721089T" :code "0721089T" :name "Collège COURTANVAUX"}
    ;{:id "fake-id-0721090U" :code "0721090U" :name "Collège VILLARET-CLAIREFONTAINE"}
    ;{:id "fake-id-0721093X" :code "0721093X" :name "Collège LA FORESTERIE"}
    ;{:id "fake-id-0721094Y" :code "0721094Y" :name "Lycée LE MANS SUD"}
    ;{:id "fake-id-0721224P" :code "0721224P" :name "Collège LE MARIN"}
    ;{:id "fake-id-0721225R" :code "0721225R" :name "Collège PASTEUR"}
    ;{:id "fake-id-0721226S" :code "0721226S" :name "Collège LES QUATRE-VENTS"}
    ;{:id "fake-id-0721261E" :code "0721261E" :name "Collège SAINT-JEAN-BAPTISTE DE LA SALL"}
    ;{:id "fake-id-0721262F" :code "0721262F" :name "Collège LES SOURCES"}
    ;{:id "fake-id-0721263G" :code "0721263G" :name "Collège MARCEL PAGNOL"}
    ;{:id "fake-id-0721281B" :code "0721281B" :name "Collège BOLLEE"}
    ;{:id "fake-id-0721301Y" :code "0721301Y" :name "Lycée Pro JEAN RONDEAU"}
    ;{:id "fake-id-0721304B" :code "0721304B" :name "Collège JACQUES PREVERT"}
    ;{:id "fake-id-0721328C" :code "0721328C" :name "Lycée Pro LES HORIZONS"}
    ;{:id "fake-id-0721329D" :code "0721329D" :name "Lycée Pro LES HORIZONS"}
    ;{:id "fake-id-0721336L" :code "0721336L" :name "Lycée Pro NOTRE DAME"}
    ;{:id "fake-id-0721337M" :code "0721337M" :name "Lycée Pro NAZARETH"}
    ;{:id "fake-id-0721363R" :code "0721363R" :name "Collège BERTHELOT"}
    ;{:id "fake-id-0721364S" :code "0721364S" :name "Collège GEORGES DESNOS"}
    ;{:id "fake-id-0721365T" :code "0721365T" :name "Collège PAUL SCARRON"}
    ;{:id "fake-id-0721405L" :code "0721405L" :name "Collège NOTRE DAME"}
    ;{:id "fake-id-0721408P" :code "0721408P" :name "Collège STE ANNE"}
    ;{:id "fake-id-0721477P" :code "0721477P" :name "Collège JACQUES PELETIER"}
    ;{:id "fake-id-0721478R" :code "0721478R" :name "Lycée ST JOSEPH LA SALLE"}
    ;{:id "fake-id-0721483W" :code "0721483W" :name "Collège PIERRE BELON"}
    ;{:id "fake-id-0721493G" :code "0721493G" :name "Lycée MARGUERITE YOURCENAR"}
    ;{:id "fake-id-0721548S" :code "0721548S" :name "Lycée ANDRE MALRAUX"}
    ;{:id "fake-id-0721549T" :code "0721549T" :name "Lycée ST PAUL-NOTRE DAME"}
    ;{:id "fake-id-0721607F" :code "0721607F" :name "Collège ANNE FRANK"}
    ;{:id "fake-id-0721608G" :code "0721608G" :name "Collège ST MARTIN"}
    ;{:id "fake-id-0721655H" :code "0721655H" :name "Collège STE THERESE ST JOSEPH"}
    ;{:id "fake-id-0721657K" :code "0721657K" :name "Collège NOTRE DAME - ST PAUL"}
    ;{:id "fake-id-0721684P" :code "0721684P" :name "Lycée SAINT-CHARLES SAINTE-CROIX"}
    ;{:id "fake-id-0850006V" :code "0850006V" :name "Lycée GEORGES CLEMENCEAU"}
    ;{:id "fake-id-0850014D" :code "0850014D" :name "Collège GOLFE DES PICTONS"}
    ;{:id "fake-id-0850015E" :code "0850015E" :name "Collège LES SICARDIERES"}
    ;{:id "fake-id-0850016F" :code "0850016F" :name "Lycée ATLANTIQUE"}
    ;{:id "fake-id-0850024P" :code "0850024P" :name "Collège GASTON CHAISSAC"}
    ;{:id "fake-id-0850025R" :code "0850025R" :name "Lycée PIERRE MENDES-FRANCE"}
    ;{:id "fake-id-0850027T" :code "0850027T" :name "Lycée ROSA PARKS"}
    ;{:id "fake-id-0850028U" :code "0850028U" :name "Lycée Pro EDOUARD BRANLY"}
    ;{:id "fake-id-0850032Y" :code "0850032Y" :name "Lycée SAVARY DE MAULEON"}
    ;{:id "fake-id-0850033Z" :code "0850033Z" :name "Lycée Pro ERIC TABARLY"}
    ;{:id "fake-id-0850039F" :code "0850039F" :name "Collège PAYS DE MONTS"}
    ;{:id "fake-id-0850043K" :code "0850043K" :name "Lycée Pro VALERE MATHE"}
    ;{:id "fake-id-0850047P" :code "0850047P" :name "EREA JEAN D'ORBESTIER"}
    ;{:id "fake-id-0850063G" :code "0850063G" :name "Collège NICOLAS HAXO"}
    ;{:id "fake-id-0850065J" :code "0850065J" :name "Collège PIERRE GARCIE FERRANDE"}
    ;{:id "fake-id-0850066K" :code "0850066K" :name "Collège FRANCOIS VIETE"}
    ;{:id "fake-id-0850067L" :code "0850067L" :name "Collège ANDRE TIRAQUEAU"}
    ;{:id "fake-id-0850068M" :code "0850068M" :name "Lycée FRANCOIS RABELAIS"}
    ;{:id "fake-id-0850069N" :code "0850069N" :name "Collège EMILE BEAUSSIRE"}
    ;{:id "fake-id-0850073T" :code "0850073T" :name "Collège STE MARIE"}
    ;{:id "fake-id-0850074U" :code "0850074U" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0850076W" :code "0850076W" :name "Lycée JEAN XXIII"}
    ;{:id "fake-id-0850077X" :code "0850077X" :name "Lycée STE URSULE"}
    ;{:id "fake-id-0850079Z" :code "0850079Z" :name "Lycée ND DE LA TOURTELIERE"}
    ;{:id "fake-id-0850082C" :code "0850082C" :name "Collège RICHELIEU"}
    ;{:id "fake-id-0850084E" :code "0850084E" :name "Collège AMIRAL MERVEILLEUX DU VIGNAUX"}
    ;{:id "fake-id-0850086G" :code "0850086G" :name "Lycée ST GABRIEL ST MICHEL"}
    ;{:id "fake-id-0850090L" :code "0850090L" :name "Collège STE MARIE"}
    ;{:id "fake-id-0850091M" :code "0850091M" :name "Collège ND DE L'ESPERANCE"}
    ;{:id "fake-id-0850092N" :code "0850092N" :name "Collège ST MARTIN"}
    ;{:id "fake-id-0850097U" :code "0850097U" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0850099W" :code "0850099W" :name "Collège ST PIERRE"}
    ;{:id "fake-id-0850103A" :code "0850103A" :name "Collège ND DU PORT"}
    ;{:id "fake-id-0850104B" :code "0850104B" :name "Collège VILLEBOIS MAREUIL"}
    ;{:id "fake-id-0850106D" :code "0850106D" :name "Collège ST JACQUES-LA FORET"}
    ;{:id "fake-id-0850107E" :code "0850107E" :name "Collège ST JACQUES"}
    ;{:id "fake-id-0850108F" :code "0850108F" :name "Collège ST PAUL"}
    ;{:id "fake-id-0850109G" :code "0850109G" :name "Collège LES SORBETS"}
    ;{:id "fake-id-0850111J" :code "0850111J" :name "Collège DU PUY CHABOT"}
    ;{:id "fake-id-0850113L" :code "0850113L" :name "Collège ST SAUVEUR"}
    ;{:id "fake-id-0850114M" :code "0850114M" :name "Collège ST LOUIS"}
    ;{:id "fake-id-0850117R" :code "0850117R" :name "Collège ND DE BOURGENAY"}
    ;{:id "fake-id-0850118S" :code "0850118S" :name "Lycée L'ESPERANCE"}
    ;{:id "fake-id-0850122W" :code "0850122W" :name "Collège ST PAUL"}
    ;{:id "fake-id-0850123X" :code "0850123X" :name "Collège LES LAURIERS"}
    ;{:id "fake-id-0850125Z" :code "0850125Z" :name "Collège ST NICOLAS"}
    ;{:id "fake-id-0850130E" :code "0850130E" :name "Lycée ND DU ROC"}
    ;{:id "fake-id-0850133H" :code "0850133H" :name "Lycée STE MARIE DU PORT"}
    ;{:id "fake-id-0850135K" :code "0850135K" :name "Lycée STE MARIE"}
    ;{:id "fake-id-0850136L" :code "0850136L" :name "Lycée JEANNE D'ARC"}
    ;{:id "fake-id-0850142T" :code "0850142T" :name "Lycée NOTRE DAME"}
    ;{:id "fake-id-0850144V" :code "0850144V" :name "Lycée LA ROCHE SUR YON"}
    ;{:id "fake-id-0850145W" :code "0850145W" :name "Collège RENE COUZINET"}
    ;{:id "fake-id-0850146X" :code "0850146X" :name "Lycée Pro RENE COUZINET"}
    ;{:id "fake-id-0850147Y" :code "0850147Y" :name "Collège CHARLES MILCENDEAU"}
    ;{:id "fake-id-0850148Z" :code "0850148Z" :name "Collège PIERRE MAUGER"}
    ;{:id "fake-id-0850149A" :code "0850149A" :name "Collège PAUL LANGEVIN"}
    ;{:id "fake-id-0850151C" :code "0850151C" :name "Lycée FONTENAY LE COMTE"}
    ;{:id "fake-id-0850152D" :code "0850152D" :name "Lycée LUCON-PETRE"}
    ;{:id "fake-id-0850604V" :code "0850604V" :name "Collège LES GONDOLIERS"}
    ;{:id "fake-id-0850605W" :code "0850605W" :name "Collège EDOUARD HERRIOT"}
    ;{:id "fake-id-0850607Y" :code "0850607Y" :name "Collège LE SOURDY"}
    ;{:id "fake-id-0850609A" :code "0850609A" :name "Lycée LES ETABLIERES"}
    ;{:id "fake-id-0850639H" :code "0850639H" :name "Collège JULES FERRY"}
    ;{:id "fake-id-0850641K" :code "0850641K" :name "Collège CORENTIN RIOU"}
    ;{:id "fake-id-0851132U" :code "0851132U" :name "Collège LES COLLIBERTS"}
    ;{:id "fake-id-0851144G" :code "0851144G" :name "Collège MOLIERE"}
    ;{:id "fake-id-0851145H" :code "0851145H" :name "Collège MARAIS POITEVIN"}
    ;{:id "fake-id-0851146J" :code "0851146J" :name "Collège DE L ANGLEE"}
    ;{:id "fake-id-0851158X" :code "0851158X" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0851159Y" :code "0851159Y" :name "Collège JEAN YOLE"}
    ;{:id "fake-id-0851160Z" :code "0851160Z" :name "Collège ANTOINE DE ST EXUPERY"}
    ;{:id "fake-id-0851163C" :code "0851163C" :name "Collège PIERRE MENDES FRANCE"}
    ;{:id "fake-id-0851181X" :code "0851181X" :name "Collège ST GILLES"}
    ;{:id "fake-id-0851191H" :code "0851191H" :name "Collège ST JOSEPH"}
    ;{:id "fake-id-0851193K" :code "0851193K" :name "Collège JEAN ROSTAND"}
    ;{:id "fake-id-0851195M" :code "0851195M" :name "Collège F.ET I.JOLIOT-CURIE"}
    ;{:id "fake-id-0851220P" :code "0851220P" :name "Collège JEAN MONNET"}
    ;{:id "fake-id-0851274Y" :code "0851274Y" :name "Collège SACRE COEUR"}
    ;{:id "fake-id-0851290R" :code "0851290R" :name "Collège STE URSULE"}
    ;{:id "fake-id-0851293U" :code "0851293U" :name "Collège ST GABRIEL ST MICHEL"}
    ;{:id "fake-id-0851295W" :code "0851295W" :name "Collège L'ESPERANCE"}
    ;{:id "fake-id-0851304F" :code "0851304F" :name "Collège AUGUSTE ET JEAN RENOIR"}
    ;{:id "fake-id-0851344Z" :code "0851344Z" :name "Lycée NOTRE DAME"}
    ;{:id "fake-id-0851346B" :code "0851346B" :name "Lycée FRANCOIS TRUFFAUT"}
    ;{:id "fake-id-0851388X" :code "0851388X" :name "Collège OLIVIER MESSIAEN"}
    ;{:id "fake-id-0851390Z" :code "0851390Z" :name "Lycée LEONARD DE VINCI"}
    ;{:id "fake-id-0851400K" :code "0851400K" :name "Lycée JEAN MONNET"}
    ;{:id "fake-id-0851401L" :code "0851401L" :name "Lycée J.DE LATTRE DE TASSIGNY"}
    ;{:id "fake-id-0851435Y" :code "0851435Y" :name "Collège ANTOINE DE ST EXUPERY"}
    ;{:id "fake-id-0851504Y" :code "0851504Y" :name "Lycée Pro ST GABRIEL"}
    ;{:id "fake-id-0851516L" :code "0851516L" :name "Lycée Pro ST MICHEL"}
    ;{:id "fake-id-0851560J" :code "0851560J" :name "Collège ALEXANDRE SOLJENITSYNE"}
    ;{:id "fake-id-0851620Z" :code "0851620Z" :name "Collège STEPHANE PIOBETTA"}
    ;{:id "fake-id-0851642Y" :code "0851642Y" :name "Lycée SAINT FRANCOIS D'ASSISE"}
    ;{:id "fake-id-0851643Z" :code "0851643Z" :name "Lycée Pro SAINT FRANCOIS D'ASSISE"}
    ;{:id "fake-id-0851647D" :code "0851647D" :name "Collège GEORGES CLEMENCEAU"}
    ;{:id "fake-id-0851655M" :code "0851655M" :name "Collège JACQUES LAURENT"}
  ])

(defn fetch-teachers-list!
  [school-id]
  (get-users!
    {:on-success
      #(rf/dispatch
        [:write-teachers-list
          (->> % data-from-js-obj
                 (filter (fn [x] (= "teacher" (:quality x))))
                 (filter (fn [x] (= school-id (:school x))))
                 (map (fn [x] {:id (:id x) :lastname (:lastname x)}))
                 vec)])}))
