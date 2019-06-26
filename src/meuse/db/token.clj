(ns meuse.db.token
  "Manage tokens in the database"
  (:require [meuse.auth.token :as auth-token]
            [meuse.db.queries.token :as token-queries]
            [meuse.db.user :as user-db]
            [clj-time.core :as t]
            [crypto.password.bcrypt :as bcrypt]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :refer [debug info error]])
  (:import java.util.UUID))

(defn by-user-and-name
  "Get a token by name for an user."
  [db-tx user-name token-name]
  (if-let [user (user-db/by-name db-tx user-name)]
    (-> (jdbc/query db-tx (token-queries/by-user-and-name
                           (:user-id user)
                           token-name))
        first
        (clojure.set/rename-keys {:token_id :token-id
                                  :token_identifier :token-identifier
                                  :token_name :token-name
                                  :token_token :token-token
                                  :token_created_at :token-created-at
                                  :token_expired_at :token-expired-at
                                  :token_user_id :token-user-id}))
    (throw (ex-info (format "the user %s does not exist"
                            user-name)
                    {:status 404}))))

(defn create
  "Creates a new token for an user. `validity` is the number of days before the
  expiration of the token.
  Returns the generated token."
  [database token]
  (jdbc/with-db-transaction [db-tx database]
    (if-let [user (user-db/by-name db-tx (:user token))]
      (do
        (when (by-user-and-name db-tx (:user token) (:name token))
          (throw (ex-info (format "a token named %s already exists for user %s"
                                  (:name token)
                                  (:user token))
                          {})))
        (let [generated-token (auth-token/generate-token)]
          (jdbc/execute! db-tx (token-queries/create
                                (auth-token/extract-identifier generated-token)
                                (bcrypt/encrypt generated-token)
                                (:name token)
                                (:user-id user)
                                (auth-token/expiration-date (:validity token))))
          generated-token))
      (throw (ex-info (format "the user %s does not exist"
                              (:user token))
                      {:status 400})))))

(defn get-token-user-role
  "Get a token by value.
  Also returns informations about the user and the role."
  [db-tx token]
  (-> (jdbc/query db-tx (token-queries/token-join-user-join-role
                         (auth-token/extract-identifier token)))
      first
      (clojure.set/rename-keys {:token_id :token-id
                                :token_identifier :token-identifier
                                :token_name :token-name
                                :token_token :token-token
                                :token_created_at :token-created-at
                                :token_expired_at :token-expired-at
                                :token_user_id :token-user-id
                                :user_name :user-name
                                :user_active :user-active
                                :user_role_id :user-role-id
                                :role_name :role-name})))

(defn by-user
  "Get the tokens for an user."
  [database user-name]
  (jdbc/with-db-transaction [db-tx database]
    (if-let [user (user-db/by-name db-tx user-name)]
      (->> (jdbc/query db-tx (token-queries/by-user
                             (:user-id user)))
           (map #(clojure.set/rename-keys % {:token_id :token-id
                                             :token_identifier :token-identifier
                                             :token_name :token-name
                                             :token_token :token-token
                                             :token_created_at :token-created-at
                                             :token_expired_at :token-expired-at
                                             :token_user_id :token-user-id})))
      (throw (ex-info (format "the user %s does not exist"
                              user-name)
                      {:status 404})))))

(defn delete
  "Deletes a token for an user."
  [database user-name token-name]
  (jdbc/with-db-transaction [db-tx database]
    (if-let [token (by-user-and-name db-tx user-name token-name)]
      (jdbc/execute! db-tx (token-queries/delete
                            (:token-id token)))
      (throw (ex-info (format "the token %s does not exist for the user %s"
                              token-name
                              user-name)
                      {:status 404})))))
