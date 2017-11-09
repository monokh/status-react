(ns status-im.chat.models.commands
  (:require [status-im.chat.constants :as chat-consts]
            [status-im.bots.constants :as bots-constants]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn- commands-responses-for-chat
  "Returns map of commands/responses eligible for current chat."
  [type
   {:keys          [access-scope->commands-responses chats]
    :contacts/keys [contacts]
    :accounts/keys [accounts current-account-id]}
   chat-id]
  (let [{:keys [address]}           (get accounts current-account-id)
        {chat-contacts :contacts
         group-chat    :group-chat} (get chats chat-id)
        dapps-only-chat?            (every? (fn [{:keys [identity]}]
                                              (get-in contacts [identity :dapp?]))
                                            chat-contacts)
        basic-access-scope (cond-> #{}
                             group-chat (conj :group-chats?)
                             (not group-chat) (conj :personal-chats?)
                             address (conj :registered-only?)
                             dapps-only-chat? (conj :can-use-for-dapps?))
        global-access-scope (conj basic-access-scope :global?)
        member-access-scopes (into #{} (map (comp (partial conj basic-access-scope) :identity))
                                   chat-contacts)]
    (reduce (fn [acc access-scope]
              (merge acc (get-in access-scope->commands-responses [access-scope type])))
            {}
            (cons global-access-scope member-access-scopes))))

(defn- commands-for-chat
  "Returns sorted list of commands eligible for current chat"
  [db chat-id]
  (->> chat-id
       (commands-responses-for-chat :command db)
       (sort-by first)
       (map second)))

(defn- requests-for-chat
  "Returns sorted list of request eligible for current chat"
  [db chat-id]
  (let [requests     (get-in db [:chats chat-id :requests])
        response-map (commands-responses-for-chat :response db chat-id)]
    (->> requests
         (map (comp name :type))
         (select-keys response-map)
         (sort-by first)
         (map second))))

(defn- commands-list->map [commands]
  (->> commands
       (map #(vector (:name %) %))
       (into {})))

(defn replace-name-with-request
  "Sets the information about command for a specified request."
  ([{:keys [content] :as message} commands requests]
   (if (map? content)
     (let [{:keys [command content-command]} content
           commands (commands-list->map commands)
           requests (commands-list->map requests)]
       (assoc content :command (or (get requests (or content-command command))
                                   (get commands command))))
     content))
  ([message commands]
   (replace-name-with-request message commands [])))
