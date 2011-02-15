(ns collepi.core
  (:use
     [compojure.core :only [GET POST defroutes wrap!]]
     [compojure.route :only [not-found]]
     [ring.util.response :only [redirect]]
     [ring.middleware.session :only [wrap-session]]
     [collepi model util]
     clj-gravatar.core
     )
  (:require
     [appengine-magic.core :as ae]
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.string :as string]
     )
  )


(defn get-current-user []
  (when (du/user-logged-in?) (create-user (du/current-user))))

(defmacro json-service [method path bind & body] ; {{{
  `(~method ~path ~bind
      (let [res# (do ~@body)]
        (if (and (map? res#) (contains? res# :status) (contains? res# :headers) (contains? res# :body))
          (assoc res# :body (to-json (:body res#)))
          (to-json res#)))))
(defmacro jsonGET [path bind & body] `(json-service GET ~path ~bind ~@body))
(defmacro jsonPOST [path bind & body] `(json-service POST ~path ~bind ~@body))
(defmacro apiGET [path fn] `(jsonGET ~path {params# :params} (~fn (convert-map params#))))
(defmacro apiGET-with-session [path fn] `(jsonGET ~path {params# :params, session# :session}
                                                  (~fn (convert-map params#) session#)))
(defmacro apiPOST [path fn] `(jsonPOST ~path {params# :params} (~fn (convert-map params#))))
(defmacro apiPOST-with-session [path fn] `(jsonPOST ~path {params# :params, session# :session}
                                                  (~fn (convert-map params#) session#)))
; }}}

; controller {{{
(defn- complete-user-and-item [obj & {:keys [user item]}]
  (if (sequential? obj)
    (map #(complete-user-and-item % :user user :item item) obj)
    (assoc obj
           :user (aif user it (get-user (:user obj)))
           :item (aif item it (get-item (:item obj))))))

(defn get-user-controller [{key-str :key}]
  (when-let [key (str->key key-str)]
    (let [user (get-user key)
          collections (get-collections-from-user user)
          histories (get-histories-from-user user)]
      (remove-extra-key
        (assoc user
               :collection (complete-user-and-item collections :user user)
               :history (complete-user-and-item histories :user user)
               )))))

(defn get-item-controller [{key-str :key}]
  (when-let [key (str->key key-str)]
    (let [key (str->key key-str)
          item (get-item key)
          collections (get-collections-from-item item)
          histories (get-histories-from-item item)]
      (remove-extra-key
        (assoc item
               ;:collection (map #(assoc % :user (get-user (:user %)) :item item) collections)
               :collection (complete-user-and-item collections :item item)
               ;:history histories
               :history (complete-user-and-item histories :item item)
               )))))


(def complete-and-remove (comp remove-extra-key complete-user-and-item))

(defn get-collection-list-controller [params]
  (let [[limit page] (params->limit-and-page params)]
    (complete-and-remove (get-collection-list :limit limit :page page))))


(defn- get-collections-from-controller [f {key-str :key, read :read :as params}]
  (let [[limit page] (params->limit-and-page params)
        key (str->key key-str)]
    (when key (complete-and-remove (f key :limit limit :page page :read? (aif read (= it "true") nil))))))

(def get-collections-from-user-controller
  (partial get-collections-from-controller get-collections-from-user))
(def get-collections-from-item-controller
  (partial get-collections-from-controller get-collections-from-item))

;(defn get-collections-from-user-controller [{key-str :key, :as params}]
;  (let [[limit page] (params->limit-and-page params)
;        key (str->key key-str)]
;    (when key
;      (complete-and-remove
;        (get-collections-from-user key :limit limit :page page)))))

;(defn get-collections-from-item-controller [{key-str :key, :as params}]
;  (let [[limit page] (params->limit-and-page params)
;        key (str->key key-str)]
;    (when key
;      (complete-and-remove
;        (get-collections-from-item key :limit limit :page page)))))

(defn get-history-list-controller [params]
  (let [[limit page] (params->limit-and-page params)]
    (complete-and-remove (get-history-list :limit limit :page page))))

(defn- get-histories-from-controller [f {key-str :key, :as params}]
  (let [[limit page] (params->limit-and-page params)
        key (str->key key-str)]
    (when key (complete-and-remove (f key :limit limit :page page)))))

(def get-histories-from-user-controller
  (partial get-histories-from-controller get-histories-from-user))
(def get-histories-from-item-controller
  (partial get-histories-from-controller get-histories-from-item))


(defn update-collection-controller [{:keys [isbn comment read]}]
  (if (du/user-logged-in?)
    (if-let [item (create-item isbn)]
      (do (update-collection
            item (get-current-user) :comment comment :read? (= read "true") :point-plus? true)
        true)
      false
      )
    false
    )
  )

(defn check-login-controller [_]
  (if (du/user-logged-in?)
    (let [user (du/current-user)]
      {
       :loggedin true
       :nickname (.getNickname user)
       :avatar (gravatar-image (.getEmail user))
       :url (du/logout-url)
       }
      )
    {:loggedin false
     :url (du/login-url)
     }
    )
  )

(defn hoge [& args] nil)
; }}}

(defroutes api-handler
  (apiGET "/user" get-user-controller)
  (apiGET "/item" get-item-controller)

  (apiGET "/check/login" check-login-controller)

  (apiGET "/collection/list" get-collection-list-controller)
  (apiGET "/collection/user" get-collections-from-user-controller)
  (apiGET "/collection/item" get-collections-from-item-controller)

  (apiGET "/history/list" get-history-list-controller)
  (apiGET "/history/user" get-histories-from-user-controller)
  (apiGET "/history/item" get-histories-from-item-controller)

  (apiPOST "/update/collection" update-collection-controller)
  )

(defroutes main-handler
  (GET "/login" _ (redirect (du/login-url)))
  (GET "/logout" _ (with-session (redirect "/") {}))
  (not-found "page not found")
  )


(defroutes app-handler
  api-handler
  main-handler
  )

(wrap! app-handler wrap-session)

(ae/def-appengine-app collepi-app #'app-handler)
