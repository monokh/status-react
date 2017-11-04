(ns status-im.commands.events.loading
  (:require [re-frame.core :as re-frame]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.js-resources :as js-resources]
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

(defn parse-commands)

(defn load-commands
  "This function contact and returns effects
  for loading all commands/responses/subscriptions.

  It's currently working only for bots, eq we are not evaluating
  dapp resources in jail at all."
  [{:keys [whisper-identity bot-url] :as contact}]
  (if-let [commands-resource (js-resources/get-resource bot-url)]))

(handlers/register-handler-db
  ::parse-commands
  [re-frame/trim-v]
  (fn [db [jail-id parsed-commands]]
    ))
