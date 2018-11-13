/**
 * Created by Orestis Melkonian on 3/11/2015.
 */

package control_panel;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
// import org.opencv.highgui.Highgui;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.String;
import java.util.*;
import java.util.concurrent.CountDownLatch;


public class Visualizer extends Application implements Runnable {
    static {
        System.out.println(System.getProperty("java.library.path"));
        System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
        nu.pattern.OpenCV.loadShared();
    }

    public static final CountDownLatch latch = new CountDownLatch(1);
    public static Visualizer viz = null;
    public static boolean faceDetection = false;

    public final GridPane grid = new GridPane();
    public final TreeItem<String> root = new TreeItem<String>("TF");
    public final ProgressBar battery = new ProgressBar();
    public final ImageView imageRGB = new ImageView();

    // Setup
    public Visualizer() { set(this); }
    public static void main(String[] args) { Application.launch(args); }
    public static Visualizer waitForVisualizer() {
        try { latch.await(); } catch (InterruptedException e) { e.printStackTrace(); }
        return viz;
    }
    public static void set(Visualizer viz0) {
        viz = viz0;
        latch.countDown();
    }

    @Override
    public void run() { launch(); }

    @Override
    public void start(Stage stage) throws Exception {
        // Grid
        grid.setHgap(30); grid.setVgap(30);
        grid.setPadding(new Insets(30, 30, 30, 30));
        grid.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));

        // Percentage sizing
        RowConstraints r1 = new RowConstraints(); r1.setPercentHeight(10);
        RowConstraints r2 = new RowConstraints(); r2.setPercentHeight(80);
        RowConstraints r3 = new RowConstraints(); r3.setPercentHeight(10);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(70);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(30);
        grid.getRowConstraints().addAll(r1, r2, r3);
        grid.getColumnConstraints().addAll(c1, c2);

        // Titles
        HBox title1 = createTitle("Camera feed");
        HBox title2 = createTitle("TF Structure");

        // Images
        BorderPane rgb = new BorderPane();
        rgb.setCenter(new ImageViewPane(imageRGB));

        // FaceDetection checkbox
        CheckBox faceCheck = new CheckBox("Face Detection"); faceCheck.setTextFill(Color.WHITE);
        faceCheck.selectedProperty().addListener((obs, o, n) -> faceDetection = (n==true)?true:false);
        faceCheck.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));
        title1.getChildren().add(faceCheck);
        title1.setSpacing(50);

        // Battery
        battery.setProgress(100);
        battery.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        battery.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));
        battery.setStyle("-fx-accent: green");

        // Tree
        root.setExpanded(true);
        TreeView<String> tree = new TreeView(root);
        tree.getStyleClass().add("myTree");
        tree.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));

        // Insert components to grid
        grid.add(title1, 0, 0);
        grid.add(rgb, 0, 1);
        grid.add(title2, 1, 0);
        grid.add(tree, 1, 1);
        grid.add(battery, 0, 2, 2, 1);

        // Scene
        Scene scene = new Scene(grid, Color.BLACK);
        File f = new File("myTree.css");
        scene.getStylesheets().add("file:///" + f.getAbsolutePath().replace("\\", "/"));
        stage.setScene(scene);
        stage.setTitle("Remote Robot-Control Panel");
        stage.setFullScreen(true);
        stage.show();
    }

    public void displayRGB(Mat mat) { Platform.runLater(() -> setImage(mat, imageRGB)); }

    public void displayBattery(double percentage) { Platform.runLater(() -> battery.setProgress(percentage)); }

    public void displayTF(Map<String, Set<String>> map) {
        // Create Items
        Map<String, TreeItem<String>> lookup = new HashMap<>();
        for (String parent : map.keySet()) {
            TreeItem<String> t = new TreeItem<>(parent);
            t.setExpanded(true);
            lookup.put(parent, t);
            for (String child : map.get(parent))
                if (!lookup.containsKey(child))
                    lookup.put(child, new TreeItem<>(child));
        }
        // Connect them
        for (String parent : map.keySet())
            for (String child : map.get(parent))
                lookup.get(parent).getChildren().add(lookup.get(child));
        for (TreeItem t : lookup.values())
            if (t.getParent() == null) root.getChildren().add(t);
    }

    private void setImage(Mat mat, ImageView imv) {
        MatOfByte byteMat = new MatOfByte();
        Imgcodecs.imencode(".bmp", mat, byteMat);
        imv.setImage(new Image(new ByteArrayInputStream(byteMat.toArray())));
    }

    private HBox createTitle(String title) {
        Text t = new Text(title);
        t.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        t.setFill(Color.WHITE);
        HBox box = new HBox(t);
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return box;
    }

    private class ImageViewPane extends Region {

        private ObjectProperty<ImageView> imageViewProperty = new SimpleObjectProperty<>();

        public ObjectProperty<ImageView> imageViewProperty() {
            return imageViewProperty;
        }

        public ImageView getImageView() {
            return imageViewProperty.get();
        }

        public void setImageView(ImageView imageView) {
            this.imageViewProperty.set(imageView);
        }

        public ImageViewPane() {
            this(new ImageView());
        }

        @Override
        protected void layoutChildren() {
            ImageView imageView = imageViewProperty.get();
            if (imageView != null) {
                imageView.setFitWidth(getWidth());
                imageView.setFitHeight(getHeight());
                layoutInArea(imageView, 0, 0, getWidth(), getHeight(), 0, HPos.CENTER, VPos.CENTER);
            }
            super.layoutChildren();
        }

        public ImageViewPane(ImageView imageView) {
            imageViewProperty.addListener(new ChangeListener<ImageView>() {

                @Override
                public void changed(ObservableValue<? extends ImageView> arg0, ImageView oldIV, ImageView newIV) {
                    if (oldIV != null) {
                        getChildren().remove(oldIV);
                    }
                    if (newIV != null) {
                        getChildren().add(newIV);
                    }
                }
            });
            this.imageViewProperty.set(imageView);
        }
    }
}

