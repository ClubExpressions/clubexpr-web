(ns club.events
  (:require
    [clojure.string :as str]
    [clojure.set :refer [difference]]
    [clojure.walk :refer [keywordize-keys]]
    [re-frame.core :as rf]
    [re-frame.db :refer [app-db]]
    [goog.object :refer [getValueByKeys]]
    [club.db]
    [club.db :refer [;log!
                     base-user-record
                     new-series
                     logout-db-fragment
                     set-auth-data!
                     fetch-teachers-list!
                     wrap-series
                     fix-ranks
                     delete-series!
                     delete-work!
                     fetch-progress!
                     save-attempt!
                     save-progress!]]
    [club.expr :refer [expr-error correct natureFromLisp]]
    [club.utils :refer [t
                        error
                        get-prop
                        data-from-js-obj
                        epoch
                        parse-url
                        get-url-all!
                        get-url-root!]]
    [cljs.spec     :as s]
    [goog.crypt.base64 :refer [decodeString]]))


;; Interceptors

(defn check-and-throw
  "Throw an exception if db doesn't match the spec"
  [a-spec db]
  (let [valid (s/valid? a-spec db)]
  (when-not valid
    (throw (ex-info (str "spec check failed: " (s/explain-str a-spec db)) {})))
  valid))

(def check-spec-interceptor (rf/after (partial check-and-throw :club.db/db)))


;; Event Handlers

(rf/reg-fx
  :msg
  (fn [msg]
    (js/alert msg)))

(rf/reg-event-fx
  :login
  (fn []
    {:login nil}))

(def webauth
  (let [auth0 (getValueByKeys js/window "deps" "auth0")
        opts (clj->js {:domain "clubexpr.eu.auth0.com"
                       :clientID "QKq48jNZqQ84VQALSZEABkuUnM74uHUa"
                       :responseType "token id_token"
                       :redirectUri (get-url-root!)
                       })]
    (new auth0.WebAuth opts)))

(rf/reg-fx
  :login
  (fn [_]
    (.authorize webauth)))

(rf/reg-event-fx
  :logout
  (fn [{:keys [db]} _]
    {:db (merge db logout-db-fragment)
     :go-to-relevant-url false}))

(rf/reg-event-fx
  :go-to-relevant-url
  (fn [{:keys [db]} [_ login]]
    {:go-to-relevant-url login}))

(rf/reg-fx
  :go-to-relevant-url
  (fn [login]
    (let [lastname (-> @app-db :profile-page :lastname)
          page (if login
                 (if (empty? lastname)
                   "/help"
                   "/work")
                 "")]
      (set! (-> js/window .-location .-hash) page))))

(rf/reg-event-fx
  :profile-cancel
  (fn []
    {:profile-cancel nil}))

(rf/reg-fx
  :profile-cancel
  (fn [_]
    (club.db/fetch-profile-data!)
    ; TODO useless use of set-auth-data! : these 4 already set
    ;(swap! app-db assoc-in [:authenticated] true)
    ;; from new-user-data
    ;(swap! app-db assoc-in [:auth-data :auth0-id] auth0-id)
    ;(swap! app-db assoc-in [:auth-data :access-token] access-token)
    ;(swap! app-db assoc-in [:auth-data :expires-at] expires-at)
    ))

(rf/reg-event-fx
  :profile-save
  (fn []
    {:profile-save nil}))

(rf/reg-fx
  :profile-save
  (fn [_]
    (club.db/save-profile-data!)))

(rf/reg-event-db
  :profile-save-ok
  [check-spec-interceptor]
  (fn [db [_ _]]
    ; TODO: set a flag in the state to display «new profile saved»
    db
    ))

(rf/reg-event-fx
  :groups-cancel
  (fn []
    {:groups-cancel nil}))

(rf/reg-fx
  :groups-cancel
  (fn [_]
    (club.db/fetch-groups-data!)))

