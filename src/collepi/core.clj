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
(defn get-user-controller [{key-str :key}]
  (let [key (str->key key-str)
        user (get-user key)
        collections (get-collections-from-user user)
        histories (get-histories-from-user user)]
    (remove-extra-key
      (assoc user
             :colletion (map #(assoc % :item (get-item (:item %))) collections)
             :history histories))))

(defn get-item-controller [{key-str :key}]
  (let [key (str->key key-str)
        item (get-item key)
        collections (get-collections-from-item item)
        histories (get-histories-from-item item)]
    (remove-extra-key
      (assoc item
             :collection (map #(assoc % :user (get-user (:user %))) collections)
             :history histories))))

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

  (apiGET "/collection/list" hoge)
  (apiGET "/collection/user" hoge)
  (apiGET "/collection/item" hoge)

  (apiGET "/history/list" hoge)
  (apiGET "/history/user" hoge)
  (apiGET "/history/item" hoge)

  (apiPOST "/update/collection" hoge)
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
