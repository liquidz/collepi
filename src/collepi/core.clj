(ns collepi.core
  (:use
     [compojure.core :only [GET POST defroutes wrap!]]
     [compojure.route :only [not-found]]
     [ring.util.response :only [redirect]]
     [ring.middleware.session :only [wrap-session]]
     [collepi model util]
     clj-gravatar.core)
  (:require
     [appengine-magic.core :as ae]
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.string :as string]))

(defn get-current-user []
  (when (du/user-logged-in?) (create-user (du/current-user))))

(defmacro json-service [method path bind & body] ; {{{
  `(~method ~path ~bind
      (let [res# (do ~@body)]
        (if (and (map? res#) (every? #(contains? res# %) [:status :headers :body]))
          (assoc res# :body (to-json (:body res#)))
          (to-json res#)))))
(defmacro jsonGET [path bind & body] `(json-service GET ~path ~bind ~@body))
(defmacro jsonPOST [path bind & body] `(json-service POST ~path ~bind ~@body))
(defmacro apiGET [path fn] `(jsonGET ~path {params# :params} (~fn (convert-map params#))))
(defmacro apiPOST [path fn] `(jsonPOST ~path {params# :params} (~fn (convert-map params#))))

(defmacro apiGET-with-session [path fn] `(jsonGET ~path {params# :params, session# :session}
                                                  (~fn (convert-map params#) session#)))
(defmacro apiPOST-with-session [path fn] `(jsonPOST ~path {params# :params, session# :session}
                                                    (~fn (convert-map params#) session#)))
; }}}

; controller {{{

(defn read?->read [obj]
  (if (sequential? obj) (map read?->read obj)
    (if-not (nil? obj) (assoc (dissoc obj :read?) :read (:read? obj)))))

(defn- complete-user-and-item [obj & {:keys [user item]}]
  (if (sequential? obj)
    (map #(complete-user-and-item % :user user :item item) obj)
    (if-not (nil? obj)
      (let [user (aif user it (get-user (:user obj)))]
        (assoc obj :user (assoc user :key (entity->key-str user))
               :item (aif item it (get-item (:item obj))))))))
(def complete-and-remove (comp remove-extra-key complete-user-and-item))

;; User
(defn get-user-controller [{key-str :key}]
  (when-let [key (str->key key-str)]
    (let [user (get-user key)
          collections (read?->read (get-collections-from-user user))
          histories (read?->read (get-histories-from-user user))]
      (remove-extra-key
        (assoc user
               :key (entity->key-str user)
               :collection (complete-user-and-item collections :user user)
               :history (complete-user-and-item histories :user user))))))

;; Item
(defn get-item-controller [{key-str :key, isbn :isbn}]
  (when-let [item (if key-str (get-item (str->key key-str)) (if isbn (get-item isbn)))]
    (let [collections (read?->read (get-collections-from-item item))
          histories (read?->read (get-histories-from-item item))]
      (remove-extra-key
        (assoc item :collection (complete-user-and-item collections :item item)
               :history (complete-user-and-item histories :item item))))))

;; Collection
(defn get-collection-list-controller [{:keys [with_total]:or {with_total "false"} :as params}]
  (let [[limit page] (params->limit-and-page params)
        res (-> (get-collection-list :limit limit :page page)
          complete-and-remove read?->read)]
    (if (= with_total "true")
      {:total (count-collection) :result res} res)))

(defn- get-collections-from-controller [type f {:keys [key read with_total] :or {with_total "false"} :as params}]
  (let [[limit page] (params->limit-and-page params)
        key* (str->key key)]
    (when key*
      (let [res (->(f key* :limit limit :page page :read? (aif read (= it "true") nil))
                  complete-and-remove read?->read)]
        (if (= with_total "true")
          {:total (count-collection type key*) :result res}
          res)))))

(def get-collections-from-user-controller
  (partial get-collections-from-controller :user get-collections-from-user))

(def get-collections-from-item-controller
  (partial get-collections-from-controller :item get-collections-from-item))

;(defn get-my-collections-controller [{with-total? :with_total, :or {with-total? "false"} :as params}]
(defn get-my-collections-controller [{:keys [with_total] :or {with_total "false"} :as params}]
  (when-let [user (get-current-user)]
    (let [[limit page] (params->limit-and-page params)
          res (-> (get-collections-from-user user :limit limit :page page)
                complete-and-remove read?->read)]
      (if (= with_total "true")
        {:total (count-collection :user user) :result res}
        res))))

;; History
(defn get-history-list-controller [params]
  (let [[limit page] (params->limit-and-page params)]
    (->(get-history-list :limit limit :page page)
      complete-and-remove read?->read)))

(defn- get-histories-from-controller [f {key-str :key, :as params}]
  (let [[limit page] (params->limit-and-page params)
        key (str->key key-str)]
    (when key
      (->(f key :limit limit :page page) complete-and-remove read?->read))))

(def get-histories-from-user-controller
  (partial get-histories-from-controller get-histories-from-user))
(def get-histories-from-item-controller
  (partial get-histories-from-controller get-histories-from-item))

(defn get-my-histories-controller [{:keys [with_total] :or {with_total "false"} :as params}]
  (when-let [user (get-current-user)]
    (let [[limit page] (params->limit-and-page params)
          res (-> (get-histories-from-user user :limit limit :page page)
                 complete-and-remove read?->read)]
      (if (= with_total "true")
        {:total (count-history :user user) :result res}
        res))))

(defn get-comment-list-controller [params]
  (let [[limit page] (params->limit-and-page params)]
    (-> (get-comment-list :limit limit :page page)
      complete-and-remove read?->read)))

(defn update-collection-controller [{:keys [isbn comment read]} session]
  (if-let [user (get-current-user)]
    (if (string/blank? isbn)
      (with-message session false "isbn is blank")
      (if-let [item (create-item isbn)]
        (do
          (update-collection item user :comment comment :read? (= read "true") :point-plus? true)
          (with-message session true "update success"))
        (with-message session false "item is not found")))
    (with-message session false "not logged in")))

(defn get-message-controller [_ session]
  (with-message session (:message session) ""))

(defn check-login-controller [_]
  (if-let [user (get-current-user)]
    {:loggedin true
     :nickname (:nickname user)
     :avatar (:avatar user)
     :url (du/logout-url)}
    {:loggedin false
     :url (du/login-url)}))
; }}}

(defroutes api-handler
  (apiGET "/user" get-user-controller)
  (apiGET "/item" get-item-controller)

  (apiGET "/check/login" check-login-controller)

  (apiGET "/collection/list" get-collection-list-controller)
  (apiGET "/collection/user" get-collections-from-user-controller)
  (apiGET "/collection/item" get-collections-from-item-controller)

  (apiGET "/my/collection" get-my-collections-controller)
  (apiGET "/my/history" get-my-histories-controller)

  (apiGET "/history/list" get-history-list-controller)
  (apiGET "/history/user" get-histories-from-user-controller)
  (apiGET "/history/item" get-histories-from-item-controller)

  (apiGET "/comment/list" get-comment-list-controller)

  (apiGET-with-session "/message" get-message-controller)
  (apiPOST-with-session "/update/collection" update-collection-controller))

(defroutes main-handler
  (GET "/login" _ (redirect (du/login-url)))
  (GET "/logout" _ (with-session (redirect "/") {}))
  (not-found "page not found"))

(defroutes app-handler
  api-handler
  main-handler)

(wrap! app-handler wrap-session)
(ae/def-appengine-app collepi-app #'app-handler)