(rf/reg-event-fx
  :groups-save
  (fn []
    {:groups-save nil}))

(rf/reg-fx
  :groups-save
  (fn [_]
    (club.db/save-groups-data!)))

(rf/reg-event-db
  :groups-save-ok
  [check-spec-interceptor]
  (fn [db [_ _]]
    ; TODO: set a flag in the state to display «new groups saved»
    db
    ))

(rf/reg-event-db
  :user-code-club-src-change
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (assoc db :attempt-code new-value)))

(rf/reg-event-db
  :game-idx
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (assoc db :game-idx new-value)))

(rf/reg-event-db
  :profile-quality
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (assoc-in db [:profile-page :quality] new-value)))

(rf/reg-event-fx
  :profile-school
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ new-value]]
    {:db (assoc-in db [:profile-page :school] new-value)
     :profile-load-teachers-list new-value}))

(rf/reg-fx
  :profile-load-teachers-list
  (fn [school-id]
    (fetch-teachers-list! school-id)))

(rf/reg-event-db
  :profile-teacher
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (assoc-in db [:profile-page :teacher] new-value)))

(rf/reg-event-db
  :profile-lastname
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (assoc-in db [:profile-page :lastname] new-value)))

(rf/reg-event-db
  :profile-firstname
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (assoc-in db [:profile-page :firstname] new-value)))

(rf/reg-event-db
  :initialize-db
  [check-spec-interceptor]
  (fn  [_ _]
    club.db/default-db))

(rf/reg-event-fx
  :nav
  (fn [{:keys [db]} _]
    (let [parsed-url (parse-url (get-url-all!))
          page (:page parsed-url)
          query-params (:query-params parsed-url)
          ; 'empty?' prevents wrecking the state at loading time
          new-db (if (empty? db) db (assoc db :current-page page))
          cofx (if (empty? query-params)
                 {:db new-db}
                 {:db new-db :auth query-params})]
       cofx)))

(defn process-user-check!
  [kinto-users new-user-data]
  (let [new-auth0-id (:auth0-id new-user-data)
        user-with-same-auth0-id (->> kinto-users
                                     data-from-js-obj
                                     (filter #(= new-auth0-id (:auth0-id %)))
                                     first)]
    (if (nil? user-with-same-auth0-id)
      (.. club.db/k-users
          (createRecord (clj->js (base-user-record new-auth0-id)))
          (then #(set-auth-data! (merge new-user-data (data-from-js-obj %))))
          (catch (error "events/process-user-check!")))
      (set-auth-data! (merge new-user-data user-with-same-auth0-id)))))

(defn id_token->json-payload
  [id_token]
  (-> id_token
      (str/split ".")
      second
      decodeString))

(rf/reg-fx
  :auth
  (fn [{:keys [access_token expires_in id_token]}]  ; we left: token_type state
    (let [error-msg (t ["Problème d’authentification, veuillez réessayer. Si le problème persiste, essayer avec une adresse email et un mot de passe."])
          expires-in-num (js/parseInt expires_in)
          expires-at (str (+ (* expires-in-num 1000) (.getTime (new js/Date))))
          json-payload (id_token->json-payload id_token)
          js-payload (try (.parse js/JSON json-payload)
                          (catch js/Object e
                            (error error-msg)
                            ; (log! {:e e
                            ;        :id_token id_token
                            ;        :json-payload json-payload}
                            js/Object)) ; default: empty obj
          auth0-id (getValueByKeys js-payload "sub")
          new-user-data {:auth0-id auth0-id
                         :access-token access_token
                         :expires-at expires-at}]
      (if (not (nil? auth0-id))
        (.. club.db/k-users
            (listRecords)
            (then #(process-user-check! % new-user-data))
            (catch (error "events/:auth"))))
    )))

(rf/reg-event-db
  :write-teachers-list
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (assoc-in db [:profile-page :teachers-list] new-value)))

; :init-groups is a merge of old and new data, like :write-groups,
; but existing scholar ids remain untouched (merge new old)
(rf/reg-event-db
  :init-groups
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (let [old-groups (:groups-page @app-db)
          new-groups (merge new-value old-groups)]
      (println ":init-groups merge new:")
      (println new-value)
      (println "with old:")
      (println old-groups)
      (assoc-in db [:groups-page] new-groups))))

; :write-groups is a merge of old and new data, like :init-groups,
; but existing scholar ids are replaced (merge old new)
(rf/reg-event-db
  :write-groups
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (let [old-groups (:groups-page @app-db)
          new-groups (merge old-groups new-value)]
      (println ":write-groups merge old:")
      (println old-groups)
      (println "with new:")
      (println new-value)
      (assoc-in db [:groups-page] new-groups))))

(rf/reg-event-db
  :groups-change
  [check-spec-interceptor]
  (fn [db [_ [scholar-id value]]]
    (let [clj-val (-> value js->clj keywordize-keys)
          groups (set (map :value clj-val))]
      (assoc-in db [:groups-page scholar-id :groups] groups))))

(rf/reg-event-db
  :current-series-id
  [check-spec-interceptor]
  (fn [db [_ new-series-id]]
    (let [current-series (->> db :series-page
                                 (filter #(= new-series-id (:id %)))
                                 first
                                 :series
                                 wrap-series)]
      (-> db (assoc-in [:current-series-id] new-series-id)
             (assoc-in [:current-series] current-series)))))

(rf/reg-event-db
  :write-series
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (assoc-in db [:series-page] new-value)))

(rf/reg-event-db
  :series-filtering-nature
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (let [nature (-> new-value js->clj keywordize-keys :value)
          new-db (assoc-in db [:series-filtering :nature] nature)
          new-filter (fn [expr] (= (get-prop expr "nature") nature))]
      (if (= "All" nature)
        (update-in new-db [:series-filtering :filters] dissoc :nature)
        (assoc-in new-db [:series-filtering :filters :nature] new-filter)))))

