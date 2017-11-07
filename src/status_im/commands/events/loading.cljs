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

(def db
  {:contacts/contacts {"transactor" {:name "transactor"
                                     :bot-url "//transactor"
                                     :commands-loaded? true}
                       "browse" {:name "browse"
                                 :bot-url "//browse"
                                 :commands-loaded? true}}
   :scope->owner->command {#{:global :personal-chats} {"transactor" {"send" {}}}
                           #{:global :group-chats} {"transactor-group" {"send" {}}}
                           #{:global} {"browse" {"browse" {}}}}})


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
         (re-frame/dispatch [::process-jail jail-id (types/json->clj jail-response)]))))))

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
  "This function takes coeffects, effects and contact and returns effects
  for loading all commands/responses/subscriptions.

  It's currently working only for bots, eq we are not evaluating
  dapp resources in jail at all."
  [cofx fx {:keys [whisper-identity bot-url]}]
  (if-let [commands-resource (js-resources/get-resource bot-url)]
    (merge-with into fx (evaluate-commands-in-jail cofx commands-resource whisper-identity))
    (assoc fx :http-get {:url                   bot-url
                         :response-validator    valid-network-resource?
                         :success-event-creator (fn [commands-resource]
                                                  [::evaluate-commands-in-jail commands-resource whisper-identity])})))

(defn add-commands
  "This function add commands/responses/subscriptions from jail-evaluated resource
  into the database"
  [db jail-id {:keys [commands responses subscriptions] :as jr}]
  (assoc-in db [:scope->owner->command jail-id] jr))

(handlers/register-handler-fx
  ::evaluate-commands-in-jail
  [re-frame/trim-v (re-frame/inject-cofx :get-local-storage-data)]
  (fn [cofx [commands-resource whisper-identity]]
    (evaluate-commands-in-jail cofx commands-resource whisper-identity)))

(handlers/register-handler-fx
  ::process-jail
  [re-frame/trim-v]
  (fn [{:keys [db]} [jail-id {:keys [error result]}]]
    (if error
      (let [message (string/join "\n" ["bot.js loading failed"
                                       jail-id
                                       :jail-error])]
        {::show-popup {:title "Error"
                       :msg   message}})
      {:db (add-commands db jail-id result)})))
