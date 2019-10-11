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
(s/def ::teacher-testing-attempt string?)
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
                          ::attempt])))

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
                              ::teacher-testing-attempt
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
   :teacher-testing-attempt ""
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
      :attempt ""}
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
        url (if false ;club.config/debug?
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
                         (filter (comp not empty? :groups second))
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
    {:id "fake-id-0440001M" :name "Lycée JOUBERT-EMILIEN MAILLARD (44)"}
    {:id "fake-id-0440005S" :name "Lycée GUY MOQUET - ETIENNE LENOIR (44)"}
    {:id "fake-id-0440012Z" :name "Lycée GRAND AIR (44)"}
    {:id "fake-id-0440013A" :name "Collège BELLEVUE (44)"}
    {:id "fake-id-0440016D" :name "Collège ANNE DE BRETAGNE (44)"}
    {:id "fake-id-0440021J" :name "Lycée CLEMENCEAU (44)"}
    {:id "fake-id-0440022K" :name "Lycée JULES VERNE (44)"}
    {:id "fake-id-0440024M" :name "Lycée GABRIEL GUISTHAU (44)"}
    {:id "fake-id-0440029T" :name "Lycée LIVET (44)"}
    {:id "fake-id-0440030U" :name "Lycée GASPARD MONGE - LA CHAUVINIERE (44)"}
    {:id "fake-id-0440033X" :name "Lycée Pro FRANCOIS ARAGO (44)"}
    {:id "fake-id-0440034Y" :name "Lycée Pro MICHELET (44)"}
    {:id "fake-id-0440035Z" :name "Lycée Pro LEONARD DE VINCI (44)"}
    {:id "fake-id-0440036A" :name "Lycée Pro DE BOUGAINVILLE (44)"}
    {:id "fake-id-0440056X" :name "Lycée Pro ALBERT CHASSAGNE (44)"}
    {:id "fake-id-0440062D" :name "Lycée JEAN PERRIN (44)"}
    {:id "fake-id-0440063E" :name "Lycée Pro LOUIS-JACQUES GOUSSIER (44)"}
    {:id "fake-id-0440069L" :name "Lycée ARISTIDE BRIAND (44)"}
    {:id "fake-id-0440074S" :name "Lycée Pro BROSSAUD-BLANCHO (44)"}
    {:id "fake-id-0440077V" :name "Lycée JACQUES PREVERT (44)"}
    {:id "fake-id-0440086E" :name "Lycée LA COLINIERE (44)"}
    {:id "fake-id-0440119R" :name "Lycée HOTELIER STE ANNE (44)"}
    {:id "fake-id-0440149Y" :name "Lycée ST JOSEPH (44)"}
    {:id "fake-id-0440151A" :name "Lycée ST JOSEPH (44)"}
    {:id "fake-id-0440154D" :name "Lycée BLANCHE DE CASTILLE (44)"}
    {:id "fake-id-0440161L" :name "Lycée ST JOSEPH DU LOQUIDY (44)"}
    {:id "fake-id-0440163N" :name "Lycée ST STANISLAS (44)"}
    {:id "fake-id-0440166S" :name "Lycée ND DE TOUTES AIDES (44)"}
    {:id "fake-id-0440172Y" :name "Lycée LA PERVERIE SACRE COEUR (44)"}
    {:id "fake-id-0440175B" :name "Lycée GABRIEL DESHAYES (44)"}
    {:id "fake-id-0440176C" :name "Lycée ST DOMINIQUE (44)"}
    {:id "fake-id-0440177D" :name "Lycée ST LOUIS (44)"}
    {:id "fake-id-0440178E" :name "Lycée ND D'ESPERANCE (44)"}
    {:id "fake-id-0440201E" :name "Lycée ST JOSEPH (44)"}
    {:id "fake-id-0440246D" :name "Lycée SACRE COEUR (44)"}
    {:id "fake-id-0440255N" :name "Lycée Pro ENCIA (44)"}
    {:id "fake-id-0440256P" :name "Lycée ST PIERRE LA JOLIVERIE (44)"}
    {:id "fake-id-0440259T" :name "Lycée ND D'ESPERANCE (44)"}
    {:id "fake-id-0440261V" :name "Lycée Pro ST THOMAS D'AQUIN (44)"}
    {:id "fake-id-0440262W" :name "Lycée Pro NAZARETH (44)"}
    {:id "fake-id-0440267B" :name "Lycée Pro COIFFURE P.MASSON (44)"}
    {:id "fake-id-0440274J" :name "Lycée NOTRE DAME (44)"}
    {:id "fake-id-0440279P" :name "Lycée LA BAUGERIE (44)"}
    {:id "fake-id-0440282T" :name "Lycée Pro LE MASLE (44)"}
    {:id "fake-id-0440283U" :name "Collège LIBERTAIRE RUTIGLIANO (44)"}
    {:id "fake-id-0440284V" :name "Collège STENDHAL (44)"}
    {:id "fake-id-0440285W" :name "Collège GASTON SERPETTE (44)"}
    {:id "fake-id-0440287Y" :name "Collège LE HERAULT (44)"}
    {:id "fake-id-0440288Z" :name "Lycée ALBERT CAMUS (44)"}
    {:id "fake-id-0440307V" :name "Lycée Pro STE THERESE (44)"}
    {:id "fake-id-0440309X" :name "Collège ROSA PARKS (44)"}
    {:id "fake-id-0440310Y" :name "Lycée Pro JEAN JACQUES AUDUBON (44)"}
    {:id "fake-id-0440311Z" :name "Collège ERNEST RENAN (44)"}
    {:id "fake-id-0440315D" :name "Lycée Pro ANDRE BOULLOCHE (44)"}
    {:id "fake-id-0440352U" :name "Lycée Pro LOUIS ARMAND (44)"}
    {:id "fake-id-0440355X" :name "Lycée Pro CHARLES PEGUY (44)"}
    {:id "fake-id-0440537V" :name "Lycée Pro LES SAVARIERES (44)"}
    {:id "fake-id-0440541Z" :name "Lycée Pro DES TROIS RIVIERES (44)"}
    {:id "fake-id-0440980B" :name "Lycée Pro BRIACE DU LANDREAU (44)"}
    {:id "fake-id-0440981C" :name "Lycée Pro GABRIEL DESHAYES (44)"}
    {:id "fake-id-0441032H" :name "Lycée Pro BLAIN DERVAL (44)"}
    {:id "fake-id-0441545R" :name "Collège LA NOE LAMBERT (44)"}
    {:id "fake-id-0441550W" :name "Lycée Pro OLIVIER GUICHARD (44)"}
    {:id "fake-id-0441552Y" :name "Lycée LES BOURDONNIERES (44)"}
    {:id "fake-id-0441608J" :name "Collège LA DURANTIERE (44)"}
    {:id "fake-id-0441653H" :name "Lycée ST JOSEPH LA JOLIVERIE (44)"}
    {:id "fake-id-0441656L" :name "Lycée Pro PABLO NERUDA (44)"}
    {:id "fake-id-0441781X" :name "Lycée Pro SECT.HORTICOLE LA GRILLONNAIS (44)"}
    {:id "fake-id-0441782Y" :name "Lycée Pro GRAND BLOTTEREAU (44)"}
    {:id "fake-id-0441783Z" :name "Lycée Pro BRIACÉ LA MARCHANDERIE (44)"}
    {:id "fake-id-0441784A" :name "Lycée Pro JEAN-BAPTISTE ERIAU (44)"}
    {:id "fake-id-0441785B" :name "Lycée Pro LES PRATEAUX (44)"}
    {:id "fake-id-0441787D" :name "Lycée Pro SAINT-EXUPERY (44)"}
    {:id "fake-id-0441788E" :name "Lycée Pro LE BOIS TILLAC (44)"}
    {:id "fake-id-0441789F" :name "Lycée Pro SAINT MARTIN (44)"}
    {:id "fake-id-0441790G" :name "Lycée Pro SAINT JOSEPH (44)"}
    {:id "fake-id-0441791H" :name "Lycée Pro DE L ERDRE (44)"}
    {:id "fake-id-0441794L" :name "Lycée Pro KERGUENEC (44)"}
    {:id "fake-id-0441795M" :name "Lycée Pro LE PELLERIN SITE DE ST PERE EN (44)"}
    {:id "fake-id-0441823T" :name "Lycée Pro HEINLEX (44)"}
    {:id "fake-id-0441982R" :name "Lycée DE BRETAGNE (44)"}
    {:id "fake-id-0441992B" :name "Lycée PAYS DE RETZ (44)"}
    {:id "fake-id-0441993C" :name "Lycée CARCOUET (44)"}
    {:id "fake-id-0442061B" :name "Lycée SAINT HERBLAIN - JULES RIEFFEL (44)"}
    {:id "fake-id-0442071M" :name "Lycée Pro DANIEL BROTTIER (44)"}
    {:id "fake-id-0442083A" :name "Lycée CENS (44)"}
    {:id "fake-id-0442092K" :name "Lycée Pro JACQUES-CASSARD (44)"}
    {:id "fake-id-0442094M" :name "Lycée NICOLAS APPERT (44)"}
    {:id "fake-id-0442095N" :name "Lycée LA HERDRIE (44)"}
    {:id "fake-id-0442112G" :name "Lycée GALILEE (44)"}
    {:id "fake-id-0442207K" :name "Lycée CAMILLE CLAUDEL (44)"}
    {:id "fake-id-0442226F" :name "Lycée LA MENNAIS (44)"}
    {:id "fake-id-0442227G" :name "Lycée IFOM (44)"}
    {:id "fake-id-0442273G" :name "Lycée CHARLES PEGUY (44)"}
    {:id "fake-id-0442286W" :name "EXP Lycée EXPERIMENTAL (44)"}
    {:id "fake-id-0442309W" :name "Lycée ALCIDE D'ORBIGNY (44)"}
    {:id "fake-id-0442699V" :name "Lycée EXTERNAT DES ENFANTS NANTAIS (44)"}
    {:id "fake-id-0442725Y" :name "Lycée TALENSAC - JEANNE BERNARD (44)"}
    {:id "fake-id-0442752C" :name "Lycée AIME CESAIRE (44)"}
    {:id "fake-id-0442765S" :name "Lycée NELSON MANDELA (44)"}
    {:id "fake-id-0442774B" :name "Lycée SAINT-FELIX - LA SALLE (44)"}
    {:id "fake-id-0442775C" :name "Lycée Pro SAINT-FELIX - LA SALLE (44)"}
    {:id "fake-id-0442778F" :name "Lycée SAINT-MARTIN (44)"}
    {:id "fake-id-0442779G" :name "Lycée Pro BOUAYE (44)"}
    {:id "fake-id-0490001K" :name "Lycée DAVID D ANGERS (49)"}
    {:id "fake-id-0490002L" :name "Lycée JOACHIM DU BELLAY (49)"}
    {:id "fake-id-0490003M" :name "Lycée CHEVROLLIER (49)"}
    {:id "fake-id-0490005P" :name "Lycée Pro SIMONE VEIL (49)"}
    {:id "fake-id-0490013Y" :name "Lycée Pro DE NARCE (49)"}
    {:id "fake-id-0490018D" :name "Lycée EUROPE ROBERT SCHUMAN (49)"}
    {:id "fake-id-0490040C" :name "Lycée DUPLESSIS MORNAY (49)"}
    {:id "fake-id-0490054T" :name "Lycée FERNAND RENAUDEAU (49)"}
    {:id "fake-id-0490055U" :name "Lycée SADI CARNOT - JEAN BERTIN (49)"}
    {:id "fake-id-0490782J" :name "Lycée BLAISE PASCAL (49)"}
    {:id "fake-id-0490784L" :name "Lycée Pro HENRI DUNANT (49)"}
    {:id "fake-id-0490801E" :name "Lycée Pro PAUL EMILE VICTOR (49)"}
    {:id "fake-id-0490819Z" :name "Lycée STE AGNES (49)"}
    {:id "fake-id-0490824E" :name "Lycée ST MARTIN (49)"}
    {:id "fake-id-0490828J" :name "Lycée ND DE BONNES NOUVELLES (49)"}
    {:id "fake-id-0490834R" :name "Lycée ND D'ORVEAU (49)"}
    {:id "fake-id-0490835S" :name "Lycée ST JOSEPH (49)"}
    {:id "fake-id-0490837U" :name "Lycée NOTRE DAME (49)"}
    {:id "fake-id-0490838V" :name "Lycée ST LOUIS (49)"}
    {:id "fake-id-0490840X" :name "Lycée BOURG CHEVREAU (49)"}
    {:id "fake-id-0490886X" :name "Lycée Pro LA PROVIDENCE (49)"}
    {:id "fake-id-0490903R" :name "Lycée Pro LE PINIER NEUF (49)"}
    {:id "fake-id-0490904S" :name "Lycée JEANNE DELANOUE (49)"}
    {:id "fake-id-0490910Y" :name "Lycée Pro LES ARDILLIERS (49)"}
    {:id "fake-id-0490946M" :name "Lycée ANGERS-LE-FRESNE (49)"}
    {:id "fake-id-0490952U" :name "Lycée CHAMP BLANC (49)"}
    {:id "fake-id-0490963F" :name "Lycée Pro EDGAR PISANI (49)"}
    {:id "fake-id-0491027A" :name "Lycée Pro POUILLE (49)"}
    {:id "fake-id-0491646Y" :name "Lycée Pro LUDOVIC MENARD (49)"}
    {:id "fake-id-0491801S" :name "Lycée Pro LES BUISSONNETS (49)"}
    {:id "fake-id-0491802T" :name "Lycée Pro ROBERT D ARBRISSEL CHEMILLE (49)"}
    {:id "fake-id-0491809A" :name "Lycée Pro LES 3 PROVINCES (49)"}
    {:id "fake-id-0491966W" :name "Lycée HENRI BERGSON (49)"}
    {:id "fake-id-0492015Z" :name "Lycée SACRE COEUR (49)"}
    {:id "fake-id-0492061Z" :name "Lycée AUGUSTE ET JEAN RENOIR (49)"}
    {:id "fake-id-0492089E" :name "Lycée EMMANUEL MOUNIER (49)"}
    {:id "fake-id-0492123S" :name "Lycée JEAN MOULIN (49)"}
    {:id "fake-id-0492148U" :name "Lycée JEAN BODIN (49)"}
    {:id "fake-id-0492224B" :name "Lycée DE L'HYROME (49)"}
    {:id "fake-id-0492285T" :name "Lycée LES ARDILLIERS (49)"}
    {:id "fake-id-0492406Z" :name "Lycée SAINTE MARIE (49)"}
    {:id "fake-id-0492407A" :name "Lycée URBAIN MONGAZON (49)"}
    {:id "fake-id-0492420P" :name "Lycée SAINT AUBIN LA SALLE (49)"}
    {:id "fake-id-0492430A" :name "Lycée BEAUPREAU-EN-MAUGES (49)"}
    {:id "fake-id-0492432C" :name "Lycée Pro JOSEPH WRESINSKI (49)"}
    {:id "fake-id-0530004S" :name "Lycée VICTOR HUGO (53)"}
    {:id "fake-id-0530010Y" :name "Lycée AMBROISE PARE (53)"}
    {:id "fake-id-0530011Z" :name "Lycée DOUANIER ROUSSEAU (53)"}
    {:id "fake-id-0530012A" :name "Lycée REAUMUR (53)"}
    {:id "fake-id-0530013B" :name "Lycée Pro ROBERT BURON (53)"}
    {:id "fake-id-0530016E" :name "Lycée LAVOISIER (53)"}
    {:id "fake-id-0530040F" :name "Lycée Pro PIERRE ET MARIE CURIE (53)"}
    {:id "fake-id-0530046M" :name "Lycée ST MICHEL (53)"}
    {:id "fake-id-0530048P" :name "Lycée IMMACULEE CONCEPTION (53)"}
    {:id "fake-id-0530049R" :name "Lycée D'AVESNIERES (53)"}
    {:id "fake-id-0530052U" :name "Lycée DON BOSCO (53)"}
    {:id "fake-id-0530068L" :name "Lycée HAUTE FOLLIS (53)"}
    {:id "fake-id-0530073S" :name "Lycée Pro DON BOSCO (53)"}
    {:id "fake-id-0530079Y" :name "Lycée Pro LEONARD DE VINCI (53)"}
    {:id "fake-id-0530081A" :name "Lycée LAVAL (53)"}
    {:id "fake-id-0530520C" :name "Lycée Pro HAUT ANJOU (53)"}
    {:id "fake-id-0530778H" :name "Lycée Pro GASTON LESNARD (53)"}
    {:id "fake-id-0530813W" :name "Lycée Pro ROBERT SCHUMAN (53)"}
    {:id "fake-id-0530815Y" :name "Lycée Pro LP RURAL PRIVE (53)"}
    {:id "fake-id-0530816Z" :name "Lycée Pro D ORION (53)"}
    {:id "fake-id-0530818B" :name "Lycée Pro ROCHEFEUILLE (53)"}
    {:id "fake-id-0530904V" :name "Lycée Pro IMMACULEE CONCEPTION (53)"}
    {:id "fake-id-0530949U" :name "Lycée RAOUL VADEPIED (53)"}
    {:id "fake-id-0720003M" :name "Lycée Pro CLAUDE CHAPPE (72)"}
    {:id "fake-id-0720010V" :name "Lycée DU MANS - LA GERMINIERE (72)"}
    {:id "fake-id-0720012X" :name "Lycée RACAN (72)"}
    {:id "fake-id-0720013Y" :name "Lycée Pro MAL LECLERC HAUTECLOCQUE (72)"}
    {:id "fake-id-0720017C" :name "Lycée ROBERT GARNIER (72)"}
    {:id "fake-id-0720021G" :name "Lycée D'ESTOURNELLES DE CONSTANT (72)"}
    {:id "fake-id-0720027N" :name "Lycée PERSEIGNE (72)"}
    {:id "fake-id-0720029R" :name "Lycée MONTESQUIEU (72)"}
    {:id "fake-id-0720030S" :name "Lycée BELLEVUE (72)"}
    {:id "fake-id-0720033V" :name "Lycée GABRIEL TOUCHARD - WASHINGTON (72)"}
    {:id "fake-id-0720034W" :name "Lycée Pro FUNAY-HELENE BOUCHER (72)"}
    {:id "fake-id-0720048L" :name "Lycée RAPHAEL ELIZE (72)"}
    {:id "fake-id-0720055U" :name "Lycée PAUL SCARRON (72)"}
    {:id "fake-id-0720822C" :name "Lycée STE CATHERINE (72)"}
    {:id "fake-id-0720825F" :name "Lycée Pro JOSEPH ROUSSEL (72)"}
    {:id "fake-id-0720833P" :name "Lycée NOTRE DAME (72)"}
    {:id "fake-id-0720837U" :name "Lycée NOTRE DAME (72)"}
    {:id "fake-id-0720843A" :name "Lycée STE ANNE (72)"}
    {:id "fake-id-0720896H" :name "Lycée PRYTANEE NATIONAL MILITAIRE (72)"}
    {:id "fake-id-0720907V" :name "Lycée Pro BRETTE LES PINS (72)"}
    {:id "fake-id-0721009F" :name "Lycée Pro VAL DE SARTHE (72)"}
    {:id "fake-id-0721094Y" :name "Lycée LE MANS SUD (72)"}
    {:id "fake-id-0721301Y" :name "Lycée Pro JEAN RONDEAU (72)"}
    {:id "fake-id-0721328C" :name "Lycée Pro LES HORIZONS (72)"}
    {:id "fake-id-0721329D" :name "Lycée Pro LES HORIZONS (72)"}
    {:id "fake-id-0721336L" :name "Lycée Pro NOTRE DAME (72)"}
    {:id "fake-id-0721337M" :name "Lycée Pro NAZARETH (72)"}
    {:id "fake-id-0721478R" :name "Lycée ST JOSEPH LA SALLE (72)"}
    {:id "fake-id-0721493G" :name "Lycée MARGUERITE YOURCENAR (72)"}
    {:id "fake-id-0721548S" :name "Lycée ANDRE MALRAUX (72)"}
    {:id "fake-id-0721549T" :name "Lycée ST PAUL-NOTRE DAME (72)"}
    {:id "fake-id-0721684P" :name "Lycée SAINT-CHARLES SAINTE-CROIX (72)"}
    {:id "fake-id-0850006V" :name "Lycée GEORGES CLEMENCEAU (85)"}
    {:id "fake-id-0850016F" :name "Lycée ATLANTIQUE (85)"}
    {:id "fake-id-0850025R" :name "Lycée PIERRE MENDES-FRANCE (85)"}
    {:id "fake-id-0850027T" :name "Lycée ROSA PARKS (85)"}
    {:id "fake-id-0850028U" :name "Lycée Pro EDOUARD BRANLY (85)"}
    {:id "fake-id-0850032Y" :name "Lycée SAVARY DE MAULEON (85)"}
    {:id "fake-id-0850033Z" :name "Lycée Pro ERIC TABARLY (85)"}
    {:id "fake-id-0850043K" :name "Lycée Pro VALERE MATHE (85)"}
    {:id "fake-id-0850068M" :name "Lycée FRANCOIS RABELAIS (85)"}
    {:id "fake-id-0850076W" :name "Lycée JEAN XXIII (85)"}
    {:id "fake-id-0850077X" :name "Lycée STE URSULE (85)"}
    {:id "fake-id-0850079Z" :name "Lycée ND DE LA TOURTELIERE (85)"}
    {:id "fake-id-0850086G" :name "Lycée ST GABRIEL ST MICHEL (85)"}
    {:id "fake-id-0850118S" :name "Lycée L'ESPERANCE (85)"}
    {:id "fake-id-0850130E" :name "Lycée ND DU ROC (85)"}
    {:id "fake-id-0850133H" :name "Lycée STE MARIE DU PORT (85)"}
    {:id "fake-id-0850135K" :name "Lycée STE MARIE (85)"}
    {:id "fake-id-0850136L" :name "Lycée JEANNE D'ARC (85)"}
    {:id "fake-id-0850142T" :name "Lycée NOTRE DAME (85)"}
    {:id "fake-id-0850144V" :name "Lycée LA ROCHE SUR YON (85)"}
    {:id "fake-id-0850146X" :name "Lycée Pro RENE COUZINET (85)"}
    {:id "fake-id-0850151C" :name "Lycée FONTENAY LE COMTE (85)"}
    {:id "fake-id-0850152D" :name "Lycée LUCON-PETRE (85)"}
    {:id "fake-id-0850609A" :name "Lycée LES ETABLIERES (85)"}
    {:id "fake-id-0851344Z" :name "Lycée NOTRE DAME (85)"}
    {:id "fake-id-0851346B" :name "Lycée FRANCOIS TRUFFAUT (85)"}
    {:id "fake-id-0851390Z" :name "Lycée LEONARD DE VINCI (85)"}
    {:id "fake-id-0851400K" :name "Lycée JEAN MONNET (85)"}
    {:id "fake-id-0851401L" :name "Lycée J.DE LATTRE DE TASSIGNY (85)"}
    {:id "fake-id-0851504Y" :name "Lycée Pro ST GABRIEL (85)"}
    {:id "fake-id-0851516L" :name "Lycée Pro ST MICHEL (85)"}
    {:id "fake-id-0851642Y" :name "Lycée SAINT FRANCOIS D'ASSISE (85)"}
    {:id "fake-id-0851643Z" :name "Lycée Pro SAINT FRANCOIS D'ASSISE (85)"}
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