(rf/reg-event-db
  :series-filtering-depth
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (let [depth-range (js->clj new-value)
          [m M] depth-range
          new-db (assoc-in db [:series-filtering :depth] depth-range)
          new-filter (fn [expr] (<= m (get-prop expr "depth") M))]
      (assoc-in new-db [:series-filtering :filters :depth] new-filter))))

(rf/reg-event-db
  :series-filtering-nb-ops
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (let [nb-ops-range (js->clj new-value)
          [m M] nb-ops-range
          new-db (assoc-in db [:series-filtering :nb-ops] nb-ops-range)
          new-filter (fn [expr] (<= m (get-prop expr "nbOps") M))]
      (assoc-in new-db [:series-filtering :filters :nb-ops] new-filter))))

(rf/reg-event-db
  :series-filtering-prevented-ops
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (let [p-ops (js->clj new-value)
          new-db (assoc-in db [:series-filtering :prevented-ops] p-ops)
          new-filter
            (apply every-pred
              ; list of prevented ops -> list of filters
              (map #(fn [expr] (not (some #{%} (get-prop expr "uniqueOps"))))
                   p-ops))]
      (if (empty? p-ops)
        (update-in new-db [:series-filtering :filters] dissoc :prevented-ops)
        (assoc-in new-db [:series-filtering :filters :prevented-ops] new-filter)))))

(rf/reg-event-db
  :new-series
  [check-spec-interceptor]
  (fn [db]
    (-> db
        (assoc-in [:editing-series] true)
        (assoc-in [:current-series-id] "")
        (assoc-in [:current-series] new-series))))

