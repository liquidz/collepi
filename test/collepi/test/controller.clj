(ns collepi.test.controller
  (:use
     [collepi core util]
     :reload)
  (:use clojure.test ring.mock.request clj-gravatar.core)

  (:require
     [appengine-magic.testing :as ae-testing]
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.json :as json]
     ;[clojure.contrib.string :as string]
     )
  )

(use-fixtures :each (ae-testing/local-services
                      :all :hook-helper (ae-testing/login "hoge@fuga.com")))


; private {{{
(defn- body->json [res] (-> res :body json/read-json))
(defn- testGET [& urls] (app-handler (request :get (apply str urls))))
(defn- testGET-with-session [res & urls]
  (let [ring-session (first (get (:headers res) "Set-Cookie"))]
    (app-handler (header (request :get (apply str urls)) "Cookie" ring-session))
    )
  )
(defn- testPOST [data & urls]
  (app-handler (body (request :post (apply str urls)) data)))
(defn- testPOST-with-session [res data & urls]
  (let [ring-session (first (get (:headers res) "Set-Cookie"))]
    (app-handler (header (body (request :post (apply str urls)) data) "Cookie" ring-session))
    )
  )

(defn- entity->key-str [e] (key->str (ds/get-key-object e)))
; }}}

(deftest test-check-login-controller
  (let [res (body->json (testGET "/check/login"))]
    (are [x y] (= x y)
      true (:loggedin res)
      "hoge" (:nickname res)
      (gravatar-image "hoge@fuga.com") (:avatar res)
      )
    )
  )

(deftest test-get-user-controller
  (let [uri "/user?"
        user (create-user "hoge@fuga.com" "hoge")
        dummy-user (create-user "dummy@fuga.com" "dummy")
        user-key-str (key->str (ds/get-key-object user))
        item1 (create-item "1" :static? true)
        item2 (create-item "2" :static? true)]
    (update-collection item1 user :comment "aaa" :date "YYYY-01-01")
    (update-collection item2 user :comment "bbb" :date "YYYY-01-02")
    (update-collection item1 dummy-user)

    (let [res (body->json (testGET uri "key=" user-key-str))]
      (are [x y] (= x y)
        "hoge" (:nickname res)
        (gravatar-image "hoge@fuga.com") (:avatar res)
        true (nil? (:email res))
        2 (count (:collection res))
        2 (count (:history res))

        "2" (-> res :collection first :item :isbn)
        "1" (-> res :collection second :item :isbn)

        "bbb" (-> res :history first :comment)
        "aaa" (-> res :history second :comment)
        )
      )
    )
  )

(deftest test-get-item-controller
  (let [uri "/item?"
        user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        item (create-item "1" :title "x" :author "y" :thumbnail "z" :static? true)
        item-key-str (key->str (ds/get-key-object item))
        dummy-item (create-item "2" :static? true)
        ]
    (update-collection item user1 :comment "aaa" :date "YYYY-01-01")
    (update-collection item user2 :comment "bbb" :date "YYYY-01-02")
    (update-collection dummy-item user1)

    (let [res (body->json (testGET uri "key=" item-key-str))]
      (are [x y] (= x y)
        "1" (:isbn res)
        "x" (:title res)
        "y" (:author res)
        "z" (:thumbnail res)

        "fuga" (-> res :collection first :user :nickname)
        "hoge" (-> res :collection second :user :nickname)

        "bbb" (-> res :history first :comment)
        "aaa" (-> res :history second :comment)
        )
      )
    )
  )

(deftest test-get-collection-list
  (let [uri "/collection/list?"
        user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        item1 (create-item "1" :static? true)
        item2 (create-item "2" :static? true)
        ]
    (update-collection item1 user1 :date "YYYY-01-01")
    (update-collection item1 user2 :date "YYYY-01-02")
    (update-collection item2 user1 :date "YYYY-01-03")
    (update-collection item2 user2 :date "YYYY-01-04")

    (are [x y] (= x (json-body y))
      (testGET uri)
      )

    (let [res (body->json (testGET uri))]
      )
    )
  )
