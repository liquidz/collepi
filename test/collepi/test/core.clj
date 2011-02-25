(ns collepi.test.core
  (:use
     [collepi core model util]
     :reload)
  (:use clojure.test ring.mock.request
     clj-gravatar.core)

  (:require
     [appengine-magic.testing :as ae-testing]
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.string :as string]
     )

  (:import
     [com.google.appengine.tools.development.testing
      LocalServiceTestConfig
      LocalServiceTestHelper
      LocalUserServiceTestConfig]
     )
  )

(defmacro with-test-user [mail & body]
  `(let [domain# (second (string/split #"@" ~mail))
         helper# (.. (LocalServiceTestHelper.
                       (into-array LocalServiceTestConfig [(LocalUserServiceTestConfig.)]))
                   (setEnvIsLoggedIn true) (setEnvEmail ~mail) (setEnvAuthDomain domain#))]
     (.setUp helper#)
     (do ~@body)
     ; tearDown effects another tearDown in ae-testing/local-services
     ;(.tearDown helper#)
     )
  )

(use-fixtures :each (ae-testing/local-services :all))

(deftest test-with-test-user
  (is (not (du/user-logged-in?)))
  (is (nil? (du/current-user)))
  (with-test-user
    "hoge@fuga.com"
    (are [x y] (= x y)
      true (du/user-logged-in?)
      "hoge@fuga.com" (.getEmail (du/current-user))
      "hoge" (.getNickname (du/current-user))
      )
    )
  )

;; User
(deftest test-create-user
  (with-test-user
    "hoge@fuga.com"
    (let [user  (create-user (du/current-user))
          avatar (gravatar-image "hoge@fuga.com")
          ]
      (are [x y] (= x y)
        "hoge@fuga.com" (:email user)
        "hoge" (:nickname user)
        avatar (:avatar user)
        )
      )
    )
  (let [user (create-user "neko@fuga.com" "neko")
        avatar (gravatar-image "neko@fuga.com")]
    (are [x y] (= x y)
      "neko@fuga.com" (:email user)
      "neko" (:nickname user)
      avatar (:avatar user)
      )
    )
  )

(deftest test-get-user
  (let [user (create-user "hoge@fuga.com" "hoge")
        key (ds/get-key-object user)]
    (are [x y] (= x y)
      user (get-user "hoge@fuga.com")
      user (get-user key)
      )
    )
  )

;; Item
(deftest test-create-item
  (let [item1 (create-item "4001156768")
        item2 (create-item "123" :static? true :title "aa" :author "bb" :smallimage "cc" :mediumimage "ccc" :largeimage "cccc")]
    (are [x y] (= x y)
      false (string/blank? (:title item1))
      false (string/blank? (:author item1))
      false (nil? (:smallimage item1))
      false (nil? (:mediumimage item1))
      false (nil? (:largeimage item1))

      "aa" (:title item2)
      "bb" (:author item2)
      "cc" (:smallimage item2)
      "ccc" (:mediumimage item2)
      "cccc" (:largeimage item2)
      )
    )
  )

(deftest test-get-item
  (let [isbn "4001156768"
        key (ds/get-key-object (create-item isbn :static? true))]

    (are [x y] (= x y)
      true (nil? (get-item "unknown"))
      false (nil? (get-item isbn))
      false (nil? (get-item key))
      )
    )
  )




;; Collection
(deftest test-create-collection
  (let [user (create-user "hoge@fuga.com" "hoge")
        user-key (ds/get-key-object user)
        item (create-item "1" :static? true)
        item-key (ds/get-key-object item)
        col (create-collection item user)
        ]
    (are [x y] (= x y)
      user-key (:user col)
      item-key (:item col)
      1 (:point col)
      false (:read? col)
      true (today? (:date col))
      )
    )
  )

(deftest test-get-collections-from-user
  (let [
        user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        item1 (create-item "1" :static? true)
        item1-key (ds/get-key-object item1)
        item2 (create-item "2" :static? true)
        item2-key (ds/get-key-object item2)
        ]
    (create-collection item1 user1 :date "YYYY-01-01")
    (create-collection item2 user1 :date "YYYY-01-02")
    (create-collection item2 user2 :date "YYYY-01-03")

    ;(println "all collection count:" (count (get-collection-list)))
    ;(println "get-collections-from-user:" (get-collections-from-user user1))

    (are [x y] (= x y)
      2 (count (get-collections-from-user user1))
      1 (count (get-collections-from-user user1 :limit 1))
      3 (count (get-collection-list))
      1 (count (get-collection-list :limit 1))

      item2-key (:item (first (get-collections-from-user user1)))
      item1-key (:item (second (get-collections-from-user user1)))
      item2-key (:item (first (get-collection-list)))
      )
    )
  )

(deftest test-update-collection
  (let [user (create-user "hoge@fuga.com" "hoge")
        item1 (create-item "1" :static? true)
        item1-key (ds/get-key-object item1)
        item2 (create-item "2" :static? true)
        item3 (create-item "3" :static? true)
        ]

    ; item1 user first update
    (let [col (update-collection item1 user :date "dummy")]
      (are [x y] (= x y)
        item1-key (:item col)
        1 (:point col)
        false (:read? col)
        "dummy" (:date col)

        1 (count (get-collections-from-user user))
        )
      )

    ; item1 user second update with point-plus?
    (let [col (update-collection item1 user :point-plus? true :read? true)]
      (are [x y] (= x y)
        2 (:point col)
        true (:read? col)
        true (today? (:date col))

        1 (count (get-collections-from-user user))
        )
      )

    ; item3 user first update with point-plus?
    (let [col (update-collection item3 user :point-plus? true)]
      (are [x y] (= x y)
        1 (:point col)
        false (:read? col)
        true (today? (:date col))

        2 (count (get-collections-from-user user))
        )
      )

    ; item1 user third update with only point-plus
    (let [col (update-collection item1 user :point-plus? true)]
      (are [x y] (= x y)
        3 (:point col)
        true (:read? col) ; set true at second update
        )
      )

    (update-collection item2 user)
    (is (= 3 (count (get-collections-from-user user))))
    )
  )

(deftest test-count-collection
  (is (zero? (count-collection)))

  (let [user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        item1 (create-item "1" :static? true)
        item2 (create-item "2" :static? true)]
    (create-collection item1 user1)
    (create-collection item1 user2)
    (create-collection item2 user2)

    (are [x y] (= x y)
      3 (count-collection)
      1 (count-collection :user user1)
      2 (count-collection :user user2)
      2 (count-collection :item item1)
      1 (count-collection :item item2)
      )
    )
  )

;; History
(deftest test-create-history
  (let [user (create-user "hoge@fuga.com" "hoge")
        user-key (ds/get-key-object user)
        item (create-item "1" :static? true)
        item-key (ds/get-key-object item)
        history (create-history item user 1 false "hello")]
    (are [x y] (= x y)
      user-key (:user history)
      item-key (:item history)
      1 (:point history)
      false (:read? history)
      true (today? (:date history))
      )
    )
  )

(deftest test-get-histories-from
  (let [
        user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        item1 (create-item "1" :static? true)
        item2 (create-item "2" :static? true)
        ]
    (update-collection item1 user1 :comment "aaa" :point 2 :date "YYYY-01-01")
    (update-collection item2 user1 :comment "bbb" :read? true :date "YYYY-01-02")
    (update-collection item2 user2 :comment "ccc" :point 3 :date "YYYY-01-03")

    (are [x y] (= x y)
      2 (count (get-histories-from-user user1))
      1 (count (get-histories-from-user user1 :limit 1))
      1 (count (get-histories-from-user user2))
      1 (count (get-histories-from-item item1))
      2 (count (get-histories-from-item item2))
      1 (count (get-histories-from-item item2 :limit 1))
      3 (count (get-history-list))
      1 (count (get-history-list :limit 1))
      )

    (let [hls (get-histories-from-user user1)
          ahls (get-history-list)]
      (are [x y] (= x y)
        "bbb" (:comment (first hls))
        "aaa" (:comment (second hls))
        1 (:point (first hls))
        2 (:point (second hls))
        true (:read? (first hls))
        false (:read? (second hls))

        "ccc" (:comment (first ahls))
        3 (:point (first ahls))
        false (:read? (first ahls))
        )
      )

    (update-collection item2 user1 :comment "bbb" :date "YYYY-01-04")
    (let [hls (get-histories-from-user user1)]
      (is (:read? (first hls)))
      )
    )
  )

(deftest test-get-comment-list
  (let [
        user (create-user "hoge@fuga.com" "hoge")
        item1 (create-item "1" :static? true)
        item2 (create-item "2" :static? true)
        item3 (create-item "3" :static? true)
        ]
    (update-collection item1 user :comment "aaa" :date "YYYY-01-01")
    (update-collection item2 user :date "YYYY-01-02")
    (update-collection item3 user :comment "bbb" :date "YYYY-01-03")

    (are [x y] (= x (count y))
      2 (get-comment-list)
      1 (get-comment-list :limit 1))

    (let [res (get-comment-list)]
      (are [x y] (= x y)
        "bbb" (-> res first :comment)
        "3" (-> res first :item get-item :isbn)
        "hoge" (-> res first :user get-user :nickname)

        "aaa" (-> res second :comment)
        "1" (-> res second :item get-item :isbn)
        "hoge" (-> res second :user get-user :nickname)
        )
      )
    )
  )

(deftest test-count-history
  (is (zero? (count-history)))

  (let [user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        item1 (create-item "1" :static? true)
        item2 (create-item "2" :static? true)]
    (update-collection item1 user1)
    (update-collection item1 user2)
    (update-collection item2 user2)

    (are [x y] (= x y)
      3 (count-history)
      1 (count-history :user user1)
      2 (count-history :user user2)
      2 (count-history :item item1)
      1 (count-history :item item2)
      )
    )
  )
