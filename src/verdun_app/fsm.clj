(ns verdun-app.fsm
  (:require [reduce-fsm :as fsm]))

(fsm/defsm task-state
  [[:initial
    :assign -> :assigned]
   [:assigned
    :stage -> :todo]
   [:todo
    :start -> :started]
   [:started
    :cancel -> :canceled
    :complete -> :qa
    :done -> :completed
    :defer -> :defered]
   [:defered
    :resume -> :started]
   [:qa
    :accept -> :accepted
    :reject -> :started
    :done -> :completed]
   [:accepted
    :done -> :completed
    :reject -> :started]
   [:canceled]
   [:completed]])

(fsm/show-fsm task-state)
