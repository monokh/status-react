(ns status-im.commands.events.loading
  (:require [re-frame.core :as re-frame]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.js-resources :as js-resources]
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
  ::parse-jail
  (fn [jail-id jail-resource]
    (status/parse-jail
     jail-id jail-resource
     (fn [jail-response]
       (re-frame/dispatch [::process-jail (types/json->clj jail-response)])))))

;; Handlers
(defn load-commands
  "This function contact and returns effects
  for loading all commands/responses/subscriptions.

  It's currently working only for bots, eq we are not evaluating
  dapp resources in jail at all."
  [{:keys [whisper-identity bot-url] :as contact}]
  (if-let [commands-resource (js-resources/get-resource bot-url)]
    ))

(handlers/register-handler-db
  ::process-jail
  [re-frame/trim-v]
  (fn [db [{:keys [error result]}]]
    ))
