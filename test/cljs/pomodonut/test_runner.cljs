(ns pomodonut.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [pomodonut.core-test]))

(enable-console-print!)

(doo-tests 'pomodonut.core-test)