(rf/reg-event-db
  :series-cancel
  [check-spec-interceptor]
  (fn [db]
    (let [current-id (:current-series-id db)]
      (if (empty? current-id)
        ; abort creating a new series
        (-> db
            (assoc-in [:editing-series] false)
            (assoc-in [:current-series-id] "")
            (assoc-in [:current-series] new-series))
        ; abort editing an existing series
        (-> db
            (assoc-in [:editing-series] false)
            (assoc-in [:current-series] (->> db :series-page
                                                (filter #(= current-id (:id %)))
                                                first
                                                :series
                                                wrap-series)))))))

(rf/reg-event-fx
  :series-save
  [check-spec-interceptor]
  (fn []
    {:series-save nil}))

(rf/reg-fx
  :series-save
  (fn []
    (club.db/save-series-data!)))

(rf/reg-event-db
  :series-edit
  [check-spec-interceptor]
  (fn [db]
    (assoc db :editing-series true)))

(rf/reg-event-fx
  :series-delete
  [check-spec-interceptor]
  (fn [{:keys [db]}]
    {:series-delete nil}))

(rf/reg-fx
  :series-delete
  (fn []
    (delete-series!)))

(rf/reg-event-db
  :series-title
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (assoc-in db [:current-series :title] new-value)))

(rf/reg-event-db
  :series-desc
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (assoc-in db [:current-series :desc] new-value)))

(defn sorted-expr->obj
  [expr-data]
  {:content (getValueByKeys expr-data "content" "props" "src")})

(rf/reg-event-db
  :series-exprs-sort
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (let [exprs (fix-ranks (map sorted-expr->obj new-value))]
      (assoc-in db [:current-series :exprs] exprs))))

(rf/reg-event-db
  :series-exprs-add
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (let [rank (count (-> db :current-series :exprs))]
      (update-in db [:current-series :exprs] conj {:content new-value :rank rank}))))

(rf/reg-event-db
  :series-exprs-delete
  [check-spec-interceptor]
  (fn [db [_ deleted-rank]]
    (let [remove-elt #(vec (concat (subvec % 0 deleted-rank )
                                   (subvec % (+ deleted-rank 1))))]
      (-> db (update-in [:current-series :exprs] remove-elt)
             (update-in [:current-series :exprs] fix-ranks)))))

(rf/reg-event-db
  :series-save-ok
  [check-spec-interceptor]
  (fn [db [_ record]]
    ; TODO: set a flag in the state to display «new series saved»
    (let [record-clj (data-from-js-obj record)
          id (:id record-clj)
          wrapped-series (-> record-clj :series wrap-series)]
      (-> db
          (update :series-page conj record-clj)
          (assoc-in [:editing-series] false)
          (assoc-in [:current-series-id] id)
          (assoc-in [:current-series] wrapped-series)))))

(rf/reg-event-db
  :series-delete-ok
  [check-spec-interceptor]
  (fn [db [_ record]]
    ; TODO: set a flag in the state to display «series deleted»
    (let [record-clj (data-from-js-obj record)
          id (:id record-clj)
          not-the-deleted #(not (= id (:id %)))]
      (-> db
          (update :series-page #(vec (filter not-the-deleted %)))
          (assoc-in [:current-series-id] "")
          (assoc-in [:current-series] new-series)))))

(rf/reg-event-fx
  :work-save
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ work-state]]
    {:work-save {:teacher-id (-> db :auth-data :kinto-id)
                 :work-state work-state}}))

(rf/reg-fx
  :work-save
  (fn [{:keys [teacher-id work-state]}]
    (let [record (-> work-state
                     (merge {:teacher-id teacher-id
                             :series-id (:series-id work-state)})
                     (dissoc (if (empty? (:id work-state)) :id)
                             :editing
                             :series-title))]
      (.. club.db/k-works
          (createRecord (clj->js record))
          (then #(rf/dispatch [:work-save-ok (data-from-js-obj %)]))
          (catch (error "event :work-save"))))))

(defn works-record->works-teacher-page-data
  [record]
  (dissoc record :id :teacher-id :last-modified))

(defn update-works-teacher
  [works record]
  (let [id (:id record)
        not-the-provided #(not (= id (:id %)))
        elt-removed (vec (filter not-the-provided works))
        elt-added
          (conj elt-removed (works-record->works-teacher-page-data record))]
    elt-added))

(rf/reg-event-db
  :work-save-ok
  [check-spec-interceptor]
  (fn [db [_ record]]
    ; TODO: set a flag in the state to display «saved»
    (update-in db [:works-teacher-page] update-works-teacher record)))

(rf/reg-event-fx
  :work-delete
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ state]]
    {:work-delete state}))

