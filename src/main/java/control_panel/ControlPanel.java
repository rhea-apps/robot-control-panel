package control_panel;

import org.rhea_core.Stream;
import org.rhea_core.util.functions.Func0;
import ros_eval.RosEvaluationStrategy;
import ros_eval.RosTopic;
import rx_eval.RxjavaEvaluationStrategy;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.CascadeClassifier;

import sensor_msgs.Image;
import sensor_msgs.LaserScan;
import geometry_msgs.TransformStamped;
import tf2_msgs.TFMessage;

import javafx.application.Application;
import java.util.*;
import java.util.concurrent.TimeUnit;
import cv_bridge.CvImage;
import cv_bridge.ImageEncodings;

public class ControlPanel {
    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }
    static final String CLASSIFIER = "./classifiers/haarcascade_frontalface_default.xml";
    static final CascadeClassifier faceDetector = new CascadeClassifier(CLASSIFIER);

    // ROS Topics
    static final RosTopic<LaserScan> LASER = new RosTopic<>("/hokuyo_base/scan", LaserScan._TYPE);
    static final RosTopic<Image> CAMERA = new RosTopic<>("/rear_cam/image_raw", Image._TYPE);
    static final RosTopic<TFMessage> TF = new RosTopic<>("/tf", TFMessage._TYPE);

    public static void main(String[] args) {
        // Visualization setup
        new Thread(() -> Application.launch(Visualizer.class)).start();
        Visualizer viz = Visualizer.waitForVisualizer();

        // RHEA setup
        Stream.evaluationStrategy = new RosEvaluationStrategy(
            new RxjavaEvaluationStrategy(),
            "localhost",
            "myclient"
        );

        // Laser
        Stream<LaserScan> laser = Stream.from(LASER);

        // Camera feed
        Stream<Mat> camera = Stream.<Image>from(CAMERA)
            .map(ControlPanel::convertImage)            // convert images
            .sample(400, TimeUnit.MILLISECONDS)         // backpressure
            .map(ControlPanel::detectFaces);            // detect faces

        // TF
        Stream<TFMessage> tf = Stream.from(TF);

        // Embed laser
        Stream.combineLatest(laser, camera, ControlPanel::embedLaser)
              .subscribe(viz::displayRGB);

        // TF relations
        tf.take(50)
          .collect((Func0<HashMap<String, Set<String>>>) HashMap::new, (m, msg) -> {
              for (TransformStamped transform : msg.getTransforms()) {
                  String parent = transform.getHeader().getFrameId();
                  String child = transform.getChildFrameId();
                  if (!m.containsKey(parent)) {
                      Set<String> init = new HashSet<>();
                      init.add(child);
                      m.put(parent, init);
                  }
                  else m.get(parent).add(child);
            }
          })
          .subscribe(viz::displayTF);

        // Battery
        Stream.interval(2, TimeUnit.SECONDS)
              .map(v -> (100 - v) / 100.0)
              .subscribe(viz::displayBattery);
    }



    private static Mat detectFaces(Mat im) {
        if (Visualizer.faceDetection) {
            // Operate on gray-scale image
            Mat temp = new Mat();
            MatOfRect faces = new MatOfRect();
            Imgproc.cvtColor(im, temp, Imgproc.COLOR_BGR2GRAY, 3);
            faceDetector.detectMultiScale(temp, faces, 1.25, 1, 0, new Size(im.rows() / 18, im.rows() / 18), new Size(im.rows() / 5, im.cols() / 5));
            for (Rect r : faces.toArray())
                Imgproc.rectangle(im, new Point(r.x, r.y), new Point(r.x + r.width, r.y + r.height), new Scalar(0, 255, 0));
        }
        return im;
    }

    private static Mat embedLaser(LaserScan l, Mat im) {
        // Operate on RGB image
        int width = im.cols(), height = im.rows();
        Point center = new Point(width / 2, height);
        float curAngle = l.getAngleMin();
        for (float range : l.getRanges()) {
            double x = center.x + (width / 2 * range * Math.cos(curAngle + Math.PI / 2));
            double y = center.y - (width / l.getRangeMax() * range * Math.sin(curAngle + Math.PI / 2));
            if ((Math.abs(curAngle) < 0.3) && (y > height / 2))
                Imgproc.line(im, center, new Point(x, y), new Scalar(0, 0, 255));
            curAngle += l.getAngleIncrement();
        }
        return im;
    }

    private static Mat convertImage(final Image source) {
        try {
            return CvImage.toCvCopy(source).image;
        } catch (Exception e) {
            System.err.println(e);
            return null;
        }
    }
}
