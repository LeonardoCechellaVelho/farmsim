package com.fazenda;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.Set;

public class IndustrialFarmGame extends Application {

    private static final int TILE_SIZE = 12;
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;

    private Image tractorSheet;
    private final double SPRITE_W = 931.0 / 6.0;
    private final double SPRITE_H = 472.0 / 4.0;
    
    // --- TAMANHO REDUZIDO AQUI ---
    private final double TRACTOR_SCALE = 0.4; 

    private double tractorX = 500, tractorY = 500;
    private double angle = 45; 
    private double currentSpeed = 0;

    private final double MAX_SPEED = 1.3;
    private final double ACCELERATION = 0.005;
    private final double FRICTION = 0.004;
    private final double BRAKE_FORCE = 0.025;
    private final double BASE_ROTATION = 1.5;

    private double cameraX, cameraY;
    private Set<KeyCode> activeKeys = new HashSet<>();
    private int[][] farmMap = new int[200][200];

    @Override
    public void start(Stage stage) {
        try {
            tractorSheet = new Image(getClass().getResourceAsStream("/trator.png"));
        } catch (Exception e) {
            System.err.println("Erro: 'trator.png' não encontrado.");
        }

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        scene.setOnKeyPressed(e -> activeKeys.add(e.getCode()));
        scene.setOnKeyReleased(e -> activeKeys.remove(e.getCode()));

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                update();
                render(gc);
            }
        }.start();

        stage.setTitle("Farm Simulator - Trator Reduzido");
        stage.setScene(scene);
        stage.show();
    }

    private void update() {
        if (activeKeys.contains(KeyCode.W)) {
            currentSpeed = Math.min(currentSpeed + ACCELERATION, MAX_SPEED);
        } else if (activeKeys.contains(KeyCode.S)) {
            if (currentSpeed > 0)
                currentSpeed = Math.max(currentSpeed - BRAKE_FORCE, 0);
            else
                currentSpeed = Math.max(currentSpeed - ACCELERATION, -MAX_SPEED / 3);
        } else {
            currentSpeed *= (1 - FRICTION);
            if (Math.abs(currentSpeed) < 0.005)
                currentSpeed = 0;
        }

        if (Math.abs(currentSpeed) > 0.02) {
            double directionFactor = currentSpeed > 0 ? 1 : -1;
            double turnAbility = Math.min(Math.abs(currentSpeed) * 1.5, BASE_ROTATION);
            if (activeKeys.contains(KeyCode.A))
                angle -= turnAbility * directionFactor;
            if (activeKeys.contains(KeyCode.D))
                angle += turnAbility * directionFactor;
        }

        double radians = Math.toRadians(angle);
        tractorX += Math.cos(radians) * currentSpeed;
        tractorY += Math.sin(radians) * currentSpeed;

        int tx = (int) (tractorX / TILE_SIZE);
        int ty = (int) (tractorY / TILE_SIZE);
        if (ty >= 0 && ty < farmMap.length && tx >= 0 && tx < farmMap[0].length) {
            farmMap[ty][tx] = 1;
        }

        cameraX = (tractorX - tractorY) - WIDTH / 2.0;
        cameraY = (tractorX + tractorY) / 2.0 - HEIGHT / 2.0;
    }

    private void render(GraphicsContext gc) {
        gc.setFill(Color.web("#1e3a13"));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.save();
        gc.translate(-cameraX, -cameraY);

        for (int row = 0; row < farmMap.length; row++) {
            for (int col = 0; col < farmMap[row].length; col++) {
                double isoX = (col * TILE_SIZE - row * TILE_SIZE);
                double isoY = (col * TILE_SIZE + row * TILE_SIZE) / 2.0;
                if (isoX > cameraX - 50 && isoX < cameraX + WIDTH + 50 &&
                        isoY > cameraY - 100 && isoY < cameraY + HEIGHT + 100) {
                    drawIsoTile(gc, isoX, isoY, farmMap[row][col] == 1 ? Color.web("#3d2611") : Color.web("#2d4c21"));
                }
            }
        }

        renderIsoTractor(gc, (tractorX - tractorY), (tractorX + tractorY) / 2.0);
        gc.restore();

        renderCompass(gc);
        renderSpeedometer(gc);
    }

    private void drawIsoTile(GraphicsContext gc, double x, double y, Color color) {
        gc.setFill(color);
        gc.setStroke(Color.web("#000000", 0.03));
        double[] xs = { x, x + TILE_SIZE, x, x - TILE_SIZE };
        double[] ys = { y, y + TILE_SIZE / 2.0, y + TILE_SIZE, y + TILE_SIZE / 2.0 };
        gc.fillPolygon(xs, ys, 4);
        gc.strokePolygon(xs, ys, 4);
    }

    private void renderIsoTractor(GraphicsContext gc, double x, double y) {
        if (tractorSheet == null) return;

        double normAngle = (angle % 360 + 360) % 360;
        double frameAngle = (90 - normAngle) % 360;
        if (frameAngle < 0) frameAngle += 360;

        int index = (int) Math.floor((frameAngle + 7.5) / 15.0) % 24;

        int col = index % 6;
        int row = index / 6;

        double drawW = SPRITE_W * TRACTOR_SCALE;
        double drawH = SPRITE_H * TRACTOR_SCALE;

        gc.save();
        gc.translate(x, y);

        // Renderiza o trator
        // Ajustei o offset vertical (-drawH * 0.9) para garantir que o trator 
        // menor fique bem assentado no chão isométrico.
        gc.drawImage(tractorSheet,
                col * SPRITE_W, row * SPRITE_H, SPRITE_W, SPRITE_H,
                -drawW / 2.0, -drawH * 0.9,
                drawW, drawH);
        gc.restore();
    }

    private void renderCompass(GraphicsContext gc) {
        double cx = WIDTH - 80, cy = 80, r = 50;
        gc.setFill(Color.web("#333333", 0.8));
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);
        gc.save();
        gc.translate(cx, cy);
        gc.rotate(angle + 135);
        gc.setFill(Color.RED);
        gc.fillPolygon(new double[] { -6, 0, 6 }, new double[] { 0, -r + 12, 0 }, 3);
        gc.setFill(Color.WHITE);
        gc.fillPolygon(new double[] { -6, 0, 6 }, new double[] { 0, r - 12, 0 }, 3);
        gc.restore();
    }

    private void renderSpeedometer(GraphicsContext gc) {
        double cx = WIDTH - 100, cy = HEIGHT - 80, r = 70;
        gc.setFill(Color.web("#111111", 0.95));
        gc.fillArc(cx - r, cy - r, r * 2, r * 2, 0, 180, ArcType.ROUND);

        String gear = "N";
        if (currentSpeed > 0.01) {
            if (currentSpeed < MAX_SPEED * 0.35) gear = "1";
            else if (currentSpeed < MAX_SPEED * 0.75) gear = "2";
            else gear = "3";
        } else if (currentSpeed < -0.01) gear = "R";

        gc.setFill(Color.web("#222222"));
        gc.fillOval(cx - 20, cy - 20, 40, 40);
        gc.setStroke(Color.ORANGE);
        gc.strokeOval(cx - 20, cy - 20, 40, 40);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 24));
        gc.fillText(gear, cx - 7, cy + 8);

        double speedPercent = Math.abs(currentSpeed) / MAX_SPEED;
        gc.save();
        gc.translate(cx, cy);
        gc.rotate(-180 + (180 * speedPercent));
        gc.setStroke(Color.RED);
        gc.setLineWidth(4);
        gc.strokeLine(20, 0, r - 10, 0);
        gc.restore();

        gc.setFill(Color.CYAN);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        gc.fillText((int) (Math.abs(currentSpeed) * 35) + " km/h", cx - 25, cy + 35);
    }

    public static void main(String[] args) { launch(args); }
}