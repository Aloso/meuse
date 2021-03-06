(ns meuse.front.pages.crates
  (:require [meuse.db.public.crate :as public-crate]
            [clojure.string :as string]))

(def interval 15)

(def letters
  [:div {:class "row" :id "letters"}
   [:div {:class "col-12 center"}
    (for [letter ["A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M" "N" "O" "P"
                  "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"]]
      [:span
       [:a {:href (str "/front/crates?letter=" letter)} letter]
       (when-not (= "Z" letter) " - ")])]])

(defn pages
  [letter nb-crates page]
  (let [last-page? (>= (* page interval) nb-crates)]

    [:div {:class "pages-next center"}
     (when-not (= 1 page)
       [:a {:href (format "/front/crates?letter=%s&page=%d"
                          letter
                          (dec page))} "previous"])
     (when-not last-page?
       [:span
        " - "
        [:a {:href (format "/front/crates?letter=%s&page=%d"
                           letter
                           (inc page))} "next"]])]))

(defn page
  [crates-db request]
  (let [letter (string/lower-case (get-in request [:params :letter] "a"))
        page (Integer/parseInt (get-in request [:params :page] "1"))
        offset (* interval (dec page))
        crates (public-crate/get-crates-range crates-db
                                              offset
                                              interval
                                              letter)
        nb-crates (:count (public-crate/count-crates crates-db))
        nb-crates-prefix (:count (public-crate/count-crates-prefix crates-db letter))]
    [:div {:id "crates"}
     [:h1 "All Crates"]
     letters
     [:p [:span {:class "bold"} nb-crates-prefix] " crates starting by "
      [:span {:class "bold"} letter]
      " on a total of " [:span {:class "bold"} nb-crates] " crates"]
     (pages letter nb-crates page)
     (for [crate crates]
       [:div {:class "row crate-list-element"}
        [:div {:class "col-7"}
         [:p [:span {:class "bold"} (:crates/name crate)]]
         "ID: " [:span {:class "bold"} (:crates/id crate)]
         [:p (:crates_versions/description crate)]
         [:a {:href (str "/front/crates/" (:crates/name crate))}
          "More informations"]]
        [:div {:class "col-5"}
         [:p [:span {:class "stat-num"} (:count crate)] " Releases"]]])]))
