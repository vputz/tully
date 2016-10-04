(ns tully-cljs.core)

(enable-console-print!)

(println "hello, wurld!")

(defn main []
  (let [c (.. js/document (createElement "DIV"))]
    (aset c "innerHTML" "<p>I'm dynamically created </p>")
    (.. js/document (getElementById "container") (appendChild c))))
