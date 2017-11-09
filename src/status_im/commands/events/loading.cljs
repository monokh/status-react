(ns status-im.commands.events.loading
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.js-resources :as js-resources]
            [status-im.utils.types :as types]
            [status-im.utils.utils :as utils]
            [status-im.native-module.core :as status]
            [status-im.data-store.local-storage :as local-storage]
            [taoensso.timbre :as log]))

;; COFX
(re-frame/reg-cofx
  :get-local-storage-data
  (fn [cofx]
    (assoc cofx :get-local-storage-data local-storage/get-data)))

;; FX
(re-frame/reg-fx
  ::evaluate-jail-n
  (fn [jail-data]
    (doseq [{:keys [jail-id jail-resource]} jail-data]
      (status/parse-jail
       jail-id jail-resource
       (fn [jail-response]
         (re-frame/dispatch [::proceed-loading jail-id (types/json->clj jail-response)]))))))

(re-frame/reg-fx
  ::show-popup
  (fn [{:keys [title msg]}]
    (utils/show-popup title msg)))

;; Handlers
(defn- valid-network-resource?
  [response]
  (some-> (.. response -headers)
          (get "Content-type")
          (string/includes? "application/javascript")))

(defn- evaluate-commands-in-jail
  [{:keys [db get-local-storage-data]} commands-resource whisper-identity]
  (let [data                  (get-local-storage-data whisper-identity)
        local-storage-snippet (js-resources/local-storage-data data)
        network-id            (get-in db [:networks/networks (:networks db) :raw-config :NetworkId])
        ethereum-id-snippet   (js-resources/network-id network-id)
        commands-snippet      (str local-storage-snippet ethereum-id-snippet commands-resource)]
    {::evaluate-jail-n [{:jail-id       whisper-identity
                         :jail-resource commands-snippet}]}))

(defn load-commands
  "This function takes coeffects, effects and contact and adds effects
  for loading all commands/responses/subscriptions.

  It's currently working only for bots, eq we are not evaluating
  dapp resources in jail at all."
  [cofx fx {:keys [whisper-identity bot-url]}]
  (if bot-url
    (if-let [commands-resource (js-resources/get-resource bot-url)]
      (merge-with into fx (evaluate-commands-in-jail cofx commands-resource whisper-identity))
      (update fx conj :http-get-n {:url                   bot-url
                                   :response-validator    valid-network-resource?
                                   :success-event-creator (fn [commands-resource]
                                                            [::evaluate-commands-in-jail commands-resource whisper-identity])
                                   :failure-event-creator (fn [error-response]
                                                            [::proceed-loading whisper-identity {:error error-response}])}))
    fx))

(defn- create-access-scopes
  "Based on command owner and command scope, create set of access-scopes which can be used to directly
  look up any commands/subscriptions relevant for actual context (type of chat opened, registred user
  or not, any DApps in conversation, etc.)"
  [jail-id scope-map]
  (let [clean-scope (into #{}
                          (comp (filter second) (map first))
                          (dissoc scope-map :bitmask))
        final-scope (cond-> clean-scope
                      (not (:global? clean-scope)) (conj jail-id))] 
    (if (and (:personal-chats? final-scope)
             (:group-chats? final-scope))
      #{(disj final-scope :personal-chats?)
        (disj final-scope :group-chats?)}
      #{final-scope})))

(defn- index-by-access-scope-type
  [init jail-id type items]
  (reduce (fn [acc [_ {:keys [scope name] :as props}]]
            (let [access-scopes (create-access-scopes jail-id scope)]
              (reduce (fn [acc access-scope]
                        (assoc-in acc [access-scope type name] (-> props
                                                                   (dissoc :scope)
                                                                   (assoc :owner-id jail-id
                                                                          :bot jail-id
                                                                          :scope-bitmask (:bitmask scope)
                                                                          :type type))))
                      acc
                      access-scopes)))
          init
          items))

(defn add-jail-result
  "This function add commands/responses/subscriptions from jail-evaluated resource
  into the database"
  [db jail-id {:keys [commands responses subscriptions]}]
  (-> db
      (update :access-scope->commands-responses (fn [acc]
                                                  (-> (or acc {})
                                                      (index-by-access-scope-type jail-id :command commands)
                                                      (index-by-access-scope-type jail-id :response responses))))
      (update-in [:contacts/contacts jail-id] assoc
                 :subscriptions subscriptions
                 :commands-loaded? true)))

(handlers/register-handler-fx
  ::evaluate-commands-in-jail
  [re-frame/trim-v (re-frame/inject-cofx :get-local-storage-data)]
  (fn [cofx [commands-resource whisper-identity]]
    (evaluate-commands-in-jail cofx commands-resource whisper-identity)))

(handlers/register-handler-fx
  ::proceed-loading
  [re-frame/trim-v]
  (fn [{:keys [db]} [jail-id {:keys [error result]}]]
    (if error
      (let [message (string/join "\n" ["bot.js loading failed"
                                       jail-id
                                       error])]
        {::show-popup {:title "Error"
                       :msg   message}})
      (let [commands-loaded-events (get-in db [:contacts/contacts jail-id :commands-loaded-events])]
        (cond-> {:db (add-jail-result db jail-id result)}
          (seq commands-loaded-events)
          (assoc :dispatch-n commands-loaded-events))))))
