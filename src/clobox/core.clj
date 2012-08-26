(ns clobox.core
  (:gen-class)
  (:import [javax.swing JPanel JFrame SwingUtilities]
           [java.awt Color]
           [java.awt.image BufferedImage]
           [java.util Timer TimerTask]))


(defn swing-invoke-and-wait
  [fn]
  (if (SwingUtilities/isEventDispatchThread)
    (fn)
    (let [outval (ref nil)]
      (SwingUtilities/invokeAndWait #(let [rc (fn)]
                                       (dosync
                                        (ref-set outval rc))))
      @outval)))

(defn new-frame
  []
  (swing-invoke-and-wait
   #(doto (new JFrame)
       (.setVisible true)
       (.setTitle "This is a window")
       (.setSize 400 400)
       (.setDefaultCloseOperation javax.swing.JFrame/DISPOSE_ON_CLOSE))))

(defn new-painter-jpanel
  [paint-in-progress-ref image]
  (proxy [JPanel] []
    (paint [g]
      (if (not @paint-in-progress-ref)
          (do
            ;; (println "Painting!")
            (doto g
              (.setColor Color/BLACK)
              (.fillRect 0 0 (.getWidth this) (.getHeight this))
              (.drawImage image 0 0 nil)))
          ))))

(defn new-mover
  "speed: milliseconds per revolution"
  [speed]
  (let [start-time (System/currentTimeMillis)]
    (fn [width height]
      (let [now (System/currentTimeMillis)
            elapsed (- now start-time)
            angle (* 2 Math/PI (/ elapsed speed))
            x-center (/ width 2)
            y-center (/ height 2)
            x-swing (/ width 4)
            y-swing (/ height 4)]
        [(double (+ x-center (* x-swing (Math/cos (double angle)))))
         (double (+ y-center (* y-swing (Math/sin (double angle)))))]
        )))
  )

(defn -main
  "Put some stuff in a frame"
  [& args]
  (let [frame (new-frame)
        paint-in-progress-ref (ref false)
        image (new BufferedImage 400 400 java.awt.image.BufferedImage/TYPE_INT_RGB)
        timer (new Timer)
        mover (new-mover 1000)
        box-size 50]
    (.addWindowListener frame (proxy [java.awt.event.WindowListener] []
                                (windowClosed [e]
                                  (.cancel timer))
                                (windowActivated [e] nil)
                                (windowClosing [e] nil)
                                (windowDeactivated [e] nil)
                                (windowDeiconified [e] nil)
                                (windowIconified [e] nil)
                                (windowOpened [e] nil)))
    (let [painter (new-painter-jpanel paint-in-progress-ref image)]
      (.add (.getContentPane frame) painter)
      (.scheduleAtFixedRate timer
                            (proxy [TimerTask] []
                              (run []
                                ;; (println (format "timer fired: paint in progress = %s" @paint-in-progress-ref))
                                (let [ok (ref false)]
                                  (dosync (if (not @paint-in-progress-ref)
                                            (do
                                              (ref-set paint-in-progress-ref true)
                                              (ref-set ok true))
                                            nil))
                                  (if @ok
                                    (do
                                      ;; (println "ok to process")
                                      (doto (.createGraphics image)
                                        (.setColor Color/BLACK)
                                        (.fillRect 0 0 (.getWidth image) (.getHeight image))
                                        (.setColor Color/GREEN)
                                        (#(apply (fn [x y w h] (.fillRect %1 x y w h))
                                                 (conj
                                                  (let [offs (mover (.getWidth image)
                                                                    (.getHeight image))]
                                                       [(- (nth offs 0) (/ box-size 2))
                                                        (- (nth offs 1) (/ box-size 2))])
                                                  box-size box-size))))
                                      (dosync (ref-set paint-in-progress-ref false))
                                      (SwingUtilities/invokeLater #(.repaint painter)))
                                    nil)
                                  )
                                ))
                            16 16))
    (println frame)))