(rf/reg-fx
  :work-delete
  (fn [state]
    (delete-work! (:id state))))

(rf/reg-event-db
  :work-delete-ok
  [check-spec-interceptor]
  (fn [db [_ record]]
    ; TODO: set a flag in the state to display «series deleted»
    (let [record-clj (data-from-js-obj record)
          id (:id record-clj)
          not-the-deleted #(not (= id (:id %)))]
      (update db :works-teacher-page #(vec (filter not-the-deleted %))))))

(rf/reg-event-fx
  :write-works-teacher
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ new-value]]
    {:db (assoc-in db [:works-teacher-page] new-value)
     :dispatch-progress-writes new-value}))

(rf/reg-fx
  :dispatch-progress-writes
  (fn [works]
    (doall (map fetch-progress! works))))

(rf/reg-event-db
  :progress-write
  (fn [db [_ work-id progress]]
    (let [works-data (:works-teacher-page db)
          works-indexed (map-indexed vector works-data)
          keep-the-good-one #(= work-id (-> % second :id))
          idx (first (first (filter keep-the-good-one works-indexed)))]
      (assoc-in db [:works-teacher-page idx :progress] progress))))

(rf/reg-event-db
  :write-works-scholar
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (assoc-in db [:works-scholar-page] new-value)))

(rf/reg-event-db
  :scholar-work
  [check-spec-interceptor]
  (fn [db [_ work]]
    (-> db
        (assoc-in [:scholar-working] true)
        (assoc-in [:scholar-work-id] (:id work))
        (assoc-in [:scholar-work :attempt] "")
    )))

(rf/reg-event-fx
  :close-scholar-work
  [check-spec-interceptor]
  (fn [{:keys [db]} _]
    (let [work (:scholar-work db)
          exprs (-> work :series :exprs)
          current-expr-idx (:current-expr-idx work)
          scholar-id (-> db :auth-data :kinto-id)
          work-id (-> db :scholar-work-id)
          reset-db (-> db (assoc-in [:scholar-working] false)
                          (assoc-in [:scholar-work-id] "")
                          (assoc-in [:scholar-work :series] new-series)
                          (assoc-in [:scholar-work :current-expr-idx] 0)
                          (assoc-in [:scholar-work :current-expr] "")
                          (assoc-in [:scholar-work :interactive] true)
                          (assoc-in [:scholar-work :shown-at] "")
                          (assoc-in [:scholar-work :attempt] "")
                          (assoc-in [:scholar-work :error] ""))]
      (if (or (= current-expr-idx (count exprs))
              (= current-expr-idx -666)  ; just finished
              (= current-expr-idx -665)) ; back to a finished work
        {:db reset-db}
        {:db reset-db
         :attempt ["aborted" scholar-id work-id work]}))))

(rf/reg-event-db
  :write-scholar-work
  [check-spec-interceptor]
  (fn [db [_ series progress]]
    ; Only write if scholar work empty. We know this with :shown-at.
    (if (= "" (-> db :scholar-work :shown-at))
      (let [scholar-id-kw (-> db :auth-data :kinto-id keyword)
            stored-progress-idx (scholar-id-kw progress)
            progress-idx (if stored-progress-idx stored-progress-idx -1)
            next-expr-idx (+ progress-idx 1)
            exprs (:exprs series)
            expr (if (>= progress-idx (count exprs))
                     ""
                     (get exprs next-expr-idx))]
        (-> db
            (assoc-in [:scholar-work :series] series)
            (assoc-in [:scholar-work :shown-at] (epoch))
            (assoc-in [:scholar-work :current-expr-idx] next-expr-idx)
            (assoc-in [:scholar-work :current-expr] expr)
            (assoc-in [:scholar-work :error] "Expression vide")))
      db)))

(rf/reg-event-db
  :scholar-work-attempt-change
  [check-spec-interceptor]
  (fn [db [_ new-value]]
    (-> db
        (assoc-in [:scholar-work :attempt] new-value)
        (assoc-in [:scholar-work :error] (expr-error new-value)))))

(rf/reg-event-fx
  :back-to-interactive
  [check-spec-interceptor]
  (fn [{:keys [db]} _]
    (let [scholar-id   (-> db :auth-data :kinto-id)
          work-id      (-> db :scholar-work-id)
          work         (-> db :scholar-work)]
      {:db (assoc-in db [:scholar-work :interactive] true)
       :attempt ["back to interactive" scholar-id work-id work]})))

(rf/reg-event-fx
  :scholar-work-attempt
  [check-spec-interceptor]
  (fn [{:keys [db]} _]
    (let [exprs        (-> db :scholar-work :series :exprs)
          idx          (-> db :scholar-work :current-expr-idx)
          next-idx     (if (= idx (- (count exprs) 1))
                         -666  ; marker for finished series
                         (+ idx 1))
          current-expr (-> db :scholar-work :current-expr)
          interactive  (-> db :scholar-work :interactive)
          attempt      (-> db :scholar-work :attempt)
          scholar-id   (-> db :auth-data :kinto-id)
          work-id      (-> db :scholar-work-id)
          work         (-> db :scholar-work)
          sw :scholar-work  ; just to shorten the code below
          ]
      (if (correct attempt current-expr)
        (if interactive
          {:db (-> db
                   (assoc-in [sw :shown-at] (epoch))
                   (assoc-in [sw :interactive] false)
                   (assoc-in [sw :attempt] "")
                   (assoc-in [sw :error] "Expression vide")
                   )
           :attempt ["ok interactive" scholar-id work-id work]
           :msg (t ["Bravo, la même en mode non interactif."])}
          {:db (-> db
                   (assoc-in [sw :current-expr-idx] next-idx)
                   (assoc-in [sw :current-expr] (or (get exprs next-idx) ""))
                   (assoc-in [sw :shown-at] (epoch))
                   (assoc-in [sw :attempt] "")
                   (assoc-in [sw :error] "Expression vide")
                   )
           :attempt ["ok" scholar-id work-id work]
           :msg (t ["Bravo."])})
        (if interactive
          {:msg (t ["Essaie encore !"])
           :attempt ["mistake interactive" scholar-id work-id work]}
          {:msg (t ["Essaie encore !"])
           :attempt ["mistake" scholar-id work-id work]})
      ))))

(rf/reg-fx
  :attempt
  (fn [[status scholar-id work-id work]]
    (let [expr-idx (:current-expr-idx work)
          exprs-count (count (-> work :series :exprs))
          expr-idx-to-store (if (= expr-idx (- exprs-count 1)) -666 expr-idx)
          expr (:current-expr work)
          attempt (:attempt work)
          t-i (:shown-at work)
          t-f (epoch)]
      (save-attempt!
        {:status status
         :scholar-id scholar-id
         :work-id work-id
         :expr-idx expr-idx
         :expr expr
         :expr-nature (natureFromLisp expr)
         :shown-at     t-i
         :attempted-at t-f
         :duration     (- t-f t-i)
         :attempt attempt
         :attempt-nature (natureFromLisp attempt)})
      (if (= "ok" status)
        (save-progress!
          {:id work-id
           scholar-id expr-idx-to-store}))
      (if (and (= "aborted" status) (= 0 expr-idx))
        (save-progress!
          {:id work-id
           scholar-id (- expr-idx 1)})))))
