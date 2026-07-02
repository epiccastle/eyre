(ns eyre.utils)

(defmacro embed [path]
  (slurp (str "src/eyre/" path)))
