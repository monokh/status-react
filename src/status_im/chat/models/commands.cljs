(ns status-im.chat.models.commands
  (:require [status-im.chat.constants :as chat-consts]
            [status-im.bots.constants :as bots-constants]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn- resolve-references
  [contacts name->ref]
  (reduce-kv (fn [acc name ref]
               (assoc acc name (get-in contacts ref)))
             {}
             name->ref))

(defn commands-responses
  "Returns map of commands/responses eligible for current chat."
  [type access-scope->commands-responses {:keys [address]} {:keys [contacts group-chat]} all-contacts]
  (let [bots-only?         (every? (fn [{:keys [identity]}]
                                     (get-in all-contacts [identity :dapp?]))
                                   contacts)
        basic-access-scope (cond-> #{}
                             group-chat (conj :group-chats)
                             (not group-chat) (conj :personal-chats)
                             address (conj :registered)
                             (not address) (conj :anonymous)
                             (not bots-only?) (conj :not-for-bots))
        global-access-scope (conj basic-access-scope :global)
        member-access-scopes (into #{} (map (comp (partial conj basic-access-scope) :identity))
                                   contacts)]
    (reduce (fn [acc access-scope]
              (merge acc (resolve-references all-contacts
                                             (get-in access-scope->commands-responses [access-scope type]))))
            {}
            (cons global-access-scope member-access-scopes))))

(def ^:private map->sorted-seq (comp (partial map second) (partial sort-by first)))

(defn commands-for-chat
  "Returns sorted list of commands eligible for current chat."
  [access-scope->commands-responses account chat contacts]
  (map->sorted-seq (commands-responses :command access-scope->commands-responses account chat contacts)))

(defn- requested-responses
  "Returns map of requested command responses eligible for current chat."
  [access-scope->commands-responses account chat contacts requests]
  (let [requested-responses (map (comp name :type) requests)
        responses-map (commands-responses :response access-scope->commands-responses account chat contacts)]
    (select-keys responses-map requested-responses)))

(defn responses-for-chat
  "Returns sorted list of requested command responses eligible for current chat."
  [access-scope->commands-responses account chat contacts requests]
  (map->sorted-seq (requested-responses access-scope->commands-responses account chat contacts requests)))

(defn commands-responses-for-chat
  "Returns sorted list of commands and requested command responses eligible for current chat."
  [access-scope->commands-responses account chat contacts requests]
  (let [commands-map (commands-responses :command access-scope->commands-responses account chat contacts)
        responses-map (requested-responses access-scope->commands-responses account chat contacts requests)]
    (map->sorted-seq (merge commands-map responses-map))))

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
