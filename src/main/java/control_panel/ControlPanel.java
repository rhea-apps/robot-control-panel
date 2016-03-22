/**
 * Created by Orestis Melkonian on 26/10/2015.
 */

package control_panel;

import javafx.application.Application;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.rhea_core.Stream;
import org.rhea_core.util.functions.Func0;
import ros_eval.RosTopic;
import sensor_msgs.Image;
import sensor_msgs.LaserScan;
import geometry_msgs.TransformStamped;
import tf2_msgs.TFMessage;
import java.util.*;
import java.util.concurrent.TimeUnit;
import cv_bridge.CvImage;
import cv_bridge.ImageEncodings;


public class ControlPanel {
    static final String CLASSIFIER = "./classifiers/haarcascade_profileface.xml";
    static final CascadeClassifier faceDetector = new CascadeClassifier(CLASSIFIER);
    static final MatOfRect faces = new MatOfRect();
    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    // ROS Topics
    static final RosTopic<LaserScan> LASER = new RosTopic<>("/scan");
    static final RosTopic<Image> CAMERA = new RosTopic<>("/camera/rgb/image_color");
    static final RosTopic<Image> DEPTH = new RosTopic<>("/camera/depth/image");
    static final RosTopic<TFMessage> TF = new RosTopic<>("/tf");

    public static void main(String[] args) {

        // Visualization setup
        new Thread(() -> Application.launch(Visualizer.class)).start();
        Visualizer viz = Visualizer.waitForVisualizer();

        // Laser
        Stream<LaserScan> laser = Stream.from(LASER);
        // Colored image
        Stream<Mat> image = Stream.<Image>from(CAMERA).flatMap(im -> {
            try {
                return Stream.just(CvImage.toCvCopy(im).image);
            } catch (Exception e) {
                return Stream.error(e);
            }
        }).sample(60, TimeUnit.MILLISECONDS) // backpressure
          .map(ControlPanel::faceDetect); // Detect faces
        // TF
        Stream<TFMessage> tf = Stream.from(TF);
        // Depth
        Stream.<Image>from(DEPTH).flatMap(im -> {
            try {
                // Convert to grayscale
                im.setEncoding(ImageEncodings.RGBA8);
                Mat mat = CvImage.toCvCopy(im).image;
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);
                Imgproc.threshold(mat, mat, 150, 255, 0);
                return Stream.just(mat);
            } catch (Exception e) {
                return Stream.error(e);
            }
        }).sample(100, TimeUnit.MILLISECONDS)
          .subscribe(viz::displayDepth);

        // Embed laser
        Stream.combineLatest(laser, image, ControlPanel::embedLaser)
              .subscribe(viz::displayRGB);

        // TF relations
        tf.take(50)
          .collect((Func0<HashMap<String, Set<String>>>) HashMap::new, (m, msg) -> {
              List<TransformStamped> transforms = msg.getTransforms();
              for (TransformStamped transform : transforms) {
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

    private static Mat faceDetect(Mat im) {
        if (Visualizer.faceDetection) {
            // Operate on gray-scale image
            Mat temp = new Mat();
            Imgproc.cvtColor(im, temp, Imgproc.COLOR_BGR2GRAY, 3);
            faceDetector.detectMultiScale(temp, faces, 1.15, 2, 0, new Size(im.rows() / 12, im.rows() / 12), new Size(im.rows() / 8, im.cols() / 8));
            for (Rect r : faces.toArray())
                Core.rectangle(im, new Point(r.x, r.y), new Point(r.x + r.width, r.y + r.height), new Scalar(0, 255, 0));
        }
        return im;
    }

    private static Mat embedLaser(LaserScan l, Mat im) {
        int width = im.cols(), height = im.rows();
        Point center = new Point(width / 2, height);
        float curAngle = l.getAngleMin();
        float[] ranges = l.getRanges();
        for (float range : ranges) {
            double x = center.x + (width / 2 * range * Math.cos(curAngle + Math.PI / 2));
            double y = center.y - (width / l.getRangeMax() * range * Math.sin(curAngle + Math.PI / 2));
            if (Math.abs(curAngle) < 0.3)
                Core.line(im, center, new Point(x, y), new Scalar(0, 0, 255));
            curAngle += l.getAngleIncrement();
        }
        Core.circle(im, center, 2, new Scalar(0, 0, 0), -1);
        return im;
    }
}